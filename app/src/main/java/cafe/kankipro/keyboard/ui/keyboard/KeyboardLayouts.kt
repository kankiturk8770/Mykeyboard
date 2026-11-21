package cafe.kankipro.keyboard.ui.keyboard

/** یک کلید. `code` صفر = کلید متن (`text` نوشته می‌شود)؛ کدهای منفی = کلیدهای کاری. */
data class Key(
    val label: String,
    val code: Int = 0,
    val text: String = label,
    val width: Float = 1f,
    val alts: List<String> = emptyList(),
    val hint: String? = null,
    val kind: Kind = Kind.CHAR,
    /** برچسب کوچک دوم (مثلاً «نیم‌فاصله» زیر کلید). */
    val caption: String? = null,
) {
    enum class Kind { CHAR, MOD, ACTION, SPACE }

    companion object {
        const val SHIFT = -1
        const val SYMBOLS = -2
        const val ALPHA = -3
        const val ENTER = -4
        const val DELETE = -5
        const val SYMBOLS2 = -6
        const val GLOBE = -7
        const val NUMPAD = -9
        const val SPACE = 32
    }
}

data class Row(val keys: List<Key>)

data class KeyboardLayout(val id: String, val rows: List<Row>, val rtl: Boolean, val lang: String) {
    /** بیشترین عرض یک ردیف به واحد کلید (عرض هر واحد = عرض صفحه ÷ این عدد). */
    val units: Float = rows.maxOf { r -> r.keys.sumOf { it.width.toDouble() }.toFloat() }.coerceAtLeast(10f)
}

enum class Layer { ALPHA, SHIFT_MARKS, SYMBOLS, SYMBOLS2, NUMBER, PHONE }

/**
 * چیدمان‌ها: فارسی استاندارد (ردیف‌های ض ص ث، ش س ی، ظ ط ژ)، انگلیسی QWERTY، عربی، علامت‌ها و صفحه‌ی عدد.
 * زبان تازه = یک تابع چیدمان تازه + یک خط در `alpha` و `BuiltinWords` (بسته‌ی زبان).
 */
object KeyboardLayouts {
    const val ZWNJ = "‌"
    private const val DOTTED = "◌"
    private val faMarks = listOf("َ", "ِ", "ُ", "ً", "ّ", "ْ", "ٌ", "ٍ")
    private val latinDigits = "1234567890"
    private val persianDigitsStr = "۱۲۳۴۵۶۷۸۹۰"
    private val arabicDigitsStr = "١٢٣٤٥٦٧٨٩٠"

    data class Options(
        val persianDigits: Boolean = true,
        val numberRow: Boolean = false,
        val shifted: Boolean = false,
        val enterLabel: String = "⏎",
    )

    fun digitsFor(lang: String, persianDigits: Boolean): String = when {
        lang == "fa" && persianDigits -> persianDigitsStr
        lang == "ar" && persianDigits -> arabicDigitsStr
        else -> latinDigits
    }

    fun build(lang: String, layer: Layer, o: Options): KeyboardLayout = when (layer) {
        Layer.ALPHA -> alpha(lang, o)
        Layer.SHIFT_MARKS -> marks(lang, o)
        Layer.SYMBOLS -> symbols(lang, o)
        Layer.SYMBOLS2 -> symbols2(lang, o)
        Layer.NUMBER -> numberPad(o, phone = false)
        Layer.PHONE -> numberPad(o, phone = true)
    }

    private fun alpha(lang: String, o: Options): KeyboardLayout = when (lang) {
        "en" -> english(o)
        "ar" -> arabic(o)
        else -> persian(o)
    }

    // ------------------------------------------------------------------------- فارسی
    private val faAlts = mapOf(
        "ا" to listOf("آ", "أ", "إ", "ء", "ٱ"), "ی" to listOf("ئ", "ي", "ى"), "ک" to listOf("ك"),
        "ه" to listOf("ۀ", "ة", "هٔ"), "و" to listOf("ؤ"), "ز" to listOf("ژ"), "ر" to listOf("ژ"), "ت" to listOf("ة", "ط"),
        "س" to listOf("ص", "ث"), "ذ" to listOf("ظ", "ض"), "گ" to listOf("ک"), "ب" to listOf("پ"), "ج" to listOf("چ"),
    )

