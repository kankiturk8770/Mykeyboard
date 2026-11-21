package cafe.kankipro.keyboard.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cafe.kankipro.keyboard.BuildConfig
import cafe.kankipro.keyboard.R
import cafe.kankipro.keyboard.core.L
import cafe.kankipro.keyboard.core.t
import cafe.kankipro.keyboard.data.macros.Macro
import cafe.kankipro.keyboard.data.macros.MacroExpander
import cafe.kankipro.keyboard.data.settings.KbSettings
import cafe.kankipro.keyboard.engine.voice.VoiceModelManager
import cafe.kankipro.keyboard.ime.TerminalDetector
import cafe.kankipro.keyboard.ui.keyboard.KeyboardCanvasView
import cafe.kankipro.keyboard.ui.keyboard.KeyboardLayouts
import cafe.kankipro.keyboard.ui.keyboard.KeyboardTheme
import cafe.kankipro.keyboard.ui.keyboard.Layer
import kotlinx.coroutines.delay

// ------------------------------------------------------------------------- رنگ‌های برنامه
internal object G {
    val bg = Color(0xFF070A14)
    val glass = Color(0x14FFFFFF)
    val line = Color(0x1FFFFFFF)
    val text = Color(0xFFEEF1FF)
    val sub = Color(0xFF8E95B5)
    val magenta = Color(0xFFFF2BD6)
    val cyan = Color(0xFF00E5FF)
    val warn = Color(0xFFFFC857)
    val good = Color(0xFF3DDC97)
    val neon = Brush.horizontalGradient(listOf(magenta, cyan))
}

private data class Page(val id: String, val icon: Int, val title: () -> String, val summary: (KbSettings) -> String)

private val PAGES = listOf(
    Page("theme", R.drawable.ic_emoji, { t("تم و شیشه", "Theme & glass") }) { s -> themeName(s.themeId) },
    Page("layout", R.drawable.ic_keyboard, { t("اندازه و چیدمان", "Size & layout") }) { s ->
        t("ارتفاع ${s.heightPct}٪", "Height ${s.heightPct}%") + if (s.oneHanded != "off") t("، یک‌دستی", ", one-handed") else ""
    },
    Page("feedback", R.drawable.ic_onehand, { t("لرزش و صدا", "Haptics & sound") }) { s ->
        (if (s.hapticOn) t("لرزش ${s.hapticStrength}٪", "Haptics ${s.hapticStrength}%") else t("بی لرزش", "No haptics")) +
            if (s.soundOn) t("، صدای کلید", ", key sound") else ""
    },
    Page("typing", R.drawable.ic_fonts, { t("تایپ و زبان‌ها", "Typing & languages") }) { s ->
        s.languages.joinToString("، ") { langName(it) } + if (s.kashida > 0) t("، کشیده", ", kashida") else ""
    },
    Page("terminal", R.drawable.ic_terminal, { t("ترمینال و بازی", "Terminal & games") }) { s ->
        if (s.terminalAuto) t("تشخیص خودکار روشن", "Auto-detect on") else t("فقط فهرست دستی", "Manual list only")
    },
    Page("voice", R.drawable.ic_mic, { t("تایپ صوتی آفلاین", "Offline voice typing") }) { _ -> t("بی اینترنت، روی خود گوشی", "On-device, no internet") },
    Page("clipboard", R.drawable.ic_clipboard, { t("کلیپ‌بورد", "Clipboard") }) { s ->
        if (!s.clipHistory) t("خاموش", "Off") else t("حساس‌ها بعد از ${s.sensitiveWipeSec} ثانیه پاک می‌شوند", "Sensitive items wiped after ${s.sensitiveWipeSec}s")
    },
    Page("macros", R.drawable.ic_pin, { t("میان‌برهای متنی", "Text shortcuts") }) { s -> if (s.macrosOn) t("روشن", "On") else t("خاموش", "Off") },
    Page("backup", R.drawable.ic_settings, { t("بکاپ و بازگردانی", "Backup & restore") }) { _ -> t("یک فایل JSON", "One JSON file") },
)

