package cafe.kankipro.keyboard.data.clipboard

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Insert
import androidx.room.Update
import android.content.Context
import cafe.kankipro.keyboard.data.macros.Macro
import cafe.kankipro.keyboard.data.macros.MacroDao
import kotlinx.coroutines.flow.Flow

/** یک متن کپی‌شده. `sensitive` = رمز، کد یک‌بارمصرف یا کارت؛ زود پاک می‌شود مگر سنجاق شود. */
@Entity(tableName = "clips", indices = [Index(value = ["text"], unique = true)])
data class ClipItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val pinned: Boolean = false,
    val sensitive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ClipDao {
    /** سنجاق‌شده‌ها بالا، بعد تازه‌ترها. */
    @Query("SELECT * FROM clips ORDER BY pinned DESC, createdAt DESC")
    fun all(): Flow<List<ClipItem>>

    @Query("SELECT * FROM clips WHERE text = :text LIMIT 1")
    suspend fun byText(text: String): ClipItem?

    @Insert
    suspend fun insert(item: ClipItem): Long

    @Update
    suspend fun update(item: ClipItem)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE clips SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clips WHERE pinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clips WHERE pinned = 0 AND createdAt < :before")
    suspend fun deleteUnpinnedBefore(before: Long): Int

    @Query("SELECT * FROM clips WHERE pinned = 0 AND sensitive = 1 AND createdAt < :before")
    suspend fun sensitiveBefore(before: Long): List<ClipItem>

    @Query("SELECT * FROM clips WHERE pinned = 1 ORDER BY createdAt DESC")
    suspend fun pinned(): List<ClipItem>

    @Query("SELECT COUNT(*) FROM clips")
    suspend fun count(): Int
}

@Database(entities = [ClipItem::class, Macro::class], version = 1, exportSchema = true)
abstract class KeyboardDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao
    abstract fun macroDao(): MacroDao

    companion object {
        fun open(context: Context): KeyboardDatabase =
            Room.databaseBuilder(context, KeyboardDatabase::class.java, "keyboard.db").build()
    }
}
