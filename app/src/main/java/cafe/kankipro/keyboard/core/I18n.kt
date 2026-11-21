package cafe.kankipro.keyboard.core

import java.util.Locale

/**
 * متن‌های دوزبانه: هر متن رابط کاربری یک‌جا با فارسی و انگلیسی نوشته می‌شود؛ `t("فارسی", "English")`.
 * زبان از تنظیمات (`uiLang`: system / fa / en) می‌آید.
 */
object L {
    @Volatile
    var mode: String = "system"

    val fa: Boolean
        get() = when (mode) {
            "fa" -> true
            "en" -> false
            else -> Locale.getDefault().language in setOf("fa", "ar", "ur", "ps")
        }
}

fun t(fa: String, en: String): String = if (L.fa) fa else en
