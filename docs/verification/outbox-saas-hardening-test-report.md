# Outbox SaaS Hardening / 첫 보강 단위 검증 보고서

- Date: 2026-10-12 (Asia/Seoul)
- Base: `a51487f`, branch: `codex/dev-outbox-saas-boundary-hardening`
- Scope: outbox 계약 결합 완화 + SaaS 미완성 경계 보강 설계. tenant migration/실제 IdP 연결 미구현.

## Changes / 변경

- orchestration의 Kafka topic/수동 JSON 생성 제거. `OrderEventType` 두 business intent만 전달.
- outbox가 routing/JSON serialization 소유. 잘못된 입력·serialization 실패 시 row 저장 없음.
- publisher port를 JPA entity에서 immutable `OutboxPublishMessage`로 변경. adapter의 entity 접근 차단.
- orchestration→infrastructure, infrastructure→outbox::entity allowed dependency 2개 제거.
- entity NamedInterface 폐쇄; 전체 Modulith verify PASS. 모든 내부 Java caller를 함께 갱신.
- 기존 저장 row/topic/key/JSON 의미, HTTP/OpenAPI, migration/Compose/dependency/CI는 미변경.
- IdP 후보와 legacy 보존/격리, 신규 SaaS 거래 owner 필수 결정은 [보강 계획](../productization/saas-hardening-plan.md)에 기록.

## Verification / 실행

```text
git diff --check: PASS
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks: PASS (48s, 6 tasks executed)
  compileJava / Modulith verification 포함
python3 scripts/test_report.py: PASS
./gradlew --no-daemon compileJava build: PASS (6s, bootJar 생성; test UP-TO-DATE)
Markdown 49개 / local inline link 417건: missing 0건
```

| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | XML suite time |
|---|---:|---:|---:|---:|---:|---:|---:|
| test | 23 | 147 | 147 | 0 | 0 | 0 | 16.655s |
| integrationTest | 8 | 36 | 36 | 0 | 0 | 0 | 19.308s |
| Total | 31 | 183 | 183 | 0 | 0 | 0 | 35.963s |

이전 173개 대비 unit 10개 추가 (OutboxService 7 + Kafka adapter 3), integration 36개 유지.
기존 테스트 삭제나 CI 완화 없음. XML 합계는 Gradle wall time과 다르며 성능 개선 수치가 아님.

## Scenarios / 검증 경계

- 두 typed event의 기존 topic/eventType/JSON 의미와 READY 초기 상태.
- null/0/음수 orderId, null event type: repository/publisher 호출 없음.
- serialization failure: partial row 저장/발행 없음.
- Kafka adapter의 orderId key, topic, payload 유지; null broker result/failed future와 reason truncation.
- 기존 publisher claim/skipped/성공/retry/dead-letter metric과 admin recovery 회귀.
- PostgreSQL/Flyway + Kafka 소비 happy path 및 failure/retry/dead-letter integration PASS.
- OIDC store READ/membership/tenant path negative test 등 이전 전체 suite 유지.

## Runtime / CI

Docker Compose PostgreSQL/Kafka healthy, Kafka UI Up 확인. 테스트 데이터는 별도 Testcontainers 사용.
최근 PostgreSQL ready/checkpoint 및 Kafka started 로그를 확인했으며 과거 controller disconnect 로그도 존재합니다.
volume 초기화/legacy 데이터 삭제 없음. DB schema V1~V8 변경 없음.
CI는 기존 두 jobs를 유지하고 PR/main 결과를 확인한 뒤 완료 보고에 기록합니다.
로컬 PASS가 아직 실행하지 않은 CI의 성공을 의미하지 않습니다.

## Remaining / 제외 범위

실제 IdP/계정 연결, 신규 거래 tenant ownership, versioned tenant event, membership provisioning,
동시 payment 최초 승인 방어, timeout confirmation/환불, compensation 실패 복구, stale lease 회수는 Planned입니다.
이번 변경으로 전체 SaaS 전환·외부 provider 호환성·기존 데이터 tenant 격리를 완료했다고 주장하지 않습니다.
binary diagrams는 재렌더링하지 않았습니다. Java 내부 source contract 변경은 같은 저장소 caller만 검증했습니다.

Suggested commit: `refactor: decouple outbox contracts for SaaS hardening`
