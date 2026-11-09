"""SQLite storage + automatic backups. Single shared DB for web panel and Android app."""
import os, sqlite3, threading, time, uuid, hashlib
from pathlib import Path

DATA_DIR = Path(os.environ.get("KAVIR_DATA") or (Path(os.environ.get("APPDATA") or Path.home()) / "KavirRemote"))
IMG_DIR = DATA_DIR / "images"
BACKUP_DIR = DATA_DIR / "backups"
DB_PATH = DATA_DIR / "launcher.db"
_lock = threading.RLock()

SCHEMA = """
CREATE TABLE IF NOT EXISTS apps(
  id TEXT PRIMARY KEY, name TEXT NOT NULL, exe_path TEXT NOT NULL,
  cover TEXT DEFAULT '', background TEXT DEFAULT '', description TEXT DEFAULT '',
  created_at REAL, updated_at REAL);
CREATE TABLE IF NOT EXISTS devices(
  id TEXT PRIMARY KEY, name TEXT, token_hash TEXT UNIQUE, created_at REAL, last_seen REAL);
"""

def init():
    for d in (DATA_DIR, IMG_DIR, BACKUP_DIR):
        d.mkdir(parents=True, exist_ok=True)
    with _lock, _conn() as c:
        c.executescript(SCHEMA)
        _migrate(c)

def _migrate(c):
    """Additive migrations only - existing apps/devices are never touched or removed."""
    cols = {r["name"] for r in c.execute("PRAGMA table_info(apps)")}
    if "icon" not in cols:
        c.execute("ALTER TABLE apps ADD COLUMN icon TEXT DEFAULT ''")
    if "position" not in cols:
        c.execute("ALTER TABLE apps ADD COLUMN position INTEGER DEFAULT 0")
        for i, r in enumerate(c.execute("SELECT id FROM apps ORDER BY name COLLATE NOCASE").fetchall(), 1):
            c.execute("UPDATE apps SET position=? WHERE id=?", (i, r["id"]))
    c.commit()

def _conn():
    c = sqlite3.connect(DB_PATH, check_same_thread=False)
    c.row_factory = sqlite3.Row
    return c

def _q(sql, args=(), one=False, write=False):
    with _lock:
        c = _conn()
        try:
            cur = c.execute(sql, args)
            if write:
                c.commit()
                return cur.rowcount
            rows = [dict(r) for r in cur.fetchall()]
            return (rows[0] if rows else None) if one else rows
        finally:
            c.close()

def backup(keep=10):
    with _lock:
        dst = BACKUP_DIR / time.strftime("launcher-%Y%m%d-%H%M%S.db")
        src, out = _conn(), sqlite3.connect(dst)
        try:
            src.backup(out)
        finally:
            out.close(); src.close()
        files = sorted(BACKUP_DIR.glob("launcher-*.db"))
        for old in files[:-keep]:
            old.unlink(missing_ok=True)
        return dst.name

# ---- apps
def list_apps():
    return _q("SELECT * FROM apps ORDER BY position, name COLLATE NOCASE")

def get_app(app_id):
    return _q("SELECT * FROM apps WHERE id=?", (app_id,), one=True)

def create_app(name, exe_path, description=""):
    app_id, now = uuid.uuid4().hex[:12], time.time()
    _q("INSERT INTO apps(id,name,exe_path,description,created_at,updated_at,position) "
       "VALUES(?,?,?,?,?,?,(SELECT COALESCE(MAX(position),0)+1 FROM apps))",
       (app_id, name, exe_path, description, now, now), write=True)
    return get_app(app_id)

def update_app(app_id, **fields):
    allowed = {k: v for k, v in fields.items() if k in ("name", "exe_path", "description", "cover", "background", "icon")}
    if not allowed:
        return get_app(app_id)
    sets = ",".join(f"{k}=?" for k in allowed)
    _q(f"UPDATE apps SET {sets}, updated_at=? WHERE id=?", (*allowed.values(), time.time(), app_id), write=True)
    return get_app(app_id)

def reorder_apps(ids):
    """Persist a new display order. Unknown ids are ignored; apps missing from `ids` keep their relative order at the end."""
    with _lock:
        known = [a["id"] for a in list_apps()]
        order = [i for i in dict.fromkeys(ids) if i in known] + [i for i in known if i not in ids]
        c = _conn()
        try:
            for pos, app_id in enumerate(order, 1):
                c.execute("UPDATE apps SET position=? WHERE id=?", (pos, app_id))
            c.commit()
        finally:
            c.close()

def delete_app(app_id):
    return _q("DELETE FROM apps WHERE id=?", (app_id,), write=True)

# ---- devices (only token hashes are stored)
def _h(token): return hashlib.sha256(token.encode()).hexdigest()

def add_device(name, token):
    dev_id = uuid.uuid4().hex[:10]
    _q("INSERT INTO devices(id,name,token_hash,created_at,last_seen) VALUES(?,?,?,?,?)",
       (dev_id, name[:60], _h(token), time.time(), time.time()), write=True)
    return dev_id

def device_by_token(token):
    d = _q("SELECT * FROM devices WHERE token_hash=?", (_h(token),), one=True)
    if d and time.time() - (d["last_seen"] or 0) > 60:
        _q("UPDATE devices SET last_seen=? WHERE id=?", (time.time(), d["id"]), write=True)
    return d

def list_devices():
    return _q("SELECT id,name,created_at,last_seen FROM devices ORDER BY created_at DESC")

def delete_device(dev_id):
    return _q("DELETE FROM devices WHERE id=?", (dev_id,), write=True)
