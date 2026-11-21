package cafe.kankipro.keyboard.engine.typography

/**
 * فونت‌ساز بی‌فایل: هر «فونت» فقط نگاشت حرف‌های لاتین به نویسه‌های دیگر یونیکد است (حروف ریاضی، دایره‌ای، مربعی،
 * تمام‌عرض، کوچک‌بزرگ، بالانویس، وارونه) یا افزودن علامت‌های ترکیبی (خط‌خورده، زیرخط، زالگو). صفر بایت فونت.
 * حرف‌های فارسی و هر نویسه‌ی بی‌نگاشت دست نمی‌خورند.
 */
object UnicodeFontStyler {
    data class Style(val id: String, val name: String, val sample: String = "")

    private class Table(val upper: Int?, val lower: Int?, val digits: Int?, val holes: Map<Char, Int> = emptyMap())

    private val math = mapOf(
        "bold" to Table(0x1D400, 0x1D41A, 0x1D7CE),
        "italic" to Table(0x1D434, 0x1D44E, null, mapOf('h' to 0x210E)),
        "bold_italic" to Table(0x1D468, 0x1D482, 0x1D7CE),
        "script" to Table(0x1D49C, 0x1D4B6, null, mapOf(
            'B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B, 'I' to 0x2110, 'L' to 0x2112, 'M' to 0x2133,
            'R' to 0x211B, 'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134)),
        "bold_script" to Table(0x1D4D0, 0x1D4EA, 0x1D7CE),
        "fraktur" to Table(0x1D504, 0x1D51E, null, mapOf('C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128)),
        "bold_fraktur" to Table(0x1D56C, 0x1D586, 0x1D7CE),
        "double" to Table(0x1D538, 0x1D552, 0x1D7D8, mapOf(
            'C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119, 'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124)),
        "sans" to Table(0x1D5A0, 0x1D5BA, 0x1D7E2),
        "sans_bold" to Table(0x1D5D4, 0x1D5EE, 0x1D7EC),
        "sans_italic" to Table(0x1D608, 0x1D622, 0x1D7E2),
        "sans_bold_italic" to Table(0x1D63C, 0x1D656, 0x1D7EC),
        "mono" to Table(0x1D670, 0x1D68A, 0x1D7F6),
        "circled" to Table(0x24B6, 0x24D0, null),
        "neg_circled" to Table(0x1F150, 0x1F150, null),
        "squared" to Table(0x1F130, 0x1F130, null),
        "neg_squared" to Table(0x1F170, 0x1F170, null),
        "paren" to Table(0x1F110, 0x249C, null),
    )

    private fun pairs(from: String, to: String): Map<Char, String> {
        val out = HashMap<Char, String>()
        val cps = to.codePoints().toArray()
        for (i in from.indices) if (i < cps.size) out[from[i]] = String(Character.toChars(cps[i]))
        return out
    }

    private const val AZ = "abcdefghijklmnopqrstuvwxyz"
    private val smallCaps = pairs(AZ, "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ")
    private val superscript = pairs(AZ + "ABDEGHIJKLMNOPRTUVW0123456789+-=()",
        "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖqʳˢᵗᵘᵛʷˣʸᶻᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂ⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾")
    private val subscript = pairs("aehijklmnoprstuvx0123456789+-=()", "ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎")
    private val upside = pairs(AZ + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!?'\"()[]{}<>&_",
        "ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz∀ꓭƆꓷƎℲ⅁HIſꓘ˥WNOԀΌꓤS⊥∩ΛMX⅄Z0ƖᄅƐㄣϛ9ㄥ86˙'¡¿,„)(][}{><⅋‾")
    private val currency = pairs(AZ.uppercase(), "₳฿₵ĐɆ₣₲ⱧłJ₭Ⱡ₥₦Ø₱QⱤ₴₮ɄV₩ӾɎⱫ")
    private val cyrillic = pairs(AZ + AZ.uppercase(), "аъсↁэfБЂіјкlмиорqѓѕтцvшхЎzДБҀↁЄFБНІЈЌLМИФРQЯЅГЦVЩЖЧZ")
    private val greek = pairs(AZ + AZ.uppercase(), "αвc∂εғgнιנкℓмηρρqяѕтυνωχуzΛBCDΣFGHIJKLMNΘPQRSTUVWXYZ")
    private val tiny = pairs(AZ, "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖᑫʳˢᵗᵘᵛʷˣʸᶻ")

    private val combining = mapOf(
        "strike" to "̶", "slash" to "̸", "underline" to "̲", "double_underline" to "̳",
        "overline" to "̅", "tilde" to "̴", "dots_above" to "̇", "cross_above" to "̽",
        "wavy" to "̰", "seagull" to "̼", "arrow_below" to "͎", "ring" to "⃝", "box" to "⃞",
        "keycap" to "️⃣", "dot_below" to "̣", "double_strike" to "̶̶",
    )

    private val joiners = mapOf("hearts" to "♥", "stars" to "★", "sparkles" to "✦", "lightning" to "ϟ", "dots" to "·")

    val styles: List<Style> by lazy { listOf(
        "bold" to "Bold", "italic" to "Italic", "bold_italic" to "Bold Italic", "script" to "Script",
        "bold_script" to "Bold Script", "fraktur" to "Gothic", "bold_fraktur" to "Bold Gothic", "double" to "Double-Struck",
        "sans" to "Sans", "sans_bold" to "Sans Bold", "sans_italic" to "Sans Italic", "sans_bold_italic" to "Sans Bold Italic",
        "mono" to "Monospace", "circled" to "Circled", "neg_circled" to "Dark Circles", "squared" to "Squared",
        "neg_squared" to "Dark Squares", "paren" to "Parenthesized", "fullwidth" to "Wide", "small_caps" to "Small Caps",
        "superscript" to "Superscript", "subscript" to "Subscript", "tiny" to "Tiny", "upside" to "Upside Down",
        "mirror" to "Reversed", "currency" to "Currency", "cyrillic" to "Faux Cyrillic", "greek" to "Greekish",
        "regional" to "Regional", "strike" to "Strikethrough", "double_strike" to "Double Strike", "slash" to "Slashed",
        "underline" to "Underline", "double_underline" to "Double Underline", "overline" to "Overline", "tilde" to "Tilde",
        "dots_above" to "Dotted", "dot_below" to "Dot Below", "cross_above" to "Crossed", "wavy" to "Wavy",
        "seagull" to "Seagull", "arrow_below" to "Arrows", "ring" to "Ringed", "box" to "Boxed", "keycap" to "Keycaps",
        "zalgo_light" to "Glitch Light", "zalgo" to "Zalgo", "zalgo_heavy" to "Zalgo Heavy", "glitch" to "Glitch",
        "spaced" to "Spaced", "hearts" to "Hearts", "stars" to "Stars", "sparkles" to "Sparkles", "lightning" to "Lightning",
        "dots" to "Dots", "brackets" to "Brackets", "wave_frame" to "Wave Frame",
    ).map { (id, name) -> Style(id, name, apply("Kanki", id)) } }

    fun apply(text: String, id: String): String = when (id) {
        in math -> mapTable(text, math.getValue(id))
        "fullwidth" -> mapChars(text) { c -> if (c == ' ') "　" else if (c.code in 0x21..0x7E) String(Character.toChars(c.code + 0xFEE0)) else null }
        "small_caps" -> mapChars(text) { smallCaps[it.lowercaseChar()] }
        "superscript" -> mapChars(text) { superscript[it] ?: superscript[it.lowercaseChar()] }
        "subscript" -> mapChars(text) { subscript[it] ?: subscript[it.lowercaseChar()] }
        "tiny" -> mapChars(text) { tiny[it.lowercaseChar()] }
        "upside" -> mapChars(text) { upside[it] }.let { reverseGraphemes(it) }
        "mirror" -> reverseGraphemes(text)
        "currency" -> mapChars(text) { currency[it.uppercaseChar()] }
        "cyrillic" -> mapChars(text) { cyrillic[it] }
        "greek" -> mapChars(text) { greek[it] }
        "regional" -> buildString {
            for (c in text) {
                val u = c.uppercaseChar()
                if (u in 'A'..'Z') { append(String(Character.toChars(0x1F1E6 + (u - 'A')))); append('​') } else append(c)
            }
        }
        in combining -> buildString {
            val mark = combining.getValue(id)
            for (c in text) { append(c); if (!c.isWhitespace()) append(mark) }
        }
        "zalgo_light" -> zalgo(text, 1)
        "zalgo" -> zalgo(text, 3)
        "zalgo_heavy" -> zalgo(text, 7)
        "glitch" -> glitch(text)
        "spaced" -> text.toCharArray().joinToString(" ")
        in joiners -> text.toCharArray().joinToString(joiners.getValue(id))
        "brackets" -> buildString { for (c in text) if (c.isWhitespace()) append(c) else append("【").append(c).append("】") }
        "wave_frame" -> "˜”*°•.˜”*°• $text •°*”˜.•°*”˜"
        else -> text
    }

    private fun mapTable(text: String, t: Table): String = mapChars(text) { c ->
        t.holes[c]?.let { return@mapChars String(Character.toChars(it)) }
        when (c) {
            in 'A'..'Z' -> t.upper?.let { String(Character.toChars(it + (c - 'A'))) }
            in 'a'..'z' -> t.lower?.let { String(Character.toChars(it + (c - 'a'))) }
            in '0'..'9' -> t.digits?.let { String(Character.toChars(it + (c - '0'))) }
            else -> null
        }
    }

    private inline fun mapChars(text: String, f: (Char) -> String?): String = buildString(text.length * 2) {
        for (c in text) append(f(c) ?: c.toString())
    }

    private fun reverseGraphemes(s: String): String {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(s)
        val parts = ArrayList<String>()
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) { parts.add(s.substring(start, end)); start = end; end = it.next() }
        return parts.asReversed().joinToString("")
    }

    private val ZALGO_UP = (0x030D..0x0315).toList() + (0x0300..0x0304).toList() + listOf(0x033E, 0x033F, 0x0346, 0x034A, 0x034B, 0x034C)
    private val ZALGO_MID = listOf(0x0334, 0x0335, 0x0336, 0x0337, 0x0338, 0x0321, 0x0322, 0x0327, 0x0328)
    private val ZALGO_DOWN = (0x0316..0x0319).toList() + (0x031C..0x0320).toList() + (0x0324..0x0326).toList() + (0x0329..0x0333).toList()

    /** زالگوی قابل پیش‌بینی (هر حرف همیشه همان شکل)، تا پیش‌نمایش هنگام تایپ نپرد. */
    private fun zalgo(text: String, intensity: Int): String = buildString {
        var seed = 0x2545F491
        fun next(n: Int): Int { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5); return (seed and 0x7FFFFFFF) % n }
        for (c in text) {
            append(c)
            if (c.isWhitespace()) continue
            repeat(intensity) { appendCodePoint(ZALGO_UP[next(ZALGO_UP.size)]) }
            if (intensity > 2) appendCodePoint(ZALGO_MID[next(ZALGO_MID.size)])
            repeat(intensity) { appendCodePoint(ZALGO_DOWN[next(ZALGO_DOWN.size)]) }
        }
    }

    private fun glitch(text: String): String = buildString {
        val marks = intArrayOf(0x0337, 0x0336, 0x0335, 0x0352, 0x0353, 0x0358)
        text.forEachIndexed { i, c -> append(c); if (!c.isWhitespace()) appendCodePoint(marks[i % marks.size]) }
    }
}

/** کتابخانه‌ی نشان‌ها و قاب‌های گیمری (کلن، تگ، نام). */
object GamerSymbols {
    data class Frame(val left: String, val right: String)
    data class Category(val id: String, val nameFa: String, val nameEn: String, val items: List<String>)

    val frames: List<Frame> = listOf(
        Frame("꧁༒", "༒꧂"), Frame("꧁", "꧂"), Frame("亗", "亗"), Frame("★彡", "彡★"), Frame("【", "】"),
        Frame("『", "』"), Frame("乂", "乂"), Frame("⚡", "⚡"), Frame("♛", "♛"), Frame("ᴳᴼᴰ", ""),
        Frame("×͜×", ""), Frame("▄︻デ", "══━一"), Frame("︻╦╤─", "─╤╦︻"), Frame("〆", "〆"), Frame("么", "么"),
        Frame("ツ", "ツ"), Frame("⫷", "⫸"), Frame("╰‿╯", ""), Frame("☬", "☬"), Frame("🔥", "🔥"), Frame("『ᴷᴵᴺᴳ』", ""),
        Frame("•°", "°•"), Frame("✧", "✧"), Frame("๖ۣۜ", ""), Frame("◥", "◤"), Frame("▓▒░", "░▒▓"), Frame("⟦", "⟧"),
        Frame("⌜", "⌟"), Frame("✿", "✿"), Frame("☠", "☠"), Frame("ᴾᴿᴼ", "ツ"), Frame("ʚ", "ɞ"),
    )

    val categories: List<Category> = listOf(
        Category("clan", "تگ کلن", "Clan tags", listOf(
            "么", "〆", "乡", "ツ", "亗", "メ", "乂", "彡", "卍", "ジ", "ᴷᴵᴺᴳ", "ᴳᴼᴰ", "ᴾᴿᴼ", "ˢᴺᴵᴾᴱᴿ", "ᴮᴼˢˢ", "ᴸᴱᴳᴱᴺᴰ",
            "ᵛᶦᵖ", "ᴬᴰᴹᴵᴺ", "ᴿᴵᴾ", "ᴷᴬᴺᴷᴵ", "๖ۣۜ", "ᴮᴼᵀ", "ᶜᴸᴬᴺ", "ᴮᴬᴰ", "ᴸᵁᶜᴷʸ")),
        Category("pro", "نشان‌ها", "Pro symbols", listOf(
            "★", "☆", "⚡", "♛", "♚", "♕", "꧁", "꧂", "༒", "✦", "✧", "✪", "✯", "✰", "⚔", "☠", "☬", "♞", "♜", "⚜", "☯",
            "✠", "✞", "❖", "◈", "⌬", "⎈", "⚚", "⚝", "⛧", "✵", "❂", "☢", "☣", "⌖", "⨳", "⟁", "⧫", "⬢", "⬡")),
        Category("deco", "تزئینی", "Decor", listOf(
            "•", "°", "·", "∘", "◦", "○", "●", "◉", "◎", "◇", "◆", "□", "■", "▪", "▫", "△", "▲", "▽", "▼", "◁", "▷",
            "♡", "♥", "❤", "❥", "❣", "✿", "❀", "❁", "✾", "☘", "♪", "♫", "☾", "☽", "☼", "☁", "∞", "✓", "✔", "✗", "✘")),
        Category("lines", "خط و فلش", "Lines & arrows", listOf(
            "═", "║", "╔", "╗", "╚", "╝", "━", "┃", "┏", "┓", "┗", "┛", "▀", "▄", "█", "▌", "▐", "░", "▒", "▓",
            "→", "←", "↑", "↓", "↔", "⇒", "⇐", "⇄", "➜", "➤", "➳", "⟶", "⟵", "↯", "↺", "↻", "⤴", "⤵")),
        Category("brackets", "پرانتزها", "Brackets", listOf(
            "【", "】", "『", "』", "「", "」", "〖", "〗", "〘", "〙", "《", "》", "〈", "〉", "⟦", "⟧", "⟨", "⟩", "﴾", "﴿",
            "⦃", "⦄", "⦅", "⦆", "⌈", "⌉", "⌊", "⌋", "«", "»")),
        Category("jp", "ژاپنی", "Japanese", listOf(
            "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ", "サ", "シ", "ス", "セ", "ソ", "タ", "チ", "ツ", "テ", "ト",
            "ナ", "ニ", "ヌ", "ネ", "ノ", "ハ", "ヒ", "フ", "ヘ", "ホ", "マ", "ミ", "ム", "メ", "モ", "ヤ", "ユ", "ヨ", "ラ", "リ",
            "ル", "レ", "ロ", "ワ", "ン", "神", "鬼", "龍", "王", "死", "愛", "夢", "影", "炎")),
    )
}
