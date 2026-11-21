package cafe.kankipro.keyboard

import cafe.kankipro.keyboard.data.clipboard.SensitiveDetector
import cafe.kankipro.keyboard.data.macros.Macro
import cafe.kankipro.keyboard.data.macros.MacroExpander
import cafe.kankipro.keyboard.data.words.WordStore
import cafe.kankipro.keyboard.engine.text.SuggestionEngine
import cafe.kankipro.keyboard.engine.voice.SpokenPunctuation
import cafe.kankipro.keyboard.engine.voice.VoiceModelManager
import cafe.kankipro.keyboard.ime.UltraKeyboardService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogicTest {
    @Test fun spokenPunctuationPersian() {
        assertEquals("سلام، خوبی؟", SpokenPunctuation.process("سلام ویرگول خوبی علامت سوال", "fa"))
        assertEquals("تمام شد.\nخط دوم", SpokenPunctuation.process("تمام شد نقطه خط بعدی خط دوم", "fa"))
        assertEquals("الف؛ ب", SpokenPunctuation.process("الف نقطه ویرگول ب", "fa"))
        assertEquals("گفت «سلام»", SpokenPunctuation.process("گفت گیومه باز سلام گیومه بسته", "fa"))
    }

    @Test fun spokenPunctuationEnglish() {
        assertEquals("Hello, how are you? I am fine.", SpokenPunctuation.process("hello comma how are you question mark i am fine period", "en"))
        assertEquals("Line one\nNext", SpokenPunctuation.process("line one new line next", "en"))
        assertEquals("go on", SpokenPunctuation.process("go on", "en", startOfSentence = false))
        assertEquals("hello comma", SpokenPunctuation.process("hello comma", "en", enabled = false, startOfSentence = false))
        assertTrue(SpokenPunctuation.endsSentence("Done. "))
        assertFalse(SpokenPunctuation.endsSentence("still going"))
    }

    @Test fun sensitiveDetection() {
        assertTrue(SensitiveDetector.isSensitive("482913"))
        assertTrue(SensitiveDetector.isSensitive("۴۸۲۹۱۳"))
        assertTrue(SensitiveDetector.isSensitive("4111 1111 1111 1111")) // Luhn درست
        assertFalse(SensitiveDetector.isSensitive("4111 1111 1111 1112"))
        assertTrue(SensitiveDetector.isSensitive("Tr0ub4dor&3"))
        assertTrue(SensitiveDetector.isSensitive("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ123456"))
        assertTrue(SensitiveDetector.isSensitive("anything", flaggedBySystem = true))
        assertFalse(SensitiveDetector.isSensitive("سلام دوست من"))
        assertFalse(SensitiveDetector.isSensitive("vless://uuid@host:443?security=reality#Kanki"))
        assertFalse(SensitiveDetector.isSensitive("meet me at 5pm"))
        assertFalse(SensitiveDetector.isSensitive("hello@example.com"))
        assertEquals("ab••••yz", SensitiveDetector.mask("abcdwxyz"))
    }

    @Test fun macros() {
        val list = listOf(Macro(1, "@@", "me@example.com"), Macro(2, "brb", "be right back"), Macro(3, "off", "x", enabled = false))
        assertEquals("me@example.com", MacroExpander.find("mail @@", list, afterSeparator = false)?.macro?.expansion)
        assertEquals(2, MacroExpander.find("@@", list, afterSeparator = false)?.deleteCount)
        assertNull(MacroExpander.find("x@@", list, afterSeparator = false)) // وسط کلمه نه
        assertNull(MacroExpander.find("ok brb", list, afterSeparator = false)) // میان‌بر حرفی فقط بعد از فاصله
        assertEquals("be right back", MacroExpander.find("ok brb", list, afterSeparator = true)?.macro?.expansion)
        assertNull(MacroExpander.find("xbrb", list, afterSeparator = true))
        assertNull(MacroExpander.find("off", list, afterSeparator = true))
        assertFalse(MacroExpander.validTrigger("a"))
        assertFalse(MacroExpander.validTrigger("a b"))
    }

    @Test fun suggestionsAndAutocorrect() {
        val dir = Files.createTempDirectory("kb").toFile()
        val e = SuggestionEngine(WordStore(dir))
        val s = e.suggest("hel", "en")
        assertEquals("hel", s[0])
        assertTrue(s.toString(), s.contains("hello") || s.contains("help"))
        assertEquals("the", e.autocorrect("teh", "en"))
        assertEquals("The", e.autocorrect("Teh", "en"))
        assertEquals("don't", e.autocorrect("dont", "en"))
        assertNull(e.autocorrect("the", "en")) // کلمه‌ی درست دست نمی‌خورد
        assertNull(e.autocorrect("zzqx", "en")) // جایگزین روشن نیست
        assertNull(e.autocorrect("سلم", "fa")) // فارسی تصحیح خودکار ندارد
        // یادگیری: کلمه‌ی تازه بعد از چند بار اول می‌آید و دیگر «غلط» نیست
        repeat(5) { e.learn("kankiy", "en") }
        assertNull(e.autocorrect("kankiy", "en"))
        assertEquals("kankiy", e.suggest("kan", "en")[1])
        assertTrue(e.suggest("سل", "fa").contains("سلام"))
        dir.deleteRecursively()
    }

    @Test fun wordStorePersists() {
        val dir = Files.createTempDirectory("kb").toFile()
        val a = WordStore(dir)
        a.learn("کانکی"); a.learn("کانکی"); a.flush()
        assertEquals(2, WordStore(dir).count("کانکی"))
        dir.deleteRecursively()
    }

    @Test fun editDistance() {
        assertTrue(SuggestionEngine.editDistanceOne("the", "teh"))
        assertTrue(SuggestionEngine.editDistanceOne("hello", "helo"))
        assertTrue(SuggestionEngine.editDistanceOne("hello", "hellos"))
        assertTrue(SuggestionEngine.editDistanceOne("cat", "cut"))
        assertFalse(SuggestionEngine.editDistanceOne("cat", "dog"))
        assertFalse(SuggestionEngine.editDistanceOne("abc", "abc"))
    }

    @Test fun deleteHelpers() {
        assertEquals(2, UltraKeyboardService.lastGraphemeLength("a😀"))
        assertEquals(1, UltraKeyboardService.lastGraphemeLength("ab"))
        assertEquals("three".length, UltraKeyboardService.wordsBackLength("one two three", 1))
        assertEquals(" three".length + "two".length, UltraKeyboardService.wordsBackLength("one two three", 2))
        assertEquals("three  ".length, UltraKeyboardService.wordsBackLength("one two three  ", 1))
        assertEquals("one two".length, UltraKeyboardService.wordsBackLength("one two", 5))
    }

    @Test fun zipSlipIsRejected() {
        val dir = Files.createTempDirectory("kb").toFile()
        val zip = File(dir, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("model/am/final.mdl")); z.write(1); z.closeEntry()
            z.putNextEntry(ZipEntry("../../escape.txt")); z.write(2); z.closeEntry()
        }
        try {
            VoiceModelManager.unzipSafely(zip, File(dir, "out"), 1_000_000)
            fail("should reject")
        } catch (_: SecurityException) {
        }
        assertFalse(File(dir.parentFile, "escape.txt").exists())
        dir.deleteRecursively()
    }

    @Test fun modelRootIsFound() {
        val dir = Files.createTempDirectory("kb").toFile()
        val zip = File(dir, "m.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("vosk-model-small-fa-0.42/am/final.mdl")); z.write(byteArrayOf(1, 2, 3)); z.closeEntry()
            z.putNextEntry(ZipEntry("vosk-model-small-fa-0.42/conf/model.conf")); z.write(byteArrayOf(4)); z.closeEntry()
        }
        val out = File(dir, "out")
        VoiceModelManager.unzipSafely(zip, out, 1_000_000)
        assertEquals("vosk-model-small-fa-0.42", VoiceModelManager.findModelRoot(out)?.name)
        // سقف اندازه
        try { VoiceModelManager.unzipSafely(zip, File(dir, "o2"), 2); fail("cap") } catch (_: SecurityException) {}
        dir.deleteRecursively()
    }
}
