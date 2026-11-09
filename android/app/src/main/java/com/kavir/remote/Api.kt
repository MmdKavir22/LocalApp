package com.kavir.remote

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(msg: String, val code: Int = 0) : Exception(msg)

data class AppItem(
    val id: String, val name: String, val exe: String,
    val cover: String, val background: String, val description: String
)

data class RunItem(val appId: String, val name: String, val pid: Int, val status: String)

fun JSONObject.toApp() = AppItem(
    getString("id"), getString("name"), getString("exe_path"),
    optString("cover", ""), optString("background", ""), optString("description", "")
)

fun JSONArray.toRunning(): List<RunItem> = (0 until length()).map {
    val o = getJSONObject(it)
    RunItem(o.getString("app_id"), o.getString("name"), o.getInt("pid"), o.optString("status", "running"))
}

class Api(private val store: Store) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private fun hostOf(h: String) = h.trim().removePrefix("http://").trimEnd('/')
    private fun base(h: String = store.host) = "http://" + hostOf(h)

    private fun errText(body: String, fallback: String): String = try {
        val d = JSONObject(body).opt("detail")
        if (d is String && d.isNotBlank()) d else fallback
    } catch (e: Exception) { fallback }

    private fun call(req: Request): String {
        return try {
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException(errText(body, "خطا (${r.code})"), r.code)
                body
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("اتصال به کامپیوتر برقرار نشد. مطمئن شوید سرور روشن است و هر دو به یک Wi-Fi وصل هستند.")
        }
    }

    private fun auth(path: String) = Request.Builder().url(base() + path).header("Authorization", "Bearer " + store.token)

    suspend fun info(host: String = store.host): String = withContext(Dispatchers.IO) {
        val b = call(Request.Builder().url(base(host) + "/api/info").build())
        JSONObject(b).getString("name")
    }

    /** Returns token + PC name. */
    suspend fun pair(host: String, code: String, device: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code.trim()).put("device_name", device).toString().toRequestBody(json)
        val b = JSONObject(call(Request.Builder().url(base(host) + "/api/pair").post(body).build()))
        b.getString("token") to b.getString("name")
    }

    suspend fun apps(): List<AppItem> = withContext(Dispatchers.IO) {
        val a = JSONArray(call(auth("/api/apps").build()))
        (0 until a.length()).map { a.getJSONObject(it).toApp() }
    }

    suspend fun running(): List<RunItem> = withContext(Dispatchers.IO) {
        JSONArray(call(auth("/api/running").build())).toRunning()
    }

    suspend fun saveApp(id: String?, name: String, exe: String, desc: String): AppItem = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).put("exe_path", exe).put("description", desc).toString().toRequestBody(json)
        val rb = if (id == null) auth("/api/apps").post(body) else auth("/api/apps/$id").put(body)
        JSONObject(call(rb.build())).toApp()
    }

    suspend fun uploadImage(id: String, kind: String, uri: Uri, cr: ContentResolver) = withContext(Dispatchers.IO) {
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException("خواندن تصویر ممکن نیست")
        if (bytes.size > 10 * 1024 * 1024) throw ApiException("حجم تصویر بیش از ۱۰ مگابایت است")
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "image", bytes.toRequestBody("application/octet-stream".toMediaType())).build()
        call(auth("/api/apps/$id/image/$kind").post(form).build())
        Unit
    }

    suspend fun deleteApp(id: String) = withContext(Dispatchers.IO) {
        call(auth("/api/apps/$id?confirm=true").delete().build()); Unit
    }

    suspend fun launch(id: String) = withContext(Dispatchers.IO) {
        call(auth("/api/apps/$id/launch").post("".toRequestBody()).build()); Unit
    }

    suspend fun closeProcess(pid: Int) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("confirm", true).toString().toRequestBody(json)
        call(auth("/api/running/$pid/close").post(body).build()); Unit
    }

    fun imageUrl(file: String) = base() + "/api/images/" + file + "?token=" + store.token

    fun openSocket(listener: WebSocketListener): WebSocket =
        client.newWebSocket(Request.Builder().url("ws://" + hostOf(store.host) + "/ws?token=" + store.token).build(), listener)
}
