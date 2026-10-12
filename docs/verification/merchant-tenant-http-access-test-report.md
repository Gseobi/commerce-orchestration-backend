# Merchant Tenant HTTP Access / 정량 검증 보고서

- Date: 2026-10-12 (Asia/Seoul)
- Base: `5a51f31` / `main`
- Branch: `codex/dev-merchant-tenant-http-access`
- Scope: OIDC-only store GET + active READ membership adapter. P0 전체 완료 아님.

## What Changed / 변경 내용

- `security/MerchantRequestAccessAdapter`: verified JWT principal과 canonical issuer/subject 확인 후 내부 membership 계약 위임.
- `security/MerchantStoreController`: READ authorize 이후 merchant-scoped store 조회, sanitized path 오류 처리.
- `SecurityConfig`, `security/package-info`: 단일 GET 허용, demo tenant 경로 차단, `merchant::api` 의존만 추가.
- 신규 adapter/controller unit test 및 기존 mode/OIDC integration suite 보강.
- OpenAPI에 구현된 GET 한 개 추가, current-state/roadmap/claim 문서 동기화.
- DB migration, Gradle dependency, Compose, CI, legacy domain/event 데이터는 변경하지 않음.

## Numeric Results / 전체 재실행

```text
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks
BUILD SUCCESSFUL in 52s, 6 tasks executed (compileJava 포함)
python3 scripts/test_report.py: PASS
git diff --check: PASS
./gradlew --no-daemon build: PASS (7s, bootJar 생성; 기존 test 결과 UP-TO-DATE)
```

| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | XML suite time |
|---|---:|---:|---:|---:|---:|---:|---:|
| test | 21 | 137 | 137 | 0 | 0 | 0 | 19.285s |
| integrationTest | 8 | 36 | 36 | 0 | 0 | 0 | 20.570s |
| Total | 29 | 173 | 173 | 0 | 0 | 0 | 39.855s |

- 이전 delivery baseline: unit 122 + integration 21 = 143.
- 증가: unit 15 + integration 15 = 30. 기존 테스트 삭제/CI 완화 없음.
- 신규 unit: adapter 10, controller 4, demo ADMIN tenant 경로 거부 1.
- 기존 `OidcApplicationIntegrationTest`: 4개에서 19개로 증가.
- 최초 targeted adapter/controller/Modulith 테스트: PASS (14s).
- 최초 targeted OIDC HTTP integration: PASS (28s).
- 마지막 전체 실행 전 1회 전체 회귀도 PASS (51s). 표는 최종 52s 실행의 XML만 사용.

XML suite time 합계와 Gradle wall time은 다릅니다. 실행시간 차이는 성능 개선/부하/SLO를 의미하지 않습니다.
unchecked/JVM sharing warning은 기존 baseline이며 오류나 skip으로 숨기지 않습니다.

## Failure and Isolation / 검증 시나리오

| 검증 | 결과 |
|---|---|
| active VIEWER/OPERATOR + 자기 merchant store READ | 200, public StoreView만 반환, no-store 헤더 |
| 없는 membership / 없는 merchant | 동일한 sanitized 403, data 없음 |
| 타 merchant path / 내 merchant 아래 타 storeId | 각각 403 / STORE_NOT_FOUND 404 |
| 같은 token의 요청 사이 membership revoke | 다음 요청 403 |
| ACTIVE membership + SUSPENDED merchant | 403 |
| token roles/scope/merchantId/actorId + header/query/body identity spoof | 403, 임의 권한 생성 없음 |
| 서로 다른 actor/merchant + 다음 무토큰 요청 | 권한 잔존 없음; 200/403/401 계약 유지 |
| missing/invalid/expired/wrong issuer/ID token/numeric subject | 401; mode test에서 application 호출 없음 확인 |
| demo ADMIN / disabled mode | merchant 조회 불가; controller/adapter demo bean 없음 |
| POST/HEAD/OPTIONS/PUT/PATCH/DELETE, 등록/목록/추가 경로 | valid OPERATOR token에도 403 |
| storeId 0/-1/문자열/Long overflow | sanitized BAD_REQUEST 400 |
| controller membership 거부 | store application 호출 없음 |
| Modulith boundary | `merchant::api`만 추가 후 verify 통과 |

## Docker / Schema / CI Baseline

```text
docker info --format '{{.ServerVersion}}': 29.4.1
docker compose up -d: PASS (기존 서비스 기동, volume 보존)
docker compose ps: PostgreSQL healthy / Kafka healthy / Kafka UI Up
docker compose logs postgres --tail=5: ready + checkpoint 완료
docker compose logs kafka --tail=5: Kafka Server started
PostgreSQL/Kafka Testcontainers + Flyway V1~V8/JPA validate: 전체 integration suite PASS
```

Compose는 runtime health 확인용이며 suite 데이터는 별도 Testcontainers에 저장합니다.
CI workflow는 미변경입니다. PR head의 build-and-test/integration-test와 merge 후 main CI를
확인하고 최종 결과는 GitHub PR/check 및 delivery 완료 보고에 남깁니다.
아직 실행하지 않은 CI를 로컬 PASS로 대신하지 않습니다.

## OpenAPI / Documentation Verification

- 기존 캐시의 SnakeYAML 2.5로 YAML parse 및 duplicate key 거부 설정 적용: PASS. 신규 설치 없음.
- 임시 Java checker: 11 paths / 11 operations / 내부 `$ref` 85건 해소 / compiled StoreView 필드 7개 일치.
- 신규 merchant path는 GET만 포함. Controller mapping과 수동 대조.
- Markdown 47개, local inline links 410건의 파일/heading 대상 점검: missing 0건.
  GitHub slug 근사 방식이며 외부 링크/ApiDog 재import는 제외.
- 이는 general OpenAPI semantic validator 또는 실제 IdP 계약 검증은 아닙니다.
- token/private key/secret 패턴 검색은 변경 문서와 코드의 제한적 검사이며 전수 secret scanner를 대신하지 않습니다.

## Remaining TODO / 잔여 범위

- 실제 IdP production 연결, membership provisioning/초대/계정 연결과 감사 절차.
- 기존 주문·결제·알림·outbox/event tenant ownership: 이번 GET으로 전체 제품 보안을 보장하지 않음.
- revoke와 in-flight request의 원자적 직렬화는 미구현. GET 조회 정책은 다음 요청부터 revoke를 반영.
- CORS/preflight, 새로운 write/list API, provider 계약과 실제 외부 네트워크 검증은 제외.
- 새 dependency/plugin/유료 계정 추가 없음.

Suggested commit: `feat: add merchant-scoped OIDC store read access`
