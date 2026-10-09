# Test Report

## Latest Baseline / 최신 기준

현재 구현 상태는 [Project Status](project-status.md), 이번 전체 재실행 결과는
[문서 정합성 보고서](verification/documentation-current-state-audit.md)를 기준으로 확인합니다.
아래 reliability/observability 및 delivery별 결과는 당시 검증 기록입니다.
과거 명령·테스트 수를 현재 전체 suite 수나 성능 개선 수치로 해석하지 않습니다.

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
| JWT token issuance | Implemented | explicit local/test demo mode의 `/api/auth/token` |
| `/api/**` authentication | Implemented | demo mode 인증 없는 주문 생성 `401`; disabled/oidc legacy 차단 |
| OIDC Resource Server | Verified (fixture) | RS256/JWKS/issuer/audience/token-kind 검증 및 mode 분리; 실제 IdP/HTTP tenant adapter 미완료 |
| Order create / detail / flow API | Implemented | `OrderFlowIntegrationTest` |
| Orchestration happy path | Implemented | 상태 전이, step, outbox 생성 검증 |
| Settlement failure compensation | Implemented | payment cancel compensation 검증 |
| Notification failure branch | Implemented | compensation step `READY` 검증 |
| Notification ignore policy | Implemented | ignore 가능한 실패는 주문 완료 유지 |
| Admin notification reprocessing | Implemented | retry 후 주문 `COMPLETED` 복구 |
| Admin recovery traceability | Implemented | optional operator/reason request body, no-body compatibility, audit detail 검증 |
| Admin outbox reprocessing | Implemented | dead-letter 즉시 재발행 검증 |
| Outbox publish unit test | Implemented | `PUBLISHED`, `RETRY_WAIT`, `DEAD_LETTER` 전이 검증 |
| PostgreSQL / Kafka outbox happy path | Implemented | publish 후 Kafka 소비 검증 |
| PostgreSQL / Kafka outbox dead-letter path | Implemented | retry 후 dead-letter 전환 검증 |
| Notification retry processor | Implemented | `RETRY_SCHEDULED` due event 재처리, 성공/재스케줄/manual 전환 검증 |
| Admin notification retry-due HTTP trigger | Implemented | `POST /api/admin/notification-events/retry-due`, batch result summary 응답 검증 |
| Notification retry claim | Implemented | due event claim, claim 실패 skippedCount 집계, 동시 실행 시 단일 성공 처리 검증 |
| Notification future retry skip | Implemented | `nextAttemptAt`이 미래인 이벤트는 처리 대상에서 제외 |
| Notification max retry exceeded | Implemented | 반복 실패 시 `MANUAL_INTERVENTION_REQUIRED` 전환 |
| Payment idempotency | Implemented | 같은 `paymentRequestId` replay 시 provider approve/save 1회 검증 |
| Mock payment timeout unknown state | Implemented | `PAYMENT_TIMEOUT_UNKNOWN` token으로 `CONFIRMATION_REQUIRED` payment 저장 검증 |
| Outbox publisher adapter | Implemented | `KafkaTemplate` 없이 `OutboxEventPublisher` mock 기반 publish/retry/dead-letter 검증 |
| Outbox publish claim | Implemented | claim 성공 시에만 publish, `PROCESSING` event 중복 publish 방지 검증 |
| Operational observability metrics | Implemented | outbox publish, notification retry, admin recovery counter와 tag normalization 검증 |
| Modulith architecture verification | Implemented | `ApplicationModules.verify()` 기준 module boundary 검증 |

## 3. Reliability Hardening Test Matrix

이번 문서 정리 전 실제 실행 결과 기준입니다.

Reliability hardening 관련 테스트는 아래 설계 흐름을 기준으로 검증했습니다.

- Payment idempotency: [commerce_orchestration_payment_idempotency_flow](/docs/diagrams/png/commerce_orchestration_payment_idempotency_flow.png)
- Notification retry claim: [commerce_orchestration_notification_outbox_processing_claim_flow](/docs/diagrams/png/commerce_orchestration_notification_outbox_processing_claim_flow.png)
- Outbox publish claim / adapter:
  [commerce_orchestration_notification_outbox_processing_claim_flow](/docs/diagrams/png/commerce_orchestration_notification_outbox_processing_claim_flow.png),
  [commerce_orchestration_outbox_publisher_adapter](/docs/diagrams/png/commerce_orchestration_outbox_publisher_adapter.png)

