package cafe.kankipro.keyboard.data.settings

import android.content.Context
import cafe.kankipro.keyboard.core.L
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** همه‌ی تنظیمات کیبورد در یک شیء تغییرناپذیر؛ هر تغییر = یک کپی تازه. */
data class KbSettings(
    // ظاهر
    val themeId: String = "cyber",
    val customBg: Int = 0xFF0B0F1E.toInt(),
    val customKey: Int = 0x33FFFFFF,
    val customGlow: Int = 0xFF00E5FF.toInt(),
    val customText: Int = 0xFFFFFFFF.toInt(),
    val glassAlpha: Int = 88, // ۰ تا ۱۰۰: درصد ماتی زمینه
    val windowBlur: Boolean = true, // بلور پشت کیبورد (اندروید ۱۲+ اگر گوشی پشتیبانی کند)
    // اندازه و حالت
    val heightPct: Int = 100, // ۷۰ تا ۱۳۰
    val keyGapDp: Float = 4f, // ۰ تا ۱۰
    val fontScale: Float = 1f, // ۰٫۸ تا ۱٫۳
    val oneHanded: String = "off", // off / left / right
    val numberRow: Boolean = false,
    val keyPreview: Boolean = true,
    val longPressMs: Int = 300,
    // بازخورد
    val hapticOn: Boolean = true,
    val hapticStrength: Int = 40,
    val soundOn: Boolean = false,
    val soundVolume: Int = 50,
    val soundProfile: String = "blue",
    // تایپ
    val languages: List<String> = listOf("fa", "en"),
    val currentLang: String = "fa",
    val suggestions: Boolean = true,
    val autocorrect: Boolean = true,
    val autoCaps: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val learnWords: Boolean = true,
    val kashida: Int = 0, // ۰ خاموش، ۱ کم، ۲ متوسط، ۳ زیاد
    val persianDigits: Boolean = true,
    // ترمینال و بازی‌ها
    val terminalAuto: Boolean = true,
    val rawPackages: List<String> = emptyList(),
    val rawOverrides: Map<String, Boolean> = emptyMap(),
    // کلیپ‌بورد
    val clipHistory: Boolean = true,
    val clipAutoWipeMin: Int = 0, // ۰ = هیچ‌وقت
    val sensitiveWipeSec: Int = 60,
    // صدا
    val voiceLang: String = "fa",
    val voicePunctuation: Boolean = true,
    // میان‌برها
    val macrosOn: Boolean = true,
    // زبان برنامه
    val uiLang: String = "system",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("themeId", themeId); put("customBg", customBg); put("customKey", customKey)
        put("customGlow", customGlow); put("customText", customText); put("glassAlpha", glassAlpha)
        put("windowBlur", windowBlur); put("heightPct", heightPct); put("keyGapDp", keyGapDp.toDouble())
        put("fontScale", fontScale.toDouble()); put("oneHanded", oneHanded); put("numberRow", numberRow)
        put("keyPreview", keyPreview); put("longPressMs", longPressMs); put("hapticOn", hapticOn)
        put("hapticStrength", hapticStrength); put("soundOn", soundOn); put("soundVolume", soundVolume)
        put("soundProfile", soundProfile); put("languages", JSONArray(languages)); put("currentLang", currentLang)
        put("suggestions", suggestions); put("autocorrect", autocorrect); put("autoCaps", autoCaps)
        put("doubleSpacePeriod", doubleSpacePeriod); put("learnWords", learnWords); put("kashida", kashida)
        put("persianDigits", persianDigits); put("terminalAuto", terminalAuto)
        put("rawPackages", JSONArray(rawPackages))
        put("rawOverrides", JSONObject().apply { rawOverrides.forEach { (k, v) -> put(k, v) } })
        put("clipHistory", clipHistory); put("clipAutoWipeMin", clipAutoWipeMin); put("sensitiveWipeSec", sensitiveWipeSec)
        put("voiceLang", voiceLang); put("voicePunctuation", voicePunctuation); put("macrosOn", macrosOn)
        put("uiLang", uiLang)
    }

    companion object {
        val LANGS = listOf("fa", "en", "ar")
        val THEMES = listOf("cyber", "amoled", "obsidian", "ice", "custom")
        val SOUND_PROFILES = listOf("blue", "brown", "red", "typewriter")

        /** خواندن امن: هر کلید نامعتبر یا خارج از بازه به پیش‌فرض یا نزدیک‌ترین مقدار مجاز برمی‌گردد. */
        fun fromJson(o: JSONObject): KbSettings {
            val d = KbSettings()
            fun strList(k: String, def: List<String>): List<String> {
                val a = o.optJSONArray(k) ?: return def
                return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
            }
            fun boolMap(k: String): Map<String, Boolean> {
                val m = o.optJSONObject(k) ?: return emptyMap()
                return m.keys().asSequence().filter { validPackage(it) }.associateWith { m.optBoolean(it) }
            }
            val langs = strList("languages", d.languages).filter { it in LANGS }.distinct().ifEmpty { d.languages }
            return KbSettings(
                themeId = o.optString("themeId", d.themeId).takeIf { it in THEMES } ?: d.themeId,
                customBg = o.optInt("customBg", d.customBg),
                customKey = o.optInt("customKey", d.customKey),
                customGlow = o.optInt("customGlow", d.customGlow),
                customText = o.optInt("customText", d.customText),
                glassAlpha = o.optInt("glassAlpha", d.glassAlpha).coerceIn(20, 100),
                windowBlur = o.optBoolean("windowBlur", d.windowBlur),
                heightPct = o.optInt("heightPct", d.heightPct).coerceIn(70, 130),
                keyGapDp = o.optDouble("keyGapDp", d.keyGapDp.toDouble()).toFloat().coerceIn(0f, 10f),
                fontScale = o.optDouble("fontScale", d.fontScale.toDouble()).toFloat().coerceIn(0.8f, 1.3f),
                oneHanded = o.optString("oneHanded", d.oneHanded).takeIf { it in listOf("off", "left", "right") } ?: "off",
                numberRow = o.optBoolean("numberRow", d.numberRow),
                keyPreview = o.optBoolean("keyPreview", d.keyPreview),
                longPressMs = o.optInt("longPressMs", d.longPressMs).coerceIn(150, 800),
                hapticOn = o.optBoolean("hapticOn", d.hapticOn),
                hapticStrength = o.optInt("hapticStrength", d.hapticStrength).coerceIn(0, 100),
                soundOn = o.optBoolean("soundOn", d.soundOn),
                soundVolume = o.optInt("soundVolume", d.soundVolume).coerceIn(0, 100),
                soundProfile = o.optString("soundProfile", d.soundProfile).takeIf { it in SOUND_PROFILES } ?: d.soundProfile,
                languages = langs,
                currentLang = o.optString("currentLang", d.currentLang).takeIf { it in langs } ?: langs.first(),
                suggestions = o.optBoolean("suggestions", d.suggestions),
                autocorrect = o.optBoolean("autocorrect", d.autocorrect),
                autoCaps = o.optBoolean("autoCaps", d.autoCaps),
                doubleSpacePeriod = o.optBoolean("doubleSpacePeriod", d.doubleSpacePeriod),
                learnWords = o.optBoolean("learnWords", d.learnWords),
                kashida = o.optInt("kashida", d.kashida).coerceIn(0, 3),
                persianDigits = o.optBoolean("persianDigits", d.persianDigits),
                terminalAuto = o.optBoolean("terminalAuto", d.terminalAuto),
                rawPackages = strList("rawPackages", emptyList()).filter { validPackage(it) }.distinct().take(200),
                rawOverrides = boolMap("rawOverrides"),
                clipHistory = o.optBoolean("clipHistory", d.clipHistory),
                clipAutoWipeMin = o.optInt("clipAutoWipeMin", d.clipAutoWipeMin).coerceIn(0, 60 * 24 * 30),
                sensitiveWipeSec = o.optInt("sensitiveWipeSec", d.sensitiveWipeSec).coerceIn(10, 3600),
                voiceLang = o.optString("voiceLang", d.voiceLang).takeIf { it in listOf("fa", "en") } ?: d.voiceLang,
                voicePunctuation = o.optBoolean("voicePunctuation", d.voicePunctuation),
                macrosOn = o.optBoolean("macrosOn", d.macrosOn),
                uiLang = o.optString("uiLang", d.uiLang).takeIf { it in listOf("system", "fa", "en") } ?: d.uiLang,
            )
        }

        /** اسم بسته‌ی اندروید: فقط حرف، عدد، نقطه و زیرخط. */
        fun validPackage(p: String): Boolean =
            p.length in 3..150 && p.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
    }
}

/**
 * انبار تنظیمات: یک رشته‌ی JSON در SharedPreferences، با StateFlow تا کیبورد و برنامه‌ی تنظیمات
 * (هر دو در یک فرایند) هم‌زمان باخبر شوند. خواندن یک بار و از حافظه (کیبورد هیچ‌وقت برای تنظیمات منتظر دیسک نمی‌ماند).
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("kb_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<KbSettings> = _state.asStateFlow()
    val value: KbSettings get() = _state.value

    init {
        L.mode = _state.value.uiLang
    }

    private fun load(): KbSettings = try {
        prefs.getString("json", null)?.let { KbSettings.fromJson(JSONObject(it)) } ?: KbSettings()
    } catch (_: Exception) {
        KbSettings()
    }

    fun update(change: (KbSettings) -> KbSettings) {
        val next = change(_state.value)
        if (next == _state.value) return
        _state.value = next
        L.mode = next.uiLang
        prefs.edit().putString("json", next.toJson().toString()).apply()
    }

    fun replace(s: KbSettings) = update { s }
}
