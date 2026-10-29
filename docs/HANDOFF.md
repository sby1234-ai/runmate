# 이어서 하기 (작업 인계 문서)

다른 계정·다른 세션에서 이 저장소를 이어서 작업할 때 이 문서부터 읽습니다. 작업할 때마다 아래 「지금 상태」와 「다음 할 일」을 고칩니다.

## 이 앱

- 런메이트(RunMate): 걷기·뛰기를 자동으로 나눠 기록하는 안드로이드 러닝 앱 (Kotlin, minSdk 26, targetSdk 34)
- ALEPH 프리미엄 「나의 앱 프로젝트」 과제용. 포털 제출: 목표·plan.md·중간 점검 1차 **제출 완료(2026-10-29)**, 2차·최종 남음
- 공개 주소: 앱 https://github.com/sby1234-ai/runmate/releases · 웹 화면 https://sby1234-ai.github.io/runmate/

## 작업 규칙 (사용자와 정한 것)

- 대화는 항상 존댓말, 이름(실명)으로 부르지 않음
- 작업 시작 전에 **예상 작업 시간**을 먼저 말함
- 이 저장소(runmate)의 확인용 커밋·푸시는 바로 해도 됨. 2026-10-29부터 **중간 저장 때마다 versionCode·versionName을 올려(0.3.1, 0.3.2 …) 설치 파일도 바로 배포**해 달라고 함(사용자가 휴대폰에 덮어 설치해 확인)
- 다른 저장소(myprofile 등)는 푸시 전에 수정본을 보여 주고 확인받음
- **30분마다 커밋·푸시 + 중간 보고**(이어서 할 때 어디서부터인지 포함), 언제 끊겨도 이어갈 수 있게 함 (2026-10-29 22:27부터 10분 → 30분)
- 권한 허용이 필요한 작업은 사용자에게 묻지 말고 바로 진행 (2026-10-29 사용자 요청)
- 사용자가 물어보기만 하면 답만, 고치라고 할 때만 수정
- 코드 수정본은 부분 조각이 아니라 파일 전체로

## 빌드·확인 방법 (로컬에 안드로이드 SDK 없음)

| 무엇 | 어떻게 |
|---|---|
| 빌드 + 단위 테스트 | main에 푸시하면 `.github/workflows/build.yml` 실행. 결과는 `ci-logs` 브랜치(`STATUS`, `build.log`, `permissions.txt`, `test-results/`) — `git clone --depth 1 -b ci-logs https://github.com/sby1234-ai/runmate` |
| 실제 화면 캡처 | `app/**`이 바뀌어 푸시되면 `.github/workflows/screens.yml`이 에뮬레이터(API 34, Pixel 6)에서 화면을 찍어 `ci-screens` 브랜치에 PNG로 올림. 찍는 순서는 `.github/screens.sh` |
| 화면 점검용 가상 데이터 | 디버그 빌드에서만 `MainActivity`에 `--es demo home|run|walk|detail|runs|settings`, `--es sky CLEAR|PARTLY|CLOUDY|FOG|RAIN|SNOW|STORM|NIGHT` (가상 기록 `logic/DemoRun.kt`) |
| 릴리스 | `app/build.gradle.kts`의 `versionName`을 올려 main에 푸시하면 그 버전 릴리스가 자동 생성(태그 푸시는 막혀 있음). 서명 키는 GitHub Secrets에만 있음 |
| 웹 화면 | `web/` 폴더가 바뀌면 `.github/workflows/pages.yml`이 GitHub Pages로 배포 |

## 지금 상태 (2026-10-30 06:50)

