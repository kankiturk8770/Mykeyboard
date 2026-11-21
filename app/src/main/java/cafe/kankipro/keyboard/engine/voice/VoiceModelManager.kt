package cafe.kankipro.keyboard.engine.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * مدل‌های گفتار آفلاین: هر زبان فقط وقتی کاربر خواست دانلود می‌شود (داخل فایل نصبی نیست).
 * دانلود فقط از آدرس ثابت HTTPS، با اثر انگشت SHA-256 ثابت (مدل دست‌کاری‌شده نصب نمی‌شود)، ادامه‌ی دانلود نیمه‌کاره،
 * و باز کردن امن zip (هیچ فایلی بیرون از پوشه‌ی مدل نوشته نمی‌شود).
 */
class VoiceModelManager(private val filesDir: File, private val scope: CoroutineScope) {

    data class ModelInfo(val lang: String, val nameFa: String, val nameEn: String, val url: String, val bytes: Long, val sha256: String)

    sealed class State {
        data object NotInstalled : State()
        data class Downloading(val done: Long, val total: Long) : State()
        data object Installing : State()
        data class Installed(val bytesOnDisk: Long) : State()
        data class Failed(val reason: String) : State()
    }

    val catalog = listOf(
        ModelInfo("fa", "فارسی", "Persian", "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip", 53_431_220L,
            "977cb5faa538f3a835ccfd35f5f6d8284b5c450b89c700b9bd4736b66536ad46"),
        ModelInfo("en", "انگلیسی", "English", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 41_205_931L,
            "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"),
    )

    private val root = File(filesDir, "voice")
    private val states = catalog.associate { it.lang to MutableStateFlow(initial(it.lang)) }
    private val jobs = HashMap<String, Job>()

    fun state(lang: String): StateFlow<State> = states[lang] ?: MutableStateFlow(State.Failed("unknown"))

    fun modelDir(lang: String): File = File(root, lang)

    fun isInstalled(lang: String) = File(modelDir(lang), "am").isDirectory || File(modelDir(lang), "conf/model.conf").isFile

    private fun initial(lang: String): State = if (isInstalled(lang)) State.Installed(dirSize(modelDir(lang))) else State.NotInstalled

    fun download(lang: String) {
        val info = catalog.firstOrNull { it.lang == lang } ?: return
        if (jobs[lang]?.isActive == true) return
        val st = states.getValue(lang)
        jobs[lang] = scope.launch(Dispatchers.IO) {
            try {
                root.mkdirs()
                val zip = File(root, "$lang.zip.part")
                fetch(info, zip) { done -> st.value = State.Downloading(done, info.bytes) }
                if (!isActive) return@launch
                st.value = State.Installing
                if (sha256(zip) != info.sha256) { zip.delete(); throw IllegalStateException("checksum") }
                val tmp = File(root, "$lang.tmp")
                tmp.deleteRecursively()
                unzipSafely(zip, tmp, maxBytes = 400L * 1024 * 1024)
                val modelRoot = findModelRoot(tmp) ?: throw IllegalStateException("layout")
                val dest = modelDir(lang)
                dest.deleteRecursively()
                if (!modelRoot.renameTo(dest)) { modelRoot.copyRecursively(dest, overwrite = true) }
                tmp.deleteRecursively()
                zip.delete()
                st.value = State.Installed(dirSize(dest))
            } catch (e: Exception) {
                if (isActive) st.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel(lang: String) {
        jobs.remove(lang)?.cancel()
        states[lang]?.value = initial(lang)
    }

    fun delete(lang: String) {
        cancel(lang)
        modelDir(lang).deleteRecursively()
        File(root, "$lang.zip.part").delete()
        File(root, "$lang.tmp").deleteRecursively()
        states[lang]?.value = State.NotInstalled
    }

    private fun CoroutineScope.fetch(info: ModelInfo, out: File, progress: (Long) -> Unit) {
        var have = if (out.exists()) out.length() else 0L
        if (have > info.bytes) { out.delete(); have = 0 }
        if (have == info.bytes) { progress(have); return }
        val conn = URL(info.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = conn.responseCode
            val append = code == HttpURLConnection.HTTP_PARTIAL && have > 0
            if (code != HttpURLConnection.HTTP_OK && !append) throw IllegalStateException("HTTP $code")
            // تغییر مسیر فقط به HTTPS
            if (conn.url.protocol != "https") throw IllegalStateException("insecure redirect")
            if (!append) have = 0
            conn.inputStream.use { input ->
                FileOutputStream(out, append).use { o ->
                    val buf = ByteArray(64 * 1024)
                    var last = 0L
                    while (isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        have += n
                        if (have > info.bytes) throw IllegalStateException("too large")
                        if (have - last > 256 * 1024) { last = have; progress(have) }
                    }
                }
            }
            progress(have)
            if (isActive && have != info.bytes) throw IllegalStateException("incomplete")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** باز کردن zip بی خطر «zip slip»: هر مسیری که از پوشه‌ی مقصد بیرون برود رد می‌شود. */
        fun unzipSafely(zip: File, dest: File, maxBytes: Long) {
            dest.mkdirs()
            val base = dest.canonicalPath + File.separator
            var total = 0L
            ZipInputStream(FileInputStream(zip).buffered()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val target = File(dest, e.name)
                    if (!target.canonicalPath.startsWith(base)) throw SecurityException("bad entry")
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw SecurityException("too large")
                            o.write(buf, 0, n)
                        }
                    }
                }
            }
        }

        /** پوشه‌ای که مدل واقعی در آن است (zip معمولاً یک پوشه‌ی بالایی دارد). */
        fun findModelRoot(dir: File): File? {
            fun isModel(d: File) = File(d, "am").isDirectory || File(d, "conf/model.conf").isFile
            if (isModel(dir)) return dir
            return dir.listFiles()?.firstOrNull { it.isDirectory && isModel(it) }
        }

        fun dirSize(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
