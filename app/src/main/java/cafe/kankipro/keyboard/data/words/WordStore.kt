package cafe.kankipro.keyboard.data.words

import java.io.File

/**
 * کلمه‌هایی که خود کاربر تایپ کرده و چند بار؛ فقط روی همین گوشی (فایل `words.tsv` داخل پوشه‌ی خصوصی برنامه).
 * در فیلد رمز و حالت ناشناس چیزی یاد گرفته نمی‌شود (کار سرویس کیبورد).
 */
class WordStore(dir: File) {
    private val file = File(dir, "words.tsv")
    private val counts = HashMap<String, Int>()
    private var dirty = false
    private var loaded = false

    private fun ensure() {
        if (loaded) return
        loaded = true
        try {
            if (file.exists()) file.forEachLine { line ->
                val i = line.lastIndexOf('\t')
                if (i > 0) line.substring(i + 1).toIntOrNull()?.let { counts[line.substring(0, i)] = it }
            }
        } catch (_: Exception) {
            counts.clear()
        }
    }

    @Synchronized
    fun learn(word: String) {
        if (word.length < 2 || word.length > 32) return
        ensure()
        counts[word] = (counts[word] ?: 0) + 1
        dirty = true
        if (counts.size > MAX) prune()
    }

    @Synchronized
    fun count(word: String): Int { ensure(); return counts[word] ?: 0 }

    @Synchronized
    fun forget(word: String) { ensure(); if (counts.remove(word) != null) dirty = true }

    @Synchronized
    fun clear() { counts.clear(); dirty = true; flush() }

    @Synchronized
    fun snapshot(): Map<String, Int> { ensure(); return HashMap(counts) }

    @Synchronized
    fun size(): Int { ensure(); return counts.size }

    private fun prune() {
        val keep = counts.entries.sortedByDescending { it.value }.take(MAX * 3 / 4)
        counts.clear()
        keep.forEach { counts[it.key] = it.value }
    }

    @Synchronized
    fun flush() {
        if (!dirty) return
        dirty = false
        try {
            val tmp = File(file.parentFile, "words.tsv.tmp")
            tmp.bufferedWriter().use { w -> counts.forEach { (k, v) -> w.append(k).append('\t').append(v.toString()).append('\n') } }
            tmp.renameTo(file)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MAX = 20_000
    }
}
