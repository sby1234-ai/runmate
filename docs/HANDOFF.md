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
- 중간중간 커밋·푸시해서 언제 끊겨도 이어갈 수 있게 함
- 코드 수정본은 부분 조각이 아니라 파일 전체로

## 빌드·확인 방법 (로컬에 안드로이드 SDK 없음)

| 무엇 | 어떻게 |
|---|---|
| 빌드 + 단위 테스트 | main에 푸시하면 `.github/workflows/build.yml` 실행. 결과는 `ci-logs` 브랜치(`STATUS`, `build.log`, `permissions.txt`, `test-results/`) — `git clone --depth 1 -b ci-logs https://github.com/sby1234-ai/runmate` |
| 실제 화면 캡처 | `app/**`이 바뀌어 푸시되면 `.github/workflows/screens.yml`이 에뮬레이터(API 34, Pixel 6)에서 화면을 찍어 `ci-screens` 브랜치에 PNG로 올림. 찍는 순서는 `.github/screens.sh` |
| 화면 점검용 가상 데이터 | 디버그 빌드에서만 `MainActivity`에 `--es demo home|run|walk|detail|runs|settings`, `--es sky CLEAR|PARTLY|CLOUDY|FOG|RAIN|SNOW|STORM|NIGHT` (가상 기록 `logic/DemoRun.kt`) |
| 릴리스 | `app/build.gradle.kts`의 `versionName`을 올려 main에 푸시하면 그 버전 릴리스가 자동 생성(태그 푸시는 막혀 있음). 서명 키는 GitHub Secrets에만 있음 |
| 웹 화면 | `web/` 폴더가 바뀌면 `.github/workflows/pages.yml`이 GitHub Pages로 배포 |

## 지금 상태 (2026-10-29)

- 배포: v0.2.0 (지도·음성 안내·자동 일시정지·인터벌·최고 기록·이미지 공유)
- main(배포 전, 0.3 예정): UI 개편
  - 첫 화면: 날씨 배경(`ui/SkyView.kt`, Open-Meteo, 위치는 소수점 첫째 자리로만 전송, 설정에서 끔), 기온·달리기 조언, 큰 「시작」 버튼, 기능 타일 6개(인터벌·내 기록·최고 기록·음성·자동 일시정지·설정)
  - 달리는 중 화면: 움직이는 러너 그림(`ui/RunnerView.kt`), 큰 시간·거리, 반투명 카드, 길게 눌러 정지
  - 지도는 첫 화면에서 빼고 달리기 후 기록 화면에만. 지도 색을 빼고 밝게 눌러 차분하게 + 경로에 흰 테두리(`ui/MapPanel.kt`)
- 단위 테스트 29개 통과, 에뮬레이터 화면 캡처 정상(앱 멈춤 없음)

## 다음 할 일

1. **사용자가 보낸 시안 스타일로 바꾸기** — 배경에 실제 사진(노을 강변 / 비 오는 숲길 / 눈 / 밤 도시 불빛 / 흐린 하늘 / 달리는 중엔 달리는 사람 발), 타일은 어두운 날씨에서 어두운 반투명(유리) 카드, 시작 버튼은 반투명 흰 원. **사진을 어디서 구할지(사용자 원본 / 무료 사진 / 그림) 사용자 답을 기다리는 중**
2. 휴대폰에서 실제 동작 확인(GPS·음성·배터리)
3. 사용자 확인 후 v0.3.0 배포 → 포털 중간 점검 2차 제출(0.2~0.3 내용)
4. 1주일 실사용(나 + 친구 2명) 후 최종 제출 — 완성 기준은 `docs/plan.md`
5. (나중에) 포트폴리오 myprofile에 웹 화면 연결 — 사용자가 미룸
