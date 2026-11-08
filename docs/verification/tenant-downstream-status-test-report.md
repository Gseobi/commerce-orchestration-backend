# HB3c-3 정산·알림 조회 검증 보고서

- 기준: `41e6c97`, 브랜치 `codex/dev-orchestration-tenant-downstream-status`
- 변경: `TenantSettlementStatusQuery`, `TenantNotificationStatusQuery`와 JDBC 구현,
  부모를 확인하는 `TenantDownstreamStatusQuery`, 단위·PostgreSQL 통합 테스트.
- 기존 LEGACY 경로, migration, HTTP/OpenAPI, dependency 및 CI workflow 변경 없음.

| Profile | Suites | Passed | Failure | Error | Skip | XML suite time |
|---|---:|---:|---:|---:|---:|---:|
| Unit | 27 | 171 | 0 | 0 | 0 | 17.009s |
| PostgreSQL/Kafka integration | 13 | 84 | 0 | 0 | 0 | 29.837s |
| Total | 40 | 255 | 0 | 0 | 0 | 46.846s |

이전 250개 대비 unit +2, integration +3. 전체 Gradle wall time 63s. XML 시간은 성능 지표가 아닙니다.

```text
git diff --check / git diff --cached --check: PASS
./gradlew --no-daemon --no-parallel compileJava test integrationTest build --rerun-tasks: PASS
python3 scripts/test_report.py: PASS (255/255)
docker compose up -d / docker compose ps: PostgreSQL/Kafka healthy, Kafka UI Up
pg_isready: accepting connections
docker compose logs kafka --tail=4: Kafka Server started
```

- 소유 주문의 최신 상태만 반환. 다른 사업자/미존재/LEGACY 주문은 동일 오류로 거절.
- 하위 모듈 자체의 교차 owner 차단과 양방향 LEGACY 격리 검증.
- READY 결제 이후 조회에도 주문 상태와 정산·알림·outbox가 변경되지 않음.
- 격리 Testcontainers에만 테스트 행을 주입했습니다. 원격 CI와 main 재검증은 PR에 기록합니다.
- 후속: 승인 확인, 정산·알림 쓰기 및 실제 실행, worker, 인증 HTTP.
- 커밋: `feat: scope tenant settlement and notification status reads`

[설계, 잔여 범위 및 달성률](../productization/tenant-downstream-status.md)
