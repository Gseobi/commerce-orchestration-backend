# HB3a Tenant Order Read / 정량 검증 보고서

- Date: 2026-10-17 (Asia/Seoul)
- Base: main `8405a88` (HB2 PR #9)
- Branch: `codex/dev-order-tenant-read-boundary`
- Unit: 내부 owner-scoped 주문 header 조회. SaaS HTTP writer/전체 HB3 완료 아님.

## What Changed / 변경 파일

- `TenantOrderQuery`, `TenantOrderView`: owner/order 필수 내부 계약과 최소 immutable header.
- `TenantOrderReadRepository`: TENANT/merchant/order를 SQL에서 함께 제한한 scalar projection.
- `TenantOrderQueryService`: 양수 입력 검증, foreign/legacy/missing 동일 not-found.
- `TenantOrderQueryServiceTest`, `TenantOrderQueryIntegrationTest`: 신규 13개 case.
- 상태/roadmap/readiness/검증 문서 동기화. dependency/migration/OpenAPI/Compose/CI는 변경 없음.

## Measured Results / 실제 실행 수치

전체 재실행 완료 후 `python3 scripts/test_report.py`로 XML 집계:

| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | Suite time |
|---|---:|---:|---:|---:|---:|---:|---:|
| test | 24 | 155 | 155 | 0 | 0 | 0 | 18.359s |
| integrationTest | 10 | 64 | 64 | 0 | 0 | 0 | 26.004s |
| Total | 34 | 219 | 219 | 0 | 0 | 0 | 44.363s |

기존 206개 대비 unit +8, integration +5. targeted 실행과 전체 실행은 중복 집계하지 않습니다.
Gradle 전체 wall time은 58s이며 XML suite time 합계와 다릅니다.
실행 시간은 부하 처리량/성능 개선/SLO 근거가 아닙니다.

## Commands / 실행 결과

```text
git diff --check                                                     PASS
./gradlew compileJava test --tests '*TenantOrderQueryServiceTest' \
  integrationTest --tests '*TenantOrderQueryIntegrationTest' \
  --rerun-tasks                                                      PASS (27s, 8 + 5 cases)
./gradlew --no-daemon --no-parallel compileJava test \
  integrationTest build --rerun-tasks                                 PASS (58s, bootJar 포함)
python3 scripts/test_report.py                                       PASS (219, failure/error/skip 0)
docker info --format '{{.ServerVersion}}'                             PASS (29.4.1)
docker compose up -d                                                 PASS (데이터/volume 삭제 없음)
docker compose ps                                                    PASS (postgres/kafka healthy, kafka-ui Up)
docker compose exec -T postgres sh -c \
  'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'                    PASS (accepting connections)
docker compose logs postgres --tail=12                               확인
docker compose logs kafka --tail=15                                  확인 (Kafka Server started)
```

unit suite의 `ModulithArchitectureTest`로 모듈 경계를 검증했습니다.
통합 테스트는 별도 PostgreSQL/Kafka Testcontainers를 사용합니다.
Compose DB에 V9 migration/fixture/초기화를 실행하지 않았습니다.
CI workflow/실패 전파 baseline은 변경하지 않았으며, 원격 PR/head와 merge 후 main CI 결과는
해당 PR의 Verification 및 post-merge 결과에 실제 SHA와 함께 기록합니다.

## Failure / Recovery Evidence

- 6개 invalid ID 조합은 DB 접근 이전 거부. foreign/unknown merchant/missing/legacy는 동일 ORDER_NOT_FOUND.
- owner A/B 반복 교차 조회, 정상 조회 후에도 legacy workflow TENANT 읽기·변경 차단 및 row 불변 확인.
- query는 읽기 전용이며 외부 PG/채널 호출이나 retry를 추가하지 않음.
- PostgreSQL은 이전 비정상 종료 후 WAL recovery/checkpoint를 마치고 ready/healthy 상태.
  최초 기본 계정 readiness probe의 `role root does not exist` 로그 후 설정된 DB 계정으로 재확인 PASS.
- 기존 unchecked/JVM class sharing 경고는 남아 있으며 이번 변경으로 해소했다고 주장하지 않음.

## Remaining TODO / 한계

- 인증/membership은 내부 caller의 선행 책임. 해당 port를 직접 외부 입력에 연결하지 않음.
- HB3b~e writer/store 정책·하위 모듈·event/worker/recovery·HTTP 검증은 미구현.
- native scalar query에 명시 predicate가 필요하며 RLS/전체 tenant 격리 보장은 아님.
- 실제 IdP/PG/알림 제공자 연결, historical FK validation, 대형 DB 배포 리허설은 남음.
- Git commit message: `feat: add internal tenant-scoped order read boundary`

[설계와 후속 분할](../productization/tenant-order-read-boundary.md),
[전체 제품 완성도 점검](../productization/product-readiness-review.md).