    private fun faKey(ch: String, hint: String? = null): Key {
        val alts = ArrayList<String>()
        hint?.let { alts.add(it) }
        faAlts[ch]?.let { alts.addAll(it) }
        faMarks.forEach { alts.add(ch + it) }
        return Key(ch, alts = alts, hint = hint)
    }

    private fun persian(o: Options): KeyboardLayout {
        val d = digitsFor("fa", o.persianDigits)
        val r1 = "ضصثقفغعهخحجچ".mapIndexed { i, c -> faKey(c.toString(), if (i < 10) d[i].toString() else null) }
        val r2 = "شسیبلاتنمکگ".map { faKey(it.toString()) }
        val r3 = listOf(Key("\u25CC\u064E", Key.SHIFT, width = 1f, kind = Key.Kind.MOD)) +
            "ظطژزرذدپو".map { faKey(it.toString()) } + deleteKey(1.5f)
        val bottom = listOf(
            Key("؟۱۲۳", Key.SYMBOLS, width = 1.5f, kind = Key.Kind.MOD),
            globe(),
            Key("،", alts = listOf("؛", ":", "«", "»", "؟", "!")),
            Key("فارسی", Key.SPACE, text = " ", width = 4.5f, kind = Key.Kind.SPACE),
            Key("‌", text = ZWNJ, width = 1.5f, kind = Key.Kind.MOD, caption = "نیم‌فاصله"),
            Key(".", alts = listOf("…", "!", "؟", ":", "«", "»", "-", "/")),
            enterKey(o),
        )
        return KeyboardLayout("fa", withNumberRow("fa", o, listOf(Row(r1), Row(r2), Row(r3), Row(bottom))), rtl = true, lang = "fa")
    }

    private fun marks(lang: String, o: Options): KeyboardLayout {
        fun m(s: String) = Key(DOTTED + s, text = s)
        val r1 = listOf("ً", "ٌ", "ٍ", "َ", "ُ", "ِ", "ّ", "ْ", "ٔ", "ٰ").map { m(it) } +
            listOf(Key("ء"), Key("آ"))
        val r2 = listOf("ئ", "ؤ", "إ", "أ", "ة", "ي", "ك", "ۀ").map { Key(it) } + listOf(m("ٓ"), Key("«"), Key("»"))
        val r3 = listOf(Key("ا‌ب", Key.SHIFT, width = 1f, kind = Key.Kind.MOD)) +
            listOf("ـ", "؛", "؟", "!", "٪", "(", ")", "﷼", "٬").map { Key(it) } + deleteKey(1.5f)
        val bottom = listOf(
            Key("؟۱۲۳", Key.SYMBOLS, width = 1.5f, kind = Key.Kind.MOD), globe(), Key("،"),
            Key(if (lang == "ar") "عربي" else "فارسی", Key.SPACE, text = " ", width = 4.5f, kind = Key.Kind.SPACE),
            Key("‌", text = ZWNJ, width = 1.5f, kind = Key.Kind.MOD, caption = "نیم‌فاصله"),
            Key("."), enterKey(o),
        )
        return KeyboardLayout("$lang-marks", listOf(Row(r1), Row(r2), Row(r3), Row(bottom)), rtl = true, lang = lang)
    }

    // ------------------------------------------------------------------------- انگلیسی
    private val enAlts = mapOf(
        'a' to "àáâäæãåā", 'e' to "èéêëēė", 'i' to "ìíîïī", 'o' to "òóôöõøœō", 'u' to "ùúûüū", 'c' to "çćč",
        'n' to "ñń", 's' to "ßśš", 'y' to "ýÿ", 'z' to "žźż", 'l' to "ł", 'g' to "ğ", 'd' to "ð",
    )

    private fun enKey(c: Char, shifted: Boolean, hint: String?): Key {
        val label = if (shifted) c.uppercaseChar().toString() else c.toString()
        val alts = ArrayList<String>()
        hint?.let { alts.add(it) }
        enAlts[c]?.forEach { alts.add(if (shifted) it.uppercaseChar().toString() else it.toString()) }
        return Key(label, alts = alts, hint = hint)
    }

