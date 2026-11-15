package com.kavir.remote

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class MainVM(private val app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val api = Api(store)

    var paired by mutableStateOf(store.token.isNotEmpty())
    var online by mutableStateOf(false)
    var pcName by mutableStateOf(store.pcName)
    var apps by mutableStateOf(listOf<AppItem>())
    var running by mutableStateOf(listOf<RunItem>())
    var busy by mutableStateOf(false)

    // personalization
    var appName by mutableStateOf(store.appName.ifBlank { "Kavir Remote" })
    var accentIdx by mutableStateOf(store.accent.coerceIn(0, Accents.size - 1))
    var bgStyle by mutableStateOf(store.bgStyle.coerceIn(0, 2))
    var dim by mutableStateOf(store.dim.coerceIn(0.2f, 0.9f))

    fun saveName(n: String) {
        val v = n.trim().take(30).ifBlank { "Kavir Remote" }
        appName = v; store.appName = v
        viewModelScope.launch { events.emit("نام برنامه ذخیره شد" to false) }
    }
    fun setAccent(i: Int) { accentIdx = i; store.accent = i }
    fun setBg(i: Int) { bgStyle = i; store.bgStyle = i }
    fun persistDim() { store.dim = dim }

    /** (message, isError) */
    val events = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 8)

    private var socket: WebSocket? = null
    private var syncJob: Job? = null

    init { if (paired) startSync() }

    private fun op(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try { block() } catch (e: ApiException) { events.emit((e.message ?: "خطا") to true) } finally { busy = false }
        }
    }

    // ---------------------------------------------------------------- connection / sync
    fun startSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            var fails = 0
            while (isActive) {
                try {
                    val inf = api.info()
                    pcName = inf.name; store.pcName = inf.name
                    if (store.serverId.isBlank() && inf.id.isNotBlank()) store.serverId = inf.id
                    apps = api.apps(); running = api.running()
                    online = true; fails = 0
                    val closed = CompletableDeferred<Unit>()
                    socket = api.openSocket(object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
                    })
                    closed.await()
                } catch (e: ApiException) {
                    if (e.code == 401) { unpair(); return@launch }
                    fails++
                    // The PC may have a new IP (DHCP): look for the same server on the network.
                    if (fails % 2 == 0 && store.serverId.isNotBlank()) {
                        val f = Discovery.find().firstOrNull { it.id == store.serverId }
                        if (f != null && f.host != store.host) store.host = f.host
                    }
                }
                online = false
                delay(3000)
            }
        }
    }

    private fun handle(text: String) {
        viewModelScope.launch {
            try {
                val m = JSONObject(text)
                when (m.getString("type")) {
                    "running" -> running = m.getJSONArray("data").toRunning()
                    "apps" -> apps = api.apps()
                }
            } catch (e: Exception) { /* ignore malformed / transient */ }
        }
    }

    private suspend fun finishPair(host: String, token: String, name: String, id: String) {
        store.host = host.trim(); store.token = token; store.pcName = name; store.serverId = id
        pcName = name; paired = true
        events.emit("به $name متصل شدید" to false)
        startSync()
    }

    /** Manual fallback: address + 6 digit code. */
    fun pair(host: String, code: String) = op {
        val (token, name) = api.pair(host, code, Build.MODEL ?: "Android")
        val id = try { api.info(host).id } catch (e: ApiException) { "" }
        finishPair(host, token, name, id)
    }

    // ---------------------------------------------------------------- automatic discovery
    var found by mutableStateOf(listOf<Found>())
    var searching by mutableStateOf(false)
    var waitMsg by mutableStateOf("")
    private var discoverJob: Job? = null

    fun discover() {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            searching = true; found = emptyList()
            found = Discovery.find()
            searching = false
            if (found.size == 1 && !paired && !busy) connect(found[0])
        }
    }

    fun connect(f: Found) = op {
        waitMsg = ""
        val first = api.autoPair(f.host, Build.MODEL ?: "Android")
        val ok: AutoResult.Approved = when (first) {
            is AutoResult.Approved -> first
            is AutoResult.Pending -> {
                waitMsg = "در انتظار تأیید روی کامپیوتر… (پنل وب ← اتصال گوشی)"
                var res: AutoResult.Approved? = null
                var tries = 0
                try {
                    while (res == null && tries < 80) { delay(1500); tries++; res = api.pollAuto(f.host, first.id) }
                } finally { waitMsg = "" }
                res ?: throw ApiException("تأیید روی کامپیوتر انجام نشد")
            }
        }
        finishPair(f.host, ok.token, ok.name, f.id)
    }

    fun unpair() {
        syncJob?.cancel(); socket?.cancel()
        store.clearServer(); paired = false; online = false; apps = emptyList(); running = emptyList()
    }

    fun refresh() = op { apps = api.apps(); running = api.running() }

    // ---------------------------------------------------------------- apps
    fun launch(a: AppItem) = op {
        api.launch(a.id); events.emit("«${a.name}» در حال اجرا روی کامپیوتر" to false)
    }

    fun save(id: String?, name: String, exe: String, desc: String, icon: Uri?, cover: Uri?, bg: Uri?, onOk: () -> Unit) = op {
        val saved = api.saveApp(id, name.trim(), exe.trim(), desc.trim())
        val cr = app.contentResolver
        icon?.let { api.uploadImage(saved.id, "icon", it, cr) }
        cover?.let { api.uploadImage(saved.id, "cover", it, cr) }
        bg?.let { api.uploadImage(saved.id, "background", it, cr) }
        apps = api.apps()
        events.emit("ذخیره شد" to false)
        onOk()
    }

    /** Move an app up/down in the list; the new order is saved on the PC and synced to every client. */
    fun move(index: Int, delta: Int) {
        val l = apps.toMutableList(); val j = index + delta
        if (index !in l.indices || j !in l.indices) return
        val t = l[index]; l[index] = l[j]; l[j] = t
        apps = l
        op { api.reorder(l.map { it.id }); apps = api.apps() }
    }

    fun delete(a: AppItem) = op { api.deleteApp(a.id); apps = api.apps(); events.emit("حذف شد" to false) }

    fun close(r: RunItem) = op { api.closeProcess(r.pid); running = api.running(); events.emit("«${r.name}» بسته شد" to false) }

    override fun onCleared() { socket?.cancel(); super.onCleared() }
}