- 배포: **v0.5.1** (versionCode 29) — 워치 실시간 심박(헬스 서비스 MeasureClient, 처음 「시작」 때 BODY_SENSORS 권한 요청 → 기록 중 5초마다 /runmate/hr → 휴대폰 `filesDir/watch_hr.txt` 저장 → 상세 화면 심박 카드가 이것을 먼저, 없으면 헬스 커넥트)
- v0.5.0 (28) — 워치 앱 첫 버전(시간·거리·걷기/뛰기 표시, 시작/일시정지·계속/종료(길게)), 릴리스에 runmate-wear-vX.apk
- 이전 **v0.4.0** (versionCode 27) — 헬스 커넥트: 기록 상세 「심박 (워치)」 카드(평균·최고·1km 구간 평균·그래프, 권한 없으면 가져오기 버튼), 기록 화면 「⌚ 워치로 뛴 기록 가져오기」(최근 30일 경로 있는 달리기·걷기, 걷기/뛰기는 속도 2.2 m/s 기준)
- 이전 **v0.3.23** (versionCode 26) — 준비 화면 시안(캔버스 「달리기 준비 — 사진 배경·구성 띠」) 적용: 첫 화면 사진(`EXTRA_BG`)을 흐리게 깔고, 카드 = 이름·준비 걷기 포함 구성 | 큰 분 | 고르면 밝은 흰 유리(`GlassKind.ON`)+청록 체크, 아래 단색 구성 띠(뛰기 진하게·걷기 옅게, 시간 비율). 「시작」 → 바로 돌아가 첫 화면 `beginCountdown()`: 달리기 화면 배경만 띄우고 3·2·1(진동) → `startRecording()`, 뒤로가기면 `cancelCountdown()`.
- 이전 **v0.3.22** — 토글 색(밝은 흰 유리/어두운 유리)
- 이전 **v0.3.21** — 토글을 58dp 아이콘 버튼으로
- 이전 **v0.3.20** (versionCode 23) — 음성 안내·자동 일시정지를 첫 화면 「달리기 시작」 위 아이콘 토글 두 칸으로(`bindToggle`, `GlassKind.ON`, `ic_slash`): 켜짐 = 청록 칸·흰 글자·100%, 꺼짐 = 무색 칸·사선·옅은 글자·94%, 누르면 색·크기 애니메이션 + 짧은 진동, 기록 중엔 바꿀 수 없음(토스트). 준비 화면의 스위치 줄은 숨김(운동 고르기만).
- 이전 **v0.3.19** (versionCode 22) — 섹션 C·D 완료: 첫 화면은 날씨 → 넓은 「기록」 카드 하나 → 「달리기 시작」. 달리는 중 뒤로가기 → 첫 화면(기록 계속), 맨 아래 버튼이 「달리는 중 12:34 · 2.31 km」로 바뀌고 누르면 달리기 화면. 계획 A~D 전부 끝, 사용자 휴대폰 확인 대기
- 이전 **v0.3.18** (versionCode 21) — 섹션 A·B: 달리기 준비 화면 + 누름 효과 통일. 다음은 섹션 D(첫 화면 정리) → C(미니 막대)
- 이전 **v0.3.17** (versionCode 20) — 첫 화면 타일 6개 → 4개(2×2): 「기록」(요약: N개 · 최고 1km), 인터벌 코칭, 음성 안내, 자동 일시정지. 「내 기록」「최고 기록」은 같은 화면이라 합치고, 「설정」 타일은 톱니바퀴와 겹쳐 뺌. 기록 화면(`RunsActivity`)에 유리 탭 「모든 기록 / 최고 기록」(`EXTRA_BEST`로 최고 기록 탭 바로 열기).
- 이전 **v0.3.16** (versionCode 19) — 인터벌 코칭 대화상자 항목을 두 줄로(윗줄 「이름 · N분」, 아랫줄 작고 옅게 「뛰기/걷기 구성」). 0.3.15의 대화상자 뒤 숨김·배경 흐림이 이 창에도 적용됨.
- 이전 **v0.3.15** (versionCode 18) — 사용자 휴대폰: 대화상자 뒤로 타일·글자가 비치고(창 뒤 흐림이 휴대폰에서 안 됨) 여백이 답답함 → `showGlass()`: 뜨는 동안 `android:tag="fg"` 뷰(첫 화면·달리는 중)는 숨기고 `tag="bgBlur"` 배경 사진만 앱이 직접 흐림(RenderEffect, 12+), 표시 없는 화면은 내용 전체 숨김. 제목 20sp·본문 15sp, 판 위아래·버튼 주변 여백 늘림, 버튼 52dp.
- 이전 **v0.3.14** (versionCode 17) — 유리 레이아웃(사용자와 Design 캔버스 「런메이트 유리 레이아웃 시안」으로 확정: 각진 모서리 6~14dp, 아주 옅은 흰 채움 3.5~6%, 왼쪽 위가 밝은 1dp 그라데이션 테두리, 대화상자는 판+뒤 흐림+반반 버튼). `ui/Glass.kt`: `GlassDrawable`(판 안에 배경 사진을 1/8로 줄여 상자 흐림 3번 한 그림을 제자리에 맞춰 깔고 SkyView와 같은 어두운 막), `Backdrop`, `Glass.apply`, `Glass.applyTagged`(XML `android:tag="glass"` 카드), `MaterialAlertDialogBuilder.showGlass()`(모든 대화상자). 첫 화면: 날씨는 판 없이 글자+그림자, 타일 다음 맨 아래에 「달리기 시작」. 배경 사진 2차 교체(범주별 4장, `docs/PHOTOS.md`), 앱을 켤 때마다 무작위(지난번 사진 피함).
- 이전 **v0.3.13** (versionCode 16) — `LiveNotif.supported()` 버그 수정: `SDK_INT_FULL`은 16.0에도 있어서 0.3.10~0.3.12가 갤럭시 16.0을 36.1로 잘못 판단 → 값(큰 번호×100000+작은 번호)으로 36.1 이상인지 비교. 설정 「실시간 알림」 카드는 켤 수 있는 휴대폰(36.1+)에서 꺼져 있을 때만 보임(사용자 휴대폰에선 숨김), 「잠금 화면 알림 내용 설정」 버튼·잠금 내용 숨김 안내 대화상자 제거(취향 설정 강요 안 함). 첫 화면 뒤로가기 → 「런메이트를 닫을까요?」 대화상자(기록 중이면 화면만 닫고 기록 계속).
- 이전 **v0.3.12** (versionCode 15) — 알림·칩·나우 바는 사용자와 합의해 현 상태로 마무리(칩은 휴대폰 36.1 업데이트 후 자동, 나우 바는 삼성 등록 앱만, 잠금화면 알림 스타일은 사용자 취향이라 강요 안 함). 지도 맞춤 버그 수정(`MapPanel.fitAll`: 정확히 남북/동서로만 달린 기록은 한쪽 폭 0이라 세계 지도처럼 보였음 → 최소 폭 0.002°). 공유 이미지 점검 추가(디버그 `--es demo share` → `ci-screens/14_share.png`).
- 이전 **v0.3.11** (versionCode 14) — 사용자 휴대폰 확인: 잠금 화면 카드 안 보인 원인은 전체 설정 「잠금화면 알림 → 알림 스타일 = 아이콘」(→ 「카드」로 바꾸라고 안내). Now bar → 실시간 정보 목록에 카카오내비·카카오 T·당근·배민·BHC 등 비삼성 앱도 있으나 런메이트는 없음(같은 `com.samsung.android.support.ongoing_activity` meta-data 있음) → 삼성 등록(제휴) 앱만 들어가는 것으로 판단, 앱 코드로 해결 불가. 설정 안내 문구를 「칩은 36.1 업데이트 후 자동, 나우 바는 삼성 등록 앱만」으로 정정, 잠금화면 카드 스타일 안내 추가.
- 이전 **v0.3.10** (versionCode 13) — 원인 확정: 안드로이드 16.0(SDK 36.0) 정식판은 실시간 알림이 꺼진 채 출시, POST_PROMOTED_NOTIFICATIONS 권한이 정의돼 있지 않아 `canPostPromotedNotifications`가 항상 false(에뮬레이터 36.0 `live_check.txt`로 확인: targetSdk=36인데 false, 권한 미부여). 다른 앱에 열린 건 SDK 36.1부터. 사용자 휴대폰 One UI 8.0 = 36.0이라 칩·나우 바 불가(앱 문제 아님), 휴대폰이 36.1 이상으로 업데이트되면 코드 수정 없이 자동 동작. `LiveNotif.supported()`를 `Build.VERSION.SDK_INT_FULL` 존재(36.1+)로 판단, `waitingForUpdate()`면 설정 카드에 안내·켜기 버튼 숨김, 시작 대화상자 없음. 잠금 화면 카드 안 보임: 사용자가 앱 알림 「잠긴 상태에서 알림 내용 표시」를 「항상 표시」로 바꿨는데도 안 보임 → 전체 설정 「잠금화면 및 AOD → 잠금화면 알림」 확인 중, 「Now bar」 메뉴 앱 목록도 확인 요청함
- 이전 **v0.3.9** (versionCode 12) — 안드로이드 16 기준으로 올림: compileSdk·targetSdk 36, AGP 8.10.1, CI Gradle 8.11.1. 실시간 알림(초록 칩·나우 바) 정식 요청(`RecordingService.liveUpdate`, 요청과 짧은 글을 따로 try), `LiveNotif.kt`(지원 여부·켜짐 확인, `android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS`로 이동, 앱 시작 때 꺼져 있으면 하루 1번 대화상자, 잠금 화면 알림 내용 숨김이면 1번 안내 — 릴리스 빌드에서만). 「개발자 옵션 열기」(`DevOptions.kt`) 삭제 → 설정에 「실시간 알림」 카드(상태 글 + 켜기 버튼 + 잠금 화면 알림 내용 설정 버튼). targetSdk 35+ 전체 화면(edge-to-edge) 대응: `ui/EdgeInsets.kt`를 Runs·Detail·Settings에 적용(Main은 원래 처리).
- 이전 **v0.3.8** (versionCode 11) — 설정 「개발자 옵션 열기」가 해당 항목을 깜빡여 강조하며 이동(`DevOptions.kt`, `:settings:fragment_args_key`). 개발자 옵션 꺼짐 → 삼성 `Settings$SoftwareInfoSettingsActivity` 시도 후 `ACTION_DEVICE_INFO_SETTINGS`, 키 `build_number` / 켜짐 → `ACTION_APPLICATION_DEVELOPMENT_SETTINGS`, 키 후보 `live_notifications_for_all_apps`(삼성 비공개 키, 실제 휴대폰에서 강조되는지 확인 필요). 버튼 글자는 상태에 따라 바뀜(onResume). 안내 문구에 나우 바(잠금 화면 아래쪽 막대) 추가.
- 이전 **v0.3.7** — 대화상자(업데이트·기록 삭제·인터벌)를 앱과 같은 어두운 테마로(`Dialog.RunMate`). v0.3.6 — 알림 「종료」 뒤 결과 화면이 열리게(`MainActivity.openDetailOnSave`). 이전 **v0.3.5** (versionCode 8) — 자동 업데이트(`Updater.kt`, 하루 1회 GitHub 최신 릴리스 확인→받기→SHA-256 확인→설치 화면), 알림 버튼 「일시정지/계속」·「종료」(종료는 앱을 열어 끝냄), 밀어서 지워도 다시 표시(deleteIntent), 잠금 위 앱 화면은 기본 끔(잠금 풀린 것처럼 보여서, 잠금 화면엔 알림 카드만). 이전 v0.3.4 (versionCode 7) — 강제 종료 복구(10초마다 `CheckpointStore`, START_STICKY, 앱 열면 이어서), 잠금 화면(화면 꺼질 때 전체 화면 알림으로 잠금 위 달리기 화면 `RecordingService.showOverLockScreen`, 기록 알림을 기본 등급 무음 채널 `recording_v2`로 바꿔 잠금 화면에 보임), 삼성 라이브 알림 extras(허용된 앱/개발자 옵션에서만). 이전 v0.3.3 (versionCode 6). 0.3.2 = 아래 잠금 화면·시간·걸음·실시간 알림, 0.3.3 = GPS 흔들림 거르기(`logic/Smoother.kt`)·멈춘 구간 거리 제외. 정확도 설명과 다른 앱 비교 방법은 `docs/ACCURACY.md`
- 0.3.1 이후 들어간 것:
  - 잠금 화면: 달리는 중이면 `MainActivity`가 잠금 위에 보임(`showOnLockScreen`), 알림은 어두운 카드(`res/layout/notif_run_small|big.xml`, 흐르는 Chronometer)
  - 시간: 운동 시간은 모두 `SystemClock.elapsedRealtime` 하나로(`RunRecorder.Snapshot.activeMs/activeNow`), 앱은 초 경계에 맞춰 갱신, 알림 Chronometer와 같은 기준점. 인터벌도 운동 시간 기준(일시정지하면 멈춤)
  - 걸음: 휴대폰 걸음 센서(`logic/Steps.kt` StepTracker, `RecordingService.startSteps`), 케이던스 140 이상이면 뛰기(`MotionClassifier.onCadence`), 기록에 `Run.steps` 저장
  - 몇 km째·구간 페이스(`logic/RunStats.kt`의 `Lap`)를 달리는 중 화면·알림에 표시
  - 안드로이드 16 이상 + 실시간 알림 허용이면 표준 알림 + 상태 표시줄 칩(`RecordingService.liveUpdate`, 이름으로 찾아 부르는 setRequestPromotedOngoing/setShortCriticalText), 아니면 어두운 카드
