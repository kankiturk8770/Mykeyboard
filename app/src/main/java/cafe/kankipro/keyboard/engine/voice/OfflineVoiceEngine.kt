package cafe.kankipro.keyboard.engine.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.sqrt

/**
 * تایپ صوتی کاملاً آفلاین با Vosk (مدل کوانتیزه‌ی کوچک هر زبان، روی خود گوشی):
 * میکروفون ← حذف نویز و اکو (`NoiseSuppressor`، `AcousticEchoCanceler`، `AutomaticGainControl` اگر گوشی دارد)
 * ← تشخیص جریانی (کلمه‌ها همان لحظه که گفته می‌شوند می‌آیند: `onPartial`) ← جمله‌ی نهایی (`onFinal`)
 * ← علامت‌گذاری گفتاری (`SpokenPunctuation`، کار سرویس). صدا هیچ‌وقت از گوشی بیرون نمی‌رود.
 */
class OfflineVoiceEngine(private val context: Context, private val models: VoiceModelManager) {

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        /** بلندی صدا ۰ تا ۱ (برای موج). */
        fun onLevel(level: Float)
        fun onError(reason: Reason)
        fun onStopped()
    }

    enum class Reason { NO_PERMISSION, NO_MODEL, MIC_BUSY, MODEL_ERROR }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null
    private var model: Model? = null
    private var modelLang: String? = null
    private val modelLock = Any()

    val isRunning get() = running

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** بارگذاری مدل از قبل (چند صد میلی‌ثانیه)، تا لمس میکروفون فوری جواب بدهد. */
    fun warmUp(lang: String) {
        if (!models.isInstalled(lang)) return
        Thread({ runCatching { loadModel(lang) } }, "voice-warm").start()
    }

    private fun loadModel(lang: String): Model = synchronized(modelLock) {
        val cur = model
        if (cur != null && modelLang == lang) return cur
        cur?.close()
        model = null
        val m = Model(models.modelDir(lang).absolutePath)
        model = m
        modelLang = lang
        m
    }

    @SuppressLint("MissingPermission")
    fun start(lang: String, listener: Listener) {
        if (running) return
        if (!hasPermission()) { listener.onError(Reason.NO_PERMISSION); return }
        if (!models.isInstalled(lang)) { listener.onError(Reason.NO_MODEL); return }
        running = true
        thread = Thread({ loop(lang, listener) }, "voice").also { it.start() }
    }

    /** پایان گفتار: آخرین کلمه‌ها نهایی می‌شوند. */
    fun stop() { running = false }

    fun release() {
        running = false
        thread?.join(500)
        synchronized(modelLock) { model?.close(); model = null; modelLang = null }
    }

    @SuppressLint("MissingPermission")
    private fun loop(lang: String, l: Listener) {
        val rate = 16_000
        val m = try { loadModel(lang) } catch (_: Throwable) {
            running = false; post { l.onError(Reason.MODEL_ERROR); l.onStopped() }; return
        }
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunk = rate / 10 // ۱۰۰ میلی‌ثانیه
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, chunk * 2 * 4))
        } catch (_: Throwable) { null }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release(); running = false; post { l.onError(Reason.MIC_BUSY); l.onStopped() }; return
        }
        val session = rec.audioSessionId
        val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(session)?.apply { enabled = true } }.getOrNull() else null
        val aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(session)?.apply { enabled = true } }.getOrNull() else null
        val agc = if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(session)?.apply { enabled = true } }.getOrNull() else null
        val recognizer = try { Recognizer(m, rate.toFloat()) } catch (_: Throwable) { null }
        if (recognizer == null) {
            rec.release(); running = false; post { l.onError(Reason.MODEL_ERROR); l.onStopped() }; return
        }
        val buf = ShortArray(chunk)
        var lastPartial = ""
        val started = System.currentTimeMillis()
        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) { post { l.onError(Reason.MIC_BUSY) }; return }
            post { l.onReady() }
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                post { l.onLevel(level(buf, n)) }
                if (recognizer.acceptWaveForm(buf, n)) {
                    val text = parse(recognizer.result, "text")
                    lastPartial = ""
                    if (text.isNotBlank()) post { l.onFinal(text) }
                } else {
                    val p = parse(recognizer.partialResult, "partial")
                    if (p != lastPartial) { lastPartial = p; post { l.onPartial(p) } }
                }
                if (System.currentTimeMillis() - started > MAX_SESSION_MS) running = false
            }
            val last = parse(recognizer.finalResult, "text")
            post { if (last.isNotBlank()) l.onFinal(last) else if (lastPartial.isNotEmpty()) l.onPartial("") }
        } catch (_: Throwable) {
            post { l.onError(Reason.MIC_BUSY) }
        } finally {
            running = false
            runCatching { rec.stop() }
            rec.release()
            ns?.release(); aec?.release(); agc?.release()
            recognizer.close()
            post { l.onLevel(0f); l.onStopped() }
        }
    }

    private fun post(r: () -> Unit) { main.post(r) }

    companion object {
        const val MAX_SESSION_MS = 120_000L

        fun parse(json: String?, key: String): String = try {
            JSONObject(json ?: "{}").optString(key, "").trim()
        } catch (_: Exception) { "" }

        /** بلندی صدا (RMS) به‌صورت ۰ تا ۱ با مقیاس لگاریتمی نرم. */
        fun level(buf: ShortArray, n: Int): Float {
            var sum = 0.0
            for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
            val rms = sqrt(sum / n.coerceAtLeast(1))
            val db = 20 * kotlin.math.log10((rms / 32768.0).coerceAtLeast(1e-5))
            return ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
        }
    }
}
