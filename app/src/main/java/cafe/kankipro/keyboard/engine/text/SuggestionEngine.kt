package cafe.kankipro.keyboard.engine.text

import cafe.kankipro.keyboard.data.words.BuiltinWords
import cafe.kankipro.keyboard.data.words.WordStore
import cafe.kankipro.keyboard.engine.typography.PersianTypographyEngine

/**
 * پیشنهاد کلمه و تصحیح خودکار، کاملاً روی گوشی.
 * امتیاز هر کلمه = امتیاز فهرست داخلی (پرکاربردتر بیشتر) + چند برابر دفعاتی که خود کاربر تایپش کرده.
 * تصحیح خودکار محتاط است: فقط انگلیسی، فقط کلمه‌ی ناشناخته، فقط یک غلط (جابه‌جایی، حذف، اضافه یا عوض شدن یک حرف)،
 * و فقط وقتی یک جایگزین روشن و پرکاربرد هست. یک بار پاک‌کن بلافاصله بعدش آن را برمی‌گرداند (کار سرویس).
 */
class SuggestionEngine(private val learned: WordStore) {
    private val builtin = HashMap<String, Map<String, Int>>()

    private fun dict(lang: String): Map<String, Int> = builtin.getOrPut(lang) {
        val list = BuiltinWords.of(lang)
        val m = HashMap<String, Int>(list.size * 2)
        list.forEachIndexed { i, w -> m[w] = 1000 - (i * 900 / list.size.coerceAtLeast(1)) }
        m
    }

    private fun key(w: String, lang: String) = if (lang == "en") w.lowercase() else PersianTypographyEngine.stripKashida(w)

    fun score(word: String, lang: String): Int {
        val k = key(word, lang)
        return (dict(lang)[k] ?: 0) + learned.count(k) * 150
    }

    fun isKnown(word: String, lang: String): Boolean {
        val k = key(word, lang)
        return dict(lang).containsKey(k) || learned.count(k) > 0
    }

    /** سه پیشنهاد برای کلمه‌ی نیمه‌تمام. خانه‌ی اول همان چیزی است که تایپ شده (اگر کلمه‌ی کاملی نبود). */
    fun suggest(typed: String, lang: String, max: Int = 3): List<String> {
        if (typed.isEmpty()) return emptyList()
        val k = key(typed, lang)
        val pool = HashMap<String, Int>()
        for ((w, s) in dict(lang)) if (w.startsWith(k) && w != k) pool[w] = s
        for ((w, c) in learned.snapshot()) if (w.startsWith(k) && w != k) pool[w] = (pool[w] ?: 0) + c * 150
        if (pool.size < max && k.length >= 3) for (w in neighbours(k, lang)) if (w != k) pool.putIfAbsent(w, score(w, lang) / 2)
        val ranked = pool.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .map { matchCase(it.key, typed, lang) }
        val out = ArrayList<String>(max)
        out.add(typed)
        for (w in ranked) { if (out.size >= max) break; if (w != typed) out.add(w) }
        return out
    }

    /** اگر کلمه غلط تایپی روشن داشت، کلمه‌ی درست؛ وگرنه null. */
    fun autocorrect(word: String, lang: String): String? {
        if (lang != "en") return null
        CONTRACTIONS[word.lowercase()]?.let { return matchCase(it, word, lang) }
        if (word == "i") return "I"
        if (word.length < 3 || word.any { !it.isLetter() } || isKnown(word, lang)) return null
        val k = word.lowercase()
        val options = neighbours(k, lang).map { it to score(it, lang) }.sortedByDescending { it.second }
        if (options.isEmpty()) return null
        val best = options[0]
        if (best.second < 300) return null
        if (options.size > 1 && options[1].second > best.second * 3 / 4) return null // دو جایگزین هم‌وزن: مطمئن نیستیم
        return matchCase(best.first, word, lang)
    }

    /** کلمه‌های شناخته‌شده با فاصله‌ی ویرایش یک (Damerau). */
    fun neighbours(k: String, lang: String): List<String> {
        val all = HashSet<String>()
        all.addAll(dict(lang).keys)
        all.addAll(learned.snapshot().keys)
        return all.filter { it.length in (k.length - 1)..(k.length + 1) && it != k && editDistanceOne(it, k) }
    }

    fun learn(word: String, lang: String) {
        if (word.length < 2 || word.any { it.isDigit() }) return
        learned.learn(key(word, lang))
    }

    companion object {
        private val CONTRACTIONS = mapOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "im" to "I'm", "ive" to "I've",
            "didnt" to "didn't", "doesnt" to "doesn't", "isnt" to "isn't", "wasnt" to "wasn't", "couldnt" to "couldn't",
            "shouldnt" to "shouldn't", "wouldnt" to "wouldn't", "thats" to "that's", "whats" to "what's", "youre" to "you're",
        )

        fun matchCase(word: String, like: String, lang: String): String {
            if (lang != "en" || like.isEmpty()) return word
            return when {
                like.length > 1 && like.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
                like[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
                else -> word
            }
        }

        fun editDistanceOne(a: String, b: String): Boolean {
            if (a == b) return false
            val la = a.length
            val lb = b.length
            if (kotlin.math.abs(la - lb) > 1) return false
            if (la == lb) {
                val diff = a.indices.filter { a[it] != b[it] }
                if (diff.size == 1) return true
                return diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
            }
            val (s, l) = if (la < lb) a to b else b to a
            var i = 0
            var j = 0
            var skipped = false
            while (i < s.length && j < l.length) {
                if (s[i] == l[j]) { i++; j++ } else { if (skipped) return false; skipped = true; j++ }
            }
            return true
        }
    }
}
