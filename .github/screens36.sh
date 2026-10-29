#!/usr/bin/env bash
# 안드로이드 16 에뮬레이터: 실시간 알림(상태 표시줄 칩)이 뜨는지 확인
set -x
P=io.github.sby1234ai.runmate
adb install -r app/build/outputs/apk/debug/app-debug.apk
for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACTIVITY_RECOGNITION POST_NOTIFICATIONS; do
  adb shell pm grant $P android.permission.$perm || true
done
adb shell dumpsys deviceidle whitelist +$P >/dev/null 2>&1 || true
mkdir -p shots36
LAT=35.85300; LON=128.55800
adb emu geo fix $LON $LAT
adb shell am start -W -n $P/.MainActivity --es demo record >/dev/null
sleep 6
for k in $(seq 1 20); do LAT=$(python3 -c "print(round($LAT+0.000025,6))"); adb emu geo fix $LON $LAT >/dev/null; sleep 1; done
adb shell input keyevent KEYCODE_HOME; sleep 3
adb exec-out screencap -p > shots36/01_home_statusbar.png
adb shell dumpsys notification --noredact | grep -B2 -A30 "pkg=$P" | head -80 > shots36/notification.txt
adb shell cmd statusbar expand-notifications; sleep 3
adb exec-out screencap -p > shots36/02_shade.png
adb shell cmd statusbar collapse
getprop() { adb shell getprop ro.build.version.sdk; }
getprop > shots36/sdk.txt
# 실시간 알림 점검: 앱이 판단한 값, 권한 상태
{
  echo "== logcat RMLIVE"; adb logcat -d -s RMLIVE:D | tail -20
  echo "== package permissions"; adb shell dumpsys package $P | grep -iE "promot|targetSdk" || true
  echo "== appops"; adb shell appops get $P 2>&1 | grep -i promot || true
  echo "== release"; adb shell getprop ro.build.version.release; adb shell getprop ro.build.version.sdk_full || true
} > shots36/live_check.txt 2>&1
