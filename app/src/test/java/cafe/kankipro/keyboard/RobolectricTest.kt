package cafe.kankipro.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import androidx.test.core.app.ApplicationProvider
import cafe.kankipro.keyboard.data.backup.BackupManager
import cafe.kankipro.keyboard.data.clipboard.ClipboardRepository
import cafe.kankipro.keyboard.data.clipboard.KeyboardDatabase
import cafe.kankipro.keyboard.data.macros.Macro
import cafe.kankipro.keyboard.data.macros.MacroRepository
import cafe.kankipro.keyboard.data.settings.KbSettings
import cafe.kankipro.keyboard.data.settings.SettingsStore
import cafe.kankipro.keyboard.ime.InputMode
import cafe.kankipro.keyboard.ime.TerminalDetector
import cafe.kankipro.keyboard.ui.keyboard.KeyboardCanvasView
import cafe.kankipro.keyboard.ui.keyboard.KeyboardLayouts
import cafe.kankipro.keyboard.ui.keyboard.KeyboardTheme
import cafe.kankipro.keyboard.ui.keyboard.Layer
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = KeyboardApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RobolectricTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<KeyboardApp>()

    @Test fun settingsRoundTripAndSanitize() {
        val s = KbSettings(themeId = "ice", heightPct = 120, kashida = 2, languages = listOf("en", "fa"), rawPackages = listOf("com.termux"))
        assertEquals(s, KbSettings.fromJson(s.toJson()))
        val bad = JSONObject().put("heightPct", 999).put("themeId", "<script>").put("languages", org.json.JSONArray(listOf("xx")))
            .put("rawPackages", org.json.JSONArray(listOf("bad name", "com.ok.app"))).put("kashida", -4)
        val f = KbSettings.fromJson(bad)
        assertEquals(130, f.heightPct)
        assertEquals("cyber", f.themeId)
        assertEquals(listOf("fa", "en"), f.languages)
        assertEquals(listOf("com.ok.app"), f.rawPackages)
        assertEquals(0, f.kashida)
        val store = SettingsStore(ctx)
        store.update { it.copy(heightPct = 80) }
        assertEquals(80, SettingsStore(ctx).value.heightPct)
    }

    @Test fun terminalDetection() {
        val s = KbSettings()
        fun ei(pkg: String, type: Int) = EditorInfo().apply { packageName = pkg; inputType = type }
        assertEquals(InputMode.RAW, TerminalDetector.detect(ei("com.termux", InputType.TYPE_CLASS_TEXT), s))
        assertEquals(InputMode.RAW, TerminalDetector.detect(ei("x.y", InputType.TYPE_NULL), s))
        assertEquals(InputMode.RAW, TerminalDetector.detect(ei("x.y", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS), s))
        assertEquals(InputMode.RICH, TerminalDetector.detect(ei("org.telegram.messenger", InputType.TYPE_CLASS_TEXT), s))
        assertEquals(InputMode.RICH, TerminalDetector.detect(ei("com.termux", InputType.TYPE_CLASS_TEXT), s.copy(rawOverrides = mapOf("com.termux" to false))))
        assertEquals(InputMode.RAW, TerminalDetector.detect(ei("org.telegram.messenger", InputType.TYPE_CLASS_TEXT), s.copy(rawOverrides = mapOf("org.telegram.messenger" to true))))
        assertTrue(TerminalDetector.isPrivate(ei("a.b", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertFalse(TerminalDetector.isPrivate(ei("a.b", InputType.TYPE_CLASS_TEXT)))
    }

    private fun db() = Room.inMemoryDatabaseBuilder(ctx, KeyboardDatabase::class.java).allowMainThreadQueries().build()

    @Test fun clipboardPinsDedupAndWipe() = runBlocking {
        val d = db()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = ClipboardRepository(d.clipDao(), scope)
        repo.addNow("hello", now = 1000)
        repo.addNow("hello", now = 2000) // تکراری: فقط بالا می‌آید
        repo.addNow("123456", now = 2000) // کد: حساس
        repo.addNow("old note", now = 0)
        var items = repo.items.first()
        assertEquals(3, items.size)
        assertTrue(items.first { it.text == "123456" }.sensitive)
        val pinned = items.first { it.text == "old note" }
        d.clipDao().setPinned(pinned.id, true)
        // بعد از ۶۱ ثانیه: حساس می‌رود، سنجاق می‌ماند؛ با پاک‌سازی ۱ دقیقه‌ای «hello» هم می‌رود
        val gone = repo.sweep(autoWipeMin = 1, sensitiveSec = 60, now = 2000 + 61_000 + 60_000)
        assertEquals(listOf("123456"), gone)
        items = repo.items.first()
        assertEquals(listOf("old note"), items.map { it.text })
        d.close()
    }

    @Test fun backupRoundTrip() = runBlocking {
        val d = db()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = SettingsStore(ctx)
        val clips = ClipboardRepository(d.clipDao(), scope)
        val macros = MacroRepository(d.macroDao(), scope)
        store.update { it.copy(themeId = "custom", customGlow = 0xFF123456.toInt(), kashida = 3) }
        clips.addNow("keep me")
        clips.addNow("secret 1")
        d.clipDao().setPinned(clips.items.first().first { it.text == "keep me" }.id, true)
        macros.upsertNow(Macro(trigger = "@@", expansion = "me@example.com"))
        val json = BackupManager(store, clips, macros).export()
        assertFalse("unpinned clips must not be exported", json.contains("secret 1"))

        val d2 = db()
        val store2 = SettingsStore(ctx).also { it.update { s -> s.copy(themeId = "ice", kashida = 0) } }
        val r = BackupManager(store2, ClipboardRepository(d2.clipDao(), scope), MacroRepository(d2.macroDao(), scope)).import(json)
        assertEquals(1, r.pins)
        assertEquals(1, r.macros)
        assertEquals("custom", store2.value.themeId)
        assertEquals(3, store2.value.kashida)
        assertEquals(listOf("keep me"), d2.clipDao().pinned().map { it.text })
        try { BackupManager(store2, ClipboardRepository(d2.clipDao(), scope), MacroRepository(d2.macroDao(), scope)).import("{\"format\":\"other\"}"); throw AssertionError() }
        catch (_: IllegalArgumentException) {}
        d.close(); d2.close()
    }

    /** عکس کیبورد در همه‌ی تم‌ها و چیدمان‌ها + زمان ساخت لایه‌ی ثابت. */
    @Test fun renderKeyboards() {
        val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
        val combos = listOf("cyber" to "fa", "amoled" to "en", "obsidian" to "fa", "ice" to "en", "custom" to "fa")
        for ((theme, lang) in combos) {
            val v = KeyboardCanvasView(ctx)
            v.theme = KeyboardTheme.from(KbSettings(themeId = theme, customGlow = 0xFF3DDC97.toInt()))
            v.layout = KeyboardLayouts.build(lang, Layer.ALPHA, KeyboardLayouts.Options(numberRow = theme == "ice"))
            v.spaceLabel = if (lang == "fa") "فارسی" else "English"
            val w = 1080
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0xFF20242E.toInt())
            v.renderTo(Canvas(bmp))
            File(out, "keyboard_${theme}_$lang.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(v.measureRebuildMs() < 200)
        }
        for (layer in listOf(Layer.SYMBOLS, Layer.SHIFT_MARKS, Layer.NUMBER)) {
            val v = KeyboardCanvasView(ctx)
            v.layout = KeyboardLayouts.build("fa", layer, KeyboardLayouts.Options())
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            v.renderTo(Canvas(bmp))
            File(out, "keyboard_layer_${layer.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun layoutsAreConsistent() {
        for (lang in listOf("fa", "en", "ar")) for (layer in Layer.entries) {
            val l = KeyboardLayouts.build(lang, layer, KeyboardLayouts.Options(numberRow = true))
            assertTrue("$lang $layer", l.rows.isNotEmpty())
            assertTrue("$lang $layer enter", l.rows.flatMap { it.keys }.any { it.code == cafe.kankipro.keyboard.ui.keyboard.Key.ENTER })
            assertTrue("$lang $layer delete", l.rows.flatMap { it.keys }.any { it.code == cafe.kankipro.keyboard.ui.keyboard.Key.DELETE })
        }
        // همه‌ی حرف‌های فارسی (۳۲ حرف) روی صفحه‌ی اصلی هستند
        val fa = KeyboardLayouts.build("fa", Layer.ALPHA, KeyboardLayouts.Options()).rows.flatMap { it.keys }.map { it.text }.toSet()
        for (c in "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی") assertTrue("missing $c", c.toString() in fa)
        // نیم‌فاصله کلید خودش را دارد
        assertTrue("‌" in fa)
        // روی حرف فارسی نگه داشتن، همه‌ی حرکت‌ها را می‌دهد
        val be = KeyboardLayouts.build("fa", Layer.ALPHA, KeyboardLayouts.Options()).rows.flatMap { it.keys }.first { it.text == "ب" }
        for (m in listOf("َ", "ِ", "ُ", "ً", "ّ", "ْ")) assertTrue(be.alts.contains("ب$m"))
    }
}
