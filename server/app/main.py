"""Kavir Remote - local server: REST + WebSocket + web dashboard."""
import asyncio, hmac, os, secrets, socket, subprocess, time, uuid
from pathlib import Path
from typing import Optional

import psutil
from fastapi import (Depends, FastAPI, File, HTTPException, Request, UploadFile,
                     WebSocket, WebSocketDisconnect)
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from . import db

VERSION = "1.0.0"
WEB_DIR = Path(__file__).resolve().parent.parent / "web"
LOCAL_TOKEN = secrets.token_urlsafe(32)          # valid only for this server run, only handed to localhost
LOOPBACK = {"127.0.0.1", "::1", "localhost"}
ALLOWED_EXT = {".exe", ".bat", ".cmd", ".lnk"}
MAX_IMG = 10 * 1024 * 1024

app = FastAPI(title="Kavir Remote", version=VERSION)
launched: dict[str, int] = {}                     # app_id -> pid we started
clients: set[WebSocket] = set()
pairing = {"code": "", "expires": 0.0, "fails": 0}
_last_running = None

# ------------------------------------------------------------------ pairing / auth
def new_code():
    pairing.update(code=f"{secrets.randbelow(1_000_000):06d}", expires=time.time() + 600, fails=0)

def _token_from(request_or_ws) -> str:
    auth = request_or_ws.headers.get("authorization", "")
    if auth.lower().startswith("bearer "):
        return auth[7:].strip()
    return request_or_ws.query_params.get("token", "")

def _valid(token: str) -> bool:
    if not token:
        return False
    return hmac.compare_digest(token, LOCAL_TOKEN) or db.device_by_token(token) is not None

def auth(request: Request):
    if not _valid(_token_from(request)):
        raise HTTPException(401, "unauthorized")

def local_only(request: Request):
    host = (request.headers.get("host") or "").rsplit(":", 1)[0].strip("[]")
    if (request.client.host if request.client else "") not in LOOPBACK or host not in LOOPBACK:
        raise HTTPException(403, "این بخش فقط روی خود کامپیوتر در دسترس است")

def lan_ips():
    ips = []
    for addrs in psutil.net_if_addrs().values():
        for a in addrs:
            if a.family == socket.AF_INET and not a.address.startswith(("127.", "169.254.")):
                ips.append(a.address)
    return ips

@app.get("/api/info")
def info():
    return {"app": "kavir-remote", "version": VERSION, "name": socket.gethostname()}

class PairIn(BaseModel):
    code: str
    device_name: str = "Android"

@app.post("/api/pair")
async def pair(body: PairIn):
    if time.time() > pairing["expires"]:
        new_code()
    if not hmac.compare_digest(body.code.strip(), pairing["code"]):
        pairing["fails"] += 1
        if pairing["fails"] >= 5:
            new_code()
        raise HTTPException(403, "کد اشتباه است")
    token = secrets.token_urlsafe(32)
    db.add_device(body.device_name or "Android", token)
    new_code()                                    # one-time code
    await broadcast({"type": "devices"})
    return {"token": token, "name": socket.gethostname()}

@app.get("/api/local-session", dependencies=[Depends(local_only)])
def local_session():
    return {"token": LOCAL_TOKEN}

@app.get("/api/pairing", dependencies=[Depends(local_only), Depends(auth)])
def get_pairing(port: Optional[int] = None):
    if time.time() > pairing["expires"]:
        new_code()
    return {"code": pairing["code"], "expires_in": int(pairing["expires"] - time.time()), "ips": lan_ips()}

@app.post("/api/pairing/refresh", dependencies=[Depends(local_only), Depends(auth)])
def refresh_pairing():
    new_code()
    return get_pairing()

@app.get("/api/devices", dependencies=[Depends(auth)])
def devices():
    return db.list_devices()

@app.delete("/api/devices/{dev_id}", dependencies=[Depends(auth)])
async def revoke(dev_id: str):
    db.delete_device(dev_id)
    await broadcast({"type": "devices"})
    return {"ok": True}

# ------------------------------------------------------------------ apps CRUD
class AppIn(BaseModel):
    name: str
    exe_path: str
    description: str = ""

def clean_exe(path: str) -> str:
    p = path.strip().strip('"')
    if not p or not os.path.isabs(p):
        raise HTTPException(400, "مسیر باید کامل باشد (مثلاً C:\\Games\\game.exe)")
    if Path(p).suffix.lower() not in ALLOWED_EXT:
        raise HTTPException(400, "فقط فایل‌های exe / bat / cmd / lnk مجاز هستند")
    if not Path(p).is_file():
        raise HTTPException(400, "فایلی با این مسیر روی کامپیوتر پیدا نشد")
    return p