- 점검: `screens.yml`의 `screens`(API 34: 화면, 일시정지 `pause_check.txt`, 시간 일치 `time_check.txt`)와 `screens36`(API 36: 실시간 알림 칩, `ci-screens36` 브랜치)
- 사진 받기: `tools/photos.txt`를 고쳐 푸시하면 `photos.yml`이 `ci-photos` 브랜치 `final/`에 받아 둠 → `res/drawable-nodpi/`에 복사

## 다음 할 일

- **(2026-10-30 05:06 사용자 승인) 워치 작업** — 대상: 웨어 OS 3+ 전 모델(갤럭시 워치4 이후·픽셀 워치 등, 워치3·액티브2 이전 타이젠은 불가). PC는 윈도우.
  - [x] 1.(0.4.0 배포, 휴대폰 실제 심박 확인 대기: `HealthLink.kt`, `ui/HeartChart.kt`, 상세 `heartCard`·`loadHeart()`, 기록 화면 `btnImport`·`importWatch()`, Kotlin 2.1.21, connect-client 1.1.0, 매니페스트 health 읽기 권한 4개+권한 설명 alias, CI 권한 허용 목록) 헬스 커넥트 연동(워치 앱 없이, 모델 무관): (a) 휴대폰으로 뛴 기록에 그 시간대 심박 붙이기(평균·최고·그래프·1km 구간별) (b) 워치로만 뛴 운동(ExerciseSession+경로+심박)을 런메이트 기록으로 가져오기(걷기·뛰기는 속도 규칙). 사용자는 삼성 헬스 → 헬스 커넥트 심박·운동 공유 켜야 함, 촘촘한 심박은 워치에서도 운동 시작해야 함
  - [~] 2.(0.5.0 ①② 빌드 확인 중: `wear/` 모듈(`WatchActivity`, applicationId 같음, versionCode=100만+휴대폰, minSdk 30), 휴대폰 `WearSync.kt`(상태 1초마다 /runmate/state, 「시작」은 첫 화면 보이면 바로 3·2·1, 아니면 알림 눌러 시작), `WearListener` 서비스, CI 릴리스에 runmate-wear-vX.apk(먼저 올림, 업데이터는 wear 이름 무시). ③ 심박 0.5.1. 남은 것: ④ 윈도우 adb 설치 안내(docs/WATCH_INSTALL.md), 실기기 확인) 워치 앱 모듈(같은 applicationId·서명 키, CI가 워치 APK도 릴리스에), 시간·거리·실시간 심박(헬스 서비스), 워치 버튼 시작/일시정지/종료, 데이터 레이어로 휴대폰과 메시지
  - [ ] 3. 윈도우 PC adb 무선 디버깅으로 워치 설치 안내(명령어 블록 위주)
  - [ ] 4. 나중에 Play 내부 테스트 트랙에 휴대폰+워치 같이(계정·앱 콘텐츠·AAB 필요)