| Test / Command | Coverage | Result |
|---|---|---|
| `PaymentServiceTest` | 같은 `paymentRequestId` replay 시 `PaymentProviderClient.approve` 중복 호출 방지, `paymentRepository.save` 1회 검증 | PASS |
| `PaymentServiceTest.approve_timeoutUnknown_savesConfirmationRequired_andDoesNotTreatAsSuccess` | mock/dummy timeout unknown result가 `CONFIRMATION_REQUIRED` payment로 저장되고 정상 승인으로 처리되지 않는지 검증 | PASS |
| `PaymentServiceTest.approve_idempotent_replay_reusesConfirmationRequiredPayment_without_provider_call` | `CONFIRMATION_REQUIRED` payment replay가 provider approve를 재호출하지 않는지 검증 | PASS |
| `MockPaymentProviderClientTest` | `PAYMENT_TIMEOUT_UNKNOWN` description token이 `CONFIRMATION_REQUIRED` result로 매핑되는지 검증 | PASS |
| `OrderFlowIntegrationTest.orchestrate_paymentTimeoutUnknown_recordsConfirmationRequiredPayment` | timeout unknown payment가 order success로 진행되지 않고 payment status로 남는지 검증 | PASS |
| `NotificationRetryProcessorIntegrationTest` | due retry event 처리, retry success/reschedule/manual 전환, skippedCount 필드 유지 검증 | PASS |
| `NotificationRetryProcessorIntegrationTest.retryDueNotificationEvents_returnsBatchResultSummary` | `POST /api/admin/notification-events/retry-due`가 due event만 처리하고 batch summary를 반환하는지 검증 | PASS |
| `AdminNotificationRetryControllerTest` | ADMIN role로 retry-due endpoint 호출 시 trigger port 위임과 응답 필드 검증 | PASS |
| `NotificationRetryProcessorTest` | claim 실패 시 skippedCount 증가, 같은 due event 동시 processor 실행 시 최종 성공 처리 1회 검증 | PASS |
| `OutboxPublisherServiceTest` | `OutboxEventPublisher` mock 기반 publish 성공/실패, retry/dead-letter, `PROCESSING` skip 검증 | PASS |
| `./gradlew clean test --rerun-tasks` | 단위 테스트, MockMvc 테스트, Modulith boundary 검증 | PASS |
| `./gradlew clean integrationTest --rerun-tasks --stacktrace` | PostgreSQL/Kafka Testcontainers, Flyway migration, outbox/notification integration flow | PASS |

## 4. Observability Tests

이번 metric/log 보강 후 실제 실행 결과 기준입니다.

| Test | Purpose | Result |
|---|---|---|
| `CommerceRecoveryMetricsTest` | custom metric counter와 tag normalization 검증 | PASS |
| `OutboxPublisherServiceTest` | outbox publish success/failure/skipped/dead-letter metric 검증 | PASS |
| `NotificationRetryProcessorTest` | retry success/skipped/manual-required metric 검증 | PASS |
| `AdminReprocessingServiceTest` | admin recovery request/success/failure metric, optional context default, blank/long context normalization, audit detail truncation 검증 | PASS |
| `AdminReprocessingIntegrationTest` | admin notification/outbox recovery body의 `operatorId`, `reason`이 audit detail에 반영되는지 검증 | PASS |

운영 alert 후보와 dashboard 후보는 [Observability Alert Candidates & Metric Naming](/docs/operations/observability-alert-candidates.md)에 문서화했습니다.
이는 현재 metric/log 신호의 운영 해석이며,
Prometheus/Grafana dashboard나 alert rule 구현 검증은 아닙니다.

실행 명령:

- `./gradlew clean test --rerun-tasks` PASS
- `./gradlew clean integrationTest --rerun-tasks --stacktrace` PASS

## 5. 테스트 종류 차이

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

## 6. GitHub Actions 검증 범위

현재 workflow는 아래 두 job을 수행합니다.

- `build-and-test`  
  `./gradlew compileJava`, `./gradlew test`, unit 리포트 업로드
- `integration-test`  
  `./gradlew integrationTest`, integration 리포트 업로드

현재 artifact 이름은 아래와 같습니다.

