const $ = (s, r = document) => r.querySelector(s);
let token = null, apps = [], running = [], editing = null, ws = null;

const toast = (msg, kind = "ok") => {
  const t = document.createElement("div"); t.className = "t " + kind; t.textContent = msg;
  $("#toast").append(t); setTimeout(() => t.remove(), 3500);
};

async function api(path, opts = {}) {
  const h = { Authorization: "Bearer " + token, ...(opts.headers || {}) };
  if (opts.json) { h["Content-Type"] = "application/json"; opts.body = JSON.stringify(opts.json); }
  const r = await fetch(path, { ...opts, headers: h });
  if (r.status === 401) { localStorage.removeItem("kr_token"); location.reload(); }
  if (!r.ok) {
    let m = r.statusText; try { const j = await r.json(); m = typeof j.detail === "string" ? j.detail : JSON.stringify(j.detail); } catch {}
    throw new Error(m);
  }
  return r.json();
}
const img = f => f ? `/api/images/${f}?token=${encodeURIComponent(token)}` : "";
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

// ---------- tabs
function show(tab) {
  document.querySelectorAll(".tab").forEach(t => t.classList.toggle("active", t.id === tab));
  document.querySelectorAll("nav button").forEach(b => b.classList.toggle("active", b.dataset.tab === tab));
  if (tab === "connect") loadPairing();
}
document.querySelectorAll("nav button").forEach(b => b.onclick = () => show(b.dataset.tab));

// ---------- library
function renderGrid() {
  const q = $("#search").value.trim().toLowerCase();
  const live = new Set(running.map(r => r.app_id));
  const list = apps.filter(a => a.name.toLowerCase().includes(q));
  $("#grid").innerHTML = list.length ? list.map(a => `
    <div class="game" data-id="${a.id}" style="${a.cover ? `background-image:url('${img(a.cover)}')` : ""}">
      ${a.cover ? "" : '<div class="ph">🎮</div>'}<div class="shade"></div>
      ${live.has(a.id) ? '<span class="live">● در حال اجرا</span>' : ""}
      <div class="tools"><button data-edit="${a.id}">✎</button><button data-del="${a.id}">🗑</button></div>
      <div class="info"><h3>${esc(a.name)}</h3><p>${esc(a.description)}</p></div>
    </div>`).join("") : `<p style="color:var(--mute)">${q ? "نتیجه‌ای پیدا نشد." : "هنوز چیزی اضافه نشده. روی «افزودن» بزنید."}</p>`;
}
$("#grid").onclick = async e => {
  const edit = e.target.closest("[data-edit]"), del = e.target.closest("[data-del]"), card = e.target.closest(".game");
  if (edit) return openForm(apps.find(a => a.id === edit.dataset.edit));
  if (del) {
    const a = apps.find(x => x.id === del.dataset.del);
    if (!confirm(`«${a.name}» از لیست حذف شود؟ (فایل اصلی روی کامپیوتر پاک نمی‌شود)`)) return;
    try { await api(`/api/apps/${a.id}?confirm=true`, { method: "DELETE" }); toast("حذف شد"); } catch (er) { toast(er.message, "err"); }
    return;
  }
  if (card) {
    try { toast("در حال اجرا…"); await api(`/api/apps/${card.dataset.id}/launch`, { method: "POST" }); toast("اجرا شد", "ok"); }
    catch (er) { toast(er.message, "err"); }
  }
};
$("#search").oninput = renderGrid;

// ---------- add / edit
function openForm(a) {
  editing = a || null; const f = $("#form");
  $("#mTitle").textContent = a ? "ویرایش" : "افزودن بازی / برنامه";
  f.name.value = a?.name || ""; f.exe_path.value = a?.exe_path || ""; f.description.value = a?.description || "";
  f.cover.value = ""; f.background.value = "";
  $("#modal").classList.remove("hidden");
}
$("#addBtn").onclick = () => openForm(null);
$("#cancel").onclick = () => $("#modal").classList.add("hidden");
$("#form").onsubmit = async e => {
  e.preventDefault(); const f = e.target;
  const body = { name: f.name.value, exe_path: f.exe_path.value, description: f.description.value };
  try {
    let a = editing ? await api(`/api/apps/${editing.id}`, { method: "PUT", json: body })
                    : await api("/api/apps", { method: "POST", json: body });
    for (const kind of ["cover", "background"]) {
      if (f[kind].files[0]) { const fd = new FormData(); fd.append("file", f[kind].files[0]);
        a = await api(`/api/apps/${a.id}/image/${kind}`, { method: "POST", body: fd }); }
    }
    $("#modal").classList.add("hidden"); toast("ذخیره شد");
  } catch (er) { toast(er.message, "err"); }
};

