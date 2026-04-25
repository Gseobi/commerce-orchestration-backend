# Test Report

이 문서는 commerce-orchestration-backend가 주장하는 orchestration, 상태 전이, 실패 분기, 보상 처리, outbox retry/dead-letter, notification retry 흐름이 실제 테스트로 어디까지 검증되었는지 정리합니다.

특히 이 프로젝트는 구현 범위를 과장하지 않고, 현재 검증한 흐름과 아직 남은 운영 확장 범위를 분리해 보여주는 것을 목표로 합니다.
 
존재하지 않는 테스트 범위는 구현된 것처럼 적지 않고, 설계 TODO는 `docs/design-notes.md`에서 다룹니다.

## 1. 로컬 검증 명령

- `./gradlew compileJava`  
  메인 소스 컴파일 확인
- `./gradlew test`  
  H2 + mock Kafka 기반 검증
- `./gradlew integrationTest`  
  Testcontainers 기반 PostgreSQL / Kafka 통합 테스트

## 2. 현재 통과하는 검증 범위

| Area | Status | Notes |
|---|---|---|
| `compileJava` | Pass | 메인 소스 컴파일 성공 |
| `test` | Pass | 단위 테스트와 MockMvc 기반 흐름 검증 |
| `integrationTest` | Pass | PostgreSQL / Kafka Testcontainers 검증 |
| JWT token issuance | Implemented | `/api/auth/token` |
| `/api/**` authentication | Implemented | 인증 없는 주문 생성 `401` 확인 |
| Order create / detail / flow API | Implemented | `OrderFlowIntegrationTest` |
| Orchestration happy path | Implemented | 상태 전이, step, outbox 생성 검증 |
| Settlement failure compensation | Implemented | payment cancel compensation 검증 |
| Notification failure branch | Implemented | compensation step `READY` 검증 |
| Notification ignore policy | Implemented | ignore 가능한 실패는 주문 완료 유지 |
| Admin notification reprocessing | Implemented | retry 후 주문 `COMPLETED` 복구 |
| Admin outbox reprocessing | Implemented | dead-letter 즉시 재발행 검증 |
| Outbox publish unit test | Implemented | `PUBLISHED`, `RETRY_WAIT`, `DEAD_LETTER` 전이 검증 |
| PostgreSQL / Kafka outbox happy path | Implemented | publish 후 Kafka 소비 검증 |
| PostgreSQL / Kafka outbox dead-letter path | Implemented | retry 후 dead-letter 전환 검증 |
| Notification retry processor | Implemented | `RETRY_SCHEDULED` due event 재처리, 성공/재스케줄/manual 전환 검증 |
| Notification retry claim | Implemented | due event claim, claim 실패 skippedCount 집계, 동시 실행 시 단일 성공 처리 검증 |
| Notification future retry skip | Implemented | `nextAttemptAt`이 미래인 이벤트는 처리 대상에서 제외 |
| Notification max retry exceeded | Implemented | 반복 실패 시 `MANUAL_INTERVENTION_REQUIRED` 전환 |
| Payment idempotency | Implemented | 같은 `paymentRequestId` replay 시 provider approve/save 1회 검증 |
| Outbox publisher adapter | Implemented | `KafkaTemplate` 없이 `OutboxEventPublisher` mock 기반 publish/retry/dead-letter 검증 |
| Outbox publish claim | Implemented | claim 성공 시에만 publish, `PROCESSING` event 중복 publish 방지 검증 |
| Modulith architecture verification | Implemented | `ApplicationModules.verify()` 기준 module boundary 검증 |

## 3. Reliability Hardening Test Matrix

이번 문서 정리 전 실제 실행 결과 기준입니다.

| Test / Command | Coverage | Result |
|---|---|---|
| `PaymentServiceTest` | 같은 `paymentRequestId` replay 시 `PaymentProviderClient.approve` 중복 호출 방지, `paymentRepository.save` 1회 검증 | PASS |
| `NotificationRetryProcessorIntegrationTest` | due retry event 처리, retry success/reschedule/manual 전환, skippedCount 필드 유지 검증 | PASS |
| `NotificationRetryProcessorTest` | claim 실패 시 skippedCount 증가, 같은 due event 동시 processor 실행 시 최종 성공 처리 1회 검증 | PASS |
| `OutboxPublisherServiceTest` | `OutboxEventPublisher` mock 기반 publish 성공/실패, retry/dead-letter, `PROCESSING` skip 검증 | PASS |
| `./gradlew clean test --rerun-tasks` | 단위 테스트, MockMvc 테스트, Modulith boundary 검증 | PASS |
| `./gradlew clean integrationTest --rerun-tasks --stacktrace` | PostgreSQL/Kafka Testcontainers, Flyway migration, outbox/notification integration flow | PASS |

## 4. 테스트 종류 차이

### `test`

- H2 메모리 DB 사용
- Flyway 비활성화
- mock adapter / mock Kafka 기반 검증 포함
- 빠른 회귀 확인 목적

### `integrationTest`

- PostgreSQL Testcontainer 사용
- Kafka Testcontainer 사용
- Flyway migration 적용 후 JPA `validate`
- outbox publish와 DB 스키마를 실인프라에 가깝게 검증

## 5. GitHub Actions 검증 범위

현재 workflow는 아래 두 job을 수행합니다.

- `build-and-test`  
  `./gradlew compileJava`, `./gradlew test`, unit 리포트 업로드
- `integration-test`  
  `./gradlew integrationTest`, integration 리포트 업로드

현재 artifact 이름은 아래와 같습니다.

- `gradle-unit-test-reports`
- `gradle-integration-test-reports`

## 6. CI 안정화 메모

이번 정리에서 `integrationTest` 실패 원인은 단순 Docker 부재가 아니라 Kafka Testcontainers 조합 문제로 확인했습니다.

기존 테스트 지원 코드는 `org.testcontainers.containers.KafkaContainer`와 `apache/kafka-native:3.8.0` 이미지를 함께 사용하고 있었고,  
GitHub Actions에서는 이 조합이 초기화 시점 `ExceptionInInitializerError`, `IllegalStateException`으로 드러났습니다.

현재는 `org.testcontainers.kafka.KafkaContainer`로 정합성을 맞췄고, 아래 기준으로 재검증했습니다.

- `./gradlew clean test --rerun-tasks`
- `./gradlew clean integrationTest --rerun-tasks --stacktrace`
- `./gradlew integrationTest --rerun-tasks --stacktrace`

## 7. 아직 검증하지 않은 범위

- 실제 외부 payment provider와의 네트워크 round-trip
- notification 채널별 retry policy / 운영자 승인 절차
- dead-letter 운영 자동화
- Kafka consumer 기반 상태 전이
- WebClient timeout 이후 confirmation flow
- provider callback API와 `providerTransactionId` 기반 callback idempotency
- admin 레벨 재처리 / 재검증 API 고도화
- refresh token / key rotation / user store 연동
