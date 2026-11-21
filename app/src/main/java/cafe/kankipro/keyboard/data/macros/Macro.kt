package cafe.kankipro.keyboard.data.macros

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** میان‌بر متنی: با تایپ `trigger` جایش `expansion` می‌نشیند (مثلاً `@@` ← ایمیل). */
@Entity(tableName = "macros", indices = [Index(value = ["trigger"], unique = true)])
data class Macro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val expansion: String,
    val enabled: Boolean = true,
)

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY trigger")
    fun all(): Flow<List<Macro>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: Macro): Long

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM macros")
    suspend fun list(): List<Macro>
}

class MacroRepository(private val dao: MacroDao, private val scope: CoroutineScope) {
    val items: Flow<List<Macro>> = dao.all()

    /** نسخه‌ی حافظه برای کیبورد (بی صبر برای دیتابیس هنگام تایپ). */
    private val _active = MutableStateFlow<List<Macro>>(emptyList())
    val active: StateFlow<List<Macro>> = _active

    init {
        scope.launch { dao.all().collect { list -> _active.value = list.filter { it.enabled } } }
    }

    fun save(m: Macro) = scope.launch {
        val trig = m.trigger.trim()
        if (MacroExpander.validTrigger(trig) && m.expansion.isNotEmpty()) dao.upsert(m.copy(trigger = trig, expansion = m.expansion.take(5000)))
    }

    fun delete(id: Long) = scope.launch { dao.delete(id) }
    suspend fun list(): List<Macro> = dao.list()
    suspend fun upsertNow(m: Macro) {
        if (MacroExpander.validTrigger(m.trigger)) dao.upsert(m.copy(id = 0, expansion = m.expansion.take(5000)))
    }
}

/**
 * پیدا کردن میان‌بر در انتهای متن قبل از نشانگر.
 * - میان‌بری که حرف غیر از حرف الفبا دارد (مثل `@@`) همان لحظه باز می‌شود.
 * - میان‌بر فقط‌حرف (مثل `brb`) فقط بعد از فاصله یا علامت باز می‌شود تا وسط کلمه‌ی دیگر نپرد.
 * - قبل از میان‌بر باید شروع متن یا فاصله باشد.
 */
object MacroExpander {
    data class Hit(val macro: Macro, val deleteCount: Int)

    fun validTrigger(t: String): Boolean = t.length in 2..32 && t.none { it.isWhitespace() }

    fun isWordTrigger(t: String) = t.all { it.isLetterOrDigit() }

    /** `before` = متن قبل از نشانگر؛ `afterSeparator` = همین الان فاصله یا علامت زده شد (که هنوز ننوشته‌ایم). */
    fun find(before: CharSequence, macros: List<Macro>, afterSeparator: Boolean): Hit? {
        var best: Hit? = null
        for (m in macros) {
            val t = m.trigger
            if (!m.enabled || !validTrigger(t)) continue
            if (isWordTrigger(t) != afterSeparator) continue
            if (before.length < t.length || !before.endsWith(t)) continue
            val start = before.length - t.length
            if (start > 0 && !before[start - 1].isWhitespace()) continue
            if (best == null || t.length > best.deleteCount) best = Hit(m, t.length)
        }
        return best
    }
}
