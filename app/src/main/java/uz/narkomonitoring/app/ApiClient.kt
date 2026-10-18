package uz.narkomonitoring.app

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bot serveridagi (gemini_improved.py) dashboard HTTP API'siga so'rov yuborish uchun
 * yengil klient. Qo'shimcha kutubxona (Retrofit/OkHttp) ishlatilmadi — shunchaki
 * tizimning o'z HttpURLConnection'i, build qilish osonroq va ishonchliroq bo'lsin uchun.
 *
 * DIQQAT: bu funksiyalar tarmoqqa chiqadi, shuning uchun UI/asosiy thread'da emas,
 * alohida Thread ichida chaqirilishi kerak (chaqiruvchi tomonda qilingan).
 */
object ApiClient {

    data class ApiResult(
        val success: Boolean,
        val code: Int,
        val body: JSONObject?,
        val error: String? = null
    )

    private fun authHeader(username: String, password: String): String {
        val token = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $token"
    }

    private fun request(
        method: String,
        baseUrl: String,
        path: String,
        username: String,
        password: String,
        jsonBody: JSONObject? = null
    ): ApiResult {
        return try {
            val url = URL(baseUrl.trimEnd('/') + path)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", authHeader(username, password))
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000

            if (jsonBody != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.outputStream.use { it.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val body = try {
                if (text.isNotBlank()) JSONObject(text) else null
            } catch (e: Exception) {
                null
            }
            ApiResult(success = code in 200..299, code = code, body = body)
        } catch (e: Exception) {
            ApiResult(success = false, code = -1, body = null, error = e.message ?: e.toString())
        }
    }

    fun get(baseUrl: String, path: String, username: String, password: String): ApiResult =
        request("GET", baseUrl, path, username, password)

    fun post(baseUrl: String, path: String, username: String, password: String, json: JSONObject): ApiResult =
        request("POST", baseUrl, path, username, password, json)
}