private fun themeName(id: String) = when (id) {
    "cyber" -> t("سایبرپانک نئون", "Cyberpunk Neon")
    "amoled" -> t("مشکی AMOLED", "AMOLED Pure Black")
    "obsidian" -> t("شیشه‌ی ابسیدین", "Frosted Obsidian")
    "ice" -> t("آبی یخی", "Ice Blue")
    else -> t("رنگ دلخواه", "Custom colors")
}

private fun langName(l: String) = when (l) { "fa" -> t("فارسی", "Persian"); "en" -> t("انگلیسی", "English"); "ar" -> t("عربی", "Arabic"); else -> l }

@Composable
fun SettingsRoot(vm: SettingsViewModel, startPage: String?, actions: SettingsActions) {
    val s by vm.settings.collectAsState()
    L.mode = s.uiLang
    val dir = if (L.fa) LayoutDirection.Rtl else LayoutDirection.Ltr
    var page by rememberSaveable { mutableStateOf(startPage) }
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) { if (toast != null) { delay(3000); vm.toast(null) } }
    MaterialTheme(colorScheme = darkColorScheme(primary = G.cyan, secondary = G.magenta, background = G.bg, surface = G.bg)) {
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            BackHandler(enabled = page != null) { page = null }
            Box(Modifier.fillMaxSize().background(G.bg).background(
                Brush.radialGradient(listOf(G.magenta.copy(alpha = 0.10f), Color.Transparent), radius = 900f)
            ).safeDrawingPadding()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    when (page) {
                        null -> Home(vm, s, actions) { page = it }
                        else -> {
                            val p = PAGES.firstOrNull { it.id == page }
                            PageHeader(p?.title?.invoke() ?: "") { page = null }
                            when (page) {
                                "theme" -> ThemePage(vm, s)
                                "layout" -> LayoutPage(vm, s)
                                "feedback" -> FeedbackPage(vm, s)
                                "typing" -> TypingPage(vm, s)
                                "terminal" -> TerminalPage(vm, s)
                                "voice" -> VoicePage(vm, s, actions)
                                "clipboard" -> ClipboardPage(vm, s)
                                "macros" -> MacrosPage(vm, s)
                                "backup" -> BackupPage(actions)
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
                toast?.let {
                    Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF0181C2E))
                        .border(1.dp, G.line, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(it, color = G.text, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------- خانه
@Composable
private fun Home(vm: SettingsViewModel, s: KbSettings, actions: SettingsActions, open: (String) -> Unit) {
    val setup by vm.setup.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(G.glass).border(1.5.dp, G.neon, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Text("K", color = G.text, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("کیبورد کانکی", "Kanki Keyboard"), color = G.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(t("فارسی و انگلیسی، ترمینال، صدا بی اینترنت", "Persian & English, terminal-ready, offline voice"), color = G.sub, fontSize = 13.sp)
        }
        UiLangSwitch(vm, s)
    }
    if (!setup.enabled || !setup.selected) {
        Glass {
            Text(t("راه‌اندازی در دو قدم", "Set up in two steps"), color = G.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            StepRow(1, t("کیبورد کانکی را در فهرست کیبوردهای گوشی روشن کن.", "Turn on Kanki Keyboard in your phone's keyboard list."), setup.enabled,
                t("روشن کردن", "Turn on"), actions::enableKeyboard)
            StepRow(2, t("کیبورد کانکی را به‌عنوان کیبورد فعلی انتخاب کن.", "Pick Kanki Keyboard as your current keyboard."), setup.selected,
                t("انتخاب", "Choose"), actions::chooseKeyboard, enabled = setup.enabled)
        }
    }
    var trial by remember { mutableStateOf("") }
    if (BuildConfig.DEBUG) LaunchedEffect(trial) { android.util.Log.d("KbTrial", "text=" + trial.replace("\n", "\\n")) }
    OutlinedTextField(trial, { trial = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).then(
        if (BuildConfig.DEBUG) Modifier.onGloballyPositioned { c ->
            // فقط دیباگ، برای تست خودکار روی شبیه‌ساز: جای فیلد امتحان
            val r = c.boundsInWindow()
            android.util.Log.d("KbTrial", "field=${r.center.x.toInt()}|${r.center.y.toInt()}")
        } else Modifier),
        placeholder = { Text(t("این‌جا امتحانش کن…", "Try it here…"), color = G.sub) }, colors = fieldColors(), minLines = 2)
    PAGES.forEach { p ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(G.glass)
                .border(1.dp, G.line, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { open(p.id) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A00E5FF)), contentAlignment = Alignment.Center) {
                Icon(painterResource(p.icon), null, tint = G.cyan, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title(), color = G.text, fontSize = 15.sp)
                Text(p.summary(s), color = G.sub, fontSize = 12.sp, maxLines = 1)
            }
            Text(if (L.fa) "‹" else "›", color = G.sub, fontSize = 22.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(t("نسخه‌ی ${BuildConfig.VERSION_NAME}. هیچ چیزی از گوشی بیرون نمی‌رود؛ اینترنت فقط برای دانلود مدل صدایی که خودت بخواهی.",
        "Version ${BuildConfig.VERSION_NAME}. Nothing leaves your phone; internet is used only to download a voice model you ask for."),
        color = G.sub, fontSize = 12.sp)
}

@Composable
private fun UiLangSwitch(vm: SettingsViewModel, s: KbSettings) {
    val label = when (s.uiLang) { "fa" -> "فا"; "en" -> "EN"; else -> t("خودکار", "Auto") }
    Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, G.line, RoundedCornerShape(50))
        .clickable(role = Role.Button, onClickLabel = t("زبان برنامه", "App language")) {
            vm.update { it.copy(uiLang = when (it.uiLang) { "system" -> "fa"; "fa" -> "en"; else -> "system" }) }
        }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text(label, color = G.text, fontSize = 13.sp) }
}

@Composable
private fun StepRow(n: Int, text: String, done: Boolean, button: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) G.good.copy(alpha = 0.2f) else G.glass), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else n.toString(), color = if (done) G.good else G.text, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = G.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!done) NeonButton(button, enabled = enabled, onClick = onClick)
    }
}

// ------------------------------------------------------------------------- اجزای مشترک
@Composable
private fun PageHeader(title: String, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = t("برگشت", "Back"), onClick = back),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_back), t("برگشت", "Back"), tint = G.text,
                modifier = Modifier.size(22.dp).then(if (L.fa) Modifier.graphicsMirror() else Modifier))
        }
        Text(title, color = G.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.graphicsMirror(): Modifier = this.graphicsLayer(scaleX = -1f)

@Composable
internal fun Glass(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(G.glass)
        .border(1.dp, G.line, RoundedCornerShape(18.dp)).padding(16.dp), content = content)
}

