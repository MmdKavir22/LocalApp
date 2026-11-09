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
import java.io.File

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

    // updater state
    var release by mutableStateOf<Release?>(null)
    var updateMsg by mutableStateOf("")
    var updateProgress by mutableStateOf(-1)
    var downloaded by mutableStateOf<File?>(null)

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
            while (isActive) {
                try {
                    pcName = api.info().also { store.pcName = it }
                    apps = api.apps(); running = api.running()
                    online = true
                    val closed = CompletableDeferred<Unit>()
                    socket = api.openSocket(object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
                    })
                    closed.await()
                } catch (e: ApiException) {
                    if (e.code == 401) { unpair(); return@launch }
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

    fun pair(host: String, code: String) = op {
        val (token, name) = api.pair(host, code, Build.MODEL ?: "Android")
        store.host = host.trim(); store.token = token; store.pcName = name
        pcName = name; paired = true
        events.emit("به $name متصل شدید" to false)
        startSync()
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

    fun save(id: String?, name: String, exe: String, desc: String, cover: Uri?, bg: Uri?, onOk: () -> Unit) = op {
        val saved = api.saveApp(id, name.trim(), exe.trim(), desc.trim())
        val cr = app.contentResolver
        cover?.let { api.uploadImage(saved.id, "cover", it, cr) }
        bg?.let { api.uploadImage(saved.id, "background", it, cr) }
        apps = api.apps()
        events.emit("ذخیره شد" to false)
        onOk()
    }

    fun delete(a: AppItem) = op { api.deleteApp(a.id); apps = api.apps(); events.emit("حذف شد" to false) }

    fun close(r: RunItem) = op { api.closeProcess(r.pid); running = api.running(); events.emit("«${r.name}» بسته شد" to false) }

    // ---------------------------------------------------------------- GitHub updater
    fun saveGithub(repo: String, token: String) {
        store.ghRepo = repo.trim(); store.ghToken = token.trim()
        viewModelScope.launch { events.emit("اطلاعات GitHub ذخیره شد" to false) }
    }

    fun checkUpdate() = op {
        val (o, r) = Updater.parseRepo(store.ghRepo) ?: throw ApiException("آدرس Repository معتبر نیست (مثلاً https://github.com/user/repo)")
        val rel = Updater.latest(o, r, store.ghToken)
        release = rel; downloaded = null; updateProgress = -1
        updateMsg = if (Updater.isNewer(rel.tag, BuildConfig.VERSION_NAME))
            "نسخه جدید ${rel.tag} موجود است (نسخه شما ${BuildConfig.VERSION_NAME})"
        else "نسخه شما به‌روز است (${BuildConfig.VERSION_NAME})"
    }

    fun update() = op {
        val rel = release ?: throw ApiException("ابتدا Check for Updates را بزنید")
        val file = downloaded ?: Updater.download(app, rel, store.ghToken) { updateProgress = it }
        downloaded = file; updateProgress = 100
        Updater.install(app, file)
    }

    override fun onCleared() { socket?.cancel(); super.onCleared() }
}
