#!/usr/bin/env bash
# Smoke test on a connected phone or emulator (adb must see exactly one device).
#   scripts/smoke-test.sh [app-debug.apk] [output-dir]
# Installs a fresh copy, then: launch -> daily gift -> Play -> Add car -> drag-merge -> tow ->
# process death + resume -> Garage -> Settings -> random-tap stress test. Saves screenshots and
# fails if the app crashes or the game does not react.
set -euo pipefail
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
OUT=${2:-smoke-out}
PKG=com.tinymotors.game
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
# Always keep the device log, so a failure shows its cause.
trap 'adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true' EXIT

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
dump() {
  for i in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null; return 0; fi
    sleep 1
  done
  echo "uiautomator dump failed"; return 1
}
tap() { dump; read -r X Y < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "$1"); adb shell input tap "$X" "$Y"; echo "tap '$1' at $X,$Y"; }
has() { dump; python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "$1" >/dev/null 2>&1; }
cars() { dump; python3 "$HERE/smoke_ui.py" cars "$OUT/ui.xml"; }
crashed() { adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $PKG" ; }

adb wait-for-device
adb install -r "$APK"
adb shell pm clear "$PKG" >/dev/null
# Keep the UI idle for uiautomator: the app honours "remove animations" by reducing motion.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
shot 01_home_gift

if has "Tap the present"; then tap "Tap the present"; sleep 2; shot 02_gift_open; tap "OK"; sleep 1; fi
shot 03_home
tap "Play"; sleep 2
shot 04_game_tutorial

BEFORE=$(cars)
echo "cars before: $BEFORE"
if crashed; then echo "FAIL: the app crashed opening the game"; exit 1; fi
test "$BEFORE" -eq 7 || { echo "FAIL: expected 7 starting cars"; exit 1; }
tap "Add car"; sleep 2
AFTER_ADD=$(cars)
echo "cars after add: $AFTER_ADD"
test "$AFTER_ADD" -eq 8 || { echo "FAIL: Add car did not add a car"; exit 1; }
shot 05_after_add

dump
read -r X1 Y1 X2 Y2 < <(python3 "$HERE/smoke_ui.py" pair "$OUT/ui.xml")
echo "dragging $X1,$Y1 -> $X2,$Y2"
adb shell input swipe "$X1" "$Y1" "$X2" "$Y2" 900
sleep 2
shot 06_after_merge
if has "OK"; then tap "OK"; sleep 1; fi   # the tutorial's last card covers the board
shot 07_tutorial_done
AFTER_MERGE=$(cars)
echo "cars after merge: $AFTER_MERGE"
test "$AFTER_MERGE" -eq 7 || { echo "FAIL: drag-merge did not merge"; exit 1; }

# Tow: drag the first car onto the tow truck button.
dump
read -r CX CY < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Kei car" || python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "City hatch")
read -r TX TY < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Tow truck")
adb shell input swipe "$CX" "$CY" "$TX" "$TY" 900
sleep 2
AFTER_TOW=$(cars)
echo "cars after tow: $AFTER_TOW"
test "$AFTER_TOW" -eq 6 || { echo "FAIL: tow did not remove a car"; exit 1; }
shot 08_after_tow

# Rapid taps on Add car until the board is full, then one more tap on a full board.
dump
read -r AX AY < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Add car")
for i in $(seq 1 30); do adb shell input tap "$AX" "$AY"; done
sleep 2
FULL=$(cars)
echo "cars after rapid taps: $FULL"
test "$FULL" -eq 30 || { echo "FAIL: board should be full"; exit 1; }
shot 09_full_board

# Process death: background, kill, relaunch -> same board.
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am kill "$PKG"
sleep 1
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
if has "Play"; then tap "Play"; sleep 2; fi
RESUMED=$(cars)
echo "cars after process death: $RESUMED"
test "$RESUMED" -eq 30 || { echo "FAIL: board not restored after process death"; exit 1; }
shot 10_resumed

adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Garage"; sleep 2; shot 11_garage
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Settings"; sleep 2; shot 12_settings
adb shell input keyevent KEYCODE_BACK; sleep 1

# Animations back on, then a random-tap stress test inside the app.
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 3
shot 13_home_animated
tap "Play" || true
sleep 3
shot 14_game_animated
adb shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --throttle 60 -s 42 1500 >/dev/null 2>&1 || true
sleep 2
shot 15_after_monkey

if crashed; then echo "FAIL: crash detected"; exit 1; fi
echo "SMOKE TEST PASSED"
