package cafe.kankipro.keyboard.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * کیبورد با Canvas خالص (بی Compose و بی View برای هر کلید) تا لمس تا نمایش زیر ۵ میلی‌ثانیه بماند:
 * - لایه‌ی ثابت (زمینه‌ی شیشه‌ای، درخشش، همه‌ی کلیدها) یک بار در یک Bitmap کشیده می‌شود؛ هر فریم فقط همان
 *   Bitmap + کلید فشرده + حباب/پنجره‌ی بازشو کشیده می‌شود (بی تخصیص حافظه در onDraw).
 * - چندلمسی: هر انگشت جدا دنبال می‌شود؛ انگشت دوم که پایین آمد، کلید انگشت اول همان لحظه نوشته می‌شود (تایپ تند).
 * - نگه داشتن = پنجره‌ی حرف‌های دیگر (حرکت‌ها، رقم، حرف‌های عربی)؛ کشیدن روی فاصله = حرکت نشانگر؛
 *   کشیدن به چپ از پاک‌کن = انتخاب و پاک کردن کلمه‌به‌کلمه؛ کشیدن رو به بالا روی فاصله = زبان بعدی.
 * پنجره‌ها و حباب کلید ردیف بالا بیرون از قاب این View کشیده می‌شوند (والد `clipChildren=false` دارد).
 */
class KeyboardCanvasView(context: Context) : View(context) {

    interface Listener {
        fun onKeyDown(key: Key)
        fun onKey(key: Key, text: String?)
        fun onCursor(steps: Int)
        fun onSpaceSwipeUp()
        fun onSpaceLongPress()
        fun onDeleteRepeat()
        fun onDeleteSwipe(words: Int, done: Boolean)
        fun onOneHanded(side: String)
    }

    var listener: Listener? = null

    // ---------------------------------------------------------------- تنظیمات ظاهر و رفتار
    var layout: KeyboardLayout = KeyboardLayouts.build("en", Layer.ALPHA, KeyboardLayouts.Options())
        set(v) { field = v; relayout() }
    var theme: KeyboardTheme = KeyboardTheme.preset("cyber")
        set(v) { field = v; cacheDirty = true; invalidate() }
    var shiftState = 0
        set(v) { if (field != v) { field = v; cacheDirty = true; invalidate() } }
    var heightScale = 1f
        set(v) { if (field != v) { field = v; requestLayout() } }
    var gapDp = 4f
        set(v) { if (field != v) { field = v; relayout() } }
    var fontScale = 1f
        set(v) { if (field != v) { field = v; cacheDirty = true; invalidate() } }
    var oneHanded = "off"
        set(v) { if (field != v) { field = v; relayout() } }
    var keyPreview = true
    /** فضای بالای این View که پنجره‌ها می‌توانند رویش بکشند (ارتفاع نوار بالا). */
    var topHeadroom = 0f
    var longPressMs = 300L
    /** متن روی فاصله (اسم زبان). */
    var spaceLabel: String? = null
        set(v) { if (field != v) { field = v; cacheDirty = true; invalidate() } }
    /** کلید فاصله در حال حرکت نشانگر است (برای نمایش فلش‌ها). */
    private var trackpadActive = false

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    // ---------------------------------------------------------------- هندسه
    private class KeyBox(val key: Key, val rect: RectF, val row: Int)

    private val boxes = ArrayList<KeyBox>()
    private val rowTops = ArrayList<Float>()
    private var rowH = 0f
    private val sideRect = RectF()
    private val sideSwap = RectF()
    private val sideExit = RectF()