@Composable
internal fun Title(text: String, hint: String? = null) {
    Text(text, color = G.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    if (hint != null) Text(hint, color = G.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
}

@Composable
internal fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Title(label, hint) }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = G.magenta, checkedThumbColor = Color.White))
    }
}

@Composable
internal fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row { Text(label, color = G.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text(format(value), color = G.cyan, fontSize = 14.sp) }
        Slider(value, onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = G.cyan, activeTrackColor = G.magenta, inactiveTrackColor = G.line))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(Modifier.clip(RoundedCornerShape(50)).then(if (on) Modifier.background(G.neon) else Modifier.border(1.dp, G.line, RoundedCornerShape(50)))
                .clickable(role = Role.RadioButton) { onSelect(id) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label, color = if (on) Color.White else G.text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun NeonButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (enabled) G.neon else Brush.linearGradient(listOf(G.line, G.line)))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = if (enabled) Color.White else G.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GhostButton(label: String, color: Color = G.text, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, G.line, RoundedCornerShape(12.dp))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = color, fontSize = 14.sp)
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = G.text, unfocusedTextColor = G.text, focusedBorderColor = G.cyan, unfocusedBorderColor = G.line, cursorColor = G.cyan,
)

/** پیش‌نمایش زنده‌ی خود کیبورد (همان View واقعی). */
@Composable
internal fun KeyboardPreview(s: KbSettings) {
    AndroidView(
        factory = { KeyboardCanvasView(it) },
        update = { v ->
            v.theme = KeyboardTheme.from(s)
            v.heightScale = s.heightPct / 100f
            v.gapDp = s.keyGapDp
            v.fontScale = s.fontScale
            v.oneHanded = s.oneHanded
            v.keyPreview = s.keyPreview
            val l = s.currentLang
            v.layout = KeyboardLayouts.build(l, Layer.ALPHA, KeyboardLayouts.Options(s.persianDigits, s.numberRow))
            v.spaceLabel = langName(l)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)),
    )
}

