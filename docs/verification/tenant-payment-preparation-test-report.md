# HB3c-1 내부 결제 준비 검증 보고서

- 기준: `0282fae`, 브랜치 `codex/dev-payment-tenant-preparation`
- 변경: 주문의 `TenantOrderPaymentClaim`과 조건부 SQL, 결제의 `TenantPaymentRecords`,
  내부 `TenantPaymentPreparation` 및 단위·통합 테스트.
- HTTP/OpenAPI, migration, dependency, CI workflow 변경 없음.

| Profile | Suites | Passed | Failure | Error | Skip | XML suite time |
|---|---:|---:|---:|---:|---:|---:|
| Unit | 26 | 169 | 0 | 0 | 0 | 16.771s |
| PostgreSQL/Kafka integration | 12 | 78 | 0 | 0 | 0 | 29.411s |
| Total | 38 | 247 | 0 | 0 | 0 | 46.182s |

HB3b의 238개 대비 unit +2, integration +7. Gradle 전체 wall time 61s.
XML 시간은 성능 지표가 아닌 실행 기록입니다.

```text
git diff --check / git diff --cached --check: PASS
./gradlew --no-daemon --no-parallel test --tests '*TenantPaymentPreparationTest' integrationTest --tests '*TenantPaymentPreparationIntegrationTest' --rerun-tasks: PASS (24s)
./gradlew --no-daemon --no-parallel compileJava test integrationTest build --rerun-tasks: PASS (61s)
python3 scripts/test_report.py: PASS (247/247)
docker compose up -d / docker compose ps: PostgreSQL/Kafka healthy, Kafka UI Up
pg_isready: accepting connections
docker compose logs kafka --tail=4: Kafka Server started
```

- 테스트는 실제 PostgreSQL에서 취소/준비 경쟁, 동시 준비 중복 방지, 결제 INSERT 실패 시 선점 롤백을 검증했습니다.
- 외부 사업자/LEGACY/missing/취소된 주문 거부, V9 부모 복합 FK, legacy 결제 조회 격리 및 단독 호출 차단을 검증했습니다.
- 운영·Compose 데이터 변경 없이 격리 Testcontainers에서 실행했습니다. 원격 CI 및 병합 후 재검증은 PR에 별도로 기록합니다.
- 설계·구현 재점검 결과, `MANDATORY`는 권한 검증이나 결제 재처리 기능이 아니며 READY는 외부 승인으로 이어지지 않습니다.
- 후속: 나머지 owner 전달 경계, 인증·멱등성, PG dispatch/confirmation, 공개 API.
- 커밋 메시지: `feat: prepare tenant payments with an atomic order claim`

[설계와 완료율 산정](../productization/tenant-payment-preparation.md)
