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


def wait_layout(want, tries=20):
    for _ in range(tries):
        layout, t, c = keys()
        if layout and layout.startswith(want):
            return t, c
        time.sleep(1)
    raise RuntimeError("layout %s never showed (last %s)" % (want, keys()[0]))


def type_text(s, t, c):
    for ch in s:
        if ch == " ":
            tap(*c[32], wait=0.35)
        else:
            if ch not in t:
                raise RuntimeError("no key for %r" % ch)
            tap(*t[ch], wait=0.35)


def _unused_clear_field(c):
    # Ctrl+A و پاک‌کن از نوار ترمینال
    b = bar()
    tap(*b[("icon", "terminal")])
    tap(*b[("term", "ctrl")])
    _, t, c2 = keys()
    tap(*t.get("a", t.get("ش")))
    tap(*c2[-5])
    tap(*b[("term", "ctrl")], wait=0.2)  # اگر روشن مانده بود
    tap(*b[("term", "ctrl")], wait=0.2)
    tap(40, b[("icon", "terminal")][1])  # برگشت


def open_field():
    sh("am", "start", "-W", "-n", PKG + "/.ui.settings.SettingsActivity")
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
    print(adb("install", "-r", "-g", APK, timeout=600).strip())
    sh("ime", "enable", IME)
    sh("ime", "set", IME)
    sh("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    adb("logcat", "-c")
    open_field()
    shot("01_keyboard_fa")
    t, c = wait_layout("fa")
    check("keyboard shows Persian layout", "ض" in t and "ش" in t, list(t)[:10])

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
    type_text("ok", t, c)
    tap(*c[32], wait=0.15)
    tap(*c[32], wait=0.8)
    _, t, c = keys()
    type_text("Go", {**t, "G": t.get("G", t.get("g"))}, c)
    v = field_text()
    check("double-space period + auto caps", v is not None and v.endswith("ok. Go"), v)

    # ۸) حرکت نشانگر با کشیدن روی فاصله
    x, y = c[32]
    swipe(x, y, x - 75, y, ms=500)
    _, t, c = keys()
    tap(*t.get("x", t.get("X")))
    v = field_text()
    check("space trackpad moves cursor", v is not None and v.endswith("ok. Gxo") or v.endswith("ok. GXo"), v)

    # ۹) حالت خام (ترمینال) با کلید RAW و CTRL+A
    b = bar()
    tap(*b[("icon", "terminal")])
    shot("03_terminal_bar")
    b = bar()
    tap(*b[("term", "raw")], wait=1.5)
    _, t, c = keys()
    type_text("ls", t, c)
    v = field_text()
    check("raw mode types directly", v is not None and v.endswith("ls") or (v and "ls" in v), v)
    tap(*b[("term", "ctrl")])
    tap(*t["a"])
    tap(*c[-5])  # پاک‌کن با KeyEvent واقعی روی متن انتخاب‌شده
    v = field_text()
    check("CTRL+A then DEL clears the field", v == "", v)
    tap(*b[("term", "raw")], wait=1.5)  # برگشت به حالت معمولی

    # ۱۰) کلیپ‌بورد: کپی کد یک‌بارمصرف = حساس
    _, t, c = keys()
    tap(*c[-2], wait=1.2)  # ?123
    _, t, c = keys()
    for d in "482913":
        tap(*t[d], wait=0.3)
    b = bar()
    tap(*b[("term", "ctrl")]); _, t2, _ = keys()
    tap(*c[-3], wait=1.2)  # ABC
    _, t, c = keys()
    tap(*b[("term", "ctrl")]); tap(*t["a"])
    tap(*b[("term", "ctrl")]); tap(*t["c"], wait=1.5)
    rows = sh("run-as", PKG, "sqlite3", "databases/keyboard.db", "select text, sensitive from clips;").strip()
    check("copied OTP saved as sensitive", "482913|1" in rows, rows)

    # ۱۱) پنل‌ها باز و بسته شوند بی خطا
    tap(40, b[("icon", "terminal")][1])
    b = bar()
    for ic in ("emoji", "fonts", "clipboard"):
        tap(*b[("icon", ic)], wait=3)
        shot("04_panel_" + ic)
        alive = "FATAL" not in adb("logcat", "-d", "-s", "AndroidRuntime:E")
        check("panel %s opens without crash" % ic, alive)
        # برگشت: دکمه‌ی کیبورد پایین چپ پنل
        _, _, c = keys()
        root_h = int(sh("wm", "size").split()[-1].split("x")[1])
        tap(60, root_h - 40 - 60, wait=1.5)
    crash = adb("logcat", "-d", "-s", "AndroidRuntime:E")
    check("no crash in the whole run", "FATAL" not in crash, crash[-2000:])
    shot("05_end")

    ok = sum(1 for r in results if r[1])
    print("\n%d/%d passed" % (ok, len(results)))
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