// ------------------------------------------------------------------------- صفحه‌ها
@Composable
private fun ThemePage(vm: SettingsViewModel, s: KbSettings) {
    KeyboardPreview(s)
    Glass {
        Title(t("تم", "Theme"))
        Choice(KbSettings.THEMES.map { it to themeName(it) }, s.themeId) { id -> vm.update { it.copy(themeId = id) } }
    }
    if (s.themeId == "custom") {
        Glass {
            Title(t("رنگ‌های دلخواه", "Custom colors"), t("هر رنگ را با چرخه‌ی رنگ، روشنی و شفافیت بساز.", "Build each color from hue, brightness and opacity."))
            ColorPicker(t("زمینه", "Background"), s.customBg, withAlpha = false) { c -> vm.update { it.copy(customBg = c) } }
            ColorPicker(t("کلید", "Key"), s.customKey, withAlpha = true) { c -> vm.update { it.copy(customKey = c) } }
            ColorPicker(t("درخشش لبه", "Edge glow"), s.customGlow, withAlpha = false) { c -> vm.update { it.copy(customGlow = c) } }
            ColorPicker(t("متن", "Text"), s.customText, withAlpha = false) { c -> vm.update { it.copy(customText = c) } }
        }
    }
    Glass {
        SliderRow(t("ماتی زمینه", "Background opacity"), s.glassAlpha.toFloat(), 20f..100f, format = { "${it.toInt()}٪" }) { v ->
            vm.update { it.copy(glassAlpha = v.toInt()) }
        }
        SwitchRow(t("بلور پشت کیبورد", "Blur behind the keyboard"),
            t("اندروید ۱۲ به بعد، اگر گوشی پشتیبانی کند. در AMOLED خاموش است تا پیکسل‌ها خاموش بمانند.",
                "Android 12+, when the phone supports it. Off in AMOLED so pixels stay dark."), s.windowBlur) { v -> vm.update { it.copy(windowBlur = v) } }
    }
}

@Composable
private fun LayoutPage(vm: SettingsViewModel, s: KbSettings) {
    KeyboardPreview(s)
    Glass {
        SliderRow(t("ارتفاع", "Height"), s.heightPct.toFloat(), 70f..130f, steps = 11, format = { "${it.toInt()}٪" }) { v -> vm.update { it.copy(heightPct = v.toInt()) } }
        SliderRow(t("فاصله‌ی کلیدها", "Key gap"), s.keyGapDp, 0f..10f, steps = 9, format = { "${it.toInt()} dp" }) { v -> vm.update { it.copy(keyGapDp = v) } }
        SliderRow(t("اندازه‌ی حروف", "Label size"), s.fontScale, 0.8f..1.3f, steps = 9, format = { "${(it * 100).toInt()}٪" }) { v -> vm.update { it.copy(fontScale = v) } }
    }
    Glass {
        Title(t("یک‌دستی", "One-handed"), t("کیبورد کوچک‌تر به یک طرف می‌چسبد؛ از نوار بالا هم عوض می‌شود.", "A narrower keyboard hugs one side; also toggled from the top bar."))
        Choice(listOf("off" to t("خاموش", "Off"), "right" to t("راست", "Right"), "left" to t("چپ", "Left")), s.oneHanded) { v -> vm.update { it.copy(oneHanded = v) } }
        SwitchRow(t("ردیف عدد", "Number row"), null, s.numberRow) { v -> vm.update { it.copy(numberRow = v) } }
        SwitchRow(t("حباب کلید", "Key preview"), t("هنگام لمس حرف بزرگ بالای کلید", "Shows the letter above your finger"), s.keyPreview) { v -> vm.update { it.copy(keyPreview = v) } }
        SliderRow(t("مکث نگه داشتن", "Long-press delay"), s.longPressMs.toFloat(), 150f..800f, steps = 12, format = { "${it.toInt()} ms" }) { v ->
            vm.update { it.copy(longPressMs = v.toInt()) }
        }
    }
}

