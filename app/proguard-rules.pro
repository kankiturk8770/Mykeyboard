# Vosk از راه JNA کتابخانه‌ی بومی را صدا می‌زند؛ اسم کلاس‌ها و متدها باید بمانند
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
