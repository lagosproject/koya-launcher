"""
screenshot_maker_tablet.py — Tablet Screenshot capture automation for Koya Launcher
=============================================================================
Automates launching and capturing screenshots of Koya Launcher's screens
(Home, App Drawer, Settings) in tablet mode (using density 280) across
multiple locales using ADB, then calls google_play_prep_tablet.py to
compile the final 7-inch and 10-inch showcase cards.

Requirements:
  1. A connected Android device/emulator (with developer options & USB debugging enabled).
  2. The Koya app (com.lagosproject.koya) installed on the device.
  3. Python 3 and Pillow installed.

Run:  python3 screenshot_maker_tablet.py
"""

import subprocess
import time
import os
import sys

LANGS = ["en-US", "es-ES", "fr-FR"]
PACKAGE = "com.lagosproject.koya"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(SCRIPT_DIR, "screenshots_tablet")


def run_cmd(cmd):
    print(f"  → {cmd}")
    r = subprocess.run(cmd, shell=True, stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, text=True)
    if r.returncode != 0:
        print(f"    ⚠ stderr: {r.stderr.strip()}")
    return r.stdout.strip()


def check_adb():
    devices = run_cmd("adb devices").splitlines()
    # Filter out header and empty lines
    active_devices = [line for line in devices[1:] if line.strip() and "device" in line]
    if not active_devices:
        print("\n❌ ERROR: No Android devices/emulators connected via ADB.")
        print("   Please connect a device, verify 'adb devices' displays it, and try again.")
        print("   Note: The tablet showcase cards can still be compiled if you have screenshots by running:")
        print("   python3 google_play_prep_tablet.py\n")
        return False
    return True


def screencap(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # Capture screen directly to local path via ADB pipe
    run_cmd(f"adb shell screencap -p > {path}")
    print(f"    📸 Saved screenshot: {path}")
    time.sleep(0.8)


def capture_screenshots_for_lang(lang):
    print(f"\n{'='*60}")
    print(f"  AUTOMATING LOCALE (TABLET): {lang}")
    print(f"{'='*60}")

    # Set app locale
    run_cmd(f"adb shell cmd locale set-app-locales {PACKAGE} --locales {lang}")
    time.sleep(0.5)

    # Force stop app to clear stack
    run_cmd(f"adb shell am force-stop {PACKAGE}")
    time.sleep(1.0)

    # 1. Capture Home Activity (First Widget)
    print("  Launching HomeActivity (First Widget)...")
    run_cmd(f"adb shell am start -n {PACKAGE}/.HomeActivity --ez screenshot_mode true")
    time.sleep(3.5)  # Wait for launcher to load clock & layouts
    screencap(f"{SRC_DIR}/{lang}/home_screenshot.png")

    # 1b. Capture Home Activity (Second Widget)
    print(f"\n👉 [ACTION REQUIRED] Please change the home screen widget to the SECOND widget on your device (for {lang}).")
    input("   Press [ENTER] when the second widget is configured and ready on screen... ")
    print("  Capturing HomeActivity (Second Widget)...")
    screencap(f"{SRC_DIR}/{lang}/home_widget2_screenshot.png")

    # 2. Capture App Drawer
    print("  Opening AppDrawer directly...")
    run_cmd(f"adb shell am start -n {PACKAGE}/.AppDrawerActivity")
    time.sleep(2.0)
    screencap(f"{SRC_DIR}/{lang}/drawer_screenshot.png")

    # Go back to home
    run_cmd("adb shell input keyevent 4")
    time.sleep(1.0)

    # 3. Capture Settings
    print("  Opening Settings directly...")
    run_cmd(f"adb shell am start -n {PACKAGE}/.SettingsActivity")
    time.sleep(2.0)
    screencap(f"{SRC_DIR}/{lang}/settings_screenshot.png")

    # Go back to home
    run_cmd("adb shell input keyevent 4")
    time.sleep(1.0)


def main():
    print("=" * 60)
    print("  Koya Launcher — Tablet Screenshot Automation via ADB")
    print("=" * 60)

    if not check_adb():
        sys.exit(1)

    # Wake up screen and unlock
    run_cmd("adb shell input keyevent KEYCODE_WAKEUP")
    run_cmd("adb shell input keyevent 82")

    print("\nSetting screen density to 280 to simulate tablet layout...")
    run_cmd("adb shell wm density 280")
    time.sleep(3.0)

    try:
        for lang in LANGS:
            try:
                capture_screenshots_for_lang(lang)
            except Exception as e:
                print(f"  ❌ Error capturing {lang}: {e}")
    finally:
        print("\nRestoring original screen density...")
        run_cmd("adb shell wm density reset")
        time.sleep(1.0)

    # Restore launcher back to normal wallpaper mode
    print("\nRestoring launcher back to normal wallpaper mode...")
    run_cmd(f"adb shell am start -n {PACKAGE}/.HomeActivity --ez screenshot_mode false")
    time.sleep(1.0)

    print("\n✅ Tablet screenshots capture completed.")
    print("Running google_play_prep_tablet.py to generate store composites...")
    
    # Run the image preparation script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    prep_script = os.path.join(script_dir, "google_play_prep_tablet.py")
    subprocess.run([sys.executable, prep_script])


if __name__ == "__main__":
    main()
