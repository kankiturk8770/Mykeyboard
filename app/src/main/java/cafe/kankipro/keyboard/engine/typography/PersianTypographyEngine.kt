package cafe.kankipro.keyboard.engine.typography

/**
 * موتور تایپوگرافی فارسی:
 * - کلاس اتصال هر حرف (دوطرفه، فقط از راست، بی‌اتصال) طبق جدول Unicode ArabicShaping.
 * - کشیده‌نویس هوشمند: کشیده (U+0640) فقط بین دو حرفی که واقعاً به هم می‌چسبند؛ هرگز بعد از حرف بی‌اتصال
 *   به چپ (ا د ذ ر ز ژ و)، هرگز داخل «لا»، هرگز آخر کلمه (چون فقط وقتی حرف بعدی تایپ شد اضافه می‌شود).
 * - رابط ساده برای فونت‌ساز یونیکد و قاب‌های گیمری (`UnicodeFontStyler`، `GamerSymbols`).
 */
object PersianTypographyEngine {
    const val TATWEEL = 'ـ'
    const val ZWNJ = '‌'

    enum class Joining { DUAL, RIGHT, NONE, TRANSPARENT, CAUSING }

    /** حرف‌هایی که از دو طرف وصل می‌شوند. */
    private const val DUAL_LETTERS = "بپتثجچحخسشصضطظعغفقکكگلمنهیيئىڤڭ"
    /** حرف‌هایی که فقط از راست وصل می‌شوند (به حرف بعدی نمی‌چسبند). */
    private const val RIGHT_LETTERS = "اآأإٱدذرزژوؤةۀ"
    /** حرف‌های اصلی کشیده (سین‌دار، صاد، کاف، گاف، میم، ها)؛ در «کم» فقط همین‌ها. */
    const val EMPHASIS = "سشصضکگمهك"
    /** حرف‌های دیگری که کشیده‌ی زیبا می‌گیرند (در «متوسط» و «زیاد»). */
    const val SECONDARY = "عغطظحجچخفقل"

    /** علامت‌های ترکیبی (حرکت‌ها، تشدید، تنوین، همزه‌ی بالا/پایین، ...) که روی اتصال اثری ندارند. */
    fun isMark(c: Char): Boolean = c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ' || c in 'ؐ'..'ؚ'

    fun joining(c: Char): Joining = when {
        c == TATWEEL || c == '‍' -> Joining.CAUSING
        isMark(c) -> Joining.TRANSPARENT
        c in DUAL_LETTERS -> Joining.DUAL
        c in RIGHT_LETTERS -> Joining.RIGHT
        else -> Joining.NONE
    }

    fun isPersianLetter(c: Char) = c in DUAL_LETTERS || c in RIGHT_LETTERS || c == 'ء'

    /** آیا حرف `prev` به حرف بعدی `next` می‌چسبد؟ */
    fun connects(prev: Char, next: Char): Boolean {
        val a = joining(prev)
        val b = joining(next)
        return (a == Joining.DUAL || a == Joining.CAUSING) && (b == Joining.DUAL || b == Joining.RIGHT || b == Joining.CAUSING)
    }

    private fun isAlef(c: Char) = c in "اآأإٱ"

    /**
     * چند کشیده بین حرف قبلی (`before` = متن قبل از نشانگر) و حرف تازه‌ی `next` لازم است.
     * سطح: ۰ خاموش، ۱ کم، ۲ متوسط، ۳ زیاد.
     */
    fun kashidaCount(before: CharSequence, next: Char, level: Int): Int {
        if (level <= 0 || before.isEmpty()) return 0
        // آخرین حرف پایه (از روی حرکت‌ها رد می‌شویم)؛ اگر قبلش کشیده بود، دوباره اضافه نمی‌کنیم
        var i = before.length - 1
        while (i >= 0 && isMark(before[i])) i--
        if (i < 0) return 0
        val prev = before[i]
        if (prev == TATWEEL) return 0
        if (!connects(prev, next) || joining(next) == Joining.CAUSING) return 0
        if (prev == 'ل' && isAlef(next)) return 0 // «لا» شکسته نشود
        return when (level) {
            1 -> if (prev in EMPHASIS) 1 else 0
            2 -> when (prev) { in EMPHASIS -> 2; in SECONDARY -> 1; else -> 0 }
            else -> when (prev) { in EMPHASIS -> 3; in SECONDARY -> 2; else -> 1 }
        }
    }

