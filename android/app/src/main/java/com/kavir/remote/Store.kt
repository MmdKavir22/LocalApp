package com.kavir.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Everything (server token, GitHub token, addresses) is kept in encrypted preferences. */
class Store(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx, "kr_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun get(k: String) = sp.getString(k, "") ?: ""
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var host: String get() = get("host"); set(v) = put("host", v)
    var token: String get() = get("token"); set(v) = put("token", v)
    var pcName: String get() = get("pcName"); set(v) = put("pcName", v)
    var ghToken: String get() = get("ghToken"); set(v) = put("ghToken", v)
    var ghRepo: String get() = get("ghRepo"); set(v) = put("ghRepo", v)

    var appName: String get() = get("appName"); set(v) = put("appName", v)
    var accent: Int get() = get("accent").toIntOrNull() ?: 0; set(v) = put("accent", v.toString())
    var bgStyle: Int get() = get("bgStyle").toIntOrNull() ?: 0; set(v) = put("bgStyle", v.toString())
    var dim: Float get() = get("dim").toFloatOrNull() ?: 0.6f; set(v) = put("dim", v.toString())

    var serverId: String get() = get("serverId"); set(v) = put("serverId", v)

    fun clearServer() { host = ""; token = ""; pcName = ""; serverId = "" }
}
