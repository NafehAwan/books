#!/usr/bin/env bash
# Smoke test on a connected phone or emulator (adb must see exactly one device).
#   scripts/smoke-test.sh [app-debug.apk] [output-dir]
# Installs a fresh copy, then: launch -> daily gift -> Play -> drag-merge -> move -> tow ->
# fill the board -> process death + resume -> Store -> Garage -> Settings -> PLAY starts fresh -> random-tap stress test. Saves screenshots and
# fails if the app crashes or the game does not react.
set -euo pipefail
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
OUT=${2:-smoke-out}
PKG=com.tinymotors.game
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
# Always keep the device log, so a failure shows its cause.
report() {
  code=$?
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  if [ "$code" -ne 0 ]; then
    echo "---- smoke test failed; what was on screen:"
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb shell cat /sdcard/ui.xml \
      | grep -o '\(text\|content-desc\|package\)="[^"]\+"' | sort | uniq -c | head -40 || true
    echo "---- app errors in the device log:"
    grep -E "ANR in|FATAL EXCEPTION|AndroidRuntime|Process: $PKG|$PKG|System.err|DEBUG +:" "$OUT/logcat.txt" \
      | grep -vE "uiautomator|RuntimeInit uid 2000|default boot image|lock profiling|Shutting down VM|NetworkFactory|ConnectivityService|AiAiEcho|ImeTracker|InputMethodService|CarrierSvc|ImsResolver" \
      | tail -60 || true
  fi
}
trap report EXIT

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
dump() {
  for i in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null; return 0; fi
    sleep 1
  done
  echo "uiautomator dump failed"; return 1
}
# Emulators sometimes show "<system app> isn't responding"; that is not our app, so tap Wait.
dismiss_anr() {
  if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb shell cat /sdcard/ui.xml | grep -q "t responding"; then
    adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
    read -r X Y < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Wait") && adb shell input tap "$X" "$Y"
    echo "dismissed a system 'not responding' dialog"; sleep 2
  fi
}
tap() { dismiss_anr; dump; read -r X Y < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "$1"); adb shell input tap "$X" "$Y"; echo "tap '$1' at $X,$Y"; }
has() { dismiss_anr; dump; python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "$1" >/dev/null 2>&1; }
cars() { dismiss_anr; dump; python3 "$HERE/smoke_ui.py" cars "$OUT/ui.xml"; }
crashed() { adb logcat -d | grep -A2 "FATAL EXCEPTION" | grep -q "Process: $PKG" || adb logcat -d | grep -q "ANR in $PKG"; }

adb wait-for-device
# Let a freshly booted emulator settle; its own launcher often freezes for a while after boot.
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 20
for i in 1 2 3; do dismiss_anr; done
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK"
adb shell pm clear "$PKG" >/dev/null
sleep 10   # let the install / clear broadcasts finish before the app starts
# Keep the UI idle for uiautomator: the app honours "remove animations" by reducing motion.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
shot 01_home_gift

# A fresh install makes Android send late package notices that can restart the screen once,
# swallowing a tap; so try the present a few times and only continue once it has opened.
for i in 1 2 3 4; do
  if has "Tap the present"; then tap "Tap the present"; sleep 2; fi
  if has "OK"; then shot 02_gift_open; tap "OK"; sleep 1; break; fi
  sleep 2
done
shot 03_home
tap "Play"; sleep 2
shot 04_game_tutorial

BEFORE=$(cars)
echo "cars before: $BEFORE"
if crashed; then echo "FAIL: the app crashed opening the game"; exit 1; fi
test "$BEFORE" -eq 7 || { echo "FAIL: expected 7 starting cars"; exit 1; }

# Drag two matching cars together: they merge, and a new car drives in by itself.
dump
CITY_BEFORE=$(python3 "$HERE/smoke_ui.py" count "$OUT/ui.xml" "City hatch")
read -r X1 Y1 X2 Y2 < <(python3 "$HERE/smoke_ui.py" pair "$OUT/ui.xml")
echo "dragging $X1,$Y1 -> $X2,$Y2"
adb shell input swipe "$X1" "$Y1" "$X2" "$Y2" 900
sleep 2
shot 05_after_merge
if has "OK"; then tap "OK"; sleep 1; fi   # the tutorial's last card covers the board
shot 06_tutorial_done
dump
AFTER_MERGE=$(python3 "$HERE/smoke_ui.py" cars "$OUT/ui.xml")
CITY_AFTER=$(python3 "$HERE/smoke_ui.py" count "$OUT/ui.xml" "City hatch")
echo "cars after merge: $AFTER_MERGE (city hatch $CITY_BEFORE -> $CITY_AFTER)"
test "$AFTER_MERGE" -eq 7 || { echo "FAIL: merge + new car should keep 7 cars"; exit 1; }
test "$CITY_AFTER" -gt "$CITY_BEFORE" || { echo "FAIL: drag-merge did not merge"; exit 1; }

# Move a car to an empty space: a new car arrives, so there is one more car.
move_to_empty() {
  dump
  read -r CX CY < <(python3 "$HERE/smoke_ui.py" anycar "$OUT/ui.xml")
  read -r EX EY < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Empty space") || return 1
  adb shell input swipe "$CX" "$CY" "$EX" "$EY" 700
  sleep 1.5
}
move_to_empty
AFTER_MOVE=$(cars)
echo "cars after move: $AFTER_MOVE"
test "$AFTER_MOVE" -eq 8 || { echo "FAIL: a new car should arrive after a move"; exit 1; }
shot 07_after_move

