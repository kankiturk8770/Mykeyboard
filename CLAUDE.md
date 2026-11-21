# کیبورد کانکی (اندروید) — راهنمای Claude

کیبورد اندروید (IME): Kotlin، کیبورد با Canvas خالص (لمس تا نمایش زیر ۵ میلی‌ثانیه)، برنامه‌ی تنظیمات و پنل‌ها با Jetpack Compose.
پکیج `cafe.kankipro.keyboard`، minSdk 26، targetSdk 35. فایل نصبی هر پردازنده زیر ۱۵ مگ (الان حدود ۴٫۵ مگ؛ CI بیشتر از ۱۵ را رد می‌کند).
قانون‌های کار همان قانون‌های اپ کانکی (`kanki-app/CLAUDE.md`) است، این‌جا خلاصه و برای همین پروژه.

## نقشه‌ی کد (`app/src/main/java/cafe/kankipro/keyboard/`)
| کار | فایل |
|---|---|
| اجزای مشترک (تنظیمات، دیتابیس، کلیپ‌بورد، میان‌برها، کلمه‌ها، مدل صدا، بکاپ) یک نمونه برای کیبورد و برنامه | `KeyboardApp.kt` |
| متن دوزبانه `t("فارسی", "English")`، زبان از `uiLang` | `core/I18n.kt` |
| همه‌ی تنظیمات (`KbSettings`، یک JSON در SharedPreferences، اعتبارسنجی هر مقدار در `fromJson`) | `data/settings/Settings.kt` |
| کلیپ‌بورد: Room (`ClipItem`، `KeyboardDatabase`)، سنجاق، پاک‌سازی خودکار، متن حساس (`SensitiveDetector`) | `data/clipboard/` |
| میان‌برهای متنی (`@@` ← ایمیل)، `MacroExpander` | `data/macros/Macro.kt` |
| کلمه‌های یادگرفته (`words.tsv`) و فهرست کوچک پرکاربردها | `data/words/` |
| بکاپ یک‌فایلی JSON (فقط سنجاق‌شده‌ها، نه کلمه‌ها) | `data/backup/BackupManager.kt` |
| کشیده‌نویس هوشمند، کلاس اتصال حروف، قالب‌های فارسی | `engine/typography/PersianTypographyEngine.kt` |
| ۵۰+ فونت یونیکد، نشان‌ها و قاب‌های گیمری | `engine/typography/UnicodeFontStyler.kt` |
| پیشنهاد و تصحیح خودکار (محتاط، فقط انگلیسی) | `engine/text/SuggestionEngine.kt` |
| تایپ صوتی آفلاین (Vosk، حذف نویز/اکو، جریانی) / دانلود مدل (SHA-256 ثابت، zip امن) / علامت‌گذاری گفتاری | `engine/voice/OfflineVoiceEngine.kt` / `VoiceModelManager.kt` / `SpokenPunctuation.kt` |
| سرویس کیبورد: مسیر RICH و RAW، Enter، پاک‌کن، اشاره‌ها، صدا، پنل‌ها، کلیپ‌بورد | `ime/UltraKeyboardService.kt` |
| تشخیص ترمینال/بازی (`TerminalDetector`)، فرستادن KeyEvent با CTRL/ALT چسبنده (`KeySender`) | `ime/InputPipeline.kt` |
| کیبورد Canvas (لمس چندانگشتی، پنجره‌ی نگه داشتن، حرکت نشانگر روی فاصله، کشیدن پاک‌کن، یک‌دستی) | `ui/keyboard/KeyboardCanvasView.kt` |
| چیدمان‌ها (فارسی، انگلیسی، عربی، علامت، عدد) | `ui/keyboard/KeyboardLayouts.kt` |
| تم‌ها (سایبرپانک، AMOLED، ابسیدین، یخی، دلخواه) | `ui/keyboard/KeyboardTheme.kt` |
| نوار بالا (آیکن‌ها، پیشنهاد، ترمینال، موج صدا) | `ui/keyboard/TopBarView.kt` |
| لرزش و صدای کلید ساخته‌شده با ریاضی | `ui/keyboard/FeedbackEngine.kt` |
| پنل‌های داخل کیبورد (Compose): کلیپ‌بورد، ایموجی/کائوموجی، استودیوی فونت | `ui/panels/` |
| برنامه‌ی تنظیمات (MVVM) | `ui/settings/SettingsActivity.kt`، `SettingsViewModel.kt`، `SettingsScreen.kt`، `ColorPicker.kt` |

## ساخت و تست
- این ماشین ابری: SDK در `/opt/android-sdk` (`local.properties`، بیرون از git)، آینه‌ی گوگل Maven در `~/.gradle/init.d/mirror.gradle` (بیرون از مخزن).
- `./gradlew :app:testDebugUnitTest` (منطق، Room، بکاپ، چیدمان‌ها و عکس کیبورد در `build/shots/`) و `./gradlew :app:assembleRelease`.
- تست روی شبیه‌ساز (بی KVM کند است): `./gradlew -PemulatorAbi :app:assembleDebug` (x86_64 هم ساخته می‌شود)، بعد `scripts/emulator_e2e.sh`.
- CI (`.github/workflows/build.yml`): هر push تست، ساخت، سقف ۱۵ مگ، APK و عکس‌ها به‌صورت artifact.

