package uz.narkomonitoring.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Telegram'dan kelgan tasdiqlash kodini (va kerak bo'lsa 2FA parolini) kiritish ekrani.
 * SettingsActivity /api/telegram-login/start chaqirgandan keyin shu ekran ochiladi.
 */
class LoginCodeActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var needPassword = false
    private lateinit var phone: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_code)

        phone = intent.getStringExtra("phone") ?: ""
        findViewById<TextView>(R.id.textPhoneLabel).text = "$phone raqamiga yuborilgan kodni kiriting."

        val editCode = findViewById<EditText>(R.id.editCode)
        val editPassword = findViewById<EditText>(R.id.editPassword2fa)
        val textStatus = findViewById<TextView>(R.id.textStatus)

        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            val url = Prefs.getServerUrl(this)
            val user = Prefs.getUsername(this)
            val pass = Prefs.getPassword(this)
            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                textStatus.text = "Server ulanishi topilmadi. \"Sozlamalar\" ekraniga qaytib, qaytadan urinib ko'ring."
                return@setOnClickListener
            }

            val body = JSONObject().put("phone", phone)
            if (needPassword) {
                body.put("password", editPassword.text.toString())
            } else {
                body.put("code", editCode.text.toString().trim())
            }

            textStatus.text = "Tekshirilmoqda..."
            Thread {
                val result = ApiClient.post(url, "/api/telegram-login/verify", user, pass, body)
                mainHandler.post {
                    val ok = result.body?.optBoolean("ok", false) ?: false
                    val needsPwd = result.body?.optBoolean("need_password", false) ?: false
                    when {
                        ok -> {
                            textStatus.text = "Muvaffaqiyatli! Bot qayta ishga tushyapti..."
                            mainHandler.postDelayed({
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }, 4000)
                        }
                        needsPwd -> {
                            needPassword = true
                            editPassword.visibility = View.VISIBLE
                            editCode.visibility = View.GONE
                            textStatus.text = "Hisobda ikki bosqichli himoya (2FA) yoqilgan. Parolingizni kiriting."
                        }
                        else -> {
                            val err = result.body?.optString("error") ?: result.error ?: "noma'lum xatolik"
                            textStatus.text = "Xato: $err"
                        }
                    }
                }
            }.start()
        }
    }
}
