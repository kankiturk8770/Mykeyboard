package cafe.kankipro.keyboard.data.words

/**
 * فهرست کوچک پرکاربردترین کلمه‌ها (داخل کد، بی فایل جدا) برای پیشنهاد از اولین تایپ.
 * ترتیب = پرکاربردتر اول؛ امتیاز از روی جای کلمه حساب می‌شود.
 */
object BuiltinWords {
    val en: List<String> = """
the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an
will my one all would there their what so up out if about who get which go me when make can like time no just him know
take people into year your good some could them see other than then now look only come its over think also back after use
two how our work first well way even new want because any these give day most us is are was were been has had did does
going yes thanks thank please hello hi okay ok sorry love great nice really very much more right here why where something
should need feel never always still today tomorrow tonight morning night week month home friend friends family phone call
message send sent text got let tell said help sure maybe better best last long little own old big high different small large
next early young important few public bad same able great problem every hand part place case point government company number
group fact world life school state week again find down off where many before must through back years being those both each
house while under around keep start might show leave put mean become while away move live believe hold bring happen write
provide sit stand lose pay meet include continue set learn change lead understand watch follow stop create speak read allow
add spend grow open walk win offer remember consider appear buy wait serve die send expect build stay fall cut reach kill
remain suggest raise pass sell require report decide pull game play player team match server connection internet network
email address password account login download update install setting settings app application android keyboard voice
language english persian music video photo picture link file folder online offline free money price office business
question answer idea information story thing things anything everything nothing someone everyone anyone already actually
probably usually almost enough together without against between during later soon ago again yet though although however
""".trim().split(Regex("\\s+")).distinct()

    val fa: List<String> = """
و در به از که این را با است برای آن یک خود تا کرد بر هم نیز شده شد می ها کند دارد بود باید شود گفت پس اما یا اگر
همه کار سال مورد بین وی کنند داد دو چه چیزی هر ما من تو او شما آنها ایشان اینکه چون حتی بسیار خیلی بیشتر کمی دیگر
سلام ممنون مرسی خواهش لطفا ببخشید چطوری خوبی خوبم خوب بد عالی دوست دوستان عزیز جان مامان بابا خانواده خانه کجا کی
چرا چطور چقدر امروز فردا دیروز الان حالا بعد قبل شب صبح ظهر عصر هفته ماه روز ساعت دقیقه وقت زمان زود دیر بله نه
آره نخیر باشه حتما شاید فکر کنم می‌خواهم می‌خوام می‌دونم نمی‌دونم می‌کنم می‌کنی می‌کنه دارم داری داره هستم هستی هست
نیست نیستم بودم بودی رفتم رفتی رفت آمد اومد اومدم بیا برو بگو بگم گفتم گفتی بده بدم بخر خریدم ببین دیدم دیدی بنویس نوشتم
پیام تماس زنگ گوشی تلفن شماره ایمیل آدرس رمز حساب کاربری ورود دانلود نصب برنامه اپ تنظیمات کیبورد صدا زبان فارسی
انگلیسی عکس فیلم ویدیو آهنگ موزیک بازی سرور اینترنت شبکه سرعت قیمت پول تومان ریال خرید فروش کار کارها شرکت دانشگاه
مدرسه درس کلاس استاد دانشجو کتاب مطالعه سوال جواب مشکل راه حل کمک لازم مهم اول آخر جدید قدیم بزرگ کوچک زیاد کم همیشه
هیچ‌وقت گاهی معمولا واقعا دقیقا اصلا فقط هنوز دوباره باهم تنها همین همان اینجا آنجا اونجا کنار پیش پیشنهاد نظر خبر
اطلاعات موضوع مسئله داستان چیز چیزها همه‌چیز هیچی کسی همه‌کس هرکس دنیا ایران تهران شهر کشور مردم زندگی سلامتی خدا
انشالله ماشالله تبریک تولد مبارک شاد خوشحال ناراحت خسته گرسنه تشنه بیمار دکتر بیمارستان داروخانه غذا آب نان چای قهوه
ماشین تاکسی مترو اتوبوس خیابان کوچه پارک فروشگاه بازار رستوران هتل سفر بلیط فرودگاه پرواز قطار ساعت‌ها هفته‌ها ماه‌ها
""".trim().split(Regex("\\s+")).distinct()

    val ar: List<String> = """
في من على إلى أن هذا هذه التي الذي مع كان عن ما لا هو هي نعم شكرا مرحبا السلام عليكم كيف حالك جيد يوم اليوم غدا الآن
""".trim().split(Regex("\\s+")).distinct()

    fun of(lang: String): List<String> = when (lang) {
        "fa" -> fa
        "ar" -> ar
        else -> en
    }
}
