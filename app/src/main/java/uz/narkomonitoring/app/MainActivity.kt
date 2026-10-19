package uz.narkomonitoring.app

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asosiy ekran: bot serveridagi dashboard'ni WebView ichida ochadi.
 * HTTP Basic Auth so'rovini avtomatik javoblaydi (saqlangan login/parol bilan),
 * shuning uchun foydalanuvchiga har safar parol so'ralmaydi.
 *
 * Fayl yuklab olish (CSV / Excel eksport):
 *  - http(s) havola  -> o'zimiz login/parol bilan yuklab, "Downloads" papkasiga saqlaymiz
 *  - blob: havola    -> sahifa ichidagi JS orqali o'qib, Android'ga uzatamiz
 *  - data: havola    -> to'g'ridan-to'g'ri dekodlab saqlaymiz
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var username = ""
    private var password = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        username = Prefs.getUsername(this)
        password = Prefs.getPassword(this)

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Telefon ekraniga moslash: sahifa butun kengligi bilan ko'rinadi,
            // kerak bo'lsa barmoq bilan kattalashtirish mumkin.
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.addJavascriptInterface(BlobBridge(), "AndroidDownloader")
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String
            ) {
                handler.proceed(username, password)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(BLOB_HOOK_JS, null)
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            when {
                url.startsWith("blob:") -> downloadBlob(url, fileName, mimeType)
                url.startsWith("data:") -> saveDataUrl(url, fileName)
                else -> downloadHttp(url, fileName, mimeType)
            }
        }

        webView.loadUrl(Prefs.getServerUrl(this))
    }

    // ---------- http(s) ----------
    private fun downloadHttp(url: String, fallbackName: String, fallbackMime: String?) {
        toast("Yuklanmoqda: $fallbackName")
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                val token = Base64.encodeToString(
                    "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                )
                conn.setRequestProperty("Authorization", "Basic $token")
                CookieManager.getInstance().getCookie(url)?.let {
                    conn.setRequestProperty("Cookie", it)
                }
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code !in 200..299) {
                    runOnUiThread { toast("Xatolik: server $code javob berdi") }
                    return@Thread
                }
                val name = URLUtil.guessFileName(
                    url, conn.getHeaderField("Content-Disposition"), conn.contentType
                ).ifBlank { fallbackName }
                val mime = conn.contentType ?: fallbackMime ?: "application/octet-stream"
                val where = conn.inputStream.use { saveStream(it, name, mime) }
                runOnUiThread { toast("Saqlandi: $where") }
            } catch (e: Exception) {
                runOnUiThread { toast("Yuklab bo'lmadi: ${e.message}") }
            }
        }.start()
    }

    // ---------- blob: (JS ichida yaratilgan fayl) ----------
    private fun downloadBlob(url: String, fileName: String, mimeType: String?) {
        val safeName = fileName.replace("'", "")
        val js = """
            (function() {
              var u = '$url';
              var name = (window.__nmNames && window.__nmNames[u]) || '$safeName';
              var kept = window.__nmBlobs && window.__nmBlobs[u];
              var p = kept ? Promise.resolve(kept) : fetch(u).then(function(r){ return r.blob(); });
              p.then(function(b){
                var reader = new FileReader();
                reader.onloadend = function() {
                  AndroidDownloader.save(reader.result, name, b.type || '${mimeType ?: ""}');
                };
                reader.readAsDataURL(b);
              }).catch(function(e){ AndroidDownloader.fail(String(e)); });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    inner class BlobBridge {
        @JavascriptInterface
        fun save(dataUrl: String, fileName: String, mimeType: String) {
            saveDataUrl(dataUrl, fileName, mimeType)
        }

        @JavascriptInterface
        fun fail(message: String) {
            runOnUiThread { toast("Yuklab bo'lmadi: $message") }
        }
    }

    // ---------- data: ----------
    private fun saveDataUrl(dataUrl: String, fileName: String, mimeOverride: String? = null) {
        try {
            val comma = dataUrl.indexOf(',')
            val header = dataUrl.substring(5, comma) // "text/csv;base64"
            val payload = dataUrl.substring(comma + 1)
            val bytes = if (header.contains(";base64")) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
            }
            val mime = mimeOverride?.takeIf { it.isNotBlank() }
                ?: header.substringBefore(';').ifBlank { "application/octet-stream" }
            val where = saveStream(bytes.inputStream(), fileName, mime)
            runOnUiThread { toast("Saqlandi: $where") }
        } catch (e: Exception) {
            runOnUiThread { toast("Saqlab bo'lmadi: ${e.message}") }
        }
    }

    // ---------- umumiy saqlash ----------
    /** Faylni telefonning "Download" papkasiga yozadi va qayerga yozilganini qaytaradi. */
    private fun saveStream(input: InputStream, fileName: String, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Download papkasiga yozib bo'lmadi")
            contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            return "Download/$fileName"
        } else {
            // Android 8-9: ruxsat so'ramaslik uchun ilovaning o'z Download papkasi
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
            val file = File(dir, fileName)
            file.outputStream().use { input.copyTo(it) }
            return file.absolutePath
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        /**
         * Dashboard faylni blob qilib yaratib, darhol URL.revokeObjectURL() bilan
         * o'chirib yuboradi — Android uni o'qishga ulgurmaydi ("Failed to fetch").
         * Shu skript blob'larni ushlab qoladi, o'chirishni 60 soniyaga kechiktiradi
         * va <a download="..."> dagi fayl nomini eslab qoladi.
         */
        private val BLOB_HOOK_JS = """
            (function(){
              if (window.__nmHooked) return; window.__nmHooked = true;
              window.__nmBlobs = {}; window.__nmNames = {};
              var oc = URL.createObjectURL.bind(URL), orv = URL.revokeObjectURL.bind(URL);
              URL.createObjectURL = function(o){
                var u = oc(o);
                if (o instanceof Blob) window.__nmBlobs[u] = o;
                return u;
              };
              URL.revokeObjectURL = function(u){
                setTimeout(function(){ orv(u); delete window.__nmBlobs[u]; delete window.__nmNames[u]; }, 60000);
              };
              var remember = function(a){
                if (a && a.href && a.href.indexOf('blob:') === 0 && a.download) window.__nmNames[a.href] = a.download;
              };
              var ocl = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function(){ remember(this); return ocl.apply(this, arguments); };
              document.addEventListener('click', function(e){
                var a = e.target && e.target.closest ? e.target.closest('a') : null; remember(a);
              }, true);
            })();
        """.trimIndent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
