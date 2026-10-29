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

# ── 백그라운드·잠금 화면, 일시정지·다시 시작 점검 ──
adb shell dumpsys deviceidle whitelist +$P >/dev/null 2>&1 || true   # 배터리 최적화 창이 뜨지 않게(점검 전용)
adb shell am force-stop $P
LAT=35.85300; LON=128.55800
adb emu geo fix $LON $LAT
go --es demo record
sleep 6
move() { for k in $(seq 1 "$1"); do LAT=$(python3 -c "print(round($LAT+0.000025,6))"); adb emu geo fix $LON $LAT >/dev/null; sleep 1; done; }
stay() { for k in $(seq 1 "$1"); do adb emu geo fix $LON $LAT >/dev/null; sleep 1; done; }
note() { T=$(adb shell dumpsys notification --noredact | grep -A1 "android.title=" | grep -m1 -o "런메이트[^)]*"); X=$(adb shell dumpsys notification --noredact | grep -m1 -o "android.text=String ([^)]*"); echo "$1 | $T | ${X#android.text=String (}" >> shots/pause_check.txt; }
send() { adb shell am start -n $P/.MainActivity --es demo "$1" >/dev/null; sleep 1; }
: > shots/pause_check.txt
move 12;               note "1 달리는 중(앱 화면)"
adb shell input keyevent KEYCODE_HOME
move 12;               note "2 홈으로 나감"
adb shell input keyevent KEYCODE_SLEEP
move 15;               note "3 화면 잠금"
adb shell input keyevent KEYCODE_WAKEUP
send pause; move 6;    note "4 일시정지 직후"
move 8;                note "5 일시정지 8초 뒤(시간·거리 그대로여야 함)"
shot 15_paused 1
send resume; move 8;   note "6 다시 시작 8초 뒤(시간 흐름)"
move 8;                note "7 다시 시작 16초 뒤"
stay 30;               note "8 30초 멈춰 섬(자동 일시정지)"
shot 16_autopaused 1
send resume; stay 8;   note "9 자동 일시정지 중 다시 시작 8초 뒤(서 있어도 시간 흐름)"
# 잠금 화면: 앱을 켜 둔 채 잠그면 달리는 중 화면이 잠금 위에, 다른 곳에서 잠그면 알림 카드가 보여야 함
send resume; move 4
adb shell am start -n $P/.MainActivity >/dev/null; move 3
adb shell input keyevent KEYCODE_SLEEP; move 4; adb shell input keyevent KEYCODE_WAKEUP; move 3
shot 17_lock_app 0
adb shell dumpsys window | grep -m3 -iE "mDreamingLockscreen|isKeyguardShowing|mShowingLockscreen|KeyguardShowing" > shots/lock_state.txt || true
adb shell input keyevent KEYCODE_HOME; move 2
adb shell input keyevent KEYCODE_SLEEP; move 4; adb shell input keyevent KEYCODE_WAKEUP; move 3
shot 18_lock_notification 0
adb shell cmd statusbar expand-notifications; move 3
shot 19_shade 0
adb shell cmd statusbar collapse
# 앱 시간과 알림(상태창) 시간이 같은지, 실제 30초가 화면에서도 30초인지
readt() {  # $1=리소스 id → 화면에 보이는 시간(초)과 그때의 실제 시각
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  adb shell cat /sdcard/u.xml > /tmp/u.xml
  python3 - "$1" <<'PY'
import re,sys,time
x=open('/tmp/u.xml',encoding='utf-8').read()
m=re.search(r'text="([0-9:]+)"[^>]*resource-id="[^"]*:id/'+sys.argv[1]+'"',x)
def sec(t):
    p=[int(v) for v in t.split(':')]; s=0
    for v in p: s=s*60+v
    return s
print(f"{sec(m.group(1)) if m else -1} {time.time():.2f} {m.group(1) if m else 'none'}")
PY
}
adb shell am start -n $P/.MainActivity >/dev/null; move 3
A1=$(readt time); move 30; A2=$(readt time)
adb shell cmd statusbar expand-notifications; sleep 2
N1=$(readt nTime)
adb shell cmd statusbar collapse; sleep 1
A3=$(readt time)
python3 - "$A1" "$A2" "$N1" "$A3" <<'PY' > shots/time_check.txt
import sys
a1,a2,n1,a3=[v.split() for v in sys.argv[1:5]]
s=lambda v:int(v[0]); t=lambda v:float(v[1])
print(f"앱 시간 {a1[2]} → {a2[2]}: 화면 {s(a2)-s(a1)}초 / 실제 {t(a2)-t(a1):.1f}초")
# 알림을 읽은 순간의 앱 시간 = 앞뒤 앱 시간 사이를 실제 시각으로 나눠 추정
est = s(a2) + (t(n1)-t(a2)) * (s(a3)-s(a2)) / max(0.1, t(a3)-t(a2))
print(f"알림 시간 {n1[2]} / 같은 순간 앱 시간(추정) {est:.1f}초 → 차이 {s(n1)-est:+.1f}초")
PY
cat shots/time_check.txt
cat shots/pause_check.txt