@Composable
private fun FeedbackPage(vm: SettingsViewModel, s: KbSettings) {
    Glass {
        SwitchRow(t("لرزش", "Haptics"), null, s.hapticOn) { v -> vm.update { it.copy(hapticOn = v) } }
        if (s.hapticOn) SliderRow(t("شدت لرزش", "Haptic strength"), s.hapticStrength.toFloat(), 0f..100f, format = { "${it.toInt()}٪" }) { v ->
            vm.update { it.copy(hapticStrength = v.toInt()) }
        }
    }
    Glass {
        SwitchRow(t("صدای کلید مکانیکی", "Mechanical key sound"), t("صدا با ریاضی ساخته می‌شود؛ هیچ فایل صوتی در برنامه نیست.", "Synthesized on the fly; no audio files in the app."), s.soundOn) { v ->
            vm.update { it.copy(soundOn = v) }
        }
        if (s.soundOn) {
            Choice(listOf("blue" to "Blue", "brown" to "Brown", "red" to "Red", "typewriter" to t("ماشین تحریر", "Typewriter")), s.soundProfile) { v ->
                vm.update { it.copy(soundProfile = v) }
            }
            SliderRow(t("بلندی", "Volume"), s.soundVolume.toFloat(), 0f..100f, format = { "${it.toInt()}٪" }) { v -> vm.update { it.copy(soundVolume = v.toInt()) } }
        }
    }
}

