"""Best-effort: extract the program's own icon on Windows (PowerShell + System.Drawing). Never raises."""
import os, subprocess, uuid

from . import db

_PS = ("Add-Type -AssemblyName System.Drawing; "
       "$i=[System.Drawing.Icon]::ExtractAssociatedIcon($env:KR_EXE); "
       "$i.ToBitmap().Save($env:KR_OUT,[System.Drawing.Imaging.ImageFormat]::Png)")

def extract_icon(exe_path: str):
    """Returns the saved image filename (inside the images dir) or None."""
    if os.name != "nt":
        return None
    name = f"{uuid.uuid4().hex}.png"
    out = db.IMG_DIR / name
    try:
        subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", _PS],
                       env={**os.environ, "KR_EXE": exe_path, "KR_OUT": str(out)},   # paths passed via env, never via the command line
                       timeout=15, capture_output=True, creationflags=0x08000000)    # CREATE_NO_WINDOW
        if out.is_file() and out.stat().st_size > 0:
            return name
    except Exception:
        pass
    out.unlink(missing_ok=True)
    return None
