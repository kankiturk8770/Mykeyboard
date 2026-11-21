plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// امضای نسخه‌ی نهایی: روی گیت‌هاب کلید و رمز از Secrets می‌آید (build.yml).
// اگر کلید نبود، release با کلید دیباگ امضا می‌شود تا ساخت خطا ندهد (فقط برای تست).
val releaseKeystore = file("kanki-keyboard.jks")
val releaseKeyPass: String? = System.getenv("KEYBOARD_KEY_PASS")
val canSignRelease = releaseKeystore.exists() && !releaseKeyPass.isNullOrEmpty()

android {
    namespace = "cafe.kankipro.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "cafe.kankipro.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        // فقط فارسی و انگلیسی: منابع زبان‌های دیگرِ کتابخانه‌ها داخل فایل نصبی نمی‌روند
        androidResources { localeFilters += listOf("en", "fa") }
    }
    // یک APK جدا برای هر پردازنده؛ arm64 هدف اصلی است، armv7 برای گوشی‌های قدیمی
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // فقط برای تست روی شبیه‌ساز: ./gradlew -PemulatorAbi ...
            if (project.hasProperty("emulatorAbi")) include("x86_64")
            isUniversalApk = false
        }
    }
    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseKeyPass
                keyAlias = "kanki"
                keyPassword = releaseKeyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { t ->
                t.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // repo1.maven.org گاهی ۴۲۹ می‌دهد؛ همان مخزن از آدرس اصلی
                t.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
                t.systemProperty("shots.dir", rootProject.file("build/shots").absolutePath)
                t.maxHeapSize = "3g"
            }
        }
    }
    packaging {
        // کتابخانه‌ی بومی فشرده داخل APK (فایل نصبی کوچک‌تر)
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += listOf("META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*", "kotlin/**", "DebugProbesKt.bin")
        }
    }
    lint { abortOnError = false }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // دیتابیس کلیپ‌بورد و میان‌برها
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // تایپ صوتی آفلاین (Vosk؛ مدل هر زبان فقط وقتی کاربر خواست دانلود می‌شود)
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
