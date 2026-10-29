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
- **10분마다 커밋·푸시 + 중간 보고**(이어서 할 때 어디서부터인지 포함), 언제 끊겨도 이어갈 수 있게 함
- 코드 수정본은 부분 조각이 아니라 파일 전체로

## 빌드·확인 방법 (로컬에 안드로이드 SDK 없음)

| 무엇 | 어떻게 |
|---|---|
| 빌드 + 단위 테스트 | main에 푸시하면 `.github/workflows/build.yml` 실행. 결과는 `ci-logs` 브랜치(`STATUS`, `build.log`, `permissions.txt`, `test-results/`) — `git clone --depth 1 -b ci-logs https://github.com/sby1234-ai/runmate` |
| 실제 화면 캡처 | `app/**`이 바뀌어 푸시되면 `.github/workflows/screens.yml`이 에뮬레이터(API 34, Pixel 6)에서 화면을 찍어 `ci-screens` 브랜치에 PNG로 올림. 찍는 순서는 `.github/screens.sh` |
| 화면 점검용 가상 데이터 | 디버그 빌드에서만 `MainActivity`에 `--es demo home|run|walk|detail|runs|settings`, `--es sky CLEAR|PARTLY|CLOUDY|FOG|RAIN|SNOW|STORM|NIGHT` (가상 기록 `logic/DemoRun.kt`) |
| 릴리스 | `app/build.gradle.kts`의 `versionName`을 올려 main에 푸시하면 그 버전 릴리스가 자동 생성(태그 푸시는 막혀 있음). 서명 키는 GitHub Secrets에만 있음 |
| 웹 화면 | `web/` 폴더가 바뀌면 `.github/workflows/pages.yml`이 GitHub Pages로 배포 |

## 지금 상태 (2026-10-29 21:45)

- 배포: **v0.3.3** (versionCode 6). 0.3.2 = 아래 잠금 화면·시간·걸음·실시간 알림, 0.3.3 = GPS 흔들림 거르기(`logic/Smoother.kt`)·멈춘 구간 거리 제외. 정확도 설명과 다른 앱 비교 방법은 `docs/ACCURACY.md`
- 0.3.1 이후 들어간 것:
  - 잠금 화면: 달리는 중이면 `MainActivity`가 잠금 위에 보임(`showOnLockScreen`), 알림은 어두운 카드(`res/layout/notif_run_small|big.xml`, 흐르는 Chronometer)
  - 시간: 운동 시간은 모두 `SystemClock.elapsedRealtime` 하나로(`RunRecorder.Snapshot.activeMs/activeNow`), 앱은 초 경계에 맞춰 갱신, 알림 Chronometer와 같은 기준점. 인터벌도 운동 시간 기준(일시정지하면 멈춤)
  - 걸음: 휴대폰 걸음 센서(`logic/Steps.kt` StepTracker, `RecordingService.startSteps`), 케이던스 140 이상이면 뛰기(`MotionClassifier.onCadence`), 기록에 `Run.steps` 저장
  - 몇 km째·구간 페이스(`logic/RunStats.kt`의 `Lap`)를 달리는 중 화면·알림에 표시
  - 안드로이드 16 이상 + 실시간 알림 허용이면 표준 알림 + 상태 표시줄 칩(`RecordingService.liveUpdate`, 이름으로 찾아 부르는 setRequestPromotedOngoing/setShortCriticalText), 아니면 어두운 카드
- 점검: `screens.yml`의 `screens`(API 34: 화면, 일시정지 `pause_check.txt`, 시간 일치 `time_check.txt`)와 `screens36`(API 36: 실시간 알림 칩, `ci-screens36` 브랜치)
- 사진 받기: `tools/photos.txt`를 고쳐 푸시하면 `photos.yml`이 `ci-photos` 브랜치 `final/`에 받아 둠 → `res/drawable-nodpi/`에 복사

## 다음 할 일

1. 사용자 휴대폰 결과 반영(시간·일시정지·잠금 화면·걸음, `docs/ACCURACY.md` 비교 표)
2. 사용자 휴대폰 기종·One UI 버전 답 반영(초록 칩: 안드로이드 16 QPR1 이상 실시간 알림에서만, API 36 에뮬레이터에선 안 켜짐 확인)
3. 단계마다 versionCode·versionName을 올려 배포(사용자 요청)
5. 포털 중간 점검 2차 제출(0.2~0.3), 1주일 실사용 후 최종 제출(`docs/plan.md`)
6. (나중에) 포트폴리오 myprofile에 웹 화면 연결 — 사용자가 미룸
