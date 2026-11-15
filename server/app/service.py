"""Windows integration: autostart (Task Scheduler), single-instance lock, crash supervisor, dependency bootstrap.
Imports only the standard library so it works before the third-party requirements are installed."""
import importlib.util, os, subprocess, sys, tempfile, time
from pathlib import Path
from typing import Optional
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent
TASK_NAME = "KavirRemote"
EXIT_ALREADY_RUNNING = 3
REQUIRED = {"fastapi": "fastapi", "uvicorn": "uvicorn", "wsproto": "wsproto", "psutil": "psutil", "multipart": "python-multipart"}
NO_WINDOW = 0x08000000 if os.name == "nt" else 0

def _data_dir() -> Path:
    from .db import DATA_DIR
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    return DATA_DIR

def log(msg: str):
    line = time.strftime("%Y-%m-%d %H:%M:%S ") + msg
    try:
        with open(_data_dir() / "server.log", "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except OSError:
        pass
    if sys.stdout:
        print(line)

# ------------------------------------------------------------------ dependencies (installed Python, no venv)
def ensure_deps():
    missing = [m for m in REQUIRED if importlib.util.find_spec(m) is None]
    if not missing:
        return
    log("Installing missing dependencies: " + ", ".join(REQUIRED[m] for m in missing))
    r = subprocess.run([sys.executable, "-m", "pip", "install", "-r", str(ROOT / "requirements.txt")],
                       capture_output=True, text=True, creationflags=NO_WINDOW)
    if r.returncode != 0:
        log("pip failed:\n" + (r.stderr or r.stdout)[-1500:])
        sys.exit("Dependency installation failed. Run install_dependencies.bat and check your internet connection.")
    importlib.invalidate_caches()

# ------------------------------------------------------------------ single instance
_held = []

def acquire_lock(name: str) -> bool:
    """Non-blocking exclusive lock on a file; released automatically when the process dies (even on crash)."""
    f = open(_data_dir() / f"{name}.lock", "a+")
    try:
        if os.name == "nt":
            import msvcrt
            f.seek(0)
            msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        f.close()
        return False
    _held.append(f)
    return True

# ------------------------------------------------------------------ crash supervisor
def supervise(cmd: list, max_restarts: Optional[int] = None) -> int:
    """Runs the server as a child and restarts it if it crashes (exit code other than 0 / 'already running')."""
    if not acquire_lock("supervisor"):
        log("Supervisor already running - exiting")
        return 0
    fails = restarts = 0
    while True:
        started = time.time()
        try:
            rc = subprocess.call(cmd, cwd=str(ROOT), creationflags=NO_WINDOW)
        except KeyboardInterrupt:
            return 0
        except OSError as e:
            rc = -1
            log(f"Could not start server: {e}")
        if rc in (0, EXIT_ALREADY_RUNNING):
            return 0
        fails = 0 if time.time() - started > 60 else fails + 1    # healthy for a minute -> reset back-off
        restarts += 1
        log(f"Server exited with code {rc}; restart #{restarts} in {min(30, 2 * fails + 1)}s")
        if max_restarts is not None and restarts > max_restarts:
            return rc
        time.sleep(0 if max_restarts is not None else min(30, 2 * fails + 1))

# ------------------------------------------------------------------ Windows autostart (Task Scheduler)
def _pythonw() -> str:
    w = Path(sys.executable).with_name("pythonw.exe")
    return str(w if w.exists() else sys.executable)

def task_xml(port: int, user: str = "", admin: bool = True) -> str:
    args = f'"{ROOT / "run.py"}" --supervise --no-browser --port {port}'
    uid = f"<UserId>{escape(user)}</UserId>" if user else ""
    return f"""<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo><Description>Kavir Remote server (starts at logon, runs elevated, restarts after a crash)</Description></RegistrationInfo>
  <Triggers><LogonTrigger><Enabled>true</Enabled>{uid}</LogonTrigger></Triggers>
  <Principals><Principal id="Author">{uid}<LogonType>InteractiveToken</LogonType><RunLevel>{"HighestAvailable" if admin else "LeastPrivilege"}</RunLevel></Principal></Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <StartWhenAvailable>true</StartWhenAvailable>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <RestartOnFailure><Interval>PT1M</Interval><Count>999</Count></RestartOnFailure>
  </Settings>
  <Actions Context="Author"><Exec>
    <Command>{escape(_pythonw())}</Command>
    <Arguments>{escape(args)}</Arguments>
    <WorkingDirectory>{escape(str(ROOT))}</WorkingDirectory>
  </Exec></Actions>
</Task>
"""

def _schtasks(*a):
    return subprocess.run(["schtasks", *a], capture_output=True, text=True, creationflags=NO_WINDOW)

def _legacy_startup_file() -> Optional[Path]:
    appdata = os.environ.get("APPDATA")
    return Path(appdata) / "Microsoft/Windows/Start Menu/Programs/Startup/KavirRemote.bat" if appdata else None

def is_admin() -> bool:
    if os.name != "nt":
        return False
    try:
        import ctypes
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False

def install_autostart(port: int, admin: bool = True) -> str:
    if os.name != "nt":
        raise SystemExit("Autostart is only supported on Windows")
    if admin and not is_admin():
        raise SystemExit("Administrator permission is required to install an elevated autostart task.\n"
                         "Run install_autostart.bat (it asks for permission), or use --no-admin.")
    user = os.environ.get("USERDOMAIN", "") + "\\" + os.environ.get("USERNAME", "")
    with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False, encoding="utf-16") as f:
        f.write(task_xml(port, user if len(user) > 1 else "", admin))
        xml_path = f.name
    try:
        r = _schtasks("/Create", "/TN", TASK_NAME, "/XML", xml_path, "/F")
    finally:
        os.unlink(xml_path)
    if r.returncode != 0:
        raise SystemExit("Could not create the scheduled task:\n" + (r.stderr or r.stdout))
    old = _legacy_startup_file()                       # migrate from the old Startup-folder method
    if old and old.exists():
        old.unlink()
    _schtasks("/Run", "/TN", TASK_NAME)                # start now; the single-instance lock prevents duplicates
    mode = "as administrator" if admin else "with normal permissions"
    return f"Task '{TASK_NAME}' installed. The server now starts {mode} at every logon and restarts after a crash. Restart Windows to apply."

def remove_autostart() -> str:
    if os.name != "nt":
        raise SystemExit("Autostart is only supported on Windows")
    _schtasks("/End", "/TN", TASK_NAME)
    r = _schtasks("/Delete", "/TN", TASK_NAME, "/F")
    old = _legacy_startup_file()
    if old and old.exists():
        old.unlink()
    return "Autostart removed" if r.returncode == 0 else "Autostart was not installed"

def autostart_status() -> str:
    if os.name != "nt":
        return "not supported on this OS"
    r = _schtasks("/Query", "/TN", TASK_NAME)
    return "installed" if r.returncode == 0 else "not installed"
