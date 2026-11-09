"""Start Kavir Remote server.  python run.py [--port 8765] [--install-autostart | --remove-autostart] [--no-browser]"""
import argparse, os, sys, webbrowser
from pathlib import Path

def startup_file():
    return Path(os.environ["APPDATA"]) / "Microsoft/Windows/Start Menu/Programs/Startup/KavirRemote.bat"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=int(os.environ.get("KAVIR_PORT", 8765)))
    ap.add_argument("--no-browser", action="store_true")
    ap.add_argument("--install-autostart", action="store_true")
    ap.add_argument("--remove-autostart", action="store_true")
    a = ap.parse_args()
    here = Path(__file__).resolve().parent
    if a.install_autostart or a.remove_autostart:
        if os.name != "nt":
            sys.exit("Autostart is only supported on Windows")
        f = startup_file()
        if a.remove_autostart:
            f.unlink(missing_ok=True); print("Autostart removed"); return
        f.write_text(f'@echo off\ncd /d "{here}"\nstart "" /min "{sys.executable}" run.py --no-browser --port {a.port}\n')
        print("Autostart installed:", f); return

    import uvicorn
    from app import main as m, db
    db.init(); m.new_code(); m.HTTP_PORT = a.port
    print("\n" + "=" * 56 + "\n  Kavir Remote Server\n" + "=" * 56)
    for ip in m.lan_ips():
        print(f"  Phone address : {ip}:{a.port}")
    print(f"  Web panel     : http://localhost:{a.port}")
    print("  Phone         : just open the app - it finds this PC by itself")
    print(f"  Manual code   : {m.pairing['code']}   (only if auto-discovery fails)")
    print("=" * 56 + "\n")
    if not a.no_browser:
        webbrowser.open(f"http://localhost:{a.port}")
    uvicorn.run(m.app, host="0.0.0.0", port=a.port, log_level="warning")

if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    main()