    private fun rowHeightPx() = 52f * dp * heightScale
    private fun padV() = 6f * dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (layout.rows.size * rowHeightPx() + padV() * 2).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBoxes()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (cafe.kankipro.keyboard.BuildConfig.DEBUG) post { dumpKeysForTests() }
    }

    /** فقط نسخه‌ی دیباگ: جای هر کلید روی صفحه در logcat (برای تست خودکار روی شبیه‌ساز). */
    private fun dumpKeysForTests() {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val sb = StringBuilder("layout=").append(layout.id)
        for (b in boxes) sb.append('\n').append(b.key.code).append('|').append(b.key.text.replace("\n", "\\n"))
            .append('|').append((loc[0] + b.rect.centerX()).toInt()).append('|').append((loc[1] + b.rect.centerY()).toInt())
        android.util.Log.d("KbKeys", sb.toString())
    }

    private fun relayout() {
        cacheDirty = true
        requestLayout()
        computeBoxes()
        invalidate()
        if (cafe.kankipro.keyboard.BuildConfig.DEBUG && isLaidOut) post { dumpKeysForTests() }
    }

    private fun computeBoxes() {
        boxes.clear(); rowTops.clear()
        val w = width.toFloat()
        if (w <= 0f) return
        rowH = rowHeightPx()
        var left = 0f
        var right = w
        sideRect.setEmpty()
        if (oneHanded != "off") {
            val side = w * 0.17f
            if (oneHanded == "left") { right = w - side; sideRect.set(right, 0f, w, height.toFloat()) }
            else { left = side; sideRect.set(0f, 0f, side, height.toFloat()) }
            val cx = sideRect.centerX()
            val s = min(sideRect.width() * 0.7f, 44f * dp)
            sideSwap.set(cx - s / 2, sideRect.centerY() - s - 8 * dp, cx + s / 2, sideRect.centerY() - 8 * dp)
            sideExit.set(cx - s / 2, sideRect.centerY() + 8 * dp, cx + s / 2, sideRect.centerY() + 8 * dp + s)
        }
        val unit = (right - left) / layout.units
        var y = padV()
        layout.rows.forEachIndexed { ri, row ->
            rowTops.add(y)
            val total = row.keys.sumOf { it.width.toDouble() }.toFloat() * unit
            var x = left + ((right - left) - total) / 2f
            for (k in row.keys) {
                val kw = k.width * unit
                boxes.add(KeyBox(k, RectF(x, y, x + kw, y + rowH), ri))
                x += kw
            }
            y += rowH
        }
        cacheDirty = true
    }

    /** پیدا کردن کلید: ردیف از روی y، بعد نزدیک‌ترین کلید آن ردیف (لمس لب کلید هم درست می‌نشیند). */
    private fun hit(x: Float, y: Float): KeyBox? {
        if (boxes.isEmpty() || (oneHanded != "off" && sideRect.contains(x, y))) return null
        var row = ((y - padV()) / rowH).toInt().coerceIn(0, layout.rows.size - 1)
        if (y < padV()) row = 0
        var best: KeyBox? = null
        var bestD = Float.MAX_VALUE
        for (b in boxes) {
            if (b.row != row) continue
            val d = if (x < b.rect.left) b.rect.left - x else if (x > b.rect.right) x - b.rect.right else 0f
            if (d < bestD) { bestD = d; best = b }
        }
        return if (bestD < 24 * dp) best else null
    }

    // ---------------------------------------------------------------- کشیدن
    private var cache: Bitmap? = null
    private var cacheDirty = true
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    private val tmp = RectF()
    private val tmp2 = RectF()

    private fun radius() = 9f * dp

    private fun ensureCache() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        var bmp = cache
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            cache = bmp
            cacheDirty = true
        }
        if (!cacheDirty) return
        cacheDirty = false
        bmp.eraseColor(0)
        val c = Canvas(bmp)
        drawBackground(c, w.toFloat(), h.toFloat())
        for (b in boxes) drawKey(c, b, pressed = false)
        if (oneHanded != "off") drawSide(c)
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        val t = theme
        fill.shader = LinearGradient(0f, 0f, 0f, h, t.bgTop, t.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        if (t.amoled) return
        // دو هاله‌ی نور نرم پشت شیشه (بی عکس)
        fill.shader = RadialGradient(w * 0.15f, h * 0.1f, max(w, h) * 0.6f,
            KeyboardTheme.withAlpha(t.accentA, 0x2A), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = RadialGradient(w * 0.9f, h * 0.95f, max(w, h) * 0.55f,
            KeyboardTheme.withAlpha(t.accentB, 0x22), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // خط نور لبه‌ی بالا
        stroke.strokeWidth = 1f * dp
        stroke.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0, KeyboardTheme.withAlpha(t.accentB, 0x99), KeyboardTheme.withAlpha(t.accentA, 0x99), 0), null, Shader.TileMode.CLAMP)
        c.drawLine(0f, 0.5f * dp, w, 0.5f * dp, stroke)
        stroke.shader = null
    }

    private fun keyRect(b: KeyBox, out: RectF) {
        val g = gapDp * dp / 2f
        out.set(b.rect.left + g, b.rect.top + g, b.rect.right - g, b.rect.bottom - g)
    }

    private fun lighten(c: Int, a: Int): Int {
        val alpha = (c ushr 24)
        return KeyboardTheme.withAlpha(c, (alpha + a).coerceAtMost(255))
    }

    private fun drawKey(c: Canvas, b: KeyBox, pressed: Boolean) {
        val t = theme
        val k = b.key
        keyRect(b, tmp)
        val r = radius()
        val accent = k.kind == Key.Kind.ACTION
        val special = k.kind == Key.Kind.MOD
        // درخشش بیرونی
        if (t.glowDp > 0f && (accent || pressed || special)) {
            glowPaint.color = KeyboardTheme.withAlpha(if (accent) t.accentA else t.glow, if (pressed) 0x90 else if (accent) 0x70 else 0x26)
            glowPaint.maskFilter = BlurMaskFilter(t.glowDp * dp * (if (accent) 1.2f else 0.8f), BlurMaskFilter.Blur.OUTER)
            c.drawRoundRect(tmp, r, r, glowPaint)
            glowPaint.maskFilter = null
        }
        // بدنه‌ی شیشه
        when {
            accent -> fill.shader = LinearGradient(tmp.left, tmp.top, tmp.right, tmp.bottom, t.accentA, t.accentB, Shader.TileMode.CLAMP)
            t.amoled -> fill.color = if (special) t.specialFill else t.keyFill
            else -> {
                val base = if (special) t.specialFill else t.keyFill
                fill.shader = LinearGradient(0f, tmp.top, 0f, tmp.bottom, lighten(base, 0x14), base, Shader.TileMode.CLAMP)
            }
        }
        c.drawRoundRect(tmp, r, r, fill)
        fill.shader = null
        if (pressed) { fill.color = t.pressed; c.drawRoundRect(tmp, r, r, fill) }
        // برق لبه‌ی بالا (شیشه)
        if (t.sheen ushr 24 != 0 && !accent) {
            stroke.strokeWidth = 1f * dp
            stroke.shader = LinearGradient(tmp.left, 0f, tmp.right, 0f, intArrayOf(0, t.sheen, 0), null, Shader.TileMode.CLAMP)
            c.drawLine(tmp.left + r, tmp.top + 1f * dp, tmp.right - r, tmp.top + 1f * dp, stroke)
            stroke.shader = null
        }
        // لبه
        stroke.strokeWidth = 1f * dp
        stroke.color = if (pressed) t.glow else t.border
        c.drawRoundRect(tmp, r, r, stroke)
        drawLabel(c, k, tmp, accent)
    }

    private fun drawLabel(c: Canvas, k: Key, rect: RectF, accent: Boolean) {
        val t = theme
        var label = k.label
        var size = when {
            k.kind == Key.Kind.SPACE -> 12f
            k.label.length > 2 && k.kind == Key.Kind.MOD -> 13.5f
            k.kind == Key.Kind.MOD || k.kind == Key.Kind.ACTION -> 19f
            label.codePointCount(0, label.length) > 2 -> 15f
            else -> 22f
        } * sp * fontScale
        text.color = when {
            accent -> 0xFFFFFFFF.toInt()
            k.code == Key.SHIFT && shiftState > 0 -> t.accentB
            k.kind == Key.Kind.SPACE -> t.subText
            else -> t.text
        }
        if (k.kind == Key.Kind.SPACE) label = spaceLabel ?: label
        if (label.isBlank() && k.caption != null) { label = k.caption; size = 11.5f * sp * fontScale; text.color = t.subText }
        text.textSize = size
        text.isFakeBoldText = false
        // متن بلند در کلید جا شود
        val maxW = rect.width() - 6 * dp
        val tw = text.measureText(label)
        if (tw > maxW && tw > 0) text.textSize = size * maxW / tw
        val fm = text.fontMetrics
        val cy = rect.centerY() - (fm.ascent + fm.descent) / 2f
        c.drawText(label, rect.centerX(), cy, text)
        if (k.code == Key.SHIFT && shiftState == 2) {
            fill.color = t.accentB
            c.drawRoundRect(rect.centerX() - 7 * dp, rect.bottom - 7 * dp, rect.centerX() + 7 * dp, rect.bottom - 5 * dp, dp, dp, fill)
        }
        k.hint?.let { h ->
            text.textSize = 10f * sp * fontScale
            text.color = t.subText
            c.drawText(h, rect.right - 7 * dp, rect.top + 12 * dp, text)
        }
        if (k.caption != null && k.label.isNotBlank()) {
            text.textSize = 9f * sp * fontScale
            text.color = t.subText
            c.drawText(k.caption, rect.centerX(), rect.bottom - 4 * dp, text)
        }
    }

    private fun drawSide(c: Canvas) {
        val t = theme
        for ((r, label) in listOf(sideSwap to "⇄", sideExit to "⤢")) {
            fill.color = t.specialFill
            c.drawRoundRect(r, r.width() / 2, r.width() / 2, fill)
            stroke.color = t.border; stroke.strokeWidth = dp
            c.drawRoundRect(r, r.width() / 2, r.width() / 2, stroke)
            text.textSize = 18 * sp; text.color = t.text
            val fm = text.fontMetrics
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
        }
    }

    override fun onDraw(canvas: Canvas) {
        ensureCache()
        cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            val b = p.box ?: continue
            if (p.mode == Mode.DONE) continue
            drawKey(canvas, b, pressed = true)
            if (p.mode == Mode.TRACKPAD) drawTrackpad(canvas, b)
            if (p.mode == Mode.DELSWIPE) drawDelSwipe(canvas, b, p.delWords)
        }
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            if (p.mode == Mode.POPUP) drawPopup(canvas, p)
            else if (p.mode == Mode.NORMAL && keyPreview && p.box?.key?.kind == Key.Kind.CHAR) drawPreview(canvas, p.box!!)
        }
    }

    private fun drawPreview(c: Canvas, b: KeyBox) {
        val t = theme
        keyRect(b, tmp)
        val w = max(tmp.width() * 1.25f, 44 * dp)
        val h = rowH * 1.15f
        tmp2.set(tmp.centerX() - w / 2, tmp.top - h - 4 * dp, tmp.centerX() + w / 2, tmp.top - 4 * dp)
        clampX(tmp2)
        clampTop(tmp2)
        panel(c, tmp2)
        text.textSize = 30f * sp * fontScale
        text.color = t.text
        val fm = text.fontMetrics
        c.drawText(b.key.label, tmp2.centerX(), tmp2.centerY() - (fm.ascent + fm.descent) / 2, text)
    }

    /** پنجره بالاتر از نوار بالا نرود (آن‌جا دیگر مال کیبورد نیست و بریده می‌شود). */
    private fun clampTop(r: RectF) {
        val minTop = -topHeadroom + 2 * dp
        if (r.top < minTop) r.offset(0f, minTop - r.top)
    }

    private fun clampX(r: RectF) {
        if (r.left < 2 * dp) r.offset(2 * dp - r.left, 0f)
        if (r.right > width - 2 * dp) r.offset(width - 2 * dp - r.right, 0f)
    }

    private fun panel(c: Canvas, r: RectF) {
        val t = theme
        val rad = 12f * dp
        if (t.glowDp > 0f) {
            glowPaint.color = KeyboardTheme.withAlpha(t.glow, 0x66)
            glowPaint.maskFilter = BlurMaskFilter(10 * dp, BlurMaskFilter.Blur.OUTER)
            c.drawRoundRect(r, rad, rad, glowPaint)
            glowPaint.maskFilter = null
        }
        fill.color = t.popupBg or 0xFF000000.toInt()
        c.drawRoundRect(r, rad, rad, fill)
        stroke.color = t.glow; stroke.strokeWidth = dp
        c.drawRoundRect(r, rad, rad, stroke)
    }

    private fun drawPopup(c: Canvas, p: Ptr) {
        val t = theme
        panel(c, p.popupRect)
        val cols = p.popupCols
        val cw = p.popupRect.width() / cols
        val ch = p.popupCellH
        p.popupItems.forEachIndexed { i, s ->
            val col = i % cols
            val row = i / cols
            val l = p.popupRect.left + col * cw
            val top = p.popupRect.top + row * ch
            tmp2.set(l + 3 * dp, top + 3 * dp, l + cw - 3 * dp, top + ch - 3 * dp)
            if (i == p.popupSel) {
                fill.shader = LinearGradient(tmp2.left, tmp2.top, tmp2.right, tmp2.bottom, t.accentA, t.accentB, Shader.TileMode.CLAMP)
                c.drawRoundRect(tmp2, 8 * dp, 8 * dp, fill)
                fill.shader = null
            }
            text.textSize = 21f * sp * fontScale
            text.color = if (i == p.popupSel) 0xFFFFFFFF.toInt() else t.text
            val shown = if (s.length == 1 && Character.getType(s[0]) == Character.NON_SPACING_MARK.toInt()) "◌$s" else s
            val tw = text.measureText(shown)
            if (tw > tmp2.width() - 4 * dp) text.textSize *= (tmp2.width() - 4 * dp) / tw
            val fm = text.fontMetrics
            c.drawText(shown, tmp2.centerX(), tmp2.centerY() - (fm.ascent + fm.descent) / 2, text)
        }
    }

    private fun drawTrackpad(c: Canvas, b: KeyBox) {
        keyRect(b, tmp)
        text.textSize = 16 * sp; text.color = theme.accentB
        val fm = text.fontMetrics
        val y = tmp.centerY() - (fm.ascent + fm.descent) / 2
        c.drawText("◀", tmp.left + 16 * dp, y, text)
        c.drawText("▶", tmp.right - 16 * dp, y, text)
    }

    private fun drawDelSwipe(c: Canvas, b: KeyBox, words: Int) {
        if (words <= 0) return
        keyRect(b, tmp)
        val r = 9 * dp
        fill.color = theme.accentA
        c.drawCircle(tmp.left + r, tmp.top + r, r, fill)
        text.textSize = 11 * sp; text.color = 0xFFFFFFFF.toInt()
        val fm = text.fontMetrics
        c.drawText(words.toString(), tmp.left + r, tmp.top + r - (fm.ascent + fm.descent) / 2, text)
    }

    // ---------------------------------------------------------------- لمس
    private enum class Mode { NORMAL, POPUP, TRACKPAD, DELSWIPE, SPACE_UP, DONE, SIDE }

    private inner class Ptr(val id: Int, var box: KeyBox?, val downX: Float, val downY: Float) {
        var mode = Mode.NORMAL
        var popupItems: List<String> = emptyList()
        var popupSel = 0
        var popupCols = 1
        var popupCellH = 0f
        val popupRect = RectF()
        var lastStepX = downX
        var delWords = 0
        var repeated = false
        var side: String? = null
        val longPress = Runnable { onLongPress(this) }
        val repeat = object : Runnable {
            var n = 0
            override fun run() {
                if (mode != Mode.NORMAL) return
                repeated = true
                listener?.onDeleteRepeat()
                n++
                handler.postDelayed(this, if (n > 12) 30L else 60L)
            }
        }
    }

    private val ptrs = android.util.SparseArray<Ptr>()
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (oneHanded != "off" && sideRect.contains(x, y)) {
            val p = Ptr(id, null, x, y)
            p.mode = Mode.SIDE
            p.side = when {
                sideSwap.contains(x, y) -> if (oneHanded == "left") "right" else "left"
                sideExit.contains(x, y) -> "off"
                else -> null
            }
            ptrs.put(id, p)
            return
        }
        // تایپ تند: کلید انگشت قبلی همین حالا نوشته می‌شود
        for (i in 0 until ptrs.size()) {
            val o = ptrs.valueAt(i)
            val k = o.box?.key ?: continue
            if (o.mode == Mode.NORMAL && k.kind == Key.Kind.CHAR) {
                handler.removeCallbacks(o.longPress)
                o.mode = Mode.DONE
                listener?.onKey(k, null)
            }
        }
        val b = hit(x, y) ?: return
        val p = Ptr(id, b, x, y)
        ptrs.put(id, p)
        val k = b.key
        listener?.onKeyDown(k)
        when {
            k.code == Key.SHIFT -> { p.mode = Mode.DONE; listener?.onKey(k, null) }
            k.code == Key.DELETE -> handler.postDelayed(p.repeat, 400L)
            k.alts.isNotEmpty() || k.code == Key.SPACE || k.code == Key.GLOBE -> handler.postDelayed(p.longPress, if (k.code == Key.SPACE) 600L else longPressMs)
        }
        invalidate()
    }

    private fun onLongPress(p: Ptr) {
        if (p.mode != Mode.NORMAL) return
        val b = p.box ?: return
        val k = b.key
        if (k.code == Key.SPACE || k.code == Key.GLOBE) {
            p.mode = Mode.DONE
            listener?.onSpaceLongPress()
            invalidate()
            return
        }
        if (k.alts.isEmpty()) return
        p.mode = Mode.POPUP
        p.popupItems = k.alts
        p.popupCols = min(k.alts.size, 7)
        val rows = ceil(k.alts.size / p.popupCols.toFloat()).toInt()
        val cellW = min(max(b.rect.width(), 38 * dp), 50 * dp)
        p.popupCellH = rowH * 0.92f
        val w = cellW * p.popupCols
        val h = p.popupCellH * rows
        keyRect(b, tmp)
        p.popupRect.set(tmp.centerX() - w / 2, tmp.top - h - 6 * dp, tmp.centerX() + w / 2, tmp.top - 6 * dp)
        clampX(p.popupRect)
        clampTop(p.popupRect)
        p.popupSel = 0
        listener?.onKeyDown(k)
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        val b = p.box
        when (p.mode) {
            Mode.POPUP -> {
                val cols = p.popupCols
                val cw = p.popupRect.width() / cols
                val col = ((x - p.popupRect.left) / cw).toInt().coerceIn(0, cols - 1)
                val rows = ceil(p.popupItems.size / cols.toFloat()).toInt()
                val row = ((y - p.popupRect.top) / p.popupCellH).toInt().coerceIn(0, rows - 1)
                val sel = (row * cols + col).coerceIn(0, p.popupItems.size - 1)
                if (sel != p.popupSel) { p.popupSel = sel; invalidate() }
            }
            Mode.NORMAL -> {
                val k = b?.key ?: return
                val dx = x - p.downX
                val dy = y - p.downY
                if (k.code == Key.SPACE) {
                    if (abs(dx) > 14 * dp && abs(dx) > abs(dy)) {
                        handler.removeCallbacks(p.longPress)
                        p.mode = Mode.TRACKPAD
                        p.lastStepX = x
                        trackpadActive = true
                        invalidate()
                    } else if (dy < -34 * dp && abs(dx) < 30 * dp) {
                        handler.removeCallbacks(p.longPress)
                        p.mode = Mode.SPACE_UP
                    }
                } else if (k.code == Key.DELETE) {
                    if (dx < -22 * dp) {
                        handler.removeCallbacks(p.repeat)
                        p.mode = Mode.DELSWIPE
                        updateDel(p, x)
                    }
                } else if (k.kind == Key.Kind.CHAR) {
                    val nb = hit(x, y)
                    if (nb != null && nb !== b && nb.key.kind == Key.Kind.CHAR) {
                        handler.removeCallbacks(p.longPress)
                        p.box = nb
                        if (nb.key.alts.isNotEmpty()) handler.postDelayed(p.longPress, longPressMs)
                        invalidate()
                    }
                }
            }
            Mode.TRACKPAD -> {
                val step = 11 * dp
                val steps = ((x - p.lastStepX) / step).toInt()
                if (steps != 0) {
                    p.lastStepX += steps * step
                    listener?.onCursor(steps)
                }
            }
            Mode.DELSWIPE -> updateDel(p, x)
            else -> {}
        }
    }

    private fun updateDel(p: Ptr, x: Float) {
        val pulled = p.downX - x - 22 * dp
        val words = if (pulled < 0) 0 else 1 + (pulled / (34 * dp)).toInt()
        if (words != p.delWords) {
            p.delWords = words
            listener?.onDeleteSwipe(words, false)
            invalidate()
        }
    }

    private fun up(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        ptrs.remove(id)
        handler.removeCallbacks(p.longPress)
        handler.removeCallbacks(p.repeat)
        val k = p.box?.key
        when (p.mode) {
            Mode.SIDE -> if (p.side != null && (sideSwap.contains(x, y) || sideExit.contains(x, y))) listener?.onOneHanded(p.side!!)
            Mode.POPUP -> if (k != null) listener?.onKey(k, p.popupItems.getOrNull(p.popupSel))
            Mode.TRACKPAD -> trackpadActive = false
            Mode.SPACE_UP -> listener?.onSpaceSwipeUp()
            Mode.DELSWIPE -> listener?.onDeleteSwipe(p.delWords, true)
            Mode.NORMAL -> if (k != null) {
                if (!(k.code == Key.DELETE && p.repeated)) listener?.onKey(k, null)
            }
            Mode.DONE -> {}
        }
        invalidate()
    }

    fun cancelAll() {
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            handler.removeCallbacks(p.longPress)
            handler.removeCallbacks(p.repeat)
            if (p.mode == Mode.DELSWIPE && p.delWords > 0) listener?.onDeleteSwipe(0, true)
        }
        ptrs.clear()
        trackpadActive = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
        cache?.recycle()
        cache = null
    }

    /** برای تست و عکس صفحه: کشیدن کامل بی لمس. */
    fun renderTo(c: Canvas) { cacheDirty = true; ensureCache(); cache?.let { c.drawBitmap(it, 0f, 0f, null) } }

    /** مدت آخرین ساخت لایه‌ی ثابت (برای سنجش سرعت در تست). */
    fun measureRebuildMs(): Double {
        val t0 = SystemClock.elapsedRealtimeNanos()
        cacheDirty = true
        ensureCache()
        return (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
    }
}
