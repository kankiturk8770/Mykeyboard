package cafe.kankipro.keyboard.data.backup

import cafe.kankipro.keyboard.data.clipboard.ClipboardRepository
import cafe.kankipro.keyboard.data.macros.Macro
import cafe.kankipro.keyboard.data.macros.MacroRepository
import cafe.kankipro.keyboard.data.settings.KbSettings
import cafe.kankipro.keyboard.data.settings.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * بکاپ یک‌فایلی (JSON): تنظیمات و تم دلخواه، متن‌های سنجاق‌شده‌ی کلیپ‌بورد، میان‌برها.
 * متن‌های سنجاق‌نشده و کلمه‌های یادگرفته عمداً در بکاپ نیستند (حریم خصوصی).
 * بازگردانی هر مقدار را دوباره اعتبارسنجی می‌کند (`KbSettings.fromJson`)؛ فایل بزرگ یا خراب رد می‌شود.
 */
class BackupManager(
    private val settings: SettingsStore,
    private val clipboard: ClipboardRepository,
    private val macros: MacroRepository,
) {
    data class Result(val settings: Boolean, val pins: Int, val macros: Int)

    suspend fun export(): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("version", 1)
        o.put("settings", settings.value.toJson())
        o.put("pinnedClips", JSONArray(clipboard.pinned().map { it.text }))
        o.put("macros", JSONArray(macros.list().map { m ->
            JSONObject().put("trigger", m.trigger).put("expansion", m.expansion).put("enabled", m.enabled)
        }))
        return o.toString(2)
    }

    suspend fun import(json: String): Result {
        require(json.length <= MAX_BYTES) { "too large" }
        val o = JSONObject(json)
        require(o.optString("format") == FORMAT) { "not a backup" }
        var okSettings = false
        o.optJSONObject("settings")?.let { settings.replace(KbSettings.fromJson(it)); okSettings = true }
        val pins = o.optJSONArray("pinnedClips")?.let { a -> (0 until minOf(a.length(), 5000)).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } } ?: emptyList()
        clipboard.restorePinned(pins)
        var count = 0
        o.optJSONArray("macros")?.let { a ->
            for (i in 0 until minOf(a.length(), 2000)) {
                val m = a.optJSONObject(i) ?: continue
                val trig = m.optString("trigger").trim()
                val exp = m.optString("expansion")
                if (trig.isEmpty() || exp.isEmpty()) continue
                macros.upsertNow(Macro(trigger = trig, expansion = exp, enabled = m.optBoolean("enabled", true)))
                count++
            }
        }
        return Result(okSettings, pins.size, count)
    }

    companion object {
        const val FORMAT = "kanki-keyboard-backup"
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
