#!/usr/bin/env bash
# 에뮬레이터에서 화면을 차례로 띄워 캡처합니다(디버그 빌드의 점검용 화면 사용, 가상 기록).
set -x
P=io.github.sby1234ai.runmate
adb install -r app/build/outputs/apk/debug/app-debug.apk
for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACTIVITY_RECOGNITION POST_NOTIFICATIONS; do
  adb shell pm grant $P android.permission.$perm || true
done
adb shell settings put system screen_off_timeout 1800000
mkdir -p shots
shot() { sleep "$2"; adb exec-out screencap -p > "shots/$1.png"; }
go() { adb shell am start -S -W -n $P/.MainActivity "$@" >/dev/null; }

go --es demo home --es sky CLEAR;  shot 01_home_clear 4
adb shell input swipe 540 1800 540 700 400; shot 02_home_tiles 2
go --es demo home --es sky RAIN;   shot 03_home_rain 4
go --es demo home --es sky SNOW;   shot 04_home_snow 4
go --es demo home --es sky NIGHT;  shot 05_home_night 4
go --es demo home --es sky CLOUDY; shot 06_home_cloudy 4
go --es demo run --es sky CLEAR;   shot 07_run_a 4; shot 08_run_b 0.3
go --es demo walk --es sky NIGHT;  shot 09_walk 4
go --es demo detail;               shot 10_detail 12
adb shell input swipe 540 1800 540 600 400; shot 11_detail_more 2
go --es demo runs;                 shot 12_runs 5
go --es demo settings;             shot 13_settings 3
adb logcat -d -s AndroidRuntime:E > shots/crash.txt || true
ls -la shots

# ── 백그라운드·잠금 화면에서도 기록이 이어지는지 점검 ──
adb shell dumpsys deviceidle whitelist +$P >/dev/null 2>&1 || true   # 배터리 최적화 창이 뜨지 않게(점검 전용)
adb shell am force-stop $P
LAT=35.85300; LON=128.55800
adb emu geo fix $LON $LAT
go --es demo record
sleep 6
move() { for k in $(seq 1 "$1"); do LAT=$(python3 -c "print(round($LAT+0.000025,6))"); adb emu geo fix $LON $LAT >/dev/null; sleep 1; done; }
move 10
adb shell dumpsys notification --noredact | grep -A2 "android.text=" | grep -i "km" | head -1 > shots/bg_1_foreground.txt
adb shell input keyevent KEYCODE_HOME
move 15
adb shell dumpsys notification --noredact | grep -A2 "android.text=" | grep -i "km" | head -1 > shots/bg_2_home.txt
adb shell input keyevent KEYCODE_SLEEP
move 25
adb shell dumpsys notification --noredact | grep -A2 "android.text=" | grep -i "km" | head -1 > shots/bg_3_locked.txt
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
shot 14_lockscreen 1
adb shell dumpsys notification --noredact | grep -B2 -A12 "pkg=$P" | head -60 > shots/bg_notification_dump.txt
cat shots/bg_*.txt