    private fun english(o: Options): KeyboardLayout {
        val r1 = "qwertyuiop".mapIndexed { i, c -> enKey(c, o.shifted, latinDigits[i].toString()) }
        val r2 = "asdfghjkl".map { enKey(it, o.shifted, null) }
        val r3 = listOf(Key("⇧", Key.SHIFT, width = 1.5f, kind = Key.Kind.MOD)) + "zxcvbnm".map { enKey(it, o.shifted, null) } + deleteKey(1.5f)
        val bottom = listOf(
            Key("?123", Key.SYMBOLS, width = 1.5f, kind = Key.Kind.MOD),
            globe(),
            Key(",", alts = listOf("?", "!", "'", "\"", ";", ":")),
            Key("English", Key.SPACE, text = " ", width = 5f, kind = Key.Kind.SPACE),
            Key(".", alts = listOf("…", "!", "?", ":", ";", "'", "\"", "-", "/", "@")),
            enterKey(o),
        )
        return KeyboardLayout("en", withNumberRow("en", o, listOf(Row(r1), Row(r2), Row(r3), Row(bottom))), rtl = false, lang = "en")
    }

    // ------------------------------------------------------------------------- عربی (بسته‌ی زبان نمونه)
    private fun arabic(o: Options): KeyboardLayout {
        val d = digitsFor("ar", o.persianDigits)
        fun k(c: Char, hint: String? = null): Key {
            val alts = ArrayList<String>()
            hint?.let { alts.add(it) }
            if (c == 'ا') alts.addAll(listOf("أ", "إ", "آ", "ء"))
            faMarks.forEach { alts.add(c + it) }
            return Key(c.toString(), alts = alts, hint = hint)
        }
        val r1 = "ضصثقفغعهخحجد".mapIndexed { i, c -> k(c, if (i < 10) d[i].toString() else null) }
        val r2 = "شسيبلاتنمكط".map { k(it) }
        val r3 = listOf(Key("\u25CC\u064E", Key.SHIFT, width = 1f, kind = Key.Kind.MOD)) + "ذءؤرىةوزظ".map { k(it) } + deleteKey(1.5f)
        val bottom = listOf(
            Key("؟١٢٣", Key.SYMBOLS, width = 1.5f, kind = Key.Kind.MOD), globe(), Key("،", alts = listOf("؛", "؟", "!")),
            Key("عربي", Key.SPACE, text = " ", width = 6f, kind = Key.Kind.SPACE), Key("."), enterKey(o),
        )
        return KeyboardLayout("ar", withNumberRow("ar", o, listOf(Row(r1), Row(r2), Row(r3), Row(bottom))), rtl = true, lang = "ar")
    }

