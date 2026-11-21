package cafe.kankipro.keyboard.ui.keyboard

import android.graphics.Color
import cafe.kankipro.keyboard.data.settings.KbSettings

/**
 * رنگ‌های یک تم. همه‌ی جلوه‌ها (شیشه، درخشش، برق لبه) از همین چند رنگ روی Canvas کشیده می‌شوند؛ بی عکس.
 */
data class KeyboardTheme(
    val id: String,
    val bgTop: Int,
    val bgBottom: Int,
    val keyFill: Int,
    val specialFill: Int,
    val accentA: Int,
    val accentB: Int,
    val border: Int,
    val glow: Int,
    val glowDp: Float,
    val text: Int,
    val subText: Int,
    val popupBg: Int,
    val pressed: Int,
    /** خط نور بالای هر کلید (برق شیشه). */
    val sheen: Int,
    val amoled: Boolean = false,
) {
    companion object {
        fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

        private fun mix(a: Int, b: Int, f: Float): Int {
            val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt()
            val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt()
            val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
            return Color.rgb(r, g, bl)
        }

        fun preset(id: String): KeyboardTheme = when (id) {
            "amoled" -> KeyboardTheme(
                id, bgTop = 0xFF000000.toInt(), bgBottom = 0xFF000000.toInt(), keyFill = 0xFF000000.toInt(),
                specialFill = 0xFF0A0A0A.toInt(), accentA = 0xFF7C6CFF.toInt(), accentB = 0xFF5B8CFF.toInt(),
                border = 0xFF232327.toInt(), glow = 0xFF7C6CFF.toInt(), glowDp = 0f, text = 0xFFE8E8EC.toInt(),
                subText = 0xFF7D7D86.toInt(), popupBg = 0xFF0E0E12.toInt(), pressed = 0xFF1C1C22.toInt(),
                sheen = 0x00FFFFFF, amoled = true,
            )
            "obsidian" -> KeyboardTheme(
                id, bgTop = 0xFF1B1E27.toInt(), bgBottom = 0xFF0C0E13.toInt(), keyFill = 0x26FFFFFF,
                specialFill = 0x14FFFFFF, accentA = 0xFF9AA6FF.toInt(), accentB = 0xFF6E7BFF.toInt(),
                border = 0x38FFFFFF, glow = 0xFF8C96FF.toInt(), glowDp = 6f, text = 0xFFF1F2F8.toInt(),
                subText = 0x99E6E8F5.toInt(), popupBg = 0xF22A2E3A.toInt(), pressed = 0x40FFFFFF, sheen = 0x55FFFFFF,
            )
            "ice" -> KeyboardTheme(
                id, bgTop = 0xFF0B2236.toInt(), bgBottom = 0xFF041420.toInt(), keyFill = 0x2E9FE6FF,
                specialFill = 0x1C7FD3FF, accentA = 0xFF7FE3FF.toInt(), accentB = 0xFF3FA9FF.toInt(),
                border = 0x669BDDFF, glow = 0xFF7FE3FF.toInt(), glowDp = 7f, text = 0xFFEFFBFF.toInt(),
                subText = 0xB3BFEFFF.toInt(), popupBg = 0xF2123048.toInt(), pressed = 0x5578D8FF, sheen = 0x66FFFFFF,
            )
            else -> KeyboardTheme(
                "cyber", bgTop = 0xFF16052E.toInt(), bgBottom = 0xFF041321.toInt(), keyFill = 0x1FFFFFFF,
                specialFill = 0x2EFF2BD6, accentA = 0xFFFF2BD6.toInt(), accentB = 0xFF00E5FF.toInt(),
                border = 0x6600E5FF, glow = 0xFF00E5FF.toInt(), glowDp = 8f, text = 0xFFF4F7FF.toInt(),
                subText = 0x99B6F6FF.toInt(), popupBg = 0xF2210A3D.toInt(), pressed = 0x55FF2BD6, sheen = 0x4DFFFFFF,
            )
        }

        fun from(s: KbSettings): KeyboardTheme {
            val base = if (s.themeId == "custom") custom(s) else preset(s.themeId)
            if (base.amoled) return base
            val a = (s.glassAlpha * 255 / 100)
            return base.copy(bgTop = withAlpha(base.bgTop, a), bgBottom = withAlpha(base.bgBottom, a))
        }

        private fun custom(s: KbSettings): KeyboardTheme {
            val bg = s.customBg or 0xFF000000.toInt()
            val glow = s.customGlow or 0xFF000000.toInt()
            val text = s.customText or 0xFF000000.toInt()
            return KeyboardTheme(
                "custom", bgTop = mix(bg, Color.WHITE, 0.06f), bgBottom = mix(bg, Color.BLACK, 0.35f), keyFill = s.customKey,
                specialFill = withAlpha(glow, 0x2E), accentA = glow, accentB = mix(glow, Color.WHITE, 0.25f),
                border = withAlpha(glow, 0x66), glow = glow, glowDp = 7f, text = text,
                subText = withAlpha(text, 0x99), popupBg = withAlpha(mix(bg, Color.WHITE, 0.12f), 0xF2),
                pressed = withAlpha(glow, 0x55), sheen = 0x4DFFFFFF,
            )
        }
    }
}