def clean_name(name: str) -> str:
    n = name.strip()
    if not n or len(n) > 80:
        raise HTTPException(400, "نام نامعتبر است")
    return n

@app.get("/api/apps", dependencies=[Depends(auth)])
def apps_list():
    return db.list_apps()

@app.post("/api/apps", dependencies=[Depends(auth)])
async def apps_create(body: AppIn):
    a = db.create_app(clean_name(body.name), clean_exe(body.exe_path), body.description.strip()[:2000])
    await broadcast({"type": "apps"})
    return a

@app.put("/api/apps/{app_id}", dependencies=[Depends(auth)])
async def apps_update(app_id: str, body: AppIn):
    if not db.get_app(app_id):
        raise HTTPException(404, "پیدا نشد")
    a = db.update_app(app_id, name=clean_name(body.name), exe_path=clean_exe(body.exe_path),
                      description=body.description.strip()[:2000])
    await broadcast({"type": "apps"})
    return a

@app.delete("/api/apps/{app_id}", dependencies=[Depends(auth)])
async def apps_delete(app_id: str, confirm: bool = False):
    a = db.get_app(app_id)
    if not a:
        raise HTTPException(404, "پیدا نشد")
    if not confirm:
        raise HTTPException(400, "برای حذف تأیید لازم است (confirm=true)")
    db.backup()                                   # never lose data silently
    db.delete_app(app_id)
    for f in (a["cover"], a["background"]):
        if f:
            (db.IMG_DIR / f).unlink(missing_ok=True)
    await broadcast({"type": "apps"})
    return {"ok": True}

# ------------------------------------------------------------------ images
def sniff(data: bytes) -> Optional[str]:
    if data.startswith(b"\x89PNG"): return ".png"
    if data.startswith(b"\xff\xd8"): return ".jpg"
    if data.startswith(b"GIF8"): return ".gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP": return ".webp"
    return None

@app.post("/api/apps/{app_id}/image/{kind}", dependencies=[Depends(auth)])
async def upload_image(app_id: str, kind: str, file: UploadFile = File(...)):
    if kind not in ("cover", "background"):
        raise HTTPException(400, "kind نامعتبر")
    a = db.get_app(app_id)
    if not a:
        raise HTTPException(404, "پیدا نشد")
    data = await file.read(MAX_IMG + 1)
    if len(data) > MAX_IMG:
        raise HTTPException(413, "حجم تصویر بیش از ۱۰ مگابایت است")
    ext = sniff(data)
    if not ext:
        raise HTTPException(400, "فقط png / jpg / webp / gif مجاز است")
    name = f"{uuid.uuid4().hex}{ext}"
    (db.IMG_DIR / name).write_bytes(data)
    if a[kind]:
        (db.IMG_DIR / a[kind]).unlink(missing_ok=True)
    res = db.update_app(app_id, **{kind: name})
    await broadcast({"type": "apps"})
    return res

@app.delete("/api/apps/{app_id}/image/{kind}", dependencies=[Depends(auth)])
async def remove_image(app_id: str, kind: str):
    a = db.get_app(app_id)
    if not a or kind not in ("cover", "background"):
        raise HTTPException(404, "پیدا نشد")
    if a[kind]:
        (db.IMG_DIR / a[kind]).unlink(missing_ok=True)
    res = db.update_app(app_id, **{kind: ""})
    await broadcast({"type": "apps"})
    return res

@app.get("/api/images/{filename}", dependencies=[Depends(auth)])
def get_image(filename: str):
    p = db.IMG_DIR / Path(filename).name
    if not p.is_file():
        raise HTTPException(404)
    return FileResponse(p, headers={"Cache-Control": "private, max-age=86400"})

# ------------------------------------------------------------------ launch / running / close
def norm(p: str) -> str:
    return os.path.normcase(os.path.abspath(p))

def running_list():
    apps = db.list_apps()
    by_exe = {norm(a["exe_path"]): a for a in apps}
    found: dict[int, dict] = {}
    for pr in psutil.process_iter(["pid", "exe", "status", "create_time"]):
        try:
            e, st = pr.info["exe"], pr.info["status"]
            if st == psutil.STATUS_ZOMBIE or not e:
                continue
            a = by_exe.get(norm(e))
            if a:
                found[pr.info["pid"]] = dict(app_id=a["id"], name=a["name"], pid=pr.info["pid"],
                                             status="running", started=pr.info["create_time"])
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    ids = {a["id"]: a for a in apps}
    for app_id, pid in list(launched.items()):    # include children of what we started (launcher -> game)
        try:
            root = psutil.Process(pid)
            if root.status() == psutil.STATUS_ZOMBIE:
                launched.pop(app_id, None); continue
            for p in [root] + root.children(recursive=True):
                if p.pid not in found and app_id in ids and p.status() != psutil.STATUS_ZOMBIE:
                    found[p.pid] = dict(app_id=app_id, name=ids[app_id]["name"], pid=p.pid,
                                        status="running", started=p.create_time())
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            launched.pop(app_id, None)
    return sorted(found.values(), key=lambda r: (r["name"].lower(), r["pid"]))

