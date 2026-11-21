package cafe.kankipro.keyboard.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import cafe.kankipro.keyboard.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * نوار بالای کیبورد (Canvas، بی Compose تا هر لمس فوری باشد). چهار حالت:
 * - ICONS: کلیپ‌بورد، ایموجی، استودیوی فونت، ترمینال، یک‌دستی، صدا، تنظیمات.
 * - SUGGESTIONS: سه پیشنهاد کلمه (وسطی = بهترین).
 * - TERMINAL: کلیدهای ترمینال و برنامه‌نویسی (ESC، TAB، CTRL و ALT چسبنده، | / \ ~ $ { } [ ] و چهار جهت)، کشیدنی.
 * - VOICE: موج زنده‌ی صدا + دکمه‌ی پایان.
 * آیکن‌ها Vector Drawable هستند (بی عکس).
 */
class TopBarView(context: Context) : View(context) {

    enum class Mode { ICONS, SUGGESTIONS, TERMINAL, VOICE }

    interface Listener {
        fun onIcon(id: String)
        fun onSuggestion(index: Int, text: String)
        fun onSuggestionLong(text: String)
        fun onTermKey(id: String)
        fun onVoiceStop()
        fun onLiveStyleOff()
    }

    var listener: Listener? = null
    var theme: KeyboardTheme = KeyboardTheme.preset("cyber")
        set(v) { field = v; invalidate() }
    var mode = Mode.ICONS
        set(v) { if (field != v) { field = v; scroller.forceFinished(true); scrollPos = 0f; invalidate() } }
    var suggestions: List<String> = emptyList()
        set(v) { field = v; invalidate() }
    var ctrlState = 0
        set(v) { field = v; invalidate() }
    var altState = 0
        set(v) { field = v; invalidate() }
    var rawMode = false
        set(v) { field = v; invalidate() }
    var liveStyleName: String? = null
        set(v) { field = v; invalidate() }
    var voiceText: String = ""
        set(v) { field = v; invalidate() }
    var voiceLang: String = "fa"
    var heightScale = 1f
        set(v) { field = v; requestLayout() }

    private var message: String? = null
    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val main = Handler(Looper.getMainLooper())

    data class Icon(val id: String, val res: Int)

    val icons = listOf(
        Icon("terminal", R.drawable.ic_terminal), Icon("clipboard", R.drawable.ic_clipboard), Icon("emoji", R.drawable.ic_emoji),
        Icon("fonts", R.drawable.ic_fonts), Icon("onehand", R.drawable.ic_onehand), Icon("mic", R.drawable.ic_mic),
        Icon("settings", R.drawable.ic_settings),
    )
    private val drawables = HashMap<Int, Drawable>()
    private fun drawable(res: Int): Drawable = drawables.getOrPut(res) { ContextCompat.getDrawable(context, res)!!.mutate() }

    /** کلیدهای ترمینال: شناسه ← برچسب. */
    val termKeys = listOf(
        "raw" to "RAW", "esc" to "ESC", "tab" to "TAB", "ctrl" to "CTRL", "alt" to "ALT", "left" to "←", "up" to "↑",
        "down" to "↓", "right" to "→", "|" to "|", "/" to "/", "\\" to "\\", "~" to "~", "$" to "$", "{" to "{", "}" to "}",
        "[" to "[", "]" to "]", "-" to "-", "_" to "_", ":" to ":", ";" to ";", "home" to "HOME", "end" to "END",
        "pgup" to "PgUp", "pgdn" to "PgDn",
    )
    private val repeatable = setOf("left", "right", "up", "down")