@Composable
private fun TypingPage(vm: SettingsViewModel, s: KbSettings) {
    Glass {
        Title(t("زبان‌ها", "Languages"), t("با کلید 🌐 یا کشیدن رو به بالا روی فاصله عوض می‌شوند.", "Switch with 🌐 or by swiping up on the space bar."))
        KbSettings.LANGS.forEach { l ->
            SwitchRow(langName(l), null, l in s.languages) { on ->
                vm.update {
                    val list = if (on) (it.languages + l).distinct() else (it.languages - l)
                    if (list.isEmpty()) it else it.copy(languages = list, currentLang = if (it.currentLang in list) it.currentLang else list.first())
                }
            }
        }
        SwitchRow(t("رقم فارسی", "Persian digits"), t("در صفحه‌ی فارسی ۱۲۳ به‌جای 123", "Persian layout types ۱۲۳ instead of 123"), s.persianDigits) { v -> vm.update { it.copy(persianDigits = v) } }
    }
    Glass {
        SwitchRow(t("پیشنهاد کلمه", "Word suggestions"), null, s.suggestions) { v -> vm.update { it.copy(suggestions = v) } }
        SwitchRow(t("تصحیح خودکار (انگلیسی)", "Auto-correct (English)"), t("پاک‌کن بلافاصله بعدش آن را برمی‌گرداند.", "Backspace right after undoes it."), s.autocorrect) { v -> vm.update { it.copy(autocorrect = v) } }
        SwitchRow(t("حرف بزرگ خودکار", "Auto-capitalize"), null, s.autoCaps) { v -> vm.update { it.copy(autoCaps = v) } }
        SwitchRow(t("دو فاصله = نقطه", "Double-space period"), null, s.doubleSpacePeriod) { v -> vm.update { it.copy(doubleSpacePeriod = v) } }
        SwitchRow(t("یادگیری کلمه‌های من", "Learn my words"), t("فقط روی همین گوشی؛ هرگز در فیلد رمز.", "Only on this phone; never in password fields."), s.learnWords) { v -> vm.update { it.copy(learnWords = v) } }
        val counts by vm.counts.collectAsState()
        if (counts.words > 0) GhostButton(t("پاک کردن ${counts.words} کلمه‌ی یادگرفته", "Forget ${counts.words} learned words"), G.warn) { vm.clearWords() }
    }
    Glass {
        Title(t("کشیده‌نویس هوشمند", "Smart kashida"), t("«ـ» فقط بین حرف‌هایی که به هم می‌چسبند، هنگام تایپ؛ «لا» و حرف‌های بی‌اتصال دست نمی‌خورند.",
            "Adds ـ only between letters that join, as you type; لا and non-joining letters stay intact."))
        Choice(listOf("0" to t("خاموش", "Off"), "1" to t("کم", "Low"), "2" to t("متوسط", "Medium"), "3" to t("زیاد", "High")), s.kashida.toString()) { v ->
            vm.update { it.copy(kashida = v.toInt()) }
        }
        val sample = cafe.kankipro.keyboard.engine.typography.PersianTypographyEngine.applyKashida("سلام کانکی گیم", s.kashida)
        Text(sample, color = G.cyan, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TerminalPage(vm: SettingsViewModel, s: KbSettings) {
    Glass {
        SwitchRow(t("تشخیص خودکار ترمینال و بازی", "Auto-detect terminals and games"),
            t("در Termux، کلاینت‌های SSH، فیلدهای بی‌نوع و بازی‌ها هر حرف مستقیم و کلیدها با KeyEvent واقعی می‌روند تا تایپ گیر نکند.",
                "In Termux, SSH clients, raw fields and games every key goes straight in, with real key events, so typing never freezes."),
            s.terminalAuto) { v -> vm.update { it.copy(terminalAuto = v) } }
        Text(t("همیشه خام: ", "Always raw: ") + TerminalDetector.KNOWN_RAW.take(6).joinToString("، ") + "…", color = G.sub, fontSize = 12.sp)
    }
    Glass {
        Title(t("برنامه‌های من", "My apps"), t("اسم بسته، مثلاً com.example.app. در کیبورد هم کلید RAW نوار ترمینال همین را برای برنامه‌ی باز عوض می‌کند.",
            "Package name, e.g. com.example.app. The RAW key on the terminal bar toggles it for the open app too."))
        var pkg by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(pkg, { pkg = it.trim() }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(),
                placeholder = { Text("com.example.app", color = G.sub) })
            Spacer(Modifier.width(8.dp))
            NeonButton(t("افزودن", "Add"), enabled = KbSettings.validPackage(pkg)) { if (vm.addRawPackage(pkg)) pkg = "" }
        }
        (s.rawPackages + s.rawOverrides.keys).distinct().forEach { p ->
            val state = s.rawOverrides[p]
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p, color = G.text, fontSize = 14.sp)
                    Text(when (state) { true -> t("خام (دستی)", "Raw (manual)"); false -> t("معمولی (دستی)", "Normal (manual)"); null -> t("خام", "Raw") },
                        color = G.sub, fontSize = 12.sp)
                }
                GhostButton(t("حذف", "Remove"), G.warn) { vm.removeRawPackage(p) }
            }
        }
    }
}

@Composable
private fun VoicePage(vm: SettingsViewModel, s: KbSettings, actions: SettingsActions) {
    var mic by remember { mutableStateOf(actions.hasMic()) }
    LaunchedEffect(Unit) { while (true) { mic = actions.hasMic(); delay(1000) } }
    Glass {
        Title(t("میکروفون", "Microphone"), t("صدا فقط روی گوشی پردازش می‌شود و هیچ‌جا فرستاده نمی‌شود.", "Audio is processed on the phone and never sent anywhere."))
        Spacer(Modifier.height(8.dp))
        if (mic) Text(t("اجازه داده شده", "Allowed"), color = G.good, fontSize = 14.sp)
        else NeonButton(t("اجازه دادن", "Allow microphone")) { actions.askMic() }
    }
    vm.voiceCatalog.forEach { m -> VoiceModelCard(vm, m) }
    Glass {
        Title(t("زبان پیش‌فرض صدا", "Default voice language"))
        Choice(listOf("fa" to t("فارسی", "Persian"), "en" to t("انگلیسی", "English")), s.voiceLang) { v -> vm.update { it.copy(voiceLang = v) } }
        SwitchRow(t("علامت‌گذاری با گفتن", "Spoken punctuation"),
            t("«نقطه»، «ویرگول»، «علامت سوال»، «خط بعدی» / period, comma, question mark, new line", "\"period\", \"comma\", \"question mark\", \"new line\" (Persian too)"),
            s.voicePunctuation) { v -> vm.update { it.copy(voicePunctuation = v) } }
    }
}

