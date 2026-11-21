package cafe.kankipro.keyboard.data.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * تاریخچه‌ی کلیپ‌بورد: نامحدود، سنجاق، و پاک‌سازی خودکار.
 * - متن تکراری ردیف تازه نمی‌سازد؛ فقط بالا می‌آید.
 * - متن حساس بعد از `sensitiveSec` پاک می‌شود (مگر سنجاق شود)؛ اگر هنوز روی کلیپ‌بورد سیستم بود، آن هم پاک می‌شود.
 * - بقیه‌ی سنجاق‌نشده‌ها بعد از `autoWipeMin` (۰ = هیچ‌وقت).
 */
class ClipboardRepository(private val dao: ClipDao, private val scope: CoroutineScope) {
    private val lock = Mutex()
    val items: Flow<List<ClipItem>> = dao.all()

    /** حداکثر طول یک متن ذخیره‌شده (متن خیلی بزرگ کیبورد را کند می‌کند). */
    val maxChars = 20_000

    fun add(text: String, flaggedBySystem: Boolean = false, now: Long = System.currentTimeMillis()) {
        if (text.isBlank()) return
        val clipped = if (text.length > maxChars) text.take(maxChars) else text
        scope.launch { addNow(clipped, flaggedBySystem, now) }
    }

    suspend fun addNow(text: String, flaggedBySystem: Boolean = false, now: Long = System.currentTimeMillis()) = lock.withLock {
        val sensitive = SensitiveDetector.isSensitive(text, flaggedBySystem)
        val old = dao.byText(text)
        if (old != null) dao.update(old.copy(createdAt = now, sensitive = old.sensitive || sensitive))
        else dao.insert(ClipItem(text = text, sensitive = sensitive, createdAt = now))
    }

    fun setPinned(id: Long, pinned: Boolean) = scope.launch { dao.setPinned(id, pinned) }
    fun delete(id: Long) = scope.launch { dao.delete(id) }
    fun clearUnpinned() = scope.launch { dao.clearUnpinned() }
    suspend fun pinned(): List<ClipItem> = dao.pinned()
    suspend fun count(): Int = dao.count()

    /**
     * پاک‌سازی؛ متن‌های حساسی را که پاک شدند برمی‌گرداند تا اگر هنوز روی کلیپ‌بورد سیستم بودند پاک شوند.
     */
    suspend fun sweep(autoWipeMin: Int, sensitiveSec: Int, now: Long = System.currentTimeMillis()): List<String> = lock.withLock {
        val gone = dao.sensitiveBefore(now - sensitiveSec * 1000L)
        gone.forEach { dao.delete(it.id) }
        if (autoWipeMin > 0) dao.deleteUnpinnedBefore(now - autoWipeMin * 60_000L)
        gone.map { it.text }
    }

    /** بازگردانی بکاپ: فقط سنجاق‌شده‌ها (متن حساس هرگز در بکاپ نیست مگر خود کاربر سنجاقش کرده). */
    suspend fun restorePinned(texts: List<String>) = lock.withLock {
        for (t in texts) {
            if (t.isBlank()) continue
            val clipped = t.take(maxChars)
            val old = dao.byText(clipped)
            if (old != null) dao.update(old.copy(pinned = true)) else dao.insert(ClipItem(text = clipped, pinned = true))
        }
    }
}