@app.post("/api/apps/{app_id}/launch", dependencies=[Depends(auth)])
async def launch(app_id: str):
    a = db.get_app(app_id)                        # only registered apps can be launched
    if not a:
        raise HTTPException(404, "پیدا نشد")
    p = Path(a["exe_path"])
    if not p.is_file() or p.suffix.lower() not in ALLOWED_EXT:
        raise HTTPException(400, "فایل اجرایی روی کامپیوتر پیدا نشد")
    try:
        if os.name == "nt":
            if p.suffix.lower() == ".lnk":
                os.startfile(str(p))              # noqa
            else:
                flags = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
                launched[app_id] = subprocess.Popen([str(p)], cwd=str(p.parent), creationflags=flags,
                                                    close_fds=True).pid
        else:
            launched[app_id] = subprocess.Popen([str(p)], cwd=str(p.parent), start_new_session=True,
                                                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                                stderr=subprocess.DEVNULL).pid
    except OSError as e:
        raise HTTPException(500, f"اجرا ناموفق بود: {e}")
    await asyncio.sleep(0.6)
    await push_running(force=True)
    return {"ok": True}

@app.get("/api/running", dependencies=[Depends(auth)])
def running():
    return running_list()

class CloseIn(BaseModel):
    confirm: bool = False

@app.post("/api/running/{pid}/close", dependencies=[Depends(auth)])
async def close_process(pid: int, body: CloseIn):
    if not body.confirm:
        raise HTTPException(400, "برای بستن برنامه تأیید لازم است")
    if pid not in {r["pid"] for r in running_list()}:   # only processes belonging to registered apps
        raise HTTPException(404, "این پردازش جزو برنامه‌های ثبت‌شده نیست")
    try:
        proc = psutil.Process(pid)
        procs = [proc] + proc.children(recursive=True)
        for p in procs:
            try: p.terminate()
            except psutil.NoSuchProcess: pass
        _, alive = await asyncio.to_thread(psutil.wait_procs, procs, 5)
        for p in alive:
            try: p.kill()
            except psutil.NoSuchProcess: pass
    except psutil.NoSuchProcess:
        pass
    except psutil.AccessDenied:
        raise HTTPException(403, "دسترسی برای بستن این برنامه وجود ندارد (سرور را به‌صورت Administrator اجرا کنید)")
    await asyncio.sleep(0.3)
    await push_running(force=True)
    return {"ok": True}

# ------------------------------------------------------------------ realtime sync
async def broadcast(msg: dict):
    for ws in list(clients):
        try:
            await ws.send_json(msg)
        except Exception:
            clients.discard(ws)

async def push_running(force=False):
    global _last_running
    cur = await asyncio.to_thread(running_list)
    sig = [(r["pid"], r["app_id"]) for r in cur]
    if force or sig != _last_running:
        _last_running = sig
        await broadcast({"type": "running", "data": cur})

@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    if not _valid(_token_from(ws)):
        await ws.close(code=4401); return
    await ws.accept()
    clients.add(ws)
    try:
        await ws.send_json({"type": "running", "data": await asyncio.to_thread(running_list)})
        while True:
            await ws.receive_text()               # keep-alive pings from clients
    except WebSocketDisconnect:
        pass
    finally:
        clients.discard(ws)

async def _watcher():
    n = 0
    while True:
        await asyncio.sleep(2.5)
        try:
            await push_running()
            n += 1
            if n % 8640 == 0:                     # ~ every 6h
                db.backup()
        except Exception as e:                    # never let the watcher die
            print("watcher error:", e)

@app.on_event("startup")
async def startup():
    db.init()
    try: db.backup()
    except Exception as e: print("backup failed:", e)
    if not pairing["code"]:
        new_code()
    asyncio.create_task(_watcher())

@app.get("/")
def index():
    return FileResponse(WEB_DIR / "index.html", headers={"Cache-Control": "no-store"})

app.mount("/", StaticFiles(directory=WEB_DIR), name="web")