    // ------------------------------------------------------------------------- علامت‌ها
    private fun symbols(lang: String, o: Options): KeyboardLayout {
        val d = digitsFor(lang, o.persianDigits)
        val rtl = lang != "en"
        val r1 = d.mapIndexed { i, c -> Key(c.toString(), alts = if (d != latinDigits) listOf(latinDigits[i].toString()) else emptyList()) }
        val r2 = (if (rtl) listOf("@", "#", "﷼", "_", "&", "-", "+", "(", ")", "/") else listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"))
            .map { Key(it, alts = symAlts[it] ?: emptyList()) }
        val r3 = listOf(Key("=\\<", Key.SYMBOLS2, width = 1.5f, kind = Key.Kind.MOD)) +
            (if (rtl) listOf("*", "«", "»", ":", "؛", "!", "؟") else listOf("*", "\"", "'", ":", ";", "!", "?")).map { Key(it, alts = symAlts[it] ?: emptyList()) } +
            deleteKey(1.5f)
        val bottom = listOf(
            Key(if (rtl) "الفبا" else "ABC", Key.ALPHA, width = 1.5f, kind = Key.Kind.MOD),
            Key(if (rtl) "،" else ","),
            Key("1234", Key.NUMPAD, width = 1f, kind = Key.Kind.MOD),
            Key(" ", Key.SPACE, text = " ", width = 4.5f, kind = Key.Kind.SPACE),
            Key("."), enterKey(o),
        )
        return KeyboardLayout("sym-$lang", listOf(Row(r1), Row(r2), Row(r3), Row(bottom)), rtl = false, lang = lang)
    }

    private val symAlts = mapOf(
        "-" to listOf("–", "—", "_", "·"), "$" to listOf("€", "£", "¥", "₿", "¢", "₺"), "﷼" to listOf("$", "€", "£", "₺"),
        "\"" to listOf("“", "”", "„", "«", "»"), "'" to listOf("‘", "’", "‚", "‹", "›"), "!" to listOf("¡"), "?" to listOf("¿"),
        "(" to listOf("[", "{", "<"), ")" to listOf("]", "}", ">"), "*" to listOf("†", "‡", "★"), "/" to listOf("\\", "|"),
        "&" to listOf("§"), "+" to listOf("±"), "#" to listOf("№"), "%" to listOf("‰", "٪"),
    )

    private fun symbols2(lang: String, o: Options): KeyboardLayout {
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { Key(it) }
        val r2 = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map { Key(it) }
        val r3 = listOf(Key("?123", Key.SYMBOLS, width = 1.5f, kind = Key.Kind.MOD)) +
            listOf("%", "©", "®", "™", "✓", "[", "]").map { Key(it) } + deleteKey(1.5f)
        val bottom = listOf(
            Key(if (lang != "en") "الفبا" else "ABC", Key.ALPHA, width = 1.5f, kind = Key.Kind.MOD), Key("<"), Key(">"),
            Key(" ", Key.SPACE, text = " ", width = 4.5f, kind = Key.Kind.SPACE), Key("…"), enterKey(o),
        )
        return KeyboardLayout("sym2-$lang", listOf(Row(r1), Row(r2), Row(r3), Row(bottom)), rtl = false, lang = lang)
    }

    // ------------------------------------------------------------------------- صفحه‌ی عدد (همیشه رقم لاتین: فیلدهای عددی رقم فارسی را رد می‌کنند)
    private fun numberPad(o: Options, phone: Boolean): KeyboardLayout {
        fun n(s: String, alts: List<String> = emptyList()) = Key(s, width = 2.5f, alts = alts)
        val rows = listOf(
            Row(listOf(n("1"), n("2"), n("3"), Key(if (phone) "+" else "-", width = 1.5f, alts = listOf("-", "+", "(", ")")))),
            Row(listOf(n("4"), n("5"), n("6"), Key(if (phone) "*" else ".", width = 1.5f, alts = listOf(".", ",", "*", "#")))),
            Row(listOf(n("7"), n("8"), n("9"), deleteKey(1.5f))),
            Row(listOf(
                Key("ABC", Key.ALPHA, width = 2.5f, kind = Key.Kind.MOD),
                n("0", listOf("+")),
                Key(if (phone) "#" else ",", width = 2.5f, alts = listOf(",", "#", " ")),
                enterKey(o).copy(width = 1.5f),
            )),
        )
        return KeyboardLayout(if (phone) "phone" else "number", rows, rtl = false, lang = "en")
    }

    // ------------------------------------------------------------------------- اجزای مشترک
    private fun deleteKey(w: Float) = Key("⌫", Key.DELETE, width = w, kind = Key.Kind.MOD)
    private fun globe() = Key("🌐", Key.GLOBE, width = 1f, kind = Key.Kind.MOD)
    private fun enterKey(o: Options) = Key(o.enterLabel, Key.ENTER, width = 1.5f, kind = Key.Kind.ACTION)

    private fun withNumberRow(lang: String, o: Options, rows: List<Row>): List<Row> {
        if (!o.numberRow) return rows
        val d = digitsFor(lang, o.persianDigits)
        val num = Row(d.mapIndexed { i, c -> Key(c.toString(), alts = if (d != latinDigits) listOf(latinDigits[i].toString()) else emptyList()) })
        // وقتی ردیف عدد هست، عدد ردیف اول دیگر لازم نیست
        val first = Row(rows[0].keys.map { k -> if (k.hint != null) k.copy(hint = null, alts = k.alts.drop(1)) else k })
        return listOf(num, first) + rows.drop(1)
    }

    /** برچسب کلید Enter بر اساس کار فیلد. */
    fun enterLabelFor(action: Int): String = when (action) {
        android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH -> "🔍"
        android.view.inputmethod.EditorInfo.IME_ACTION_SEND -> "➤"
        android.view.inputmethod.EditorInfo.IME_ACTION_GO -> "→"
        android.view.inputmethod.EditorInfo.IME_ACTION_NEXT -> "⇥"
        android.view.inputmethod.EditorInfo.IME_ACTION_DONE -> "✓"
        else -> "⏎"
    }
}
