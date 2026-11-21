package cafe.kankipro.keyboard.ime

import android.os.SystemClock
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import cafe.kankipro.keyboard.data.settings.KbSettings

/**
 * دو مسیر ورودی:
 * - RICH: متن ترکیبی (`setComposingText`) برای پیشنهاد و تصحیح، در برنامه‌های معمولی.
 * - RAW: هر حرف مستقیم با `commitText` و کلیدهای کاری با `KeyEvent` واقعی (DEL، ENTER، جهت‌ها، CTRL/ALT)،
 *   بی متن ترکیبی؛ برای ترمینال‌ها (Termux، JuiceSSH، ...)، فیلدهای بی‌نوع و بازی‌ها که با متن ترکیبی گیر می‌کنند.
 */
enum class InputMode { RICH, RAW }

object TerminalDetector {
    /** برنامه‌هایی که همیشه RAW می‌گیرند (ترمینال، SSH، ویرایشگرهای کد، بازی‌ها). */
    val KNOWN_RAW = setOf(
        "com.termux", "com.termux.nix", "com.sonelli.juicessh", "org.connectbot", "com.server.auditor.ssh.client",
        "jackpal.androidterm", "yarr.term", "com.offsec.nhterm", "com.zpwebsites.linuxonandroid", "ru.meefik.linuxdeploy",
        "de.mud.ssh", "com.arjanvlek.cyborg", "com.droidvim", "io.github.rosemoe.sora.app", "tech.ula", "com.userland",
        "com.tencent.ig", "com.pubg.imobile", "com.pubg.krmobile", "com.vng.pubgmobile", "com.rekoo.pubgm",
        "com.tencent.tmgp.pubgmhd", "com.activision.callofduty.shooter", "com.garena.game.codm", "com.dts.freefireth",
        "com.dts.freefiremax", "com.mobile.legends", "com.supercell.clashofclans", "com.miHoYo.GenshinImpact",
    )

    /**
     * انتخاب مسیر: اول انتخاب دستی کاربر برای همین برنامه، بعد فهرست‌ها، بعد نوع فیلد
     * (`TYPE_NULL` یعنی فیلد خودش متن ترکیبی نمی‌فهمد؛ `VISIBLE_PASSWORD` را ترمینال‌ها برای خاموش کردن پیشنهاد می‌گذارند).
     */
    fun detect(info: EditorInfo?, s: KbSettings): InputMode {
        val pkg = info?.packageName ?: return InputMode.RICH
        s.rawOverrides[pkg]?.let { return if (it) InputMode.RAW else InputMode.RICH }
        if (!s.terminalAuto) return if (pkg in s.rawPackages) InputMode.RAW else InputMode.RICH
        if (pkg in KNOWN_RAW || pkg in s.rawPackages) return InputMode.RAW
        val type = info.inputType
        if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL) return InputMode.RAW
        if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            type and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return InputMode.RAW
        return InputMode.RICH
    }

    /** فیلد رمز یا ناشناس: هیچ یادگیری، پیشنهاد یا ذخیره‌ی کلیپ‌بورد. */
    fun isPrivate(info: EditorInfo?): Boolean {
        info ?: return false
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        return (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
}

/**
 * فرستادن کلید با کلیدهای کمکی چسبنده (CTRL و ALT): یک ضربه = فقط کلید بعدی، دو ضربه = قفل.
 * با CTRL هر حرف به‌صورت KeyEvent با META_CTRL_ON می‌رود (مثلاً Ctrl+C در Termux).
 */
class KeySender(private val ic: () -> InputConnection?) {
    var ctrl = 0
    var alt = 0
    private var lastCtrlTap = 0L
    private var lastAltTap = 0L

    fun tapCtrl() {
        val now = SystemClock.uptimeMillis()
        ctrl = when { ctrl == 0 -> 1; ctrl == 1 && now - lastCtrlTap < 350 -> 2; else -> 0 }
        lastCtrlTap = now
    }

    fun tapAlt() {
        val now = SystemClock.uptimeMillis()
        alt = when { alt == 0 -> 1; alt == 1 && now - lastAltTap < 350 -> 2; else -> 0 }
        lastAltTap = now
    }

    val hasModifier get() = ctrl > 0 || alt > 0

    private fun meta(): Int {
        var m = 0
        if (ctrl > 0) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt > 0) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        return m
    }

    /** کلیدهای یک‌بار مصرف بعد از استفاده خاموش می‌شوند. */
    private fun consume() {
        if (ctrl == 1) ctrl = 0
        if (alt == 1) alt = 0
    }

    fun key(code: Int, extraMeta: Int = 0) {
        val c = ic() ?: return
        val now = SystemClock.uptimeMillis()
        val m = meta() or extraMeta
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, m, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        c.sendKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, m, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        consume()
    }

    /**
     * نوشتن متن. با CTRL/ALT روشن و یک حرف لاتین/عدد، کلید واقعی با meta می‌رود؛ وگرنه `commitText`.
     * برمی‌گرداند آیا به‌صورت کلید فرستاده شد.
     */
    fun text(s: String): Boolean {
        val c = ic() ?: return false
        if (hasModifier && s.length == 1) {
            val code = keyCodeFor(s[0])
            if (code != KeyEvent.KEYCODE_UNKNOWN) {
                key(code, if (s[0].isUpperCase()) KeyEvent.META_SHIFT_ON else 0)
                return true
            }
        }
        c.commitText(s, 1)
        consume()
        return false
    }

    companion object {
        fun keyCodeFor(ch: Char): Int = when (ch.lowercaseChar()) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch.lowercaseChar() - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '\\' -> KeyEvent.KEYCODE_BACKSLASH
            '/' -> KeyEvent.KEYCODE_SLASH
            '-' -> KeyEvent.KEYCODE_MINUS
            '=' -> KeyEvent.KEYCODE_EQUALS
            ',' -> KeyEvent.KEYCODE_COMMA
            '.' -> KeyEvent.KEYCODE_PERIOD
            ';' -> KeyEvent.KEYCODE_SEMICOLON
            '\'' -> KeyEvent.KEYCODE_APOSTROPHE
            '`' -> KeyEvent.KEYCODE_GRAVE
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
}
