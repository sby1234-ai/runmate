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