@Composable
private fun VoiceModelCard(vm: SettingsViewModel, m: VoiceModelManager.ModelInfo) {
    val st by vm.voiceState(m.lang).collectAsState()
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Title((if (L.fa) m.nameFa else m.nameEn) + t(" (مدل آفلاین)", " (offline model)"), t("حدود ${m.bytes / 1_000_000} مگابایت دانلود", "About ${m.bytes / 1_000_000} MB download"))
            }
            when (st) {
                is VoiceModelManager.State.Installed -> GhostButton(t("حذف", "Delete"), G.warn) { vm.deleteVoice(m.lang) }
                is VoiceModelManager.State.Downloading, VoiceModelManager.State.Installing -> GhostButton(t("لغو", "Cancel")) { vm.cancelVoice(m.lang) }
                is VoiceModelManager.State.Failed -> NeonButton(t("دوباره", "Retry")) { vm.downloadVoice(m.lang) }
                else -> NeonButton(t("دانلود", "Download")) { vm.downloadVoice(m.lang) }
            }
        }
        when (val x = st) {
            is VoiceModelManager.State.Downloading -> {
                val f = if (x.total > 0) x.done.toFloat() / x.total else 0f
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(G.line)) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(6.dp).background(G.neon))
                }
                Text("${(f * 100).toInt()}٪", color = G.sub, fontSize = 12.sp)
            }
            VoiceModelManager.State.Installing -> Text(t("در حال بررسی و نصب…", "Verifying and installing…"), color = G.sub, fontSize = 12.sp)
            is VoiceModelManager.State.Installed -> Text(t("نصب شده (${x.bytesOnDisk / 1_000_000} مگابایت)", "Installed (${x.bytesOnDisk / 1_000_000} MB)"), color = G.good, fontSize = 12.sp)
            is VoiceModelManager.State.Failed -> Text(t("دانلود نشد (${x.reason}). اینترنت را چک کن و دوباره بزن.", "Download failed (${x.reason}). Check your connection and retry."), color = G.warn, fontSize = 12.sp)
            else -> {}
        }
    }
}

@Composable
private fun ClipboardPage(vm: SettingsViewModel, s: KbSettings) {
    val counts by vm.counts.collectAsState()
    Glass {
        SwitchRow(t("ذخیره‌ی کپی‌ها", "Keep copied items"), t("فقط روی همین گوشی؛ از نوار بالای کیبورد باز می‌شود.", "Only on this phone; opened from the keyboard's top bar."), s.clipHistory) { v ->
            vm.update { it.copy(clipHistory = v) }
        }
        Title(t("پاک شدن خودکار سنجاق‌نشده‌ها", "Auto-delete unpinned items"))
        Choice(listOf("0" to t("هیچ‌وقت", "Never"), "60" to t("۱ ساعت", "1 hour"), "1440" to t("۱ روز", "1 day"), "10080" to t("۷ روز", "7 days")), s.clipAutoWipeMin.toString()) { v ->
            vm.update { it.copy(clipAutoWipeMin = v.toInt()) }
        }
        Title(t("متن حساس (رمز، کد یک‌بارمصرف، کارت)", "Sensitive text (passwords, codes, cards)"),
            t("خودکار شناخته و بعد از این مدت از تاریخچه و کلیپ‌بورد گوشی پاک می‌شود، مگر سنجاقش کنی.", "Detected automatically and wiped from history and the system clipboard after this time unless pinned."))
        Choice(listOf("30" to t("۳۰ ثانیه", "30 s"), "60" to t("۱ دقیقه", "1 min"), "300" to t("۵ دقیقه", "5 min")), s.sensitiveWipeSec.toString()) { v ->
            vm.update { it.copy(sensitiveWipeSec = v.toInt()) }
        }
        Spacer(Modifier.height(6.dp))
        GhostButton(t("پاک کردن سنجاق‌نشده‌ها (${counts.clips} مورد کل)", "Clear unpinned (${counts.clips} items total)"), G.warn) { vm.clearClipboard() }
    }
}

