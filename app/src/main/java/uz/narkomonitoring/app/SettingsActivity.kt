package uz.narkomonitoring.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Sozlamalar ekrani:
 *  1) Server (kompyuterdagi bot) manzili + dashboard login/parol.
 *  2) Telegram hisobini ulash: API_ID/API_HASH ni serverga yuboradi, so'ngra
 *     telefon raqamga tasdiqlash kodi so'raydi (LoginCodeActivity'da davom etadi).
 */
class SettingsActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var editServerUrl: EditText
    private lateinit var editDashUser: EditText
    private lateinit var editDashPass: EditText
    private lateinit var textServerStatus: TextView

    private lateinit var editApiId: EditText
    private lateinit var editApiHash: EditText
    private lateinit var editPhone: EditText
    private lateinit var textTelegramStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editServerUrl = findViewById(R.id.editServerUrl)
        editDashUser = findViewById(R.id.editDashUser)
        editDashPass = findViewById(R.id.editDashPass)
        textServerStatus = findViewById(R.id.textServerStatus)

        editApiId = findViewById(R.id.editApiId)
        editApiHash = findViewById(R.id.editApiHash)
        editPhone = findViewById(R.id.editPhone)
        textTelegramStatus = findViewById(R.id.textTelegramStatus)

        editServerUrl.setText(Prefs.getServerUrl(this))
        editDashUser.setText(Prefs.getUsername(this))
        editDashPass.setText(Prefs.getPassword(this))

        findViewById<Button>(R.id.btnSaveServer).setOnClickListener { onSaveServerClicked() }
        findViewById<Button>(R.id.btnConnectTelegram).setOnClickListener { onConnectTelegramClicked() }
    }

    private fun onSaveServerClicked() {
        val url = editServerUrl.text.toString().trim()
        val user = editDashUser.text.toString().trim()
        val pass = editDashPass.text.toString()

        if (url.isBlank() || user.isBlank() || pass.isBlank()) {
            textServerStatus.text = "Barcha maydonlarni to'ldiring."
            return
        }

        textServerStatus.text = "Tekshirilmoqda..."
        Thread {
            val result = ApiClient.get(url, "/api/status", user, pass)
            mainHandler.post {
                when {
                    result.success -> {
                        Prefs.save(this, url, user, pass)
                        textServerStatus.text = "Ulandi! Dashboard ochilmoqda..."
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    result.code == 401 -> {
                        textServerStatus.text = "Login yoki parol noto'g'ri."
                    }
                    else -> {
                        textServerStatus.text = "Ulanib bo'lmadi" +
                            (result.error?.let { ": $it" } ?: "") +
                            ". Server manzili to'g'riligini va botning ishlab turganini tekshiring."
                    }
                }
            }
        }.start()
    }

    private fun onConnectTelegramClicked() {
        val url = editServerUrl.text.toString().trim()
        val user = editDashUser.text.toString().trim()
        val pass = editDashPass.text.toString()
        val apiId = editApiId.text.toString().trim()
        val apiHash = editApiHash.text.toString().trim()
        val phone = editPhone.text.toString().trim()

        if (url.isBlank() || user.isBlank() || pass.isBlank()) {
            textTelegramStatus.text = "Avval yuqorida server manzili, login va parolni kiriting."
            return
        }
        if (apiId.isBlank() || apiHash.isBlank() || phone.isBlank()) {
            textTelegramStatus.text = "API_ID, API_HASH va telefon raqamini kiriting."
            return
        }

        textTelegramStatus.text = "Sozlamalar serverga yuborilmoqda..."
        Thread {
            val cfgBody = JSONObject().put("api_id", apiId).put("api_hash", apiHash)
            val cfgResult = ApiClient.post(url, "/api/config", user, pass, cfgBody)

            if (!cfgResult.success) {
                mainHandler.post {
                    val err = cfgResult.error ?: cfgResult.body?.optString("error") ?: "server xatosi"
                    textTelegramStatus.text = "Sozlamalarni saqlab bo'lmadi: $err"
                }
                return@Thread
            }

            val restartRequired = cfgResult.body?.optBoolean("restart_required", false) ?: false

            if (restartRequired) {
                mainHandler.post { textTelegramStatus.text = "Bot API_ID/API_HASH bilan qayta ishga tushyapti, biroz kutamiz..." }
                var upAgain = false
                for (i in 1..15) {
                    Thread.sleep(2000)
                    val status = ApiClient.get(url, "/api/status", user, pass)
                    if (status.success) {
                        upAgain = true
                        break
                    }
                }
                if (!upAgain) {
                    mainHandler.post {
                        textTelegramStatus.text = "Bot qayta ishga tushmadi. Kompyuterda bot ishlab turganini tekshiring."
                    }
                    return@Thread
                }
            }

            mainHandler.post { textTelegramStatus.text = "Telefon raqamga kod yuborilmoqda..." }
            val startResult = ApiClient.post(
                url, "/api/telegram-login/start", user, pass,
                JSONObject().put("phone", phone)
            )

            mainHandler.post {
                val ok = startResult.body?.optBoolean("ok", false) ?: false
                if (startResult.success && ok) {
                    // Server ulanishi aniq ishlayotgani tasdiqlandi — shu bilan birga saqlaymiz,
                    // keyingi (LoginCodeActivity) ekrani ham shu ma'lumotlardan foydalanadi.
                    Prefs.save(this, url, user, pass)
                    textTelegramStatus.text = "Kod yuborildi."
                    val intent = Intent(this, LoginCodeActivity::class.java)
                    intent.putExtra("phone", phone)
                    startActivity(intent)
                } else {
                    val err = startResult.body?.optString("error") ?: startResult.error ?: "noma'lum xatolik"
                    textTelegramStatus.text = "Kod yuborilmadi: $err"
                }
            }
        }.start()
    }
}
