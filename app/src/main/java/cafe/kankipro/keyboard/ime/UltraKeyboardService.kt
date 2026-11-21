package cafe.kankipro.keyboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cafe.kankipro.keyboard.KeyboardApp
import cafe.kankipro.keyboard.core.L
import cafe.kankipro.keyboard.core.t
import cafe.kankipro.keyboard.data.macros.MacroExpander
import cafe.kankipro.keyboard.data.settings.KbSettings
import cafe.kankipro.keyboard.engine.typography.PersianTypographyEngine
import cafe.kankipro.keyboard.engine.typography.UnicodeFontStyler
import cafe.kankipro.keyboard.engine.voice.OfflineVoiceEngine
import cafe.kankipro.keyboard.engine.voice.SpokenPunctuation
import cafe.kankipro.keyboard.ui.keyboard.FeedbackEngine
import cafe.kankipro.keyboard.ui.keyboard.Key
import cafe.kankipro.keyboard.ui.keyboard.KeyboardCanvasView
import cafe.kankipro.keyboard.ui.keyboard.KeyboardLayouts
import cafe.kankipro.keyboard.ui.keyboard.KeyboardTheme
import cafe.kankipro.keyboard.ui.keyboard.Layer
import cafe.kankipro.keyboard.ui.keyboard.TopBarView
import cafe.kankipro.keyboard.ui.panels.ImeActions
import cafe.kankipro.keyboard.ui.panels.Panel
import cafe.kankipro.keyboard.ui.panels.PanelColors
import cafe.kankipro.keyboard.ui.panels.PanelDeps
import cafe.kankipro.keyboard.ui.panels.PanelHost
import cafe.kankipro.keyboard.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.BreakIterator

/**
 * سرویس کیبورد: همه‌ی ورودی از این‌جا به برنامه‌ی دیگر می‌رود.
 * مسیر هر کلید: لمس (`KeyboardCanvasView`) ← `onKey` ← مسیر RICH (متن ترکیبی + پیشنهاد + تصحیح + کشیده + میان‌بر)
 * یا RAW (`KeySender`: commitText و KeyEvent واقعی برای ترمینال و بازی) ← `InputConnection` برنامه.
 */
class UltraKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner,
    KeyboardCanvasView.Listener, TopBarView.Listener, ImeActions {

    // ---------------------------------------------------------------- چرخه‌ی عمر برای Compose داخل کیبورد
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var app: KeyboardApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var feedback: FeedbackEngine
    private lateinit var sender: KeySender
    private lateinit var voice: OfflineVoiceEngine

    private var keyboard: KeyboardCanvasView? = null
    private var topBar: TopBarView? = null
    private var panelView: ComposeView? = null
    private val panelState = mutableStateOf(Panel.NONE)
    private val panelColors = mutableStateOf(PanelColors.from(KeyboardTheme.preset("cyber")))

    // ---------------------------------------------------------------- حالت تایپ
    private val s: KbSettings get() = app.settings.value
    private var mode = InputMode.RICH
    private var privateField = false
    private var lang = "fa"
    private var tempLang: String? = null // زبان موقت فیلد ایمیل/آدرس/رمز
    private var layer = Layer.ALPHA
    private var shift = 0
    private var lastShiftTap = 0L
    private var enterAction = EditorInfo.IME_ACTION_UNSPECIFIED
    private var useEnterAction = false
    private var inputType = 0
    private val composing = StringBuilder()
    private var selStart = -1
    private var selEnd = -1
    private var lastSpaceAt = 0L
    private var undoCorrection: Triple<String, String, String>? = null // اصل، تصحیح، جداکننده
    /** کلمه‌ای که کاربر تصحیحش را برگرداند؛ دوباره تصحیح نمی‌شود. */
    private var keepAsTyped: String? = null
    private var liveStyle: String? = null
    private var lastReplaced: String? = null
    private var sourceWasSelection = false
    private var delSwipeBase = -1
    private var delSwipeText = ""
    private var voicePartial = false
    private var partialPrefix = ""
    private var voiceLang = "fa"

    private val curLang get() = tempLang ?: lang
    private val ic: InputConnection? get() = currentInputConnection

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        app = application as KeyboardApp
        feedback = FeedbackEngine(this)
        sender = KeySender { currentInputConnection }
        voice = OfflineVoiceEngine(this, app.voiceModels)
        lang = s.currentLang
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipListener)
        scope.launch { app.settings.state.collect { applySettings(it) } }
        scope.launch {
            while (isActive) {
                sweepClipboard()
                delay(15_000)
            }
        }
    }

    override fun onDestroy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(clipListener)
        voice.release()
        feedback.release()
        app.words.flush()
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---------------------------------------------------------------- ساخت نما
    override fun onCreateInputView(): View {
        window.window?.decorView?.let { d ->
            d.setViewTreeLifecycleOwner(this)
            d.setViewTreeSavedStateRegistryOwner(this)
            d.setViewTreeViewModelStoreOwner(this)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        val bar = TopBarView(this).also { it.listener = this }
        val frame = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        val kb = KeyboardCanvasView(this).also { it.listener = this }
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            visibility = View.GONE
            setContent {
                val px = (keyboard?.height ?: 0) + (topBar?.height ?: 0)
                val h = (px / resources.displayMetrics.density).dp
                PanelHost(panelState.value, panelColors.value, if (h.value > 0) h else 260.dp, this@UltraKeyboardService, panelDeps)
            }
        }
        frame.addView(kb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> kb.topHeadroom = v.height.toFloat() }
        keyboard = kb
        topBar = bar
        panelView = compose
        applySettings(s)
        return root
    }

    private val panelDeps by lazy { PanelDeps(app.clipboard, app.settings, getSharedPreferences("kb_panels", MODE_PRIVATE)) }

    private fun applySettings(st: KbSettings) {
        L.mode = st.uiLang
        val theme = KeyboardTheme.from(st)
        panelColors.value = PanelColors.from(theme)
        if (st.currentLang != lang && st.currentLang in st.languages) lang = st.currentLang
        if (lang !in st.languages) lang = st.languages.first()
        keyboard?.apply {
            this.theme = theme
            heightScale = st.heightPct / 100f
            gapDp = st.keyGapDp
            fontScale = st.fontScale
            oneHanded = st.oneHanded
            keyPreview = st.keyPreview
            longPressMs = st.longPressMs.toLong()
        }
        topBar?.apply { this.theme = theme; heightScale = st.heightPct / 100f }
        if (st.soundOn) feedback.prepare(st.soundProfile) else feedback.release()
        applyBlur(st)
        rebuildLayout()
    }

    private fun applyBlur(st: KbSettings) {
        val w = window?.window ?: return
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val wm = getSystemService(WindowManager::class.java)
            val on = st.windowBlur && wm.isCrossWindowBlurEnabled && st.themeId != "amoled" && st.glassAlpha < 100
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setBackgroundBlurRadius(if (on) (28 * resources.displayMetrics.density).toInt() else 0)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- شروع و پایان هر فیلد
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val st = s
        mode = TerminalDetector.detect(info, st)
        privateField = TerminalDetector.isPrivate(info)
        inputType = info?.inputType ?: 0
        enterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        useEnterAction = !noEnterAction && !multiLine && enterAction != EditorInfo.IME_ACTION_NONE && enterAction != EditorInfo.IME_ACTION_UNSPECIFIED
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        layer = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layer.NUMBER
            InputType.TYPE_CLASS_PHONE -> Layer.PHONE
            else -> Layer.ALPHA
        }
        tempLang = if (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ) && "en" in st.languages) "en" else null
        composing.setLength(0)
        undoCorrection = null
        lastReplaced = null
        selStart = info?.initialSelStart ?: -1
        selEnd = info?.initialSelEnd ?: -1
        sender.ctrl = 0; sender.alt = 0
        shift = 0
        closePanel()
        topBar?.apply {
            val raw = this@UltraKeyboardService.mode == InputMode.RAW
            rawMode = raw
            mode = if (raw) TopBarView.Mode.TERMINAL else TopBarView.Mode.ICONS
            suggestions = emptyList()
            ctrlState = 0; altState = 0
            liveStyleName = liveStyle?.let { id -> UnicodeFontStyler.styles.firstOrNull { it.id == id }?.name }
        }
        rebuildLayout()
        updateAutoCaps()
        scope.launch { sweepClipboard() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (voice.isRunning) voice.stop()
        finishComposing()
        keyboard?.cancelAll()
        closePanel()
        app.words.flush()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (composing.isNotEmpty() && !voicePartial && (candidatesEnd == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // نشانگر از کلمه‌ی در حال تایپ بیرون رفت
            composing.setLength(0)
            ic?.finishComposingText()
            showSuggestions(emptyList())
        }
        if (composing.isEmpty() && delSwipeBase < 0) updateAutoCaps()
    }

    // ---------------------------------------------------------------- چیدمان
    private fun rebuildLayout() {
        val kb = keyboard ?: return
        val st = s
        val effLayer = if (layer == Layer.SHIFT_MARKS && curLang == "en") Layer.ALPHA else layer
        val opts = KeyboardLayouts.Options(
            persianDigits = st.persianDigits,
            numberRow = st.numberRow,
            shifted = shift > 0 && curLang == "en",
            enterLabel = if (useEnterAction) KeyboardLayouts.enterLabelFor(enterAction) else "⏎",
        )
        kb.layout = KeyboardLayouts.build(curLang, effLayer, opts)
        kb.shiftState = if (effLayer == Layer.SHIFT_MARKS) 1 else shift
        kb.spaceLabel = when (curLang) { "fa" -> "فارسی"; "ar" -> "عربي"; else -> "English" }.let { if (mode == InputMode.RAW) "$it  ⌁ RAW" else it }
    }

    private fun updateAutoCaps() {
        if (!s.autoCaps || curLang != "en" || layer != Layer.ALPHA || shift == 2 || mode == InputMode.RAW) {
            if (shift == 1 && curLang != "en") { shift = 0; rebuildLayout() }
            return
        }
        val caps = (ic?.getCursorCapsMode(inputType) ?: 0) != 0 && inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val want = if (caps) 1 else 0
        if (want != shift) { shift = want; rebuildLayout() }
    }

    // ---------------------------------------------------------------- لمس کلیدها
    override fun onKeyDown(key: Key) {
        val st = s
        if (st.hapticOn) feedback.haptic(st.hapticStrength)
        if (st.soundOn) feedback.play(
            when (key.code) {
                Key.SPACE -> FeedbackEngine.Sound.SPACE
                Key.DELETE -> FeedbackEngine.Sound.DELETE
                Key.ENTER -> FeedbackEngine.Sound.ENTER
                else -> FeedbackEngine.Sound.NORMAL
            }, st.soundVolume,
        )
    }

    override fun onKey(key: Key, text: String?) {
        when (key.code) {
            Key.SHIFT -> onShift()
            Key.SYMBOLS -> { layer = Layer.SYMBOLS; rebuildLayout() }
            Key.SYMBOLS2 -> { layer = Layer.SYMBOLS2; rebuildLayout() }
            Key.ALPHA -> { layer = Layer.ALPHA; rebuildLayout(); updateAutoCaps() }
            Key.NUMPAD -> { layer = Layer.NUMBER; rebuildLayout() }
            Key.GLOBE -> nextLanguage()
            Key.ENTER -> onEnter()
            Key.DELETE -> onDelete()
            else -> {
                val out = text ?: key.text
                onText(out)
                if (layer == Layer.SHIFT_MARKS) { layer = Layer.ALPHA; rebuildLayout() }
                else if ((layer == Layer.SYMBOLS || layer == Layer.SYMBOLS2) && out == " ") { layer = Layer.ALPHA; rebuildLayout() }
            }
        }
    }

    private fun onShift() {
        if (curLang != "en") {
            layer = if (layer == Layer.SHIFT_MARKS) Layer.ALPHA else Layer.SHIFT_MARKS
            rebuildLayout()
            return
        }
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 0 -> 1
            shift == 1 && now - lastShiftTap < 400 -> 2
            else -> 0
        }
        lastShiftTap = now
        rebuildLayout()
    }

    private fun nextLanguage() {
        val langs = s.languages
        if (langs.size < 2) return
        finishComposing()
        tempLang = null
        lang = langs[(langs.indexOf(lang) + 1) % langs.size]
        shift = 0
        if (layer == Layer.SHIFT_MARKS) layer = Layer.ALPHA
        app.settings.update { it.copy(currentLang = lang) }
        rebuildLayout()
        updateAutoCaps()
    }

    private fun isSeparator(t: String): Boolean = t.length == 1 && t[0] in SEPARATORS

    private fun isWordChar(t: String): Boolean = t.isNotEmpty() && t.all {
        it.isLetter() || PersianTypographyEngine.isMark(it) || it == PersianTypographyEngine.ZWNJ || it == PersianTypographyEngine.TATWEEL ||
            (curLang == "en" && it == '\'')
    }

    private fun onText(raw: String) {
        if (isSeparator(raw)) { onSeparator(raw); return }
        var out = raw
        val style = liveStyle
        if (style != null && out.length == 1 && out[0].code < 128 && out[0].isLetterOrDigit()) out = UnicodeFontStyler.apply(out, style)
        val c = ic ?: return
        undoCorrection = null
        if (sender.hasModifier) {
            finishComposing()
            sender.text(out)
            syncModifiers()
            return
        }
        if (mode == InputMode.RAW) {
            sender.text(out)
            if (shift == 1) { shift = 0; rebuildLayout() }
            return
        }
        // کشیده‌نویس هوشمند
        val st = s
        if (st.kashida > 0 && (curLang == "fa" || curLang == "ar") && out.length == 1 && PersianTypographyEngine.isPersianLetter(out[0]) && style == null) {
            val before: CharSequence = if (composing.isNotEmpty()) composing else c.getTextBeforeCursor(8, 0) ?: ""
            val n = PersianTypographyEngine.kashidaCount(before, out[0], st.kashida)
            if (n > 0) out = PersianTypographyEngine.TATWEEL.toString().repeat(n) + out
        }
        val composeWords = st.suggestions && !privateField && style == null && layer != Layer.NUMBER && layer != Layer.PHONE
        if (composeWords && isWordChar(out)) {
            composing.append(out)
            c.setComposingText(composing, 1)
            updateSuggestions()
        } else {
            finishComposing()
            c.commitText(out, 1)
            if (st.macrosOn && !privateField) expandMacro(afterSeparator = false)
        }
        if (shift == 1) { shift = 0; rebuildLayout() }
    }

    private fun onSeparator(sep: String) {
        val c = ic ?: return
        if (sender.hasModifier) { finishComposing(); sender.text(sep); syncModifiers(); return }
        if (mode == InputMode.RAW) {
            if (sep == "\n") sender.key(KeyEvent.KEYCODE_ENTER) else sender.text(sep)
            return
        }
        val st = s
        val now = SystemClock.uptimeMillis()
        if (sep == " " && st.doubleSpacePeriod && curLang == "en" && composing.isEmpty() && now - lastSpaceAt < 500) {
            val before = c.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                c.deleteSurroundingText(1, 0)
                c.commitText(". ", 1)
                lastSpaceAt = 0
                updateAutoCaps()
                return
            }
        }
        undoCorrection = null
        if (composing.isNotEmpty()) {
            val word = composing.toString()
            val fixed = if (st.autocorrect && !privateField && liveStyle == null && sep in AUTOCORRECT_ON && word != keepAsTyped) app.suggestions.autocorrect(word, curLang) else null
            keepAsTyped = null
            val final = fixed ?: word
            c.commitText(final, 1)
            if (fixed != null && fixed != word) undoCorrection = Triple(word, fixed, sep)
            if (st.learnWords && !privateField) app.suggestions.learn(final, curLang)
            composing.setLength(0)
        }
        // میان‌بر فقط‌حرف: قبل از نوشتن جداکننده
        if (st.macrosOn && !privateField) expandMacro(afterSeparator = true)
        c.commitText(sep, 1)
        if (sep == " ") lastSpaceAt = now
        showSuggestions(emptyList())
        updateAutoCaps()
    }

    private fun expandMacro(afterSeparator: Boolean) {
        val c = ic ?: return
        val before = c.getTextBeforeCursor(40, 0) ?: return
        val hit = MacroExpander.find(before, app.macros.active.value, afterSeparator) ?: return
        c.beginBatchEdit()
        c.deleteSurroundingText(hit.deleteCount, 0)
        c.commitText(hit.macro.expansion, 1)
        c.endBatchEdit()
    }

    private fun onEnter() {
        val c = ic ?: return
        if (mode == InputMode.RAW || sender.hasModifier) { sender.key(KeyEvent.KEYCODE_ENTER); syncModifiers(); return }
        if (composing.isNotEmpty()) {
            val word = composing.toString()
            if (s.learnWords && !privateField) app.suggestions.learn(word, curLang)
            c.commitText(word, 1)
            composing.setLength(0)
        }
        undoCorrection = null
        showSuggestions(emptyList())
        when {
            useEnterAction -> c.performEditorAction(enterAction)
            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL -> sender.key(KeyEvent.KEYCODE_ENTER)
            else -> c.commitText("\n", 1)
        }
        updateAutoCaps()
    }

    private fun onDelete() {
        val c = ic ?: return
        if (mode == InputMode.RAW || sender.hasModifier) { sender.key(KeyEvent.KEYCODE_DEL); syncModifiers(); return }
        undoCorrection?.let { (orig, fixed, sep) ->
            undoCorrection = null
            val before = c.getTextBeforeCursor(fixed.length + sep.length, 0)?.toString()
            if (before == fixed + sep) {
                c.beginBatchEdit()
                c.deleteSurroundingText(fixed.length + sep.length, 0)
                composing.setLength(0); composing.append(orig)
                keepAsTyped = orig
                c.setComposingText(composing, 1)
                c.endBatchEdit()
                updateSuggestions()
                return
            }
        }
        val selected = c.getSelectedText(0)
        if (!selected.isNullOrEmpty()) { finishComposing(); c.commitText("", 1); return }
        if (composing.isNotEmpty()) {
            val n = if (s.kashida > 0) PersianTypographyEngine.deleteLength(composing) else lastGraphemeLength(composing)
            composing.setLength((composing.length - n).coerceAtLeast(0))
            if (composing.isEmpty()) { c.commitText("", 1); showSuggestions(emptyList()) }
            else { c.setComposingText(composing, 1); updateSuggestions() }
            return
        }
        val before = c.getTextBeforeCursor(32, 0)
        if (before.isNullOrEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        val n = if (s.kashida > 0) PersianTypographyEngine.deleteLength(before) else lastGraphemeLength(before)
        c.deleteSurroundingText(n, 0)
        // جای نشانگر هنوز از onUpdateSelection نیامده؛ خودمان حساب می‌کنیم
        restartComposing(cursorNow())
    }

    /**
     * جای نشانگر همین الان، از خود برنامه (onUpdateSelection دیر می‌رسد و بعد از پاک کردن هنوز عدد قبلی را دارد).
     * ۱- = انتخاب هست یا معلوم نیست.
     */
    private fun cursorNow(): Int {
        val c = ic ?: return -1
        val et = try { c.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) } catch (_: Exception) { null }
            ?: return if (selStart >= 0 && selStart == selEnd) selStart else -1
        if (et.selectionStart != et.selectionEnd || et.selectionStart < 0) return -1
        return et.startOffset + et.selectionStart
    }

    /** بعد از پاک کردن، اگر نشانگر ته یک کلمه است، همان کلمه دوباره «در حال تایپ» شود (پیشنهادها برمی‌گردند). */
    private fun restartComposing(cursor: Int) {
        val c = ic ?: return
        if (!s.suggestions || privateField || liveStyle != null || cursor < 0) return
        val before = c.getTextBeforeCursor(48, 0)?.toString() ?: return
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1].toString())) i--
        val word = before.substring(i)
        if (word.isEmpty() || word.length > 32) return
        val after = c.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (after.isNotEmpty() && isWordChar(after)) return
        if (cursor < word.length) return
        c.setComposingRegion(cursor - word.length, cursor)
        composing.setLength(0)
        composing.append(word)
        updateSuggestions()
    }

    private fun finishComposing() {
        if (composing.isEmpty()) return
        val word = composing.toString()
        if (s.learnWords && !privateField && !voicePartial) app.suggestions.learn(word, curLang)
        composing.setLength(0)
        ic?.finishComposingText()
        showSuggestions(emptyList())
    }

    // ---------------------------------------------------------------- پیشنهادها
    private fun updateSuggestions() {
        if (!s.suggestions || privateField || composing.isEmpty()) { showSuggestions(emptyList()); return }
        val word = PersianTypographyEngine.stripKashida(composing.toString())
        showSuggestions(app.suggestions.suggest(word, curLang))
    }

    private fun showSuggestions(list: List<String>) {
        val bar = topBar ?: return
        bar.suggestions = list
        if (bar.mode == TopBarView.Mode.VOICE || bar.mode == TopBarView.Mode.TERMINAL) return
        bar.mode = if (list.isEmpty()) TopBarView.Mode.ICONS else TopBarView.Mode.SUGGESTIONS
    }

    override fun onSuggestion(index: Int, text: String) {
        val c = ic ?: return
        if (composing.isEmpty()) return
        var out = text
        // کشیده‌ی کاربر روی کلمه‌ی پیشنهادی هم بنشیند
        if (s.kashida > 0 && (curLang == "fa" || curLang == "ar")) out = PersianTypographyEngine.applyKashida(out, s.kashida)
        c.commitText("$out ", 1)
        if (s.learnWords && !privateField) app.suggestions.learn(text, curLang)
        composing.setLength(0)
        undoCorrection = null
        lastSpaceAt = SystemClock.uptimeMillis()
        showSuggestions(emptyList())
        updateAutoCaps()
    }

    override fun onSuggestionLong(text: String) {
        app.words.forget(if (curLang == "en") text.lowercase() else text)
        topBar?.flash(t("«$text» از کلمه‌های یادگرفته پاک شد.", "Removed \"$text\" from learned words."))
        updateSuggestions()
    }

    // ---------------------------------------------------------------- اشاره‌ها
    override fun onCursor(steps: Int) {
        finishComposing()
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            sendDownUpKeyEvents(code)
            if (s.hapticOn) feedback.tick(s.hapticStrength)
        }
    }

    override fun onSpaceSwipeUp() = nextLanguage()

    override fun onSpaceLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    override fun onDeleteRepeat() = onDelete()

    override fun onDeleteSwipe(words: Int, done: Boolean) {
        val c = ic ?: return
        if (mode == InputMode.RAW) {
            if (done && words > 0) {
                val before = c.getTextBeforeCursor(400, 0)?.toString() ?: ""
                // ترمینال متن قبل از نشانگر را نمی‌دهد: Ctrl+W (پاک کردن کلمه در شل) به تعداد کلمه‌ها
                if (before.isEmpty()) repeat(words) { sender.key(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON) }
                else repeat(wordsBackLength(before, words)) { sender.key(KeyEvent.KEYCODE_DEL) }
            }
            return
        }
        if (delSwipeBase < 0) {
            if (words == 0 && done) return
            finishComposing()
            val cur = cursorNow()
            if (cur < 0) { if (done) onDelete(); return }
            delSwipeBase = cur
            delSwipeText = c.getTextBeforeCursor(400, 0)?.toString() ?: ""
        }
        val len = wordsBackLength(delSwipeText, words)
        val base = delSwipeBase
        if (done) {
            delSwipeBase = -1
            if (len > 0) { c.setSelection(base - len, base); c.commitText("", 1) } else c.setSelection(base, base)
            restartComposing(base - len)
        } else {
            c.setSelection(base - len, base)
        }
    }

    override fun onOneHanded(side: String) = app.settings.update { it.copy(oneHanded = side) }

    // ---------------------------------------------------------------- نوار بالا
    override fun onIcon(id: String) {
        when (id) {
            "terminal" -> topBar?.mode = TopBarView.Mode.TERMINAL
            "terminal_close" -> topBar?.mode = if (composing.isEmpty()) TopBarView.Mode.ICONS else TopBarView.Mode.SUGGESTIONS
            "clipboard" -> openPanel(Panel.CLIPBOARD)
            "emoji" -> openPanel(Panel.EMOJI)
            "fonts" -> openPanel(Panel.FONTS)
            "onehand" -> app.settings.update {
                it.copy(oneHanded = when (it.oneHanded) { "off" -> "right"; "right" -> "left"; else -> "off" })
            }
            "mic" -> toggleVoice()
            "settings" -> openSettings(null)
        }
    }

    override fun onTermKey(id: String) {
        when (id) {
            "raw" -> {
                val pkg = currentInputEditorInfo?.packageName ?: return
                val next = mode != InputMode.RAW
                finishComposing()
                app.settings.update { it.copy(rawOverrides = it.rawOverrides + (pkg to next)) }
                mode = if (next) InputMode.RAW else InputMode.RICH
                topBar?.rawMode = next
                topBar?.flash(if (next) t("حالت خام (ترمینال) روشن شد برای این برنامه.", "Raw (terminal) mode on for this app.")
                    else t("حالت خام خاموش شد برای این برنامه.", "Raw mode off for this app."), 1800)
                rebuildLayout()
            }
            "ctrl" -> { sender.tapCtrl(); syncModifiers() }
            "alt" -> { sender.tapAlt(); syncModifiers() }
            "esc" -> termKey(KeyEvent.KEYCODE_ESCAPE)
            "tab" -> termKey(KeyEvent.KEYCODE_TAB)
            "left" -> termKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> termKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "up" -> termKey(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> termKey(KeyEvent.KEYCODE_DPAD_DOWN)
            "home" -> termKey(KeyEvent.KEYCODE_MOVE_HOME)
            "end" -> termKey(KeyEvent.KEYCODE_MOVE_END)
            "pgup" -> termKey(KeyEvent.KEYCODE_PAGE_UP)
            "pgdn" -> termKey(KeyEvent.KEYCODE_PAGE_DOWN)
            else -> { finishComposing(); sender.text(id); syncModifiers() }
        }
        if (s.hapticOn) feedback.haptic(s.hapticStrength / 2)
    }

    private fun termKey(code: Int) {
        finishComposing()
        sender.key(code)
        syncModifiers()
    }

    private fun syncModifiers() {
        topBar?.ctrlState = sender.ctrl
        topBar?.altState = sender.alt
    }

    override fun onVoiceStop() { voice.stop() }

    override fun onLiveStyleOff() = setLiveStyle(null, null)

    // ---------------------------------------------------------------- صدا
    private fun toggleVoice() {
        if (voice.isRunning) { voice.stop(); return }
        if (!voice.hasPermission()) {
            topBar?.flash(t("اجازه‌ی میکروفون را در برنامه بده.", "Allow the microphone in the app."))
            openSettings("mic")
            return
        }
        val want = if (curLang == "fa" || curLang == "en") curLang else s.voiceLang
        voiceLang = when {
            app.voiceModels.isInstalled(want) -> want
            app.voiceModels.isInstalled(s.voiceLang) -> s.voiceLang
            else -> {
                topBar?.flash(t("اول مدل صدای این زبان را در تنظیمات › صدا دانلود کن.", "Download this language's voice model in Settings › Voice first."))
                openSettings("voice")
                return
            }
        }
        finishComposing()
        topBar?.voiceLang = voiceLang
        topBar?.voiceText = t("در حال آماده شدن…", "Getting ready…")
        topBar?.mode = TopBarView.Mode.VOICE
        voice.start(voiceLang, voiceListener)
    }

    private val voiceListener = object : OfflineVoiceEngine.Listener {
        override fun onReady() { topBar?.voiceText = t("بگو…", "Speak…") }

        override fun onPartial(text: String) {
            topBar?.voiceText = text
            if (mode == InputMode.RAW || privateField) return
            val c = ic ?: return
            if (text.isEmpty()) { if (voicePartial) c.commitText("", 1); voicePartial = false; return }
            if (!voicePartial) partialPrefix = leadingSpace(c, text)
            voicePartial = true
            c.setComposingText(partialPrefix + text, 1)
        }

        override fun onFinal(text: String) {
            val c = ic ?: return
            // متن موقت (جزئی) اول برداشته می‌شود تا فاصله و بزرگی حرف از روی متن واقعی قبلی حساب شود
            if (voicePartial) { c.commitText("", 1); voicePartial = false }
            val before = c.getTextBeforeCursor(64, 0)
            val out = SpokenPunctuation.process(text, voiceLang, s.voicePunctuation, SpokenPunctuation.endsSentence(before))
            if (out.isEmpty()) return
            val prefix = if (out[0] in ".,،؛;:!?؟)»\n") "" else leadingSpace(c, out)
            if (mode == InputMode.RAW || privateField) sender.text(prefix + out) else c.commitText(prefix + out, 1)
            topBar?.voiceText = ""
        }

        override fun onLevel(level: Float) { topBar?.pushLevel(level) }

        override fun onError(reason: OfflineVoiceEngine.Reason) {
            topBar?.mode = TopBarView.Mode.ICONS
            topBar?.flash(when (reason) {
                OfflineVoiceEngine.Reason.NO_PERMISSION -> t("اجازه‌ی میکروفون نیست.", "Microphone permission is missing.")
                OfflineVoiceEngine.Reason.NO_MODEL -> t("مدل صدا نصب نیست.", "Voice model is not installed.")
                OfflineVoiceEngine.Reason.MIC_BUSY -> t("میکروفون دست برنامه‌ی دیگری است.", "Another app is using the microphone.")
                OfflineVoiceEngine.Reason.MODEL_ERROR -> t("مدل صدا خراب است؛ در تنظیمات دوباره دانلودش کن.", "The voice model is damaged; download it again in Settings.")
            })
        }

        override fun onStopped() {
            if (voicePartial) { ic?.finishComposingText(); voicePartial = false }
            topBar?.voiceText = ""
            if (topBar?.mode == TopBarView.Mode.VOICE) topBar?.mode = if (mode == InputMode.RAW) TopBarView.Mode.TERMINAL else TopBarView.Mode.ICONS
        }
    }

    private fun leadingSpace(c: InputConnection, text: String): String {
        if (voicePartial) return "" // متن موقت همان جای قبلی را عوض می‌کند
        val b = c.getTextBeforeCursor(1, 0)?.toString() ?: ""
        return if (b.isEmpty() || b[0].isWhitespace() || text.isEmpty()) "" else " "
    }

    // ---------------------------------------------------------------- پنل‌ها
    private fun openPanel(p: Panel) {
        finishComposing()
        lastReplaced = null
        panelState.value = p
        panelView?.visibility = View.VISIBLE
        keyboard?.visibility = View.INVISIBLE
        topBar?.visibility = View.GONE
    }

    override fun closePanel() {
        if (panelState.value == Panel.NONE && panelView?.visibility != View.VISIBLE) return
        panelState.value = Panel.NONE
        panelView?.visibility = View.GONE
        keyboard?.visibility = View.VISIBLE
        topBar?.visibility = View.VISIBLE
    }

    override fun commit(text: String) {
        val c = ic ?: return
        finishComposing()
        if (mode == InputMode.RAW) sender.text(text) else c.commitText(text, 1)
    }

    override fun deleteBackward() = onDelete()

    override fun sourceText(): String {
        val c = ic ?: return ""
        val sel = c.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) { sourceWasSelection = true; return sel.take(200) }
        sourceWasSelection = false
        val before = c.getTextBeforeCursor(64, 0)?.toString() ?: return ""
        var i = before.length
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        return before.substring(i).take(40)
    }

    override fun replaceSource(newText: String) {
        val c = ic ?: return
        c.beginBatchEdit()
        val sel = c.getSelectedText(0)?.toString()
        when {
            !sel.isNullOrEmpty() -> c.commitText(newText, 1)
            lastReplaced != null && c.getTextBeforeCursor(lastReplaced!!.length, 0)?.toString() == lastReplaced ->
                { c.deleteSurroundingText(lastReplaced!!.length, 0); c.commitText(newText, 1) }
            else -> {
                val word = sourceText()
                if (word.isNotEmpty()) c.deleteSurroundingText(word.length, 0)
                c.commitText(newText, 1)
            }
        }
        c.endBatchEdit()
        lastReplaced = newText
    }

    override fun setLiveStyle(id: String?, name: String?) {
        finishComposing()
        liveStyle = id
        topBar?.liveStyleName = name
        if (id != null) showSuggestions(emptyList())
    }

    override fun setKashida(level: Int) = app.settings.update { it.copy(kashida = level.coerceIn(0, 3)) }
    override fun kashida(): Int = s.kashida

    override fun openSettings(page: String?) {
        val i = Intent(this, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (page != null) i.putExtra(SettingsActivity.EXTRA_PAGE, page)
        startActivity(i)
    }

    // ---------------------------------------------------------------- کلیپ‌بورد
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!s.clipHistory) return@OnPrimaryClipChangedListener
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@OnPrimaryClipChangedListener
        try {
            val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
            val flagged = clip.description?.extras?.getBoolean(EXTRA_SENSITIVE) == true ||
                (Build.VERSION.SDK_INT >= 33 && clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
            // کپی از فیلد رمز همین الان: همیشه حساس
            app.clipboard.add(text, flaggedBySystem = flagged || privateField)
            // فقط دیباگ (برای تست خودکار): بی خود متن
            if (cafe.kankipro.keyboard.BuildConfig.DEBUG) android.util.Log.d("KbClip", "added len=${text.length} sensitive=${cafe.kankipro.keyboard.data.clipboard.SensitiveDetector.isSensitive(text, flagged || privateField)}")
        } catch (_: Exception) {
        }
    }

    private suspend fun sweepClipboard() {
        val st = s
        val gone = withContext(Dispatchers.IO) { runCatching { app.clipboard.sweep(st.clipAutoWipeMin, st.sensitiveWipeSec) }.getOrDefault(emptyList()) }
        if (gone.isEmpty() || Build.VERSION.SDK_INT < 28) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        try {
            val cur = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (cur != null && cur in gone) cm.clearPrimaryClip()
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- کمکی
    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (voice.isRunning) voice.stop()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    companion object {
        const val EXTRA_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private const val SEPARATORS = " .,،؛;:!?؟)»]}\"\n"
        private val AUTOCORRECT_ON = setOf(" ", ".", ",", "!", "?", ";", ":", "\n")

        fun lastGraphemeLength(s: CharSequence): Int {
            if (s.isEmpty()) return 0
            val str = s.toString()
            val it = BreakIterator.getCharacterInstance()
            it.setText(str)
            var prev = 0
            var b = it.first()
            while (true) {
                val n = it.next()
                if (n == BreakIterator.DONE) break
                prev = b; b = n
            }
            var len = (b - prev).coerceAtLeast(1)
            // هرگز نیمی از یک جفت surrogate (ایموجی) را جدا نکن
            val start = str.length - len
            if (start > 0 && Character.isLowSurrogate(str[start]) && Character.isHighSurrogate(str[start - 1])) len++
            return len
        }

        /** طول متنی که `n` کلمه‌ی آخر (با فاصله‌های بعدشان) می‌گیرند. */
        fun wordsBackLength(text: String, n: Int): Int {
            var i = text.length
            repeat(n) {
                while (i > 0 && text[i - 1].isWhitespace()) i--
                while (i > 0 && !text[i - 1].isWhitespace()) i--
            }
            return text.length - i
        }
    }
}
