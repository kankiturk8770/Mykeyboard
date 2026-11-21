package cafe.kankipro.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.kankipro.keyboard.core.t

/** ایموجی (با «اخیر»)، کائوموجی و هنر متنی. */
@Composable
fun EmojiPanel(c: PanelColors, actions: ImeActions, deps: PanelDeps) {
    var recent by remember { mutableStateOf(loadRecent(deps)) }
    val tabs = buildList {
        add(t("اخیر", "Recent"))
        EmojiData.emoji.forEach { add(it.icon) }
        add(t("کائوموجی", "Kaomoji"))
        add(t("هنر متنی", "Text art"))
    }
    var tab by rememberSaveable { mutableIntStateOf(if (recent.isEmpty()) 1 else 0) }
    fun pick(s: String) {
        actions.commit(s)
        if (tab in 1..EmojiData.emoji.size || tab == 0) {
            recent = (listOf(s) + recent.filter { it != s }).take(32)
            deps.prefs.edit().putString("recent_emoji", recent.joinToString("\u0001")).apply()
        }
    }
    Column(Modifier.fillMaxSize()) {
        PanelTabs(c, tabs, tab) { tab = it }
        val kaoTab = EmojiData.emoji.size + 1
        when {
            tab == 0 && recent.isEmpty() -> EmptyNote(c, t("ایموجی‌هایی که می‌زنی این‌جا می‌مانند.", "Emoji you use appear here."))
            tab == 0 -> EmojiGrid(c, recent, ::pick)
            tab in 1..EmojiData.emoji.size -> EmojiGrid(c, EmojiData.emoji[tab - 1].items, ::pick)
            tab == kaoTab -> TextGrid(c, EmojiData.kaomoji, ::pick)
            else -> TextGrid(c, EmojiData.asciiArt, ::pick)
        }
    }
}

private fun loadRecent(deps: PanelDeps): List<String> =
    deps.prefs.getString("recent_emoji", null)?.split("\u0001")?.filter { it.isNotEmpty() } ?: emptyList()

@Composable
private fun EmojiGrid(c: PanelColors, items: List<String>, onPick: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(44.dp), contentPadding = PaddingValues(6.dp), modifier = Modifier.fillMaxSize()) {
        items(items) { e ->
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onPick(e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 26.sp) }
        }
    }
}

@Composable
private fun TextGrid(c: PanelColors, items: List<String>, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        GridCells.Adaptive(140.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items) { k ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onPick(k) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(k, color = c.text, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 3) }
        }
    }
}