## قوانین هر تغییر (از اپ کانکی)
1. نسخه در `app/build.gradle.kts` بالا برود (`versionCode` و `versionName`).
2. یک بخش تازه‌ی فارسی بالای `CHANGES.md` و «تازه‌های نسخه» کوتاه در `README.md`.
3. هر متن رابط کاربری با `t("فارسی", "English")`. کامنت‌ها فارسی، بدون اسم مدل در کامنت یا کامیت.
4. کار که تمام شد: commit، push به برنچ کار و Pull Request. **قبل از مرج به main یک بار از صاحب ریپو بپرس.** اگر CI قرمز است، اول درستش کن.
5. اگر ساختار عوض شد (فایل تازه)، همین `CLAUDE.md` به‌روز شود.
6. **هر تغییر تا ته مسیرش:** لمس ← `KeyboardCanvasView` ← سرویس ← RICH یا RAW ← `InputConnection` برنامه ← و برگشت (`onUpdateSelection`)؛ هر دو زبان، هر دو مسیر،
   فیلد رمز، ترمینال، پنل‌ها، صدا. تست کارایی (تست واحد، ساخت، شبیه‌ساز) و تست امنیت (چه کسی می‌رسد، نشت متن، دانلود، بکاپ) هر دو. در گزارش بنویس کدام ایستگاه تست شد و کدام نه.
7. **بدون اختلال (دستور صاحب پروژه):** کیبورد هیچ‌وقت نباید تایپ را گیر بیندازد؛ هر خطا در یک بخش (صدا، پنل، کلیپ‌بورد) نباید خود تایپ را بخواباند.
8. **Play Protect (درس اپ کانکی):** کلید یا سوییچ دست‌ساز بی‌نقش نساز؛ در Compose همیشه `Switch`/دکمه‌ی استاندارد یا `clickable(role = …)`.

## حریم خصوصی و امنیت (ثابت)
- هیچ داده‌ای از گوشی بیرون نمی‌رود. اینترنت فقط برای دانلود مدل صدا (فقط `alphacephei.com` با HTTPS، اثر انگشت SHA-256 ثابت در `VoiceModelManager.catalog`).
- فیلد رمز و `IME_FLAG_NO_PERSONALIZED_LEARNING`: بی پیشنهاد، بی یادگیری، کپی از آن حساس.
- متن حساس کلیپ‌بورد (کد، کارت با Luhn، رمز، توکن) بعد از `sensitiveWipeSec` از تاریخچه و از کلیپ‌بورد سیستم پاک می‌شود مگر سنجاق شود.
- `allowBackup=false` و قانون‌های data-extraction: هیچ چیز به بکاپ ابری نمی‌رود؛ بکاپ فقط فایل JSON خود کاربر (بی کپی‌های سنجاق‌نشده و کلمه‌ها).
- بی فونت، عکس یا فایل صوتی در `res/` و `assets/`: آیکن‌ها Vector، جلوه‌ها روی Canvas، صدای کلید با ریاضی.

## زبان
صاحب پروژه فارسی‌زبان است و دارد برنامه‌نویسی یاد می‌گیرد: جواب‌ها فارسی، کوتاه و ساده. کار در پس‌زمینه، وسط کار گزارش نده، آخر یک گزارش کوتاه.

## گزارش کارها
### کارهای باز
- [ ] روی گوشی واقعی صاحب پروژه: نصب، فعال کردن، تایپ فارسی/انگلیسی، Termux، پابجی (چت بازی)، تایپ صوتی با مدل فارسی، کلیپ‌بورد حساس، بکاپ.
- [ ] کلید امضای ثابت (Secrets `KEYBOARD_KEYSTORE` و `KEYBOARD_KEY_PASS`)؛ بی آن هر ساخت CI کلید دیباگ دیگری دارد و نسخه‌ی تازه روی قبلی نصب نمی‌شود.
- [ ] فهرست کلمه‌های فارسی بزرگ‌تر برای پیشنهاد (الان چند صد کلمه + یادگیری از خود کاربر).

### فهرست نسخه‌ها
| نسخه | تاریخ | خلاصه |
|---|---|---|
| 1.0.0 | 2026-11-21 | نسخه‌ی اول: کیبورد Canvas، فارسی/انگلیسی/عربی، RAW برای ترمینال و بازی، کشیده‌نویس، ۵۰+ فونت، صدا آفلاین، کلیپ‌بورد، میان‌بر، تم‌ها، بکاپ |

### آخرین کارها — 1.0.0 (2026-11-21، برنچ `ccr-d2b4bc5d-4yv4y2`)
ساخت کامل پروژه از صفر طبق سند صاحب پروژه. جزئیات و نتیجه‌ی تست‌ها در `CHANGES.md`.
