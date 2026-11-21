package cafe.kankipro.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.kankipro.keyboard.R
import cafe.kankipro.keyboard.core.t
import cafe.kankipro.keyboard.ui.keyboard.KeyboardTheme

/** پنل‌هایی که جای کیبورد باز می‌شوند. */
enum class Panel { NONE, CLIPBOARD, EMOJI, FONTS }

/** کارهایی که پنل‌ها از سرویس کیبورد می‌خواهند. */
interface ImeActions {
    fun commit(text: String)
    fun deleteBackward()
    fun closePanel()
    /** متن انتخاب‌شده، یا کلمه‌ی پیش از نشانگر (برای فونت‌ساز و قاب‌ها). */
    fun sourceText(): String
    /** همان متن منبع را با متن تازه عوض کن (اگر منبعی نبود، فقط بنویس). */
    fun replaceSource(newText: String)
    fun setLiveStyle(id: String?, name: String?)
    fun setKashida(level: Int)
    fun kashida(): Int
    fun openSettings(page: String?)
}

/** رنگ‌های Compose از روی تم کیبورد. */
@Immutable
data class PanelColors(
    val bg: Color, val surface: Color, val text: Color, val sub: Color, val accentA: Color, val accentB: Color, val border: Color,
) {
    val accent get() = Brush.horizontalGradient(listOf(accentA, accentB))

    companion object {
        fun from(t: KeyboardTheme) = PanelColors(
            bg = Color(t.bgBottom or 0xFF000000.toInt()), surface = Color(t.keyFill), text = Color(t.text),
            sub = Color(t.subText), accentA = Color(t.accentA), accentB = Color(t.accentB), border = Color(t.border),
        )
    }
}

@Composable
fun PanelHost(panel: Panel, colors: PanelColors, height: Dp, actions: ImeActions, deps: PanelDeps) {
    Column(
        Modifier.fillMaxWidth().height(height).background(
            Brush.verticalGradient(listOf(colors.bg.copy(alpha = 0.96f), colors.bg))
        )
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (panel) {
                Panel.CLIPBOARD -> ClipboardPanel(colors, actions, deps)
                Panel.EMOJI -> EmojiPanel(colors, actions, deps)
                Panel.FONTS -> FontStudioPanel(colors, actions)
                Panel.NONE -> {}
            }
        }
        BottomStrip(colors, actions)
    }
}

/** نوار پایین مشترک پنل‌ها: برگشت به کیبورد و پاک‌کن. */
@Composable
private fun BottomStrip(c: PanelColors, actions: ImeActions) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StripButton(c, R.drawable.ic_keyboard, t("کیبورد", "Keyboard")) { actions.closePanel() }
        StripButton(c, R.drawable.ic_backspace, t("پاک کردن", "Delete")) { actions.deleteBackward() }
    }
}

@Composable
private fun StripButton(c: PanelColors, icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 64.dp, height = 38.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), contentDescription = label, tint = c.text, modifier = Modifier.size(22.dp)) }
}

/** ردیف زبانه‌ها (کشیدنی). */
@Composable
fun PanelTabs(c: PanelColors, tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(tabs) { i, label ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).then(
                    if (on) Modifier.background(c.accent) else Modifier.background(c.surface).border(1.dp, c.border, RoundedCornerShape(50))
                ).clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text(label, color = if (on) Color.White else c.text, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
fun EmptyNote(c: PanelColors, text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = c.sub, fontSize = 14.sp)
    }
}