- `gradle-unit-test-reports-${{ github.run_id }}-${{ github.run_attempt }}`
- `gradle-integration-test-reports-${{ github.run_id }}-${{ github.run_attempt }}`

## 7. OpenAPI 문서 검증

`docs/openapi/openapi.yaml`은 구현된 HTTP API만 포함하는 static OpenAPI 3.0.3 문서입니다.

이번 OpenAPI partition에서는 아래를 확인합니다.

- YAML syntax parse
- ApiDog manual import: OpenAPI spec 생성 후 개발자가 로컬에서 수동 import 확인
- `git diff --check`
- `./gradlew compileJava`
- `./gradlew test`
- Docker 사용 가능 시 `./gradlew integrationTest --rerun-tasks`

ApiDog import 확인은 수동 검증 기록이며, CI 자동 import 검증으로 주장하지 않습니다.

## 8. CI 안정화 메모

이번 정리에서 `integrationTest` 실패 원인은 단순 Docker 부재가 아니라 Kafka Testcontainers 조합 문제로 확인했습니다.

기존 테스트 지원 코드는 `org.testcontainers.containers.KafkaContainer`와 `apache/kafka-native:3.8.0` 이미지를 함께 사용하고 있었고,  
GitHub Actions에서는 이 조합이 초기화 시점 `ExceptionInInitializerError`, `IllegalStateException`으로 드러났습니다.

현재는 `org.testcontainers.kafka.KafkaContainer`로 정합성을 맞췄고, 아래 기준으로 재검증했습니다.

- `./gradlew clean test --rerun-tasks`
- `./gradlew clean integrationTest --rerun-tasks --stacktrace`
- `./gradlew integrationTest --rerun-tasks --stacktrace`

## 9. 아직 검증하지 않은 범위

- 실제 외부 payment provider와의 네트워크 round-trip
- notification 채널별 retry policy / 운영자 승인 절차
- dead-letter 운영 자동화
- Kafka consumer 기반 상태 전이
- WebClient timeout 이후 full confirmation flow 구현
  - 설계 문서: [Payment Timeout Confirmation Flow](/docs/flows/payment-timeout-confirmation-flow.md)
  - 현재 구현 범위는 mock/dummy provider 기반 `CONFIRMATION_REQUIRED` 상태 기록까지입니다.
  - 실제 external provider confirmation 요청, admin confirmation API, OpenAPI path는 아직 없습니다.
- provider callback API와 `providerTransactionId` 기반 callback idempotency
- admin 레벨 재처리 / 재검증 API 고도화
- Prometheus/Grafana dashboard와 alert rule
- stale `PROCESSING` automatic recovery job
- refresh token / demo signing-key rotation / user store 연동
- 실제 IdP production 연결과 merchant membership HTTP/tenant API

외부 JWKS rotation/cache/장애는 로컬 signed JWT fixture에서 검증했습니다.
위 demo signing-key rotation 미구현과 구분하며 실제 IdP 운영 검증으로 주장하지 않습니다.

## 10. Merchant Foundation Core (delivery snapshot)

merchant/store core delivery unit의 로컬 정량 검증 결과는
[Merchant Foundation Core Test Report](/docs/verification/merchant-foundation-core-test-report.md)에 기록합니다.

- unit test: 44개에서 55개로 증가, 전체 PASS
- integration test: 11개에서 13개로 증가, 전체 PASS
- 신규 merchant unit test: 11개 PASS
- 신규 merchant integration test: 2개 PASS
- PostgreSQL/Kafka Docker Compose health: healthy
- HTTP endpoint는 추가하지 않았으므로 OpenAPI path 변경 없음

## 11. Merchant Membership Access (delivery snapshot)

[Membership Access 정량 보고서](/docs/verification/merchant-membership-access-test-report.md)를 참고합니다.
단위 68개, 통합 17개가 모두 통과했으며 membership 활성 상태, 사업자 상태, READ/WRITE 권한을 검증합니다.
HTTP 인증 연결은 아직 구현하지 않았습니다.

## 12. OIDC Resource Server (delivery snapshot)

[OIDC 검증 보고서](verification/oidc-resource-server-test-report.md)에 unit 122개,
integration 21개, 실패·오류·skip 0개를 기록했습니다.
Resource Server/mode 격리 구현만 검증했으며 HTTP membership adapter는 미완료입니다.