    /** کشیده‌دار کردن کل یک متن (برای فونت‌ساز و قالب‌های فارسی). */
    fun applyKashida(text: String, level: Int): String {
        if (level <= 0) return text
        val clean = stripKashida(text)
        val sb = StringBuilder(clean.length * 2)
        for (c in clean) {
            if (isPersianLetter(c)) {
                val n = kashidaCount(sb, c, level)
                if (n > 0) {
                    // کشیده بعد از حرکت‌های حرف قبلی می‌نشیند (حرکت روی حرف خودش می‌ماند)
                    repeat(n) { sb.append(TATWEEL) }
                }
            }
            sb.append(c)
        }
        return sb.toString()
    }

    fun stripKashida(text: String): String = if (text.indexOf(TATWEEL) < 0) text else text.replace(TATWEEL.toString(), "")

    /**
     * طول چیزی که پاک‌کن باید پاک کند وقتی کشیده‌نویس روشن است: آخرین حرف به‌علاوه‌ی کشیده‌هایی که فقط برای
     * چسبیدن به همین حرف اضافه شده بودند (و حرکت‌های خود حرف).
     */
    fun deleteLength(before: CharSequence): Int {
        if (before.isEmpty()) return 0
        var i = before.length - 1
        while (i > 0 && isMark(before[i])) i--
        if (!isPersianLetter(before[i])) return before.length - i
        var j = i - 1
        while (j >= 0 && before[j] == TATWEEL) j--
        // کشیده‌ها فقط وقتی با حرف می‌روند که پیش از آن‌ها حرف دیگری باشد (یعنی کشیده‌ی اتصال بوده‌اند)
        val start = if (j < i - 1 && j >= 0) j + 1 else i
        return before.length - start
    }

    /** تبدیل رقم‌ها: لاتین ← فارسی یا برعکس. */
    fun toPersianDigits(s: String): String = buildString(s.length) { for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c) }
    fun toLatinDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '۰'..'۹') '0' + (c - '۰') else if (c in '٠'..'٩') '0' + (c - '٠') else c)
    }

    // --------------------------------------------------------- فونت‌ساز و قاب‌ها (رابط یک‌جا)
    fun style(text: String, styleId: String): String = UnicodeFontStyler.apply(text, styleId)
    fun wrap(name: String, frame: GamerSymbols.Frame): String = frame.left + name + frame.right
    fun persianTemplate(text: String, id: String): String = PersianTemplates.apply(text, id)
}

/** قالب‌های تزئینی متن فارسی (بی فونت؛ فقط نویسه‌های یونیکد). */
object PersianTemplates {
    data class Template(val id: String, val nameFa: String, val nameEn: String)

    val all = listOf(
        Template("kashida", "کشیده", "Stretched"),
        Template("kashida_max", "کشیده‌ی بلند", "Extra stretched"),
        Template("maddah", "موجی", "Wave marks"),
        Template("shadda", "تشدیدی", "Shadda glow"),
        Template("dots", "نقطه‌چین", "Dotted"),
        Template("ornate", "قاب اسلیمی", "Ornate brackets"),
        Template("lantern", "فانوسی", "Lantern"),
        Template("flower", "گل‌دار", "Flowers"),
        Template("stars", "ستاره‌ای", "Stars"),
        Template("royal", "شاهانه", "Royal"),
        Template("hearts", "قلب‌دار", "Hearts"),
        Template("spaced", "فاصله‌دار", "Spaced"),
    )

    fun apply(text: String, id: String): String {
        val t = text.trim()
        if (t.isEmpty()) return t
        val e = PersianTypographyEngine
        return when (id) {
            "kashida" -> e.applyKashida(t, 2)
            "kashida_max" -> e.applyKashida(t, 3)
            "maddah" -> marks(t, 'ٓ')
            "shadda" -> marks(t, 'ّ')
            "dots" -> marks(t, '۬')
            "ornate" -> "﴿ $t ﴾"
            "lantern" -> "۞ $t ۞"
            "flower" -> "❀ ${e.applyKashida(t, 1)} ❀"
            "stars" -> "✧･ﾟ $t ﾟ･✧"
            "royal" -> "♛ ${e.applyKashida(t, 2)} ♛"
            "hearts" -> "♡ $t ♡"
            "spaced" -> t.toCharArray().joinToString(" ")
            else -> t
        }
    }

    /** یک علامت بالای هر حرف فارسی (حرف‌های دیگر دست نمی‌خورند). */
    private fun marks(t: String, mark: Char) = buildString {
        for (c in t) { append(c); if (PersianTypographyEngine.isPersianLetter(c)) append(mark) }
    }
}
