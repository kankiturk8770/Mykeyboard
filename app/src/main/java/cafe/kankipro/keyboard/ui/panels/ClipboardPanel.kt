package cafe.kankipro.keyboard.ui.panels

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.kankipro.keyboard.R
import cafe.kankipro.keyboard.core.t
import cafe.kankipro.keyboard.data.clipboard.ClipboardRepository
import cafe.kankipro.keyboard.data.clipboard.SensitiveDetector
import cafe.kankipro.keyboard.data.settings.SettingsStore
import kotlinx.coroutines.delay

/** وابستگی‌های پنل‌ها (از سرویس کیبورد). */
class PanelDeps(val clipboard: ClipboardRepository, val settings: SettingsStore, val prefs: SharedPreferences)

@Composable
fun ClipboardPanel(c: PanelColors, actions: ImeActions, deps: PanelDeps) {
    val items by deps.clipboard.items.collectAsState(initial = emptyList())
    val s by deps.settings.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anySensitive = items.any { it.sensitive && !it.pinned }
    LaunchedEffect(anySensitive) { while (anySensitive) { delay(1000); now = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("کلیپ‌بورد", "Clipboard"), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (!s.clipHistory) Text(t("ذخیره خاموش است", "History is off"), color = c.sub, fontSize = 12.sp)
            if (items.any { !it.pinned }) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.border, RoundedCornerShape(50))
                        .clickable(role = Role.Button) { deps.clipboard.clearUnpinned() }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(t("پاک کردن سنجاق‌نشده‌ها", "Clear unpinned"), color = c.text, fontSize = 12.sp) }
            }
        }
        if (items.isEmpty()) {
            EmptyNote(c, t("هر چه کپی کنی این‌جا می‌آید. سنجاق کن تا بماند.", "Anything you copy shows up here. Pin it to keep it."))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.id }) { item ->
                val left = if (item.sensitive && !item.pinned) ((item.createdAt + s.sensitiveWipeSec * 1000L - now) / 1000).coerceAtLeast(0) else -1
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (item.pinned) c.accentA.copy(alpha = 0.16f) else c.surface)
                        .border(1.dp, if (item.pinned) c.accentA.copy(alpha = 0.6f) else c.border, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = t("چسباندن", "Paste")) { actions.commit(item.text) }
                        .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (item.sensitive && !item.pinned) SensitiveDetector.mask(item.text) else item.text,
                            color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (left >= 0) Text(t("حساس؛ پاک می‌شود تا $left ثانیه", "Sensitive, wiped in ${left}s"), color = Color(0xFFFFC857), fontSize = 11.sp)
                    }
                    IconButton(onClick = { deps.clipboard.setPinned(item.id, !item.pinned) }) {
                        Icon(painterResource(R.drawable.ic_pin), contentDescription = if (item.pinned) t("برداشتن سنجاق", "Unpin") else t("سنجاق", "Pin"),
                            tint = if (item.pinned) c.accentB else c.sub, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { deps.clipboard.delete(item.id) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = t("حذف", "Delete"), tint = c.sub, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item { Spacer(Modifier.width(1.dp).padding(bottom = 6.dp)) }
        }
    }
}
