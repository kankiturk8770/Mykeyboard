package cafe.kankipro.keyboard.ui.settings

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cafe.kankipro.keyboard.KeyboardApp
import cafe.kankipro.keyboard.data.backup.BackupManager
import cafe.kankipro.keyboard.data.macros.Macro
import cafe.kankipro.keyboard.data.settings.KbSettings
import cafe.kankipro.keyboard.engine.voice.VoiceModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** وضعیت و کارهای برنامه‌ی تنظیمات (MVVM: صفحه فقط نشان می‌دهد، کار این‌جاست). */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KeyboardApp

    val settings: StateFlow<KbSettings> = app.settings.state
    val macros: StateFlow<List<Macro>> = app.macros.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val voiceCatalog: List<VoiceModelManager.ModelInfo> = app.voiceModels.catalog
    fun voiceState(lang: String) = app.voiceModels.state(lang)

    private val _counts = MutableStateFlow(Counts())
    val counts: StateFlow<Counts> = _counts
    data class Counts(val clips: Int = 0, val words: Int = 0)

    private val _setup = MutableStateFlow(Setup())
    val setup: StateFlow<Setup> = _setup
    data class Setup(val enabled: Boolean = false, val selected: Boolean = false)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun toast(msg: String?) { _toast.value = msg }

    fun update(change: (KbSettings) -> KbSettings) = app.settings.update(change)

    fun refresh() {
        val ctx = getApplication<Application>()
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val mine = imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
        val current = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        _setup.value = Setup(mine, current.startsWith(ctx.packageName + "/"))
        viewModelScope.launch(Dispatchers.IO) { _counts.value = Counts(app.clipboard.count(), app.words.size()) }
    }

    fun downloadVoice(lang: String) = app.voiceModels.download(lang)
    fun cancelVoice(lang: String) = app.voiceModels.cancel(lang)
    fun deleteVoice(lang: String) = app.voiceModels.delete(lang)

    fun saveMacro(m: Macro) = app.macros.save(m)
    fun deleteMacro(id: Long) = app.macros.delete(id)

    fun clearClipboard() { app.clipboard.clearUnpinned(); refresh() }
    fun clearWords() = viewModelScope.launch(Dispatchers.IO) { app.words.clear(); refresh() }

    fun addRawPackage(p: String): Boolean {
        val pkg = p.trim()
        if (!KbSettings.validPackage(pkg)) return false
        update { it.copy(rawPackages = (it.rawPackages + pkg).distinct()) }
        return true
    }

    fun removeRawPackage(p: String) = update { it.copy(rawPackages = it.rawPackages - p, rawOverrides = it.rawOverrides - p) }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) { app.backup.export() }

    suspend fun importBackup(json: String): BackupManager.Result = withContext(Dispatchers.IO) { app.backup.import(json) }
}