    // ---------------------------------------------------------------- اندازه
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (44 * dp * (0.85f + 0.15f * heightScale)).toInt())
    }

    // ---------------------------------------------------------------- کشیدن
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val levels = FloatArray(48)
    private var levelHead = 0

    fun pushLevel(l: Float) {
        levels[levelHead] = l
        levelHead = (levelHead + 1) % levels.size
        invalidate()
    }

    private val clearMessage = Runnable { message = null; invalidate() }

    fun flash(msg: String, ms: Long = 2600) {
        message = msg
        invalidate()
        main.removeCallbacks(clearMessage)
        main.postDelayed(clearMessage, ms)
    }

    private fun termKeyWidth(label: String): Float = max(44 * dp, text.apply { textSize = 13 * sp }.measureText(label) + 22 * dp)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (cafe.kankipro.keyboard.BuildConfig.DEBUG) post {
            // فقط دیباگ: جای آیکن‌ها و کلیدهای ترمینال برای تست خودکار
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val (start, slot) = iconSlots(width.toFloat())
            val sb = StringBuilder("bar")
            icons.forEachIndexed { i, ic -> sb.append("\nicon|").append(ic.id).append('|').append((loc[0] + start + slot * i + slot / 2).toInt()).append('|').append(loc[1] + height / 2) }
            var x = 40 * dp + 4 * dp
            for ((id, label) in termKeys) { val kw = termKeyWidth(label); sb.append("\nterm|").append(id).append('|').append((loc[0] + x + kw / 2).toInt()).append('|').append(loc[1] + height / 2); x += kw + 6 * dp }
            android.util.Log.d("KbBar", sb.toString())
        }
    }

    override fun onDraw(c: Canvas) {
        val t = theme
        val w = width.toFloat()
        val h = height.toFloat()
        fill.shader = LinearGradient(0f, 0f, 0f, h, t.bgTop, t.bgTop, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        message?.let { m ->
            text.textSize = 13 * sp; text.color = t.text
            drawCentered(c, m, w / 2, h / 2, w - 16 * dp)
            return
        }
        when (mode) {
            Mode.ICONS -> drawIcons(c, w, h)
            Mode.SUGGESTIONS -> drawSuggestions(c, w, h)
            Mode.TERMINAL -> drawTerminal(c, w, h)
            Mode.VOICE -> drawVoice(c, w, h)
        }
    }

    private fun liveChipWidth(): Float {
        val n = liveStyleName ?: return 0f
        text.textSize = 12 * sp
        return text.measureText("$n ✕") + 20 * dp
    }

    private fun drawLiveChip(c: Canvas, h: Float) {
        val n = liveStyleName ?: return
        val t = theme
        val cw = liveChipWidth()
        r.set(6 * dp, h * 0.2f, 6 * dp + cw, h * 0.8f)
        fill.shader = LinearGradient(r.left, 0f, r.right, 0f, t.accentA, t.accentB, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        fill.shader = null
        text.textSize = 12 * sp; text.color = 0xFFFFFFFF.toInt()
        drawCentered(c, "$n ✕", r.centerX(), r.centerY(), cw)
    }

    private fun iconSlots(w: Float): Pair<Float, Float> {
        val start = if (liveStyleName != null) liveChipWidth() + 10 * dp else 0f
        return start to (w - start) / icons.size
    }

    private fun drawIcons(c: Canvas, w: Float, h: Float) {
        val t = theme
        drawLiveChip(c, h)
        val (start, slot) = iconSlots(w)
        val s = (22 * dp).toInt()
        icons.forEachIndexed { i, ic ->
            val cx = start + slot * i + slot / 2
            val active = (ic.id == "terminal" && rawMode)
            if (active) {
                fill.color = KeyboardTheme.withAlpha(t.accentB, 0x40)
                c.drawCircle(cx, h / 2, 17 * dp, fill)
            }
            val d = drawable(ic.res)
            d.setTint(if (ic.id == "mic") t.accentB else t.text)
            d.setBounds((cx - s / 2).toInt(), (h / 2 - s / 2).toInt(), (cx + s / 2).toInt(), (h / 2 + s / 2).toInt())
            d.draw(c)
        }
    }

    private fun drawSuggestions(c: Canvas, w: Float, h: Float) {
        val t = theme
        drawLiveChip(c, h)
        val start = if (liveStyleName != null) liveChipWidth() + 10 * dp else 0f
        val list = suggestions.take(3)
        if (list.isEmpty()) { drawIcons(c, w, h); return }
        val order = slotOrder(list.size)
        val slot = (w - start) / 3f
        for ((i, idx) in order.withIndex()) {
            val s = list[idx]
            val cx = start + slot * i + slot / 2
            val primary = idx == primaryIndex(list.size)
            text.textSize = (if (primary) 16f else 15f) * sp
            text.isFakeBoldText = primary
            text.color = if (primary) t.text else t.subText
            drawCentered(c, s, cx, h / 2, slot - 10 * dp)
            text.isFakeBoldText = false
            if (i > 0) {
                stroke.color = KeyboardTheme.withAlpha(t.text, 0x22); stroke.strokeWidth = dp
                c.drawLine(start + slot * i, h * 0.28f, start + slot * i, h * 0.72f, stroke)
            }
        }
    }

    /** خانه‌ی وسط = بهترین پیشنهاد (اندیس ۱ اگر دست‌کم دو پیشنهاد هست). */
    private fun primaryIndex(n: Int) = if (n >= 2) 1 else 0
    private fun slotOrder(n: Int): List<Int> = when (n) { 1 -> listOf(0); 2 -> listOf(0, 1); else -> listOf(0, 1, 2) }

    private var scrollPos = 0f
    private fun termContentWidth(): Float = termKeys.sumOf { termKeyWidth(it.second).toDouble() }.toFloat() + 6 * dp * termKeys.size

    private fun drawTerminal(c: Canvas, w: Float, h: Float) {
        val t = theme
        // دکمه‌ی برگشت ثابت در چپ
        val back = 40 * dp
        val bd = drawable(R.drawable.ic_back)
        bd.setTint(t.text)
        val s = (20 * dp).toInt()
        bd.setBounds((back / 2 - s / 2).toInt(), (h / 2 - s / 2).toInt(), (back / 2 + s / 2).toInt(), (h / 2 + s / 2).toInt())
        bd.draw(c)
        c.save()
        c.clipRect(back, 0f, w, h)
        var x = back + 4 * dp - scrollPos
        for ((id, label) in termKeys) {
            val kw = termKeyWidth(label)
            if (x + kw > back && x < w) {
                r.set(x, h * 0.16f, x + kw, h * 0.84f)
                val on = (id == "ctrl" && ctrlState > 0) || (id == "alt" && altState > 0) || (id == "raw" && rawMode)
                val locked = (id == "ctrl" && ctrlState == 2) || (id == "alt" && altState == 2)
                if (on) {
                    fill.shader = LinearGradient(r.left, 0f, r.right, 0f, t.accentA, t.accentB, Shader.TileMode.CLAMP)
                } else fill.color = t.keyFill
                c.drawRoundRect(r, 8 * dp, 8 * dp, fill)
                fill.shader = null
                stroke.color = if (locked) 0xFFFFFFFF.toInt() else t.border
                stroke.strokeWidth = if (locked) 2 * dp else dp
                c.drawRoundRect(r, 8 * dp, 8 * dp, stroke)
                text.textSize = 13 * sp
                text.color = if (on) 0xFFFFFFFF.toInt() else t.text
                text.typeface = android.graphics.Typeface.MONOSPACE
                drawCentered(c, label, r.centerX(), r.centerY(), kw)
                text.typeface = android.graphics.Typeface.DEFAULT
            }
            x += kw + 6 * dp
        }
        c.restore()
        // سایه‌ی نرم لبه‌ی راست: هنوز کلید هست
        if (scrollPos < maxScroll(w)) {
            fill.shader = LinearGradient(w - 24 * dp, 0f, w, 0f, 0, t.bgTop or 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(w - 24 * dp, 0f, w, h, fill)
            fill.shader = null
        }
    }

    private fun maxScroll(w: Float) = max(0f, termContentWidth() + 44 * dp - w)

    private fun drawVoice(c: Canvas, w: Float, h: Float) {
        val t = theme
        // دکمه‌ی پایان
        val cx = 26 * dp
        fill.shader = LinearGradient(cx - 16 * dp, 0f, cx + 16 * dp, 0f, t.accentA, t.accentB, Shader.TileMode.CLAMP)
        c.drawCircle(cx, h / 2, 16 * dp, fill)
        fill.shader = null
        fill.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(cx - 5 * dp, h / 2 - 5 * dp, cx + 5 * dp, h / 2 + 5 * dp, 2 * dp, 2 * dp, fill)
        // موج
        val left = 56 * dp
        val right = w - 44 * dp
        val n = levels.size
        val bw = (right - left) / n
        for (i in 0 until n) {
            val l = levels[(levelHead + i) % n]
            val bh = max(3 * dp, l * h * 0.7f)
            val x = left + i * bw + bw / 2
            fill.color = blend(t.accentA, t.accentB, i / n.toFloat())
            fill.alpha = (110 + 145 * l).toInt().coerceIn(0, 255)
            c.drawRoundRect(x - bw * 0.3f, h / 2 - bh / 2, x + bw * 0.3f, h / 2 + bh / 2, bw * 0.3f, bw * 0.3f, fill)
        }
        if (voiceText.isNotEmpty()) {
            text.textSize = 13 * sp; text.color = t.text
            fill.color = KeyboardTheme.withAlpha(t.bgBottom or 0xFF000000.toInt(), 0xB0)
            val tw = min(text.measureText(voiceText), right - left)
            c.drawRoundRect((left + right) / 2 - tw / 2 - 8 * dp, h * 0.18f, (left + right) / 2 + tw / 2 + 8 * dp, h * 0.82f, 8 * dp, 8 * dp, fill)
            drawCentered(c, voiceText.takeLast(60), (left + right) / 2, h / 2, right - left)
        }
        text.textSize = 12 * sp; text.color = t.subText
        drawCentered(c, voiceLang.uppercase(), w - 22 * dp, h / 2, 40 * dp)
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * f)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun drawCentered(c: Canvas, s: String, cx: Float, cy: Float, maxW: Float) {
        var shown = s
        if (text.measureText(shown) > maxW) {
            while (shown.length > 1 && text.measureText("$shown…") > maxW) shown = shown.dropLast(1)
            shown += "…"
        }
        val fm = text.fontMetrics
        c.drawText(shown, cx, cy - (fm.ascent + fm.descent) / 2, text)
    }

    // ---------------------------------------------------------------- لمس
    private val scroller = OverScroller(context)
    private var vt: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var downTime = 0L
    private var repeatId: String? = null
    private val repeatRun = object : Runnable {
        override fun run() {
            val id = repeatId ?: return
            listener?.onTermKey(id)
            main.postDelayed(this, 55)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; dragging = false; downTime = e.eventTime
                scroller.forceFinished(true)
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                if (mode == Mode.TERMINAL) {
                    val id = termAt(e.x)
                    if (id != null && id in repeatable) { repeatId = id; main.postDelayed(repeatRun, 380) }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (mode == Mode.TERMINAL) {
                    if (!dragging && abs(e.x - downX) > 10 * dp) { dragging = true; cancelRepeat() }
                    if (dragging) {
                        scrollPos = (scrollPos - (e.x - lastX)).coerceIn(0f, maxScroll(width.toFloat()))
                        invalidate()
                    }
                }
                lastX = e.x
            }
            MotionEvent.ACTION_UP -> {
                vt?.addMovement(e)
                val wasRepeating = repeatId != null && e.eventTime - downTime > 380
                cancelRepeat()
                if (dragging) {
                    vt?.computeCurrentVelocity(1000)
                    val v = -(vt?.xVelocity ?: 0f)
                    scroller.fling(scrollPos.toInt(), 0, v.toInt(), 0, 0, maxScroll(width.toFloat()).toInt(), 0, 0)
                    postInvalidateOnAnimation()
                } else if (!wasRepeating) tap(e.x, e.y, longPress = e.eventTime - downTime > 500)
                vt?.recycle(); vt = null
            }
            MotionEvent.ACTION_CANCEL -> { cancelRepeat(); vt?.recycle(); vt = null }
        }
        return true
    }

    private fun cancelRepeat() { repeatId = null; main.removeCallbacks(repeatRun) }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPos = scroller.currX.toFloat()
            postInvalidateOnAnimation()
        }
    }

    private fun termAt(x: Float): String? {
        val back = 40 * dp
        if (x < back) return null
        var cx = back + 4 * dp - scrollPos
        for ((id, label) in termKeys) {
            val kw = termKeyWidth(label)
            if (x >= cx && x <= cx + kw + 6 * dp) return id
            cx += kw + 6 * dp
        }
        return null
    }

    private fun tap(x: Float, y: Float, longPress: Boolean) {
        if (message != null) { message = null; invalidate(); return }
        val w = width.toFloat()
        if (liveStyleName != null && (mode == Mode.ICONS || mode == Mode.SUGGESTIONS) && x < liveChipWidth() + 10 * dp) {
            listener?.onLiveStyleOff(); return
        }
        when (mode) {
            Mode.ICONS -> iconTap(x, w)
            Mode.SUGGESTIONS -> {
                val list = suggestions.take(3)
                if (list.isEmpty()) { iconTap(x, w); return }
                val start = if (liveStyleName != null) liveChipWidth() + 10 * dp else 0f
                val i = ((x - start) / ((w - start) / 3f)).toInt().coerceIn(0, 2)
                val order = slotOrder(list.size)
                val idx = order.getOrNull(i) ?: return
                if (longPress) listener?.onSuggestionLong(list[idx]) else listener?.onSuggestion(idx, list[idx])
            }
            Mode.TERMINAL -> {
                if (x < 40 * dp) { listener?.onIcon("terminal_close"); return }
                termAt(x)?.let { listener?.onTermKey(it) }
            }
            Mode.VOICE -> if (x < 56 * dp) listener?.onVoiceStop()
        }
    }

    private fun iconTap(x: Float, w: Float) {
        val (start, slot) = iconSlots(w)
        val i = ((x - start) / slot).toInt()
        icons.getOrNull(i)?.let { listener?.onIcon(it.id) }
    }
}