// ---------- running
function renderRunning() {
  $("#runCount").textContent = running.length;
  $("#runList").innerHTML = running.length ? running.map(r => `
    <div class="row"><span class="dot on"></span>
      <div class="grow"><b>${esc(r.name)}</b><br><small>PID ${r.pid} · ${r.status}</small></div>
      <button class="danger" data-kill="${r.pid}" data-name="${esc(r.name)}">بستن</button></div>`).join("")
    : '<p style="color:var(--mute)">برنامه‌ای در حال اجرا نیست.</p>';
  renderGrid();
}
$("#runList").onclick = async e => {
  const b = e.target.closest("[data-kill]"); if (!b) return;
  if (!confirm(`«${b.dataset.name}» (PID ${b.dataset.kill}) بسته شود؟`)) return;
  try { await api(`/api/running/${b.dataset.kill}/close`, { method: "POST", json: { confirm: true } }); toast("بسته شد"); }
  catch (er) { toast(er.message, "err"); }
};
$("#refreshRun").onclick = async () => { running = await api("/api/running"); renderRunning(); };

// ---------- pairing + devices
async function loadPairing() {
  try {
    const p = await api("/api/pairing");
    const port = location.port || "80";
    $("#pairBox").innerHTML = `<p>در اپ اندروید این اطلاعات را وارد کنید:</p>
      <div class="code">${p.code}</div>
      ${p.ips.map(ip => `<div class="addr">${ip}:${port}</div>`).join("") || '<div class="addr">آدرس شبکه پیدا نشد (به Wi-Fi وصل شوید)</div>'}
      <p><small>کد یک‌بار مصرف است و ${Math.ceil(p.expires_in / 60)} دقیقه اعتبار دارد.</small></p>
      <button id="newCode">کد جدید</button>`;
    $("#newCode").onclick = async () => { await api("/api/pairing/refresh", { method: "POST" }); loadPairing(); };
  } catch (er) { $("#pairBox").innerHTML = `<p>${esc(er.message)}</p>`; }
  loadDevices();
}
async function loadDevices() {
  const d = await api("/api/devices");
  $("#devList").innerHTML = d.length ? d.map(x => `<div class="row"><div class="grow"><b>${esc(x.name)}</b><br>
    <small>آخرین اتصال: ${new Date(x.last_seen * 1000).toLocaleString("fa-IR")}</small></div>
    <button class="danger" data-rev="${x.id}">قطع دسترسی</button></div>`).join("")
    : '<p style="color:var(--mute)">هنوز دستگاهی متصل نشده.</p>';
}
$("#devList").onclick = async e => {
  const b = e.target.closest("[data-rev]"); if (!b || !confirm("دسترسی این دستگاه قطع شود؟")) return;
  await api(`/api/devices/${b.dataset.rev}`, { method: "DELETE" }); loadDevices();
};

// ---------- realtime
function connectWS() {
  ws = new WebSocket(`${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws?token=${encodeURIComponent(token)}`);
  ws.onopen = () => $("#ws").classList.add("on");
  ws.onclose = () => { $("#ws").classList.remove("on"); setTimeout(connectWS, 2000); };
  ws.onmessage = async ev => {
    const m = JSON.parse(ev.data);
    if (m.type === "running") { running = m.data; renderRunning(); }
    if (m.type === "apps") { apps = await api("/api/apps"); renderGrid(); }
    if (m.type === "devices" && $("#connect").classList.contains("active")) loadDevices();
  };
  setInterval(() => ws.readyState === 1 && ws.send("ping"), 25000);
}

// ---------- boot
async function boot() {
  try { token = (await (await fetch("/api/local-session")).json()).token; } catch {}
  if (!token || !(token.length > 10)) token = localStorage.getItem("kr_token");
  if (!token) { document.querySelector("header nav").style.display = "none"; show("login"); return; }
  try { apps = await api("/api/apps"); running = await api("/api/running"); } catch { return; }
  renderGrid(); renderRunning(); connectWS();
}
$("#loginBtn").onclick = async () => {
  try {
    const r = await fetch("/api/pair", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code: $("#loginCode").value, device_name: "Web (" + navigator.platform + ")" }) });
    if (!r.ok) throw new Error((await r.json()).detail);
    localStorage.setItem("kr_token", (await r.json()).token); location.reload();
  } catch (e) { toast(e.message, "err"); }
};
boot();