- **(2026-10-30 00:40 사용자 승인, 섹션별 진행 중)** 0.3.18~ 계획:
  - [x] A.(0.3.18) 누름 효과 통일 — `Glass.press()`(StateListAnimator 0.97) 모든 유리 버튼에, item_tile foreground 제거. 남은 것: 음성·자동 타일 깜빡임은 D에서 타일 자체를 없애며 해결: `item_tile.xml`의 foreground(selectableItemBackground)와 Glass 리플이 겹침 → 하나로, 눌릴 때 살짝 작아짐(scale 0.97). 음성·자동 일시정지 타일은 `renderHome()`으로 그리드를 다시 만들어 옆 글자까지 깜빡임 → 바뀐 글자만 갱신
  - [x] B.(0.3.18 `PrepActivity`, `activity_prep.xml`, `GlassKind.SELECTED`, MainActivity `prep` 결과로 `startRecording()`) 「달리기 시작」 → 새 「달리기 준비」 화면(`PrepActivity`): 운동 고르기(자유 달리기/인터벌 4종 카드, 고른 카드 청록 테두리), 음성 안내·자동 일시정지 스위치(켜짐 청록/꺼짐 회색 + 글자), 큰 「시작」 → 3·2·1 카운트다운 → 기록 시작
  - [x] C.(0.3.19 `homeWhileRunning`, `renderMiniBar`, `startLabel`) 달리는 중 뒤로가기 → 앱 닫지 말고 첫 화면으로(기록 계속), 첫 화면 맨 아래 「달리기 시작」 자리에 「달리는 중 12:34 · 2.31 km」 유리 막대, 누르면 달리기 화면. 첫 화면 뒤로가기만 「닫을까요?」
  - [x] D.(0.3.19) 첫 화면: 음성·자동 일시정지·인터벌 타일 빼고(준비 화면으로 옮김) 「기록」 카드 하나를 넓게(이번 주 거리·횟수, 최고 1km)

