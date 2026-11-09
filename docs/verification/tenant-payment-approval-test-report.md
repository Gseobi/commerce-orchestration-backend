# HB3c-4 승인 결과 적용 검증 보고서

- 기준: `8bdebb3`, 브랜치 `codex/dev-payment-tenant-approval`
- 변경: `TenantPaymentApproval`과 verifier SPI, order/payment 조건부 전이 포트 및 JDBC 구현, tenant 승인 감사.
- HTTP/OpenAPI, legacy provider, migration, dependency, CI workflow는 변경하지 않았습니다.

| Profile | Suites | Passed | Failure | Error | Skip | XML suite time |
|---|---:|---:|---:|---:|---:|---:|
| Unit | 27 | 171 | 0 | 0 | 0 | 16.795s |
| PostgreSQL/Kafka integration | 14 | 98 | 0 | 0 | 0 | 32.910s |
| Total | 41 | 269 | 0 | 0 | 0 | 49.705s |

이전 255개 대비 통합 +14. Gradle wall time 67s. XML 시간은 성능 지표가 아닙니다.

```text
git diff --check / git diff --cached --check: PASS
./gradlew --no-daemon --no-parallel compileJava test integrationTest build --rerun-tasks: PASS (67s)
python3 scripts/test_report.py: PASS (269/269)
docker compose up -d / docker compose ps: PostgreSQL/Kafka healthy, Kafka UI Up
pg_isready: accepting connections
docker compose logs kafka --tail=4: Kafka Server started
```

- 기본 환경에서 verifier 없음/승인 불가. 별도 테스트 verifier에서만 성공 경로 검증.
- 외부 owner 및 다른 결제 ID는 verifier 호출 전 거부. 빈 결과, ID/금액/통화/거래 ID 오류 거부.
- verifier는 쓰기 트랜잭션 밖에서 실행. 검증 중 결제 변경, 거래 ID 중복, 감사 실패에서 전이 롤백.
- 정상 적용 시 결제 APPROVED, 주문 PAID, 승인 감사 1건. 재호출 및 정산·알림·outbox 부작용 없음.
- 테스트는 격리 Testcontainers에서 실행했습니다. 원격 CI와 main 재검증은 PR에 기록합니다.
- 커밋: `feat: apply verified tenant payment approvals atomically`

[신뢰 경계, 후속 조건 및 달성률](../productization/tenant-payment-approval.md)
