"""Start Kavir Remote server with the installed Python (no virtualenv needed).

  python run.py                      run now (opens the web panel)
  python run.py --port 9000
  python run.py --install-autostart  start at Windows logon as administrator via Task Scheduler (+ restart after crash)
                                     (add --no-admin for normal permissions)
  python run.py --remove-autostart | --autostart-status
"""
import argparse, os, sys, webbrowser
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from app import db, service  # noqa: E402  (standard library only; safe before dependencies are installed)

def parse():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=int(os.environ.get("KAVIR_PORT", 8765)))
    ap.add_argument("--no-browser", action="store_true")
    ap.add_argument("--supervise", action="store_true", help="restart the server automatically if it crashes")
    ap.add_argument("--install-autostart", action="store_true")
    ap.add_argument("--no-admin", action="store_true", help="install the autostart task without administrator rights")
    ap.add_argument("--remove-autostart", action="store_true")
    ap.add_argument("--autostart-status", action="store_true")
    ap.add_argument("--diagnose", action="store_true", help="print a connectivity report (firewall, addresses, network profile)")
    ap.add_argument("--print-task-xml", action="store_true", help=argparse.SUPPRESS)
    return ap.parse_args()

def attach_log():
    """pythonw.exe has no console: send output to a log file so nothing crashes on print()."""
    if sys.stdout is None or sys.stderr is None:
        db.DATA_DIR.mkdir(parents=True, exist_ok=True)
        f = open(db.DATA_DIR / "server.log", "a", buffering=1, encoding="utf-8")
        sys.stdout = sys.stdout or f
        sys.stderr = sys.stderr or f

def main():
    a = parse()
    attach_log()
    if a.print_task_xml:
        print(service.task_xml(a.port, admin=not a.no_admin)); return 0
    if a.install_autostart:
        print(service.install_autostart(a.port, admin=not a.no_admin)); return 0
    if a.remove_autostart:
        print(service.remove_autostart()); return 0
    if a.diagnose:
        print(service.diagnose(a.port)); return 0
    if a.autostart_status:
        print("Autostart:", service.autostart_status()); return 0

    service.ensure_deps()
    if a.supervise:
        return service.supervise([sys.executable, str(HERE / "run.py"), "--no-browser", "--port", str(a.port)])

    if not service.acquire_lock("server"):
        print("Kavir Remote is already running.")
        if not a.no_browser:
            webbrowser.open(f"http://localhost:{a.port}")
        return service.EXIT_ALREADY_RUNNING

    import uvicorn
    from app import main as m
    db.init(); m.new_code(); m.HTTP_PORT = a.port
    service.ensure_firewall(a.port, m.DISCOVERY_PORT)
    print("\n" + "=" * 56 + "\n  Kavir Remote Server\n" + "=" * 56)
    for ip in m.lan_ips():
        print(f"  Phone address : {ip}:{a.port}")
    print(f"  Web panel     : http://localhost:{a.port}")
    print("  Phone         : just open the app - it finds this PC by itself")
    print(f"  Manual code   : {m.pairing['code']}   (only if auto-discovery fails)")
    print("=" * 56 + "\n")
    if not a.no_browser:
        webbrowser.open(f"http://localhost:{a.port}")
    # wsproto = pure-Python WebSocket library: no compiled packages, so it installs on any Python version
    uvicorn.run(m.app, host="0.0.0.0", port=a.port, log_level="warning", ws="wsproto")
    return 0

if __name__ == "__main__":
    sys.exit(main())