- **(2026-10-30 내일) 중간 점검 2차 제출** — 사용자가 직접 포털 입력. 준비물: `docs/midcheck2.txt`(제출 글, 칸에 맞춰 나눠 붙이기, 「AI와 내 판단 3줄」은 사용자 말투로 다듬기), `docs/plan.md` v2, 링크(Releases latest · https://sby1234-ai.github.io/runmate/ · 소스 저장소), 현재 v0.3.13. 사용자 확인 대기: 0.3.13 설정에서 실시간 알림 카드 안 보이는지, 첫 화면 뒤로가기 대화상자. 제출 뒤 → 1주일 실사용 → 최종 제출, 그 뒤 워치 심박(헬스 커넥트)

- (2026-10-29 23:20 진행 중) 사용자와 정한 순서: ① 공유 이미지 확인(완료, `ci-screens/14_share.png` 정상) ② 웹·README 0.3 반영(완료: 웹은 앱과 같은 어두운 색·0.3 기능·앱 화면 사진 `web/img/`(에뮬레이터 캡처, `screens.sh`에 시스템 오류 창 숨김 추가)) ③ plan.md v2(완료) → 중간 점검 2차 제출 글 `docs/midcheck2.txt`(초안 완료, 사용자에게 전달·포털 입력은 사용자가) ④ 워치 심박(헬스 커넥트)

00. (2026-10-29 22:40 확인) **사용자 휴대폰 One UI 8.0 / 안드로이드 16 정식판에는 「모든 앱의 라이브 알림」 개발자 옵션이 없음**(베타 전용이었음). 앱 알림 설정에도 실시간 알림 스위치가 없음 → 원인은 targetSdk 34 + 직접 만든 카드 알림으로 판단. 0.3.9에서 진행함(확인 필요: 에뮬레이터 API 36 `ci-screens36`에서 칩이 뜨는지, 사용자 휴대폰에서 앱 알림 설정에 실시간 알림 스위치가 생기는지·칩·나우 바가 뜨는지, 하위 화면 상단이 상태 표시줄에 가리지 않는지). 내용: compile/targetSdk 36, 안드로이드 16 이상은 표준 모양 알림 + setRequestPromotedOngoing 정식 호출(칩·나우 바 같이 노림, 나우 바는 삼성 허용 여부 미확인), 앱 시작 때 canPostPromotedNotifications()가 false면 대화상자 → ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS로 이동, 「개발자 옵션 열기」 버튼 제거, 잠금 화면 알림 내용 숨김(사용자 휴대폰이 「잠겼을 때 알림 내용 숨기기」) 첫 기록 때 한 번 안내. 에뮬레이터 `22_after_stop`: 종료 뒤 결과 화면 열림 확인(단, 지도가 경로를 못 맞추고 넓게 보임 — 확인 필요)
0. (0.3.8에서 함) 개발자 옵션 항목 강조 이동. **사용자 휴대폰 확인 필요**: 빌드 번호가 깜빡이는지, 개발자 옵션 켠 뒤 「모든 앱의 라이브 알림」이 깜빡이는지(안 깜빡이면 키가 틀린 것 → 실제 키를 찾아 `DevOptions.KEY_LIVE_ALL_APPS` 수정). 아직 못 받은 결과: 0.3.7 종료→결과 화면, 대화상자 색, One UI 버전·나우 바·칩 표시 여부

1. 사용자 휴대폰 결과 반영(시간·일시정지·잠금 화면·걸음, `docs/ACCURACY.md` 비교 표)
2. 초록 칩: 삼성은 나우 바·라이브 알림을 자기 앱/허용 앱에만 엶. One UI 8 이상은 개발자 옵션 「모든 앱의 라이브 알림」을 켜면 됨(설정 화면에 안내). 사용자 확인 결과 반영
3. 다음: 갤럭시 워치 심박 ② 헬스 커넥트로 달린 뒤 심박(평균·최고·그래프·구간) → 배포 → ③ 워치(웨어 OS) 앱으로 실시간 연동(시간·거리·심박 표시, 워치 버튼으로 시작/일시정지/종료). 사용자가 ② 다음 ③ 순서로 하기로 함. 0.3.5 배포 뒤 "잠시 멈춰" 요청 → 사용자 말 기다리기
4. (0.3.6에서 고침) 알림 「종료」 뒤 결과 화면 — 에뮬레이터 22_after_stop으로 확인 필요. 다음 후보: 공유 이미지 확인, 웹·README 0.3 반영, plan.md v2, 중간 점검 2차
5. 미니 타이머(상태 표시줄 아래 떠 있는 알약, 다른 앱 위에 표시) — 미리보기만 보여 줌, 진행 여부 미정
4. 단계마다 versionCode·versionName을 올려 배포(사용자 요청), 보고 후 멈추지 말고 다음 작업 이어서
5. 포털 중간 점검 2차 제출(0.2~0.3), 1주일 실사용 후 최종 제출(`docs/plan.md`)
6. (나중에) 포트폴리오 myprofile에 웹 화면 연결 — 사용자가 미룸
