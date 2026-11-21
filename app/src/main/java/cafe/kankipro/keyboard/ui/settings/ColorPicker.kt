package cafe.kankipro.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.kankipro.keyboard.core.t

/** انتخاب رنگ دلخواه: چرخه‌ی رنگ (Hue)، اشباع، روشنی و در صورت نیاز شفافیت؛ با چند رنگ آماده‌ی نئونی. */
@Composable
internal fun ColorPicker(label: String, argb: Int, withAlpha: Boolean, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
    val alpha = (argb ushr 24) and 0xFF
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(role = Role.Button) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(argb)).border(1.dp, G.line, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(label, color = G.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("#%08X".format(argb), color = G.sub, fontSize = 12.sp)
    }
    if (!open) return
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            SWATCHES.forEach { c ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).border(1.dp, G.line, CircleShape)
                    .clickable(role = Role.Button) { onChange(if (withAlpha) (c and 0x00FFFFFF) or (alpha shl 24) else c) })
            }
        }
        fun emit(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2], a: Int = alpha) {
            val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
            onChange((rgb and 0x00FFFFFF) or ((if (withAlpha) a else 255) shl 24))
        }
        SliderRow(t("رنگ", "Hue"), hsv[0], 0f..360f, format = { "${it.toInt()}°" }) { emit(h = it) }
        SliderRow(t("اشباع", "Saturation"), hsv[1], 0f..1f, format = { "${(it * 100).toInt()}٪" }) { emit(s = it) }
        SliderRow(t("روشنی", "Brightness"), hsv[2], 0f..1f, format = { "${(it * 100).toInt()}٪" }) { emit(v = it) }
        if (withAlpha) SliderRow(t("شفافیت", "Opacity"), alpha.toFloat(), 0f..255f, format = { "${(it * 100 / 255).toInt()}٪" }) { emit(a = it.toInt()) }
    }
}

private val SWATCHES = listOf(
    0xFFFF2BD6.toInt(), 0xFF00E5FF.toInt(), 0xFF7C6CFF.toInt(), 0xFF3DDC97.toInt(), 0xFFFFC857.toInt(), 0xFFFF5A5F.toInt(),
    0xFFFFFFFF.toInt(), 0xFF0B0F1E.toInt(),
)
