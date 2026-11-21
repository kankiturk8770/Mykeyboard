#!/usr/bin/env python3
"""
تست سرتاسری کیبورد روی شبیه‌ساز (یا گوشی با USB): نصب، فعال کردن، لمس واقعی کلیدها با `adb input`،
و خواندن نتیجه از فیلد متن برنامه‌ی تنظیمات (uiautomator). جای کلیدها را نسخه‌ی دیباگ در logcat می‌نویسد (KbKeys، KbBar).

اجرا:  ./gradlew -PemulatorAbi :app:assembleDebug && python3 scripts/emulator_e2e.py [APK] [SERIAL]
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-x86_64-debug.apk"
SERIAL = sys.argv[2] if len(sys.argv) > 2 else "emulator-5554"
ADB = [os.environ.get("ADB", "adb"), "-s", SERIAL]
PKG = "cafe.kankipro.keyboard"
IME = PKG + "/.ime.UltraKeyboardService"
SHOTS = os.environ.get("SHOTS", "build/e2e")
os.makedirs(SHOTS, exist_ok=True)

results = []


def adb(*a, timeout=120):
    return subprocess.run(ADB + list(a), capture_output=True, text=True, timeout=timeout).stdout


def sh(*a, timeout=120):
    return adb("shell", *a, timeout=timeout)


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + repr(detail)), flush=True)


def shot(name):
    with open(os.path.join(SHOTS, name + ".png"), "wb") as f:
        f.write(subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=120).stdout)


def trial_log():
    return adb("logcat", "-d", "-v", "raw", "-s", "KbTrial:D").splitlines()


def field_text(wait=1.2):
    """متن فیلد امتحان (نسخه‌ی دیباگ هر تغییرش را در logcat می‌نویسد)."""
    time.sleep(wait)
    lines = [l for l in trial_log() if l.startswith("text=")]
    return lines[-1][5:].replace("\\n", "\n") if lines else None


def tap(x, y, wait=0.6):
    sh("input", "tap", str(x), str(y))
    time.sleep(wait)


def long_press(x, y, ms=900, wait=0.8):
    sh("input", "swipe", str(x), str(y), str(x), str(y), str(ms))
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=600, wait=0.8):
    sh("input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(wait)


def keys():
    """آخرین چیدمان کیبورد از logcat: {متن: (x, y)} و {کد: (x, y)}."""
    log = adb("logcat", "-d", "-v", "raw", "-s", "KbKeys:D")
    blocks = log.split("layout=")
    last = blocks[-1].strip().splitlines() if len(blocks) > 1 else []
    layout = last[0].strip() if last else None
    by_text, by_code = {}, {}
    for line in last[1:]:
        p = line.strip().split("|")
        if len(p) != 4:
            continue
        code, text, x, y = int(p[0]), p[1], int(p[2]), int(p[3])
        if code == 0 or code == 32:
            by_text.setdefault(text, (x, y))
        by_code.setdefault(code, (x, y))
    return layout, by_text, by_code


def bar():
    log = adb("logcat", "-d", "-v", "raw", "-s", "KbBar:D")
    last = log.split("bar")[-1].strip().splitlines()
    out = {}
    for line in last:
        p = line.strip().split("|")
        if len(p) == 4:
            out[(p[0], p[1])] = (int(p[2]), int(p[3]))
    return out


def wait_layout(want, tries=40):
    for i in range(tries):
        if i == 15 and not keys()[0]:
            # کیبورد هنوز نیامده: دوباره فیلد را لمس کن
            f = [l for l in trial_log() if l.startswith("field=")]
            if f:
                tap(*map(int, f[-1][6:].split("|")), wait=3)
        layout, t, c = keys()
        if layout and layout.startswith(want):
            return t, c
        time.sleep(1)
    raise RuntimeError("layout %s never showed (last %s)" % (want, keys()[0]))


def type_text(s, t=None, c=None):
    """هر حرف با چیدمان همان لحظه (بعد از حرف بزرگ خودکار، چیدمان عوض می‌شود)."""
    for ch in s:
        _, t, c = keys()
        if ch == " ":
            tap(*c[32], wait=0.4)
            continue
        key = t.get(ch) or t.get(ch.lower()) or t.get(ch.upper())
        if key is None:
            raise RuntimeError("no key for %r" % ch)
        tap(*key, wait=0.4)


def open_field():
    sh("am", "start", "-W", "-n", PKG + "/.ui.settings.SettingsActivity")
    time.sleep(5)
    for _ in range(60):
        f = [l for l in trial_log() if l.startswith("field=")]
        if f:
            x, y = map(int, f[-1][6:].split("|"))
            # boundsInWindow + نوار وضعیت (برنامه لبه‌به‌لبه است، پس پنجره از ۰ شروع می‌شود)
            tap(x, y, wait=4)
            return
        time.sleep(2)
    raise RuntimeError("trial field never laid out")


def main():
    # شبیه‌سازِ تازه‌بالاآمده پنجره‌ی «پاسخ نمی‌دهد» نشان می‌دهد که لمس‌ها را می‌گیرد
    sh("settings", "put", "global", "hide_error_dialogs", "1")
    sh("settings", "put", "secure", "anr_show_background", "0")
    time.sleep(45)
    sh("am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS")
    sh("input", "keyevent", "KEYCODE_HOME")
    print(adb("install", "-r", "-g", APK, timeout=600).strip())
    for _ in range(10):
        print("ime enable:", sh("ime", "enable", IME).strip())
        print("ime set:", sh("ime", "set", IME).strip())
        if IME.split("/")[0] in sh("settings", "get", "secure", "default_input_method"):
            break
        print(sh("ime", "list", "-a", "-s"))
        time.sleep(5)
    time.sleep(3)
    sh("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    adb("logcat", "-c")
    open_field()
    shot("01_keyboard_fa")
    t, c = wait_layout("fa")
    check("keyboard shows Persian layout", "ض" in t and "ش" in t, list(t)[:10])
    # کیبورد زیر نوار ناوبری نرود (اندروید ۱۵ لبه‌به‌لبه)
    screen_h = int(sh("wm", "size").split()[-1].split("x")[1])
    pads = [l for l in adb("logcat", "-d", "-v", "raw", "-s", "KbInsets:D").splitlines() if l.startswith("navPad=")]
    nav = int(pads[-1][7:]) if pads else -1
    lowest = max(y for (_, y) in c.values())
    half_row = int(26 * int(sh("wm", "density").split()[-1]) / 160)
    check("keyboard sits above the navigation bar", nav >= 0 and lowest + half_row <= screen_h - nav, (lowest, half_row, screen_h, nav))

    # ۱) تایپ فارسی + فاصله
    type_text("سلام ", t, c)
    v = field_text()
    check("Persian typing", v == "سلام ", v)

    # ۲) پاک‌کن یک حرف
    tap(*c[-5])
    v = field_text()
    check("delete one char", v == "سلام", v)
    tap(*c[32])

    # ۳) نیم‌فاصله
    type_text("می", t, c)
    tap(*t["‌"])
    type_text("روم ", t, c)
    v = field_text()
    check("ZWNJ key", v == "سلام می‌روم ", v)

    # ۴) نگه داشتن ض = رقم ۱
    long_press(*t["ض"])
    v = field_text()
    check("long-press popup gives digit", v == "سلام می‌روم ۱", v)
    tap(*c[-5])

    # ۵) کشیدن پاک‌کن به چپ = پاک کردن یک کلمه
    x, y = c[-5]
    swipe(x, y, x - 90, y, ms=700)
    v = field_text()
    check("swipe-delete one word", v == "سلام ", v)

    # ۶) انگلیسی و تصحیح خودکار
    tap(*c[-7], wait=1.5)
    t, c = wait_layout("en")
    shot("02_keyboard_en")
    type_text("teh ", t, c)
    v = field_text()
    check("English autocorrect teh->the", v == "سلام the ", v)
    # یک پاک‌کن بلافاصله تصحیح را برمی‌گرداند
    tap(*c[-5])
    v = field_text()
    check("backspace undoes autocorrect", v == "سلام teh", v)
    tap(*c[32])

    # ۷) دو فاصله = نقطه و حرف بزرگ بعدش
    type_text("ok")
    tap(*c[32], wait=0.15)
    tap(*c[32], wait=1.2)
    _, t, c = keys()
    check("auto caps after a sentence", "G" in t, sorted(t)[:5])
    type_text("Go")
    v = field_text()
    check("double-space period + auto caps", v is not None and v.endswith("teh ok. Go"), v)

    # ۸) حرکت نشانگر با کشیدن روی فاصله
    x, y = c[32]
    swipe(x, y, x - 75, y, ms=500)
    _, t, c = keys()
    tap(*t.get("x", t.get("X")))
    v = field_text()
    check("space trackpad moves cursor", v is not None and v.endswith("ok. Gxo"), v)
    # کلمه‌ی در حال تایپ را تمام کن تا نوار بالا آیکن‌ها را نشان دهد
    _, t, c = keys()
    tap(*c[32])

    # ۹) حالت خام (ترمینال) با کلید RAW و CTRL+A
    b = bar()
    tap(*b[("icon", "terminal")])
    shot("03_terminal_bar")
    b = bar()
    tap(*b[("term", "raw")], wait=1.5)
    _, t, c = keys()
    type_text("ls", t, c)
    v = field_text()
    check("raw mode types directly", v is not None and "ls" in v, v)
    tap(*b[("term", "ctrl")])
    tap(*t["a"])
    tap(*c[-5])  # پاک‌کن با KeyEvent واقعی روی متن انتخاب‌شده
    v = field_text()
    check("CTRL+A then DEL clears the field", v == "", v)
    tap(*b[("term", "raw")], wait=1.5)  # برگشت به حالت معمولی

    # ۱۰) کلیپ‌بورد: کپی کد یک‌بارمصرف = حساس (نوار ترمینال هنوز باز است)
    _, t, c = keys()
    tap(*c[-2], wait=1.2)  # ?123
    _, t, c = keys()
    for d in "482913":
        tap(*t[d], wait=0.3)
    tap(*c[-3], wait=1.2)  # ABC
    _, t, c = keys()
    tap(*b[("term", "ctrl")]); tap(*t["a"])
    tap(*b[("term", "ctrl")]); tap(*t["c"], wait=2)
    # اگر سیستم خبر کپی را نداد، باز کردن پنل کلیپ‌بورد آن را می‌خواند
    tap(50, b[("term", "ctrl")][1], wait=1.5)
    b = bar()
    tap(*b[("icon", "clipboard")], wait=3)
    shot("04_clipboard_after_copy")
    root_h = int(sh("wm", "size").split()[-1].split("x")[1])
    tap(110, root_h - 200, wait=1.5)
    clip = adb("logcat", "-d", "-v", "raw", "-s", "KbClip:D")
    check("copied OTP saved as sensitive", "added len=6 sensitive=true" in clip, clip[-300:])

    # ۱۱) پنل‌ها باز و بسته شوند بی خطا
    b = bar()
    for ic in ("emoji", "fonts", "clipboard"):
        tap(*b[("icon", ic)], wait=3)
        shot("04_panel_" + ic)
        alive = "FATAL" not in adb("logcat", "-d", "-s", "AndroidRuntime:E")
        check("panel %s opens without crash" % ic, alive)
        # برگشت: دکمه‌ی کیبورد پایین چپ پنل
        _, _, c = keys()
        root_h = int(sh("wm", "size").split()[-1].split("x")[1])
        tap(110, root_h - 200, wait=1.5)
    crash = adb("logcat", "-d", "-s", "AndroidRuntime:E")
    check("no crash in the whole run", "FATAL" not in crash, crash[-2000:])
    shot("05_end")

    ok = sum(1 for r in results if r[1])
    print("\n%d/%d passed" % (ok, len(results)))
    return 0 if ok == len(results) else 1


def diagnose():
    print("---- default IME:", sh("settings", "get", "secure", "default_input_method").strip())
    print("---- enabled IMEs:", sh("settings", "get", "secure", "enabled_input_methods").strip())
    print("\n".join(l for l in sh("dumpsys", "input_method").splitlines() if any(k in l for k in ("mCurId", "mInputShown", "mCurMethodId", "mServedView"))))
    print("---- errors:\n" + adb("logcat", "-d", "*:E")[-6000:])
    print("---- keyboard logs:\n" + adb("logcat", "-d", "-s", "KbKeys:D", "KbBar:D", "KbTrial:D", "InputMethodManagerService:*")[-3000:])
    shot("99_failure")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        diagnose()
        raise
