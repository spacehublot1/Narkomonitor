package uz.narkomonitoring.app

import android.content.Context

/**
 * Telefonda saqlanadigan sozlamalar: server manzili va dashboard login/parol.
 * Telegram API_ID/API_HASH telefonda SAQLANMAYDI — ular to'g'ridan-to'g'ri
 * serverga yuborilib, u yerdagi config.json fayliga yoziladi.
 */
object Prefs {
    private const val PREFS_NAME = "narkomonitor_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_DASH_USER = "dash_user"
    private const val KEY_DASH_PASS = "dash_pass"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerUrl(ctx: Context): String = prefs(ctx).getString(KEY_SERVER_URL, "") ?: ""
    fun getUsername(ctx: Context): String = prefs(ctx).getString(KEY_DASH_USER, "") ?: ""
    fun getPassword(ctx: Context): String = prefs(ctx).getString(KEY_DASH_PASS, "") ?: ""

    fun isConfigured(ctx: Context): Boolean =
        getServerUrl(ctx).isNotBlank() && getUsername(ctx).isNotBlank() && getPassword(ctx).isNotBlank()

    fun save(ctx: Context, serverUrl: String, username: String, password: String) {
        // Oxiridagi "/" belgisini olib tashlaymiz, aks holda "server.com//api/..."
        // kabi ikki marta chiziq paydo bo'lib, so'rov ishlamay qolishi mumkin.
        val cleanUrl = serverUrl.trim().trimEnd('/')
        prefs(ctx).edit()
            .putString(KEY_SERVER_URL, cleanUrl)
            .putString(KEY_DASH_USER, username.trim())
            .putString(KEY_DASH_PASS, password)
            .apply()
    }
}
