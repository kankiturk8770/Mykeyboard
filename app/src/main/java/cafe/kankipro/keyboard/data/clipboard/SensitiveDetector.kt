package cafe.kankipro.keyboard.data.clipboard

/**
 * تشخیص متن حساس کلیپ‌بورد (بی شبکه، فقط با قاعده): کد یک‌بارمصرف، شماره‌ی کارت (الگوریتم Luhn)،
 * رمزهای شبیه رمز (یک تکه‌ی بی‌فاصله با چند نوع حرف)، کلید خصوصی و توکن‌ها.
 * لینک‌ها (مثل کانفیگ‌های `vless://`) حساس حساب نمی‌شوند تا بشود نگهشان داشت.
 */
object SensitiveDetector {
    private val otp = Regex("^\\s*[0-9۰-۹]{4,8}\\s*$")
    private val cardish = Regex("^[0-9۰-۹ \\-]{13,23}$")
    private val tokenPrefixes = listOf("sk-", "ghp_", "gho_", "github_pat_", "xox", "AKIA", "AIza", "glpat-")

    fun isSensitive(text: String, flaggedBySystem: Boolean = false): Boolean {
        if (flaggedBySystem) return true
        val s = text.trim()
        if (s.isEmpty()) return false
        if (s.contains("-----BEGIN") && s.contains("PRIVATE KEY")) return true
        if (s.contains("://")) return false
        if (otp.matches(s)) return true
        if (cardish.matches(s)) {
            val digits = latinDigits(s).filter { it.isDigit() }
            if (digits.length in 13..19 && luhn(digits)) return true
        }
        if (s.any { it.isWhitespace() }) return false
        if (tokenPrefixes.any { s.startsWith(it) } && s.length >= 20) return true
        if (s.length in 8..64 && !s.contains('@')) {
            var classes = 0
            if (s.any { it.isLowerCase() }) classes++
            if (s.any { it.isUpperCase() }) classes++
            if (s.any { it.isDigit() }) classes++
            if (s.any { !it.isLetterOrDigit() }) classes++
            if (classes >= 3) return true
        }
        // رشته‌ی بلند هگز یا base64 بی‌فاصله (توکن، کلید)
        if (s.length >= 32 && s.all { it.isLetterOrDigit() || it in "+/=_-" } && s.any { it.isDigit() } && s.any { it.isLetter() }) return true
        return false
    }

    fun latinDigits(s: String): String = buildString {
        for (c in s) append(if (c in '۰'..'۹') ('0' + (c - '۰')) else if (c in '٠'..'٩') ('0' + (c - '٠')) else c)
    }

    fun luhn(digits: String): Boolean {
        var sum = 0
        var alt = false
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (alt) { n *= 2; if (n > 9) n -= 9 }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }

    /** نمایش پوشیده: فقط دو حرف اول و آخر. */
    fun mask(s: String): String = if (s.length <= 4) "••••" else s.take(2) + "•".repeat((s.length - 4).coerceAtMost(12)) + s.takeLast(2)
}
