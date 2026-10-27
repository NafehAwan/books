#!/usr/bin/env bash
# Takes Google Play screenshots on a connected emulator or phone (best: 1080 x 1920, 9:16, which
# Play accepts; 1080 x 2400 is too tall). Plays a real game: daily gift, tutorial, merges until a
# few new cars are unlocked (catching the "New car!" ribbon), then Garage, Store and Settings.
# Airplane mode is switched on so no test ads show in the pictures.
#   scripts/store-screenshots.sh app-debug.apk out/
set -euo pipefail
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
OUT=${2:-store-shots}
PKG=com.tinymotors.game
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
dump() {
  for i in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null; return 0; fi
    sleep 1
  done
  return 1
}
dismiss_anr() {
  if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb shell cat /sdcard/ui.xml | grep -q "t responding"; then
    adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
    read -r X Y < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "Wait") && adb shell input tap "$X" "$Y"
    sleep 2
  fi
}
tap() { dismiss_anr; dump; read -r X Y < <(python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "$1"); adb shell input tap "$X" "$Y"; echo "tap '$1'"; }
has() { dismiss_anr; dump; python3 "$HERE/smoke_ui.py" center "$OUT/ui.xml" "$1" >/dev/null 2>&1; }

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 20
for i in 1 2 3; do dismiss_anr; done
adb shell cmd connectivity airplane-mode enable || true
# clean status bar for the pictures
adb shell settings put global sysui_demo_allowed 1 || true
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930 >/dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null || true

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK"
sleep 10   # let the install broadcasts finish before the app starts
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5

for i in 1 2 3 4; do
  if has "Tap the present"; then tap "Tap the present"; sleep 2; fi
  if has "OK"; then shot gift; tap "OK"; sleep 1; break; fi
  sleep 2
done
sleep 1; shot home
tap "Play"; sleep 2

# Merge the lowest pair again and again. When a merge makes a car never seen before, the
# "New car!" ribbon shows for about 2.6 s: take that picture straight away.
BEST=2
for i in $(seq 1 60); do
  dismiss_anr; dump
  if has "OK"; then tap "OK"; sleep 1; continue; fi        # tutorial card
  read -r X1 Y1 X2 Y2 LEVEL < <(python3 "$HERE/smoke_ui.py" pairlevel "$OUT/ui.xml") || break
  adb shell input swipe "$X1" "$Y1" "$X2" "$Y2" 600
  if [ "$LEVEL" -gt "$BEST" ]; then
    sleep 0.7; shot "new_car_$LEVEL"; BEST=$LEVEL
    sleep 3
  else
    sleep 1.2
  fi
  [ "$BEST" -ge 7 ] && break
done
sleep 3; dismiss_anr; shot game

adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Garage"; sleep 2; shot garage
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Store"; sleep 2; shot store_themes
tap "Coins"; sleep 2; shot store_coins
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Settings"; sleep 2; shot settings
adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
adb shell cmd connectivity airplane-mode disable || true
echo "best car reached: level $BEST"
ls "$OUT"
