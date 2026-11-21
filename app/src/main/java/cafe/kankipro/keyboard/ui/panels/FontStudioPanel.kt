package cafe.kankipro.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.kankipro.keyboard.core.L
import cafe.kankipro.keyboard.core.t
import cafe.kankipro.keyboard.engine.typography.GamerSymbols
import cafe.kankipro.keyboard.engine.typography.PersianTemplates
import cafe.kankipro.keyboard.engine.typography.UnicodeFontStyler

/**
 * استودیوی فونت و نماد: ۵۰+ فونت یونیکد (روی متن انتخاب‌شده یا کلمه‌ی آخر، یا «تایپ زنده» که هر حرف را
 * همان لحظه تبدیل می‌کند)، قالب‌های فارسی و کشیده، نشان‌های گیمری و قاب نام با یک لمس.
 */
@Composable
fun FontStudioPanel(c: PanelColors, actions: ImeActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val source = remember { mutableStateOf(actions.sourceText()) }
    val tabs = listOf(t("فونت‌ها", "Fonts"), t("فارسی", "Persian"), t("قاب نام", "Name frames"), t("نشان‌ها", "Symbols"))
    Column(Modifier.fillMaxSize()) {
        PanelTabs(c, tabs, tab) { tab = it }
        val src = source.value
        val sample = src.ifBlank { if (tab == 1) "کانکی" else "Kanki" }
        when (tab) {
            0 -> FontsTab(c, src, sample, actions) { source.value = it }
            1 -> PersianTab(c, src, sample, actions) { source.value = it }
            2 -> FramesTab(c, src, actions) { source.value = it }
            else -> SymbolsTab(c, actions)
        }
    }
}

@Composable
private fun Hint(c: PanelColors, src: String, liveHint: Boolean) {
    Text(
        if (src.isBlank()) {
            if (liveHint) t("متنی انتخاب نشده: لمس هر فونت = تایپ زنده با همان فونت.", "No text selected: tap a font to type live in it.")
            else t("متنی انتخاب نشده: نمونه نوشته می‌شود.", "No text selected: the sample is inserted.")
        } else t("روی «$src» اعمال می‌شود.", "Applies to \"$src\"."),
        color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun FontsTab(c: PanelColors, src: String, sample: String, actions: ImeActions, onSource: (String) -> Unit) {
    Hint(c, src, liveHint = true)
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(UnicodeFontStyler.styles, key = { it.id }) { st ->
            Card(c, UnicodeFontStyler.apply(sample.take(24), st.id), st.name) {
                if (src.isBlank()) { actions.setLiveStyle(st.id, st.name); actions.closePanel() }
                else { val out = UnicodeFontStyler.apply(src, st.id); actions.replaceSource(out); onSource(out) }
            }
        }
    }
}

@Composable
private fun PersianTab(c: PanelColors, src: String, sample: String, actions: ImeActions, onSource: (String) -> Unit) {
    var level by remember { mutableIntStateOf(actions.kashida()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t("کشیده‌ی هنگام تایپ:", "Kashida while typing:"), color = c.text, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        listOf(t("خاموش", "Off"), t("کم", "Low"), t("متوسط", "Med"), t("زیاد", "High")).forEachIndexed { i, label ->
            Chip(c, label, on = level == i) { level = i; actions.setKashida(i) }
        }
    }
    Hint(c, src, liveHint = false)
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(PersianTemplates.all, key = { it.id }) { tp ->
            Card(c, PersianTemplates.apply(sample.take(24), tp.id), if (L.fa) tp.nameFa else tp.nameEn) {
                val out = PersianTemplates.apply(src.ifBlank { sample }, tp.id)
                if (src.isBlank()) actions.commit(out) else { actions.replaceSource(out); onSource(out) }
            }
        }
    }
}

@Composable
private fun FramesTab(c: PanelColors, src: String, actions: ImeActions, onSource: (String) -> Unit) {
    val name = src.ifBlank { "NAME" }
    Text(
        if (src.isBlank()) t("اسمت را تایپ کن، بعد قاب را بزن تا دورش بنشیند.", "Type your name, then tap a frame to wrap it.")
        else t("قاب دور «$src» می‌نشیند.", "Wraps \"$src\"."),
        color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(GamerSymbols.frames) { f ->
            Card(c, f.left + name.take(16) + f.right, null) {
                if (src.isBlank()) actions.commit(f.left + f.right) else { val out = f.left + src + f.right; actions.replaceSource(out); onSource(out) }
            }
        }
    }
}

@Composable
private fun SymbolsTab(c: PanelColors, actions: ImeActions) {
    var cat by rememberSaveable { mutableIntStateOf(0) }
    PanelTabs(c, GamerSymbols.categories.map { if (L.fa) it.nameFa else it.nameEn }, cat) { cat = it }
    LazyVerticalGrid(GridCells.Adaptive(46.dp), contentPadding = PaddingValues(6.dp), modifier = Modifier.fillMaxSize()) {
        items(GamerSymbols.categories[cat].items) { s ->
            Box(
                Modifier.aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .clickable(role = Role.Button) { actions.commit(s) },
                contentAlignment = Alignment.Center,
            ) { Text(s, color = c.text, fontSize = if (s.length > 2) 12.sp else 20.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun Card(c: PanelColors, preview: String, name: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(preview, color = c.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        if (name != null) Text(name, color = c.sub, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Chip(c: PanelColors, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(50))
            .then(if (on) Modifier.background(c.accent) else Modifier.border(1.dp, c.border, RoundedCornerShape(50)))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, color = if (on) Color.White else c.text, fontSize = 12.sp) }
}
