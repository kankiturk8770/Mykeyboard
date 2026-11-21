package cafe.kankipro.keyboard.engine.voice

/**
 * تبدیل فرمان‌های گفتاری به علامت: «نقطه» ← «.»، «ویرگول» ← «،»، «علامت سوال» ← «؟»، «خط بعدی» ← خط تازه؛
 * انگلیسی: period, comma, question mark, new line. فاصله‌ها درست می‌شوند (علامت به کلمه‌ی قبل می‌چسبد) و
 * در انگلیسی اول هر جمله بزرگ می‌شود. فقط قاعده، بی شبکه.
 */
object SpokenPunctuation {
    private enum class Kind { CLOSE, OPEN, NEWLINE }
    private data class Cmd(val words: List<String>, val out: String, val kind: Kind)

    private fun cmd(phrase: String, out: String, kind: Kind = Kind.CLOSE) = Cmd(phrase.split(' '), out, kind)

    private val fa = listOf(
        cmd("علامت سوال", "؟"), cmd("علامت سؤال", "؟"), cmd("علامت پرسش", "؟"), cmd("علامت تعجب", "!"),
        cmd("نقطه ویرگول", "؛"), cmd("نقطه کاما", "؛"), cmd("دو نقطه", ":"), cmd("سه نقطه", "…"),
        cmd("نقطه", "."), cmd("ویرگول", "،"), cmd("کاما", "،"),
        cmd("خط بعدی", "\n", Kind.NEWLINE), cmd("خط جدید", "\n", Kind.NEWLINE), cmd("سر خط", "\n", Kind.NEWLINE),
        cmd("پاراگراف جدید", "\n\n", Kind.NEWLINE),
        cmd("پرانتز باز", "(", Kind.OPEN), cmd("پرانتز بسته", ")"), cmd("گیومه باز", "«", Kind.OPEN), cmd("گیومه بسته", "»"),
    ).sortedByDescending { it.words.size }

    private val en = listOf(
        cmd("question mark", "?"), cmd("exclamation mark", "!"), cmd("exclamation point", "!"), cmd("full stop", "."),
        cmd("period", "."), cmd("comma", ","), cmd("colon", ":"), cmd("semicolon", ";"), cmd("ellipsis", "…"),
        cmd("new line", "\n", Kind.NEWLINE), cmd("next line", "\n", Kind.NEWLINE), cmd("new paragraph", "\n\n", Kind.NEWLINE),
        cmd("open parenthesis", "(", Kind.OPEN), cmd("close parenthesis", ")"), cmd("open quote", "“", Kind.OPEN),
        cmd("close quote", "”"),
    ).sortedByDescending { it.words.size }

    /**
     * `startOfSentence` = متن قبل از این تکه با پایان جمله تمام شده (یا خالی است)؛ برای بزرگ کردن حرف اول انگلیسی.
     */
    fun process(text: String, lang: String, enabled: Boolean = true, startOfSentence: Boolean = true): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val cmds = if (!enabled) emptyList() else if (lang == "fa") fa else en
        val sb = StringBuilder()
        var openPending = false
        var capNext = startOfSentence && lang == "en"
        var i = 0
        while (i < words.size) {
            val hit = cmds.firstOrNull { c -> c.words.size <= words.size - i && c.words.indices.all { k -> words[i + k].equals(c.words[k], ignoreCase = true) } }
            if (hit != null) {
                when (hit.kind) {
                    Kind.CLOSE -> { trimEndSpaces(sb); sb.append(hit.out); sb.append(' ') }
                    Kind.OPEN -> { if (sb.isNotEmpty() && sb.last() != ' ' && sb.last() != '\n') sb.append(' '); sb.append(hit.out); openPending = true }
                    Kind.NEWLINE -> { trimEndSpaces(sb); sb.append(hit.out) }
                }
                if (hit.out in setOf(".", "?", "!", "؟") || hit.kind == Kind.NEWLINE) capNext = lang == "en"
                i += hit.words.size
                continue
            }
            var w = words[i]
            if (lang == "en") {
                if (w == "i") w = "I"
                if (capNext) { w = w.replaceFirstChar { it.uppercaseChar() }; capNext = false }
            }
            if (sb.isNotEmpty() && !openPending && sb.last() != ' ' && sb.last() != '\n') sb.append(' ')
            sb.append(w)
            openPending = false
            i++
        }
        return sb.toString().trimEnd(' ')
    }

    private fun trimEndSpaces(sb: StringBuilder) { while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1) }

    /** آیا متن قبلی با پایان جمله تمام شده است؟ */
    fun endsSentence(before: CharSequence?): Boolean {
        val t = before?.trimEnd(' ') ?: return true
        if (t.isEmpty()) return true
        return t.last() in ".?!؟\n"
    }
}