# Tow: drag a car onto the tow truck button.
dump
read -r CX CY < <(python3 "$HERE/smoke_ui.py" anycar "$OUT/ui.xml")
read -r TX TY < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Tow truck")
adb shell input swipe "$CX" "$CY" "$TX" "$TY" 900
sleep 2
AFTER_TOW=$(cars)
echo "cars after tow: $AFTER_TOW"
test "$AFTER_TOW" -eq 7 || { echo "FAIL: tow did not remove a car"; exit 1; }
shot 08_after_tow

# Keep moving cars into empty spaces until the board is full.
for i in $(seq 1 40); do
  if ! has "Empty space"; then break; fi
  move_to_empty || break
done
FULL=$(cars)
echo "cars after filling: $FULL"
test "$FULL" -eq 30 || { echo "FAIL: board should be full"; exit 1; }
shot 09_full_board

# Process death: background, kill, relaunch -> same board.
adb shell input keyevent KEYCODE_HOME
sleep 2
# Leaving the game sets up the three "come back and play" reminders (1, 3 and 7 days).
REMINDERS=$(adb shell dumpsys alarm | grep -c "SHOW_REMINDER" || true)
echo "reminder alarms waiting: $REMINDERS"
test "$REMINDERS" -ge 3 || { adb shell dumpsys alarm | grep -B2 -A6 "$PKG" | head -40; echo "FAIL: reminders were not scheduled"; exit 1; }
adb shell am kill "$PKG"
sleep 1
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
RESUMED=$(cars)
echo "cars after process death: $RESUMED"
test "$RESUMED" -eq 30 || { echo "FAIL: board not restored after process death"; exit 1; }
shot 10_resumed

adb shell input keyevent KEYCODE_BACK; sleep 3
# Back home after a first game: Android 13+ asks once whether reminders may be shown.
if has "Allow"; then shot 10b_reminder_question; tap "Allow"; sleep 1; else echo "no reminder question (older Android or asked before)"; fi
REMINDERS=$(adb shell dumpsys alarm | grep -c "SHOW_REMINDER" || true)
echo "reminder alarms while playing: $REMINDERS"
test "$REMINDERS" -eq 0 || { echo "FAIL: reminders should be cancelled while the game is open"; exit 1; }
tap "Store"; sleep 2; shot 11_store_skins
tap "Moo Car"; sleep 1; shot 11b_skin_picked
has "Make the Kei car to unlock!" && { echo "FAIL: the first car's skins should not be locked"; exit 1; }
tap "Themes"; sleep 2; shot 11c_store_themes
tap "Coins"; sleep 2; shot 12_store_coins
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Garage"; sleep 2; shot 13_garage
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Settings"; sleep 2; shot 14_settings
adb shell input keyevent KEYCODE_BACK; sleep 1

# PLAY always starts a fresh board.
tap "Play"; sleep 2
FRESH=$(cars)
echo "cars after PLAY: $FRESH"
test "$FRESH" -eq 7 || { echo "FAIL: PLAY should start a new game with 7 cars"; exit 1; }
# The test banner above the board (loading needs network, so this only reports).
sleep 4; dump
echo "game banner: $(python3 "$HERE/smoke_ui.py" bounds "$OUT/ui.xml" "Test Ad" 2>/dev/null || echo "not loaded")," \
  "home button: $(python3 "$HERE/smoke_ui.py" bounds "$OUT/ui.xml" "Home" 2>/dev/null)"
shot 14b_game_banner
adb shell input keyevent KEYCODE_BACK; sleep 2

# Android backup: back up, uninstall, reinstall -> best score and Garage come back.
dump
SAVED=$(python3 "$HERE/smoke_ui.py" progress "$OUT/ui.xml")
echo "progress before uninstall: $SAVED"
[ -n "$SAVED" ] && [ "$SAVED" != "0 2/11" ] || { echo "FAIL: expected some progress on Home"; exit 1; }
adb shell bmgr enable true >/dev/null
LOCAL=$(adb shell bmgr list transports | tr -d '\r* ' | grep -m1 localtransport || true)
[ -n "$LOCAL" ] && adb shell bmgr transport "$LOCAL" >/dev/null
adb shell bmgr backupnow "$PKG" | tail -2
adb uninstall "$PKG" >/dev/null
adb install "$APK" >/dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell bmgr restore "$PKG" | tail -1 || true
sleep 3
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 5
dismiss_anr
dump
RESTORED=$(python3 "$HERE/smoke_ui.py" progress "$OUT/ui.xml")
echo "progress after reinstall: $RESTORED"
shot 14c_restored_home
test "$RESTORED" = "$SAVED" || { echo "FAIL: progress was not restored from the backup"; exit 1; }

# Animations back on, then a random-tap stress test inside the app.
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 3
shot 15_home_animated
tap "Play" || true
sleep 3
shot 16_game_animated
adb shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --throttle 60 -s 42 1500 >/dev/null 2>&1 || true
sleep 2
shot 17_after_monkey

if crashed; then echo "FAIL: crash detected"; exit 1; fi
echo "SMOKE TEST PASSED"
