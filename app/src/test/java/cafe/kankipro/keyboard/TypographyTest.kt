package cafe.kankipro.keyboard

import cafe.kankipro.keyboard.engine.typography.PersianTemplates
import cafe.kankipro.keyboard.engine.typography.PersianTypographyEngine as P
import cafe.kankipro.keyboard.engine.typography.UnicodeFontStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {
    private val k = P.TATWEEL.toString()

    @Test fun kashidaOnlyBetweenJoiningLetters() {
        // «س» دوطرفه است و به «ل» می‌چسبد
        assertEquals(1, P.kashidaCount("س", 'ل', 1))
        // «ا» به بعدی نمی‌چسبد: هرگز کشیده
        assertEquals(0, P.kashidaCount("سا", 'م', 3))
        // «د»، «ر»، «و» هم همین
        for (c in "دذرزژو") assertEquals("after $c", 0, P.kashidaCount("ب$c", 'ن', 3))
        // «لا» شکسته نمی‌شود
        assertEquals(0, P.kashidaCount("ل", 'ا', 3))
        // اول متن یا بعد از فاصله چیزی نیست
        assertEquals(0, P.kashidaCount("", 'س', 3))
        assertEquals(0, P.kashidaCount("سلام ", 'ب', 3))
        // بعد از کشیده‌ی موجود دوباره نه
        assertEquals(0, P.kashidaCount("س$k", 'ل', 3))
        // حرف لاتین یا عدد نه
        assertEquals(0, P.kashidaCount("س", '1', 3))
    }

    @Test fun kashidaLevels() {
        assertEquals(0, P.kashidaCount("ب", 'ن', 1)) // «ب» در سطح کم نیست
        assertEquals(1, P.kashidaCount("ک", 'ا', 1))
        assertEquals(2, P.kashidaCount("ک", 'ا', 2))
        assertEquals(1, P.kashidaCount("ع", 'ل', 2))
        assertEquals(3, P.kashidaCount("م", 'ی', 3))
        assertEquals(1, P.kashidaCount("ب", 'ن', 3))
    }

    @Test fun kashidaSkipsMarks() {
        // حرکت روی «س» مانع نیست
        assertEquals(1, P.kashidaCount("سَ", 'ل', 1))
    }

    @Test fun applyAndStrip() {
        val out = P.applyKashida("سلام کانکی", 1)
        assertEquals("س${k}لام کانکی".replace("کانکی", "ک${k}انک${k}ی"), out)
        assertEquals("سلام کانکی", P.stripKashida(out))
        // هیچ کلمه‌ای با کشیده تمام یا شروع نمی‌شود
        for (w in P.applyKashida("سلام گیمر عزیز لاله کشیده", 3).split(' ')) {
            assertFalse(w, w.endsWith(k)); assertFalse(w, w.startsWith(k))
        }
        assertFalse(P.applyKashida("لاله", 3).contains("ل${k}ا"))
    }

    @Test fun deleteRemovesLetterWithItsKashida() {
        assertEquals(2, P.deleteLength("س${k}ل"))
        assertEquals(4, P.deleteLength("م$k$k${k}ی"))
        assertEquals(1, P.deleteLength("سل"))
        assertEquals(2, P.deleteLength("سلَ")) // حرف با حرکتش
        assertEquals(1, P.deleteLength("a"))
    }

    @Test fun digits() {
        assertEquals("۱۲۳abc", P.toPersianDigits("123abc"))
        assertEquals("123", P.toLatinDigits("۱۲۳"))
        assertEquals("45", P.toLatinDigits("٤٥"))
    }

    @Test fun fontStylerCoversFiftyStylesAndHoles() {
        val styles = UnicodeFontStyler.styles
        assertTrue("${styles.size}", styles.size >= 50)
        assertEquals(styles.size, styles.map { it.id }.toSet().size)
        // هر سبک واقعاً متن را عوض می‌کند (جز آن‌هایی که فقط ترتیب را عوض می‌کنند نه؛ آن هم عوض می‌کند)
        for (s in styles) assertTrue(s.id, UnicodeFontStyler.apply("Kanki 123", s.id) != "Kanki 123")
        assertEquals("𝐊𝐚𝐧𝐤𝐢", UnicodeFontStyler.apply("Kanki", "bold"))
        assertEquals("ℎ", UnicodeFontStyler.apply("h", "italic")) // جای خالی جدول ایتالیک
        assertEquals("ℭ", UnicodeFontStyler.apply("C", "fraktur"))
        assertEquals("ℝ", UnicodeFontStyler.apply("R", "double"))
        assertEquals("ｋａｎｋｉ", UnicodeFontStyler.apply("kanki", "fullwidth"))
        assertEquals("ᴋᴀɴᴋɪ", UnicodeFontStyler.apply("kanki", "small_caps"))
        assertEquals("ᴉʞuɐʞ", UnicodeFontStyler.apply("kanki", "upside"))
        // فارسی دست نمی‌خورد
        assertEquals("سلام", UnicodeFontStyler.apply("سلام", "bold"))
        // زالگو پایدار است (پیش‌نمایش نمی‌پرد)
        assertEquals(UnicodeFontStyler.apply("abc", "zalgo"), UnicodeFontStyler.apply("abc", "zalgo"))
    }

    @Test fun persianTemplates() {
        for (tp in PersianTemplates.all) assertTrue(tp.id, PersianTemplates.apply("کانکی", tp.id).isNotBlank())
        assertEquals("﴿ کانکی ﴾", PersianTemplates.apply("کانکی", "ornate"))
    }
}
