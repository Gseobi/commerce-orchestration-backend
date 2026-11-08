# HB3b 내부 주문 생성·취소 검증 보고서

- 기준: `fda2d99`, 2026-11-08 (Asia/Seoul)
- 브랜치: `codex/dev-order-tenant-write-boundary`
- 변경: `TenantOrderCommands` / `TenantOrderCommandService` / `TenantOrderWriteRepository`,
  merchant의 `OrderStoreAccess`, V10 migration과 관련 fixture·설계 문서

## Measured Results

| Profile | Suites | Tests passed | Failure | Error | Skipped | XML suite time |
|---|---:|---:|---:|---:|---:|---:|
| Unit | 25 | 167 | 0 | 0 | 0 | 17.216s |
| PostgreSQL/Kafka integration | 11 | 71 | 0 | 0 | 0 | 29.010s |
| Total | 36 | 238 | 0 | 0 | 0 | 46.226s |

HB3a의 219개 대비 unit +12, integration +7. Gradle wall time 61s.
XML 합계는 실행 시점의 관측치이며 성능 개선 수치가 아닙니다.

## Verification

```text
git diff --check / git diff --cached --check: PASS
./gradlew --no-daemon compileJava: PASS
./gradlew --no-daemon --no-parallel compileJava test integrationTest build --rerun-tasks: PASS
python3 scripts/test_report.py: PASS
docker compose up -d / docker compose ps: PostgreSQL, Kafka healthy; Kafka UI Up
pg_isready (Compose DB configured user): accepting connections
docker compose logs kafka --tail=8: Kafka Server started
```

기존 Modulith 경계와 CI workflow는 유지했습니다. V10은 격리 Testcontainers에서만 검증했으며
기존 Compose/운영 데이터에는 적용하지 않았습니다. 원격 CI와 병합 후 검증은 PR에 별도 기록합니다.

## Review And Follow-up

- 초기 targeted 실행에서 PostgreSQL의 실제 lock timeout이 Spring의 예상 예외 subtype과 달랐습니다.
  테스트가 root SQLSTATE `55P03`을 직접 확인하도록 보완한 후 전체 통과했습니다.
- foreign/inactive store와 suspended merchant에서 생성 없음. 두 row lock 및 rollback, snapshot 불변,
  V9 기존 TENANT/LEGACY 보존과 V10 신규 제약을 검증했습니다.
- 취소는 owner/scope/snapshot/CREATED 조건을 원자적으로 검사하고, replay 시 행을 다시 쓰지 않습니다.
  LEGACY, foreign/missing, PAYMENT_PENDING 경로를 검증했습니다.
- 후속: HB3c 하위 거래 owner 전파, HB3d/e event·worker·인증 HTTP 및 생성 멱등성.
  결제·환불·SaaS HTTP는 제공하지 않습니다.
- Commit message: `feat: add internal tenant order commands and store snapshots`

[설계·사후 검토](../productization/tenant-order-write-boundary.md)
