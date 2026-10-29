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
# (디버그 빌드는 앱이 화면에 보인 초와 알림 시계의 기준값을 로그 RMTIME으로 남김)
adb logcat -c
adb shell am start -n $P/.MainActivity >/dev/null; move 3
H1=$(date +%s.%N); move 30; H2=$(date +%s.%N)
send pause; move 5; send resume; move 10
adb logcat -d -s RMTIME:D > shots/rmtime.log
python3 - "$H1" "$H2" <<'PY' > shots/time_check.txt
import sys,re
h1,h2=float(sys.argv[1]),float(sys.argv[2])
app=[];notif=[]
for line in open('shots/rmtime.log',encoding='utf-8',errors='ignore'):
    m=re.search(r'RMTIME\s*:\s*app (\d+) (\d+)',line)
    if m: app.append((int(m.group(1)),int(m.group(2))))
    m=re.search(r'RMTIME\s*:\s*notif (\d+) (\d+) (true|false)',line)
    if m: notif.append((int(m.group(1)),int(m.group(2)),m.group(3)=='true'))
print(f"앱 화면 갱신 {len(app)}번, 알림 기준값 {len(notif)}번")
# 1) 같은 순간 알림 시계가 보여 줄 초 = (그때 기기 시계 - 알림 기준값)/1000 → 앱이 보인 초와 같은지
bad=0; checked=0
for e,shown in app:
    cur=[n for n in notif if n[0]<=e]
    if not cur: continue
    ne,base,running=cur[-1]
    if not running: continue
    checked+=1
    if (e-base)//1000 != shown: bad+=1
print(f"앱 초 vs 알림 시계 초: {checked}번 비교, 다른 경우 {bad}번")
# 2) 1초 단위: 앱이 보인 초가 한 번에 2초 이상 뛰거나 거꾸로 간 적이 있는지
jumps=[(a,b) for a,b in zip(app,app[1:]) if b[1]-a[1] not in (0,1)]
print(f"초가 0·1이 아닌 만큼 바뀐 경우 {len(jumps)}번 (일시정지·다시 시작 순간 제외 시 0이어야 함): {jumps[:3]}")
# 3) 실제 30초 동안 화면 시간 증가
w=[x for x in app if True]
print(f"호스트 실제 {h2-h1:.1f}초 동안(달리는 중) 기기 시계 기준 화면 초 증가는 아래 1초 간격 확인으로 대신")
if len(app)>=2:
    spans=[(b[0]-a[0], b[1]-a[1]) for a,b in zip(app,app[1:]) if b[1]-a[1]==1]
    avg=sum(s for s,_ in spans)/len(spans) if spans else 0
    print(f"화면 초가 1 오를 때 기기 시계 간격 평균 {avg:.0f}ms (1000ms 근처여야 함)")
PY
cat shots/time_check.txt
cat shots/pause_check.txt
