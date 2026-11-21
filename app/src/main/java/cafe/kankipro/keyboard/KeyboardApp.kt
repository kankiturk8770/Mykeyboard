package cafe.kankipro.keyboard

import android.app.Application
import cafe.kankipro.keyboard.data.clipboard.ClipboardRepository
import cafe.kankipro.keyboard.data.clipboard.KeyboardDatabase
import cafe.kankipro.keyboard.data.macros.MacroRepository
import cafe.kankipro.keyboard.data.settings.SettingsStore
import cafe.kankipro.keyboard.data.words.WordStore
import cafe.kankipro.keyboard.engine.text.SuggestionEngine
import cafe.kankipro.keyboard.engine.voice.VoiceModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * نقطه‌ی شروع برنامه و جای همه‌ی اجزای مشترک (یک نمونه برای کیبورد و برنامه‌ی تنظیمات).
 * عمداً بدون کتابخانه‌ی تزریق وابستگی: کوچک و سریع.
 */
class KeyboardApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsStore(this) }
    val database by lazy { KeyboardDatabase.open(this) }
    val clipboard by lazy { ClipboardRepository(database.clipDao(), scope) }
    val macros by lazy { MacroRepository(database.macroDao(), scope) }
    val words by lazy { WordStore(filesDir) }
    val suggestions by lazy { SuggestionEngine(words) }
    val voiceModels by lazy { VoiceModelManager(filesDir, scope) }
    val backup by lazy { cafe.kankipro.keyboard.data.backup.BackupManager(settings, clipboard, macros) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: KeyboardApp
            private set
    }
}
