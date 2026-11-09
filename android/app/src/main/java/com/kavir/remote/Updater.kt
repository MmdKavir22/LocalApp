package com.kavir.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Release(val tag: String, val name: String, val notes: String, val assetUrl: String, val assetName: String, val size: Long)

/** Checks GitHub Releases and installs the APK. The token is only ever sent to api.github.com. */
object Updater {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    fun parseRepo(url: String): Pair<String, String>? {
        val m = Regex("""github\.com[/:]([\w.-]+)/([\w.-]+?)(?:\.git)?/?$""").find(url.trim()) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    private fun req(url: String, token: String, accept: String): Request {
        val b = Request.Builder().url(url).header("Accept", accept).header("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) b.header("Authorization", "Bearer " + token.trim())
        return b.build()
    }

    suspend fun latest(owner: String, repo: String, token: String): Release = withContext(Dispatchers.IO) {
        try {
            http.newCall(req("https://api.github.com/repos/$owner/$repo/releases/latest", token, "application/vnd.github+json")).execute().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    r.code == 404 -> throw ApiException(if (token.isBlank()) "ریلیزی پیدا نشد (اگر ریپو خصوصی است توکن لازم است)" else "ریلیزی پیدا نشد یا توکن به این ریپو دسترسی ندارد")
                    r.code == 401 || r.code == 403 -> throw ApiException("توکن نامعتبر است یا دسترسی کافی ندارد (${r.code})")
                    !r.isSuccessful -> throw ApiException("خطای GitHub (${r.code})")
                }
                val j = JSONObject(body)
                val assets = j.getJSONArray("assets")
                var found: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk", true)) { found = a; break }
                }
                val a = found ?: throw ApiException("در این ریلیز فایل APK وجود ندارد")
                Release(j.getString("tag_name"), j.optString("name", ""), j.optString("body", ""),
                    a.getString("url"), a.getString("name"), a.optLong("size", 0))
            }
        } catch (e: IOException) {
            throw ApiException("اتصال به GitHub برقرار نشد (اینترنت لازم است)")
        }
    }

    private fun nums(v: String) = v.trim().removePrefix("v").removePrefix("V").split(".", "-").mapNotNull { it.toIntOrNull() }

    fun isNewer(tag: String, current: String): Boolean {
        val a = nums(tag); val b = nums(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(ctx: Context, rel: Release, token: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "update.apk")
        try {
            http.newCall(req(rel.assetUrl, token, "application/octet-stream")).execute().use { r ->
                if (!r.isSuccessful) throw ApiException("دانلود ناموفق بود (${r.code})")
                val body = r.body ?: throw ApiException("پاسخ خالی از GitHub")
                val total = body.contentLength().takeIf { it > 0 } ?: rel.size
                var done = 0L
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw ApiException("دانلود قطع شد")
        }
        out
    }

    /** Opens the system installer (the user confirms there). */
    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            throw ApiException("اجازه‌ی نصب برنامه را برای Kavir Remote فعال کنید، سپس دوباره Update بزنید")
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
