# 이어서 하기 (작업 인계 문서)

다른 계정·다른 세션에서 이 저장소를 이어서 작업할 때 이 문서부터 읽습니다. 작업할 때마다 아래 「지금 상태」와 「다음 할 일」을 고칩니다.

## 이 앱

- 런메이트(RunMate): 걷기·뛰기를 자동으로 나눠 기록하는 안드로이드 러닝 앱 (Kotlin, minSdk 26, targetSdk 34)
- ALEPH 프리미엄 「나의 앱 프로젝트」 과제용. 포털 제출: 목표·plan.md·중간 점검 1차 **제출 완료(2026-10-29)**, 2차·최종 남음
- 공개 주소: 앱 https://github.com/sby1234-ai/runmate/releases · 웹 화면 https://sby1234-ai.github.io/runmate/

## 작업 규칙 (사용자와 정한 것)

- 대화는 항상 존댓말, 이름(실명)으로 부르지 않음
- 작업 시작 전에 **예상 작업 시간**을 먼저 말함
- 이 저장소(runmate)의 확인용 커밋·푸시는 바로 해도 됨. **새 버전 배포(versionName 올리기)는 사용자 확인 후**
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

## 지금 상태 (2026-10-29 19:10)

- 배포: v0.2.0 (지도·음성 안내·자동 일시정지·인터벌·최고 기록·이미지 공유)
- main(배포 전, 0.3 예정): **사용자 시안(어두운 유리 + 사진 배경)대로 UI 개편 중**
  - 첫 화면: 날씨별 사진 배경(`res/drawable-nodpi/bg_<날씨>_<번호>.jpg`, Unsplash 무료, 출처 `docs/PHOTOS.md`, 하루마다 다른 사진), 사진이 없으면 `ui/SkyView.kt` 그라데이션. 날씨는 Open-Meteo(위치 소수점 첫째 자리만 전송, 설정에서 끔)
  - 기능 타일 6개(어두운 유리), 가로로 긴 「달리기 시작」 버튼
  - 달리는 중: 달리는 사진 배경, 작은 움직이는 러너(`ui/RunnerView.kt`), 「일시정지/다시 시작」(수동 일시정지 새 기능: `RunRecorder.setManualPause`, 다시 시작 첫 점은 `TrackPoint.breakBefore`라 그 사이 거리·시간 제외) + 「운동 종료」(길게 누름)
  - 앱 전체 어두운 테마(`values/colors.xml`, `Theme.Material3.Dark`), 기록 화면 지도는 어두운 색(`ui/MapPanel.kt` 색 행렬)
- 사진 받기: `tools/photos.txt`(이름 사진ID)를 고쳐 푸시하면 `.github/workflows/photos.yml`이 `ci-photos` 브랜치 `final/`에 받아 둠 → 그 파일을 `app/src/main/res/drawable-nodpi/`에 복사해 커밋

## 다음 할 일

1. 마지막 커밋의 빌드(`ci-logs`)·화면 캡처(`ci-screens`) 확인 → 시안과 비교해 다듬기
2. 일시정지 단위 테스트 추가(`breakBefore` 구간이 거리에서 빠지는지)
3. 휴대폰에서 실제 동작 확인(GPS·음성·배터리)
4. 사용자 확인 후 v0.3.0 배포 → 포털 중간 점검 2차 제출(0.2~0.3 내용)
5. 1주일 실사용(나 + 친구 2명) 후 최종 제출 — 완성 기준은 `docs/plan.md`
6. (나중에) 포트폴리오 myprofile에 웹 화면 연결 — 사용자가 미룸