@Composable
private fun MacrosPage(vm: SettingsViewModel, s: KbSettings) {
    val list by vm.macros.collectAsState()
    var editing by remember { mutableStateOf<Macro?>(null) }
    Glass {
        SwitchRow(t("میان‌برهای متنی", "Text shortcuts"),
            t("مثلاً با تایپ @@ ایمیلت می‌آید. میان‌بر حرفی (مثل brb) بعد از فاصله باز می‌شود.", "Type @@ to drop in your email. Letter-only shortcuts like brb expand after a space."),
            s.macrosOn) { v -> vm.update { it.copy(macrosOn = v) } }
        NeonButton(t("میان‌بر تازه", "New shortcut")) { editing = Macro(trigger = "", expansion = "") }
    }
    list.forEach { m ->
        Glass {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { editing = m }) {
                    Text(m.trigger, color = G.cyan, fontSize = 15.sp)
                    Text(m.expansion, color = G.text, fontSize = 13.sp, maxLines = 2)
                }
                Switch(m.enabled, { on -> vm.saveMacro(m.copy(enabled = on)) }, colors = SwitchDefaults.colors(checkedTrackColor = G.magenta))
            }
        }
    }
    editing?.let { m -> MacroDialog(m, onDismiss = { editing = null }, onSave = { vm.saveMacro(it); editing = null },
        onDelete = if (m.id != 0L) ({ vm.deleteMacro(m.id); editing = null }) else null) }
}

@Composable
private fun MacroDialog(m: Macro, onDismiss: () -> Unit, onSave: (Macro) -> Unit, onDelete: (() -> Unit)?) {
    var trig by remember { mutableStateOf(m.trigger) }
    var exp by remember { mutableStateOf(m.expansion) }
    val valid = MacroExpander.validTrigger(trig.trim()) && exp.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121626),
        title = { Text(t("میان‌بر", "Shortcut"), color = G.text) },
        text = {
            Column {
                OutlinedTextField(trig, { trig = it.take(32) }, label = { Text(t("وقتی تایپ کنم", "When I type")) }, singleLine = true, colors = fieldColors())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(exp, { exp = it.take(5000) }, label = { Text(t("جایش بنویس", "Replace with")) }, minLines = 3, colors = fieldColors())
                if (trig.isNotEmpty() && !MacroExpander.validTrigger(trig.trim()))
                    Text(t("۲ تا ۳۲ نویسه و بی فاصله.", "2 to 32 characters, no spaces."), color = G.warn, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(m.copy(trigger = trig.trim(), expansion = exp)) }, enabled = valid) { Text(t("ذخیره", "Save")) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف", "Delete"), color = G.warn) }
                TextButton(onClick = onDismiss) { Text(t("بستن", "Close")) }
            }
        },
    )
}

@Composable
private fun BackupPage(actions: SettingsActions) {
    Glass {
        Title(t("بکاپ", "Backup"), t("تنظیمات، تم دلخواه، متن‌های سنجاق‌شده و میان‌برها در یک فایل JSON. کپی‌های سنجاق‌نشده و کلمه‌های یادگرفته نمی‌روند.",
            "Settings, custom theme, pinned clips and shortcuts in one JSON file. Unpinned clips and learned words are left out."))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton(t("ساخت فایل بکاپ", "Export backup"), onClick = actions::exportBackup)
            GhostButton(t("بازگردانی از فایل", "Restore from file"), onClick = actions::importBackup)
        }
    }
}
