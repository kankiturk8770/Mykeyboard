package cafe.kankipro.keyboard.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import cafe.kankipro.keyboard.core.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** برنامه‌ی همراه کیبورد: راه‌اندازی، تم، اندازه، بازخورد، صدا، کلیپ‌بورد، میان‌برها، بکاپ. */
class SettingsActivity : ComponentActivity() {
    private val vm: SettingsViewModel by viewModels()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.toast(if (ok) t("میکروفون آماده است.", "Microphone allowed.") else t("بدون اجازه‌ی میکروفون تایپ صوتی کار نمی‌کند.", "Voice typing needs the microphone."))
    }

    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val ok = runCatching {
                val json = vm.exportBackup()
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }
            }.isSuccess
            vm.toast(if (ok) t("بکاپ ذخیره شد.", "Backup saved.") else t("ذخیره‌ی بکاپ نشد.", "Couldn't save the backup."))
        }
    }

    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val res = runCatching {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.use { input ->
                        val bytes = input.readNBytesCompat(4 * 1024 * 1024 + 1)
                        require(bytes.size <= 4 * 1024 * 1024) { "too large" }
                        String(bytes)
                    }
                }
                vm.importBackup(text)
            }
            vm.toast(res.fold(
                { r -> t("بازگردانی شد: ${r.pins} سنجاق، ${r.macros} میان‌بر.", "Restored ${r.pins} pins and ${r.macros} shortcuts.") },
                { t("این فایل بکاپ کیبورد کانکی نیست یا خراب است.", "This isn't a valid Kanki Keyboard backup.") },
            ))
            vm.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent.getStringExtra(EXTRA_PAGE)
        if (start == "mic") askMic()
        setContent {
            SettingsRoot(vm, startPage = if (start == "mic") "voice" else start, actions = object : SettingsActions {
                override fun enableKeyboard() = startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                override fun chooseKeyboard() { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
                override fun askMic() = this@SettingsActivity.askMic()
                override fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                override fun exportBackup() {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportDoc.launch("kanki-keyboard-$stamp.json")
                }
                override fun importBackup() = importDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.refresh() // بعد از انتخاب کیبورد از پنجره‌ی سیستم
    }

    private fun askMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

interface SettingsActions {
    fun enableKeyboard()
    fun chooseKeyboard()
    fun askMic()
    fun hasMic(): Boolean
    fun exportBackup()
    fun importBackup()
}

/** خواندن حداکثر n بایت (readNBytes فقط از اندروید ۱۳). */
private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < n) {
        val r = read(buf, 0, minOf(buf.size, n - out.size()))
        if (r < 0) break
        out.write(buf, 0, r)
    }
    return out.toByteArray()
}
