package cafe.kankipro.keyboard.ui.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * بازخورد لمس: لرزش میلی‌ثانیه‌ای با شدت قابل تنظیم و صدای کلید مکانیکی که با ریاضی ساخته می‌شود
 * (هیچ فایل صوتی داخل برنامه نیست؛ نمونه‌ها یک بار در پوشه‌ی موقت ساخته و در SoundPool بار می‌شوند).
 */
class FeedbackEngine(private val context: Context) {
    enum class Sound { NORMAL, SPACE, DELETE, ENTER }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var pool: SoundPool? = null
    private var loadedProfile: String? = null
    private val ids = IntArray(Sound.entries.size)
    private val ready = BooleanArray(Sound.entries.size)

    fun haptic(strength: Int) {
        val v = vibrator ?: return
        if (strength <= 0 || !v.hasVibrator()) return
        try {
            if (v.hasAmplitudeControl()) {
                val amp = (30 + strength * 225 / 100).coerceIn(1, 255)
                val ms = (6 + strength / 12).toLong()
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(if (strength > 60) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot((5 + strength / 10).toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    /** تیک ریز برای حرکت نشانگر روی فاصله (خیلی سبک‌تر از لرزش کلید). */
    fun tick(strength: Int) {
        val v = vibrator ?: return
        if (strength <= 0 || !v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            else if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createOneShot(4, (strength * 120 / 100).coerceIn(1, 255)))
        } catch (_: Exception) {
        }
    }

    fun prepare(profile: String) {
        if (profile == loadedProfile && pool != null) return
        release()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, id, status -> val i = ids.indexOf(id); if (i >= 0 && status == 0) ready[i] = true }
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        for (s in Sound.entries) {
            val f = File(dir, "${profile}_${s.name.lowercase()}_v1.wav")
            if (!f.exists() || f.length() < 64) writeWav(f, SwitchSynth.render(profile, s))
            ids[s.ordinal] = p.load(f.absolutePath, 1)
        }
        pool = p
        loadedProfile = profile
    }

    fun play(s: Sound, volume: Int) {
        val p = pool ?: return
        if (!ready[s.ordinal] || volume <= 0) return
        val v = (volume / 100f).coerceIn(0f, 1f)
        val rate = 0.97f + (System.nanoTime() % 7) / 100f // کمی تفاوت تا مصنوعی نباشد
        p.play(ids[s.ordinal], v, v, 1, 0, rate)
    }

    fun release() {
        pool?.release()
        pool = null
        loadedProfile = null
        ready.fill(false)
    }

    private fun writeWav(f: File, pcm: ShortArray, rate: Int = SwitchSynth.RATE) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + pcm.size * 2); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(rate); h.putInt(rate * 2)
        h.putShort(2); h.putShort(16); h.put("data".toByteArray()); h.putInt(pcm.size * 2)
        FileOutputStream(f).use { it.write(h.array()); it.write(data.array()) }
    }
}

/**
 * ساخت صدای کلید مکانیکی با ریاضی: یک «تق» کوتاه نویز (برخورد) + چند سینوس میرا (طنین بدنه و فنر).
 * Blue = تیز و دومرحله‌ای، Brown = نرم‌تر، Red = بم و خطی، Typewriter = فلزی با زنگ کوتاه.
 */
object SwitchSynth {
    const val RATE = 44_100

    private data class Voice(val noise: Float, val noiseDecay: Float, val tones: List<Triple<Float, Float, Float>>, val second: Float, val ms: Int)

    private fun voice(profile: String, s: FeedbackEngine.Sound): Voice {
        val pitch = when (s) {
            FeedbackEngine.Sound.SPACE -> 0.72f
            FeedbackEngine.Sound.ENTER -> 0.85f
            FeedbackEngine.Sound.DELETE -> 1.08f
            else -> 1f
        }
        return when (profile) {
            "brown" -> Voice(0.35f, 900f, listOf(Triple(1250f * pitch, 0.35f, 70f), Triple(520f * pitch, 0.3f, 45f)), 0f, 55)
            "red" -> Voice(0.22f, 700f, listOf(Triple(420f * pitch, 0.45f, 35f), Triple(880f * pitch, 0.15f, 60f)), 0f, 60)
            "typewriter" -> Voice(0.5f, 1400f, listOf(Triple(2600f * pitch, 0.25f, 40f), Triple(1650f * pitch, 0.25f, 30f), Triple(330f * pitch, 0.25f, 50f)), 0.018f, 90)
            else -> Voice(0.55f, 1600f, listOf(Triple(4100f * pitch, 0.25f, 120f), Triple(2200f * pitch, 0.3f, 80f), Triple(700f * pitch, 0.2f, 60f)), 0.012f, 60)
        }
    }

    fun render(profile: String, s: FeedbackEngine.Sound): ShortArray {
        val v = voice(profile, s)
        val n = RATE * v.ms / 1000
        val out = FloatArray(n)
        var seed = 0x1234567
        fun noise(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 32768f - 1f }
        fun hit(offset: Int, gain: Float) {
            for (i in 0 until n - offset) {
                val t = i / RATE.toFloat()
                var x = v.noise * noise() * exp(-t * v.noiseDecay)
                for ((f, a, d) in v.tones) x += a * sin(2 * PI * f * t).toFloat() * exp(-t * d)
                out[i + offset] += x * gain
            }
        }
        hit(0, 1f)
        if (v.second > 0f) hit((v.second * RATE).toInt(), 0.55f)
        // هموار کردن شروع (بی تق دیجیتال) و نرمال‌سازی
        val attack = RATE / 2000
        for (i in 0 until minOf(attack, n)) out[i] *= i / attack.toFloat()
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        return ShortArray(n) { (out[it] / peak * 0.85f * 32767).toInt().toShort() }
    }
}
