# Commerce Orchestration Technical Discussion Points

이 문서는 commerce-orchestration-backend의 주요 설계 선택, 구현 근거, 검증 근거, 현재 Boundary를 기술 검토 Q&A 형태로 정리합니다. 모든 설명은 현재 구현된 코드와 테스트 범위를 기준으로 합니다.

## Q1. 왜 Orchestration 구조를 선택했나요?

### Technical Explanation

주문 이후 payment, settlement, notification, outbox publish는 같은 요청 흐름 안에 있지만 실패 의미가 서로 다릅니다. 각 도메인 service가 다음 단계를 직접 호출하면 실패 분기와 보상 정책이 흩어집니다.

그래서 `CommerceOrchestrationService`를 중심에 두고 주문 이후 흐름을 한곳에서 조율했습니다. 도메인 내부 저장소는 각 module이 소유하고, orchestration은 공개 application contract를 통해 필요한 작업만 조합합니다.

### Implementation Evidence

- [CommerceOrchestrationService](/src/main/java/io/github/gseobi/commerce/orchestration/orchestration/service/CommerceOrchestrationService.java)
- `OrderController#orchestrate`
- `PaymentApplication`, `SettlementApplication`, `NotificationApplication`, `OutboxApplication`

### Test / Verification Evidence

- [OrderFlowIntegrationTest](/src/test/java/io/github/gseobi/commerce/orchestration/order/controller/OrderFlowIntegrationTest.java)
- `OrderOutboxHappyPathIntegrationTest`
- [ModulithArchitectureTest#verifiesModularStructure](/src/test/java/io/github/gseobi/commerce/orchestration/architecture/ModulithArchitectureTest.java)

### Known Limitation / Boundary

- choreography나 Kafka consumer 기반 상태 전이를 구현한 프로젝트는 아닙니다.

### Review Summary

- 이 구조의 핵심은 후속 거래 흐름을 단순 호출 순서가 아니라 실패 분기와 복구 단위가 보이는 workflow로 만든 것입니다.

## Q2. 왜 상태 전이를 명시적으로 남겼나요?

### Technical Explanation

커머스 거래에서는 최종 성공 여부보다 어디까지 성공했고 어디에서 실패했는지가 중요합니다. 그래서 order status, orchestration step, payment status, notification event, outbox event, audit log를 남겨 복구와 검증의 기준으로 삼았습니다.

### Implementation Evidence

- `OrderStatus`
- `OrchestrationStep`
- `PaymentStatus`
- `NotificationEventStatus`
- `OutboxStatus`
- `AuditLog`

### Test / Verification Evidence

- `OrderFlowIntegrationTest`
- `NotificationRecoveryIntegrationTest`
- `OutboxRetryDeadLetterIntegrationTest`

### Known Limitation / Boundary

- 모든 상태 전이가 event sourcing 형태로 저장되는 것은 아닙니다.

### Review Summary

- 상태를 명시적으로 남겼기 때문에 장애 이후에도 추측이 아니라 데이터 기준으로 복구 지점을 잡을 수 있습니다.

## Q3. settlement 실패와 notification 실패를 왜 다르게 처리했나요?

### Technical Explanation

settlement 실패는 결제 승인 이후 거래 정합성에 영향을 주므로 payment cancel compensation으로 연결했습니다. 반면 notification 실패는 거래 자체를 되돌릴 실패가 아닐 수 있어 retry, manual intervention, ignore 정책으로 분리했습니다.

### Implementation Evidence

- `CommerceOrchestrationService#handleSettlementFailure`
- [PaymentService#cancelLatestApprovedPayment](/src/main/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentService.java)
- `CommerceOrchestrationService#handleNotificationFailure`
- `NotificationHandlingPolicy`

### Test / Verification Evidence

- `OrderFlowIntegrationTest#orchestrate_settlementFailure_recordsCompensation`
- `NotificationRecoveryIntegrationTest`

### Known Limitation / Boundary

- 실제 정산 provider 연동과 채널별 notification provider는 mock 중심입니다.

### Review Summary

- 같은 실패라는 이름으로 묶지 않고 거래 정합성 영향 여부에 따라 보상과 복구 정책을 분리했습니다.

## Q4. payment idempotency를 왜 `paymentRequestId` 기준으로 잡았나요?

### Technical Explanation

결제 approve는 client retry, orchestration replay, timeout 이후 재호출로 반복될 수 있습니다. 같은 주문의 같은 approve 시도는 deterministic `paymentRequestId`를 사용하고, 기존 payment가 있으면 provider approve를 다시 호출하지 않게 했습니다.

### Implementation Evidence

- `CommerceOrchestrationService#paymentRequestId`
- [PaymentService#approve](/src/main/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentService.java)
- `PaymentRepository#findByPaymentRequestId`

### Test / Verification Evidence

- [PaymentServiceTest#approve_idempotent_replay_reuses_existing_payment_without_provider_call](/src/test/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentServiceTest.java)
- [PaymentServiceTest#approve_idempotent_replay_reusesConfirmationRequiredPayment_without_provider_call](/src/test/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentServiceTest.java)

### Known Limitation / Boundary

- provider callback idempotency까지 구현한 것은 아닙니다. `providerTransactionId` 조회 포트는 확장 지점입니다.

### Review Summary

- approve replay 제어의 핵심은 provider를 다시 부르기 전에 내부 idempotency key로 이미 생성된 payment를 먼저 확인하는 것입니다.

## Q5. timeout unknown 상태를 왜 단순 실패로 처리하지 않았나요?

### Technical Explanation

외부 결제 approve timeout은 provider가 승인했는지, 거절했는지, 요청을 받지 못했는지 단정할 수 없습니다. 단순 실패로 처리하면 중복 승인이나 잘못된 취소 위험이 생깁니다.

현재 구현은 mock/dummy scenario에서 `PAYMENT_TIMEOUT_UNKNOWN`을 `CONFIRMATION_REQUIRED`로 저장하고, 정상 승인처럼 settlement를 시작하지 않습니다.

### Implementation Evidence

- `MockPaymentProviderClient`
- `PaymentStatus.CONFIRMATION_REQUIRED`
- [PaymentService#approve](/src/main/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentService.java)

### Test / Verification Evidence

- `MockPaymentProviderClientTest#approve_timeoutUnknownToken_returnsConfirmationRequired`
- [PaymentServiceTest#approve_timeoutUnknown_savesConfirmationRequired_andDoesNotTreatAsSuccess](/src/test/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentServiceTest.java)
- [OrderFlowIntegrationTest#orchestrate_paymentTimeoutUnknown_recordsConfirmationRequiredPayment](/src/test/java/io/github/gseobi/commerce/orchestration/order/controller/OrderFlowIntegrationTest.java)

### Known Limitation / Boundary

- external provider confirmation request는 구현하지 않았습니다.

### Review Summary

- timeout은 확정 실패가 아니라 확인이 필요한 상태로 남기는 것이 중복 결제 위험을 줄이는 데 더 안전합니다.

## Q6. 현재 timeout confirmation 구현 범위는 어디까지인가요?

### Technical Explanation

현재 구현은 mock/dummy provider 기반 unknown state 기록까지입니다. `CONFIRMATION_REQUIRED` payment를 저장하고, order는 정상 성공으로 진행하지 않습니다.

full WebClient confirmation flow, confirmation client 계약, admin confirmation API, OpenAPI path는 Future Scope입니다.

### Implementation Evidence

- `PaymentStatus.CONFIRMATION_REQUIRED`
- `MockPaymentProviderClient`
- `ExternalPaymentProviderClient`

### Test / Verification Evidence

- `PaymentServiceTest`
- `OrderFlowIntegrationTest#orchestrate_paymentTimeoutUnknown_recordsConfirmationRequiredPayment`
- `docs/flows/payment-timeout-confirmation-flow.md`

### Known Limitation / Boundary

- 실제 PG confirmation round-trip 검증은 없습니다.

### Review Summary

- 현재는 unknown state를 안전하게 기록하는 최소 구현이고, 실제 confirmation workflow는 설계 문서로 경계를 분리했습니다.

## Q7. Outbox를 왜 적용했나요?

### Technical Explanation

주문 이후 내부 상태 변경과 외부 이벤트 발행은 실패 시점이 다릅니다. outbox는 내부 거래 상태와 후속 publish를 분리해서 publish 실패를 retry/dead-letter 단위로 복구할 수 있게 만듭니다.

### Implementation Evidence

- `OutboxService`
- [OutboxPublisherService](/src/main/java/io/github/gseobi/commerce/orchestration/outbox/service/OutboxPublisherService.java)
- `OutboxEvent`
- `OutboxStatus`

### Test / Verification Evidence

- `OrderOutboxHappyPathIntegrationTest`
- [OutboxRetryDeadLetterIntegrationTest](/src/test/java/io/github/gseobi/commerce/orchestration/integration/OutboxRetryDeadLetterIntegrationTest.java)
- `OutboxPublisherServiceTest`

### Known Limitation / Boundary

- Kafka consumer가 event를 받아 상태 전이를 수행하는 구조는 구현하지 않았습니다.

### Review Summary

- Outbox는 주문 상태 변경과 이벤트 발행 실패를 분리해 운영자가 복구 가능한 지점을 만들어 줍니다.

## Q8. Outbox publisher adapter를 왜 분리했나요?

### Technical Explanation

outbox reliability 정책과 Kafka send 구현을 한 class에 두면 상태 전이 테스트가 KafkaTemplate 세부 구현에 묶입니다. 그래서 `OutboxPublisherService`는 상태 전이와 retry/dead-letter를 담당하고, `OutboxEventPublisher` 구현체가 실제 발행을 담당하게 했습니다.

### Implementation Evidence

- [OutboxPublisherService](/src/main/java/io/github/gseobi/commerce/orchestration/outbox/service/OutboxPublisherService.java)
- `OutboxEventPublisher`
- [KafkaOutboxEventPublisher](/src/main/java/io/github/gseobi/commerce/orchestration/infrastructure/kafka/KafkaOutboxEventPublisher.java)

### Test / Verification Evidence

- [OutboxPublisherServiceTest](/src/test/java/io/github/gseobi/commerce/orchestration/outbox/service/OutboxPublisherServiceTest.java)
- `OrderOutboxHappyPathIntegrationTest`

### Known Limitation / Boundary

- consumer ack 기반 publish 확정 모델은 아닙니다.

### Review Summary

- 상태 전이 정책을 adapter 밖으로 분리했기 때문에 Kafka 없이도 retry/dead-letter 정책을 빠르게 검증할 수 있습니다.

## Q9. `PROCESSING` claim은 어떤 중복 실행 문제를 줄이나요?

### Technical Explanation

scheduler, admin trigger, 여러 worker가 같은 notification retry event나 outbox event를 동시에 처리할 수 있습니다. 처리 전 DB conditional update로 `PROCESSING`을 선점하면 claim에 성공한 실행자만 실제 retry/publish를 수행합니다.

### Implementation Evidence

- `NotificationEventRepository#claimRetryScheduledEvent`
- `OutboxEventRepository#claimPublishableEvent`
- [NotificationRetryProcessor#processDueRetryEvents](/src/main/java/io/github/gseobi/commerce/orchestration/orchestration/service/NotificationRetryProcessor.java)
- [OutboxPublisherService#publishReadyEvents](/src/main/java/io/github/gseobi/commerce/orchestration/outbox/service/OutboxPublisherService.java)

### Test / Verification Evidence

- `NotificationRetryProcessorTest`
- [NotificationRetryProcessorIntegrationTest](/src/test/java/io/github/gseobi/commerce/orchestration/integration/NotificationRetryProcessorIntegrationTest.java)
- `OutboxPublisherServiceTest#publishReadyEvents_skipsAlreadyProcessingEventWhenClaimFails`

### Known Limitation / Boundary

- 오래 머무는 `PROCESSING` automatic recovery job은 아직 구현하지 않았습니다. SQL/runbook 점검만 있습니다.

### Review Summary

- `PROCESSING` claim은 분산 락 없이 DB 상태 전이로 중복 실행 가능성을 줄이는 장치입니다.

## Q10. admin recovery API를 전체 orchestration 재실행이 아니라 하위 처리 단위로 둔 이유는?

### Technical Explanation

장애 후 복구는 실패한 하위 단위만 재처리하는 편이 안전합니다. 전체 orchestration을 다시 실행하면 이미 성공한 payment, settlement, outbox append가 중복될 수 있습니다.

그래서 admin recovery는 notification event, outbox dead-letter 같은 복구 단위를 대상으로 둡니다.

### Implementation Evidence

- `AdminController`
- [AdminReprocessingService](/src/main/java/io/github/gseobi/commerce/orchestration/admin/service/AdminReprocessingService.java)
- `NotificationAdminApplication`
- `OutboxAdminApplication`

### Test / Verification Evidence

- `AdminReprocessingServiceTest`
- [AdminReprocessingIntegrationTest](/src/test/java/io/github/gseobi/commerce/orchestration/admin/controller/AdminReprocessingIntegrationTest.java)
- `AdminNotificationRetryControllerTest`

### Known Limitation / Boundary

- payment confirmation admin API는 구현하지 않았습니다.

### Review Summary

- 운영 복구는 전체 workflow 재실행이 아니라 실패한 처리 단위를 정확히 다시 움직이는 방식으로 설계했습니다.

## Q11. 운영 관측성에서 metric/log/runbook을 먼저 둔 이유는?

### Technical Explanation

현재 단계에서는 dashboard 자체보다 어떤 신호를 남길지와 그 신호를 어떻게 해석할지가 중요합니다. 그래서 recovery metric, structured log, audit detail, SQL/runbook을 먼저 정리했습니다.

### Implementation Evidence

- [CommerceRecoveryMetrics](/src/main/java/io/github/gseobi/commerce/orchestration/common/metrics/CommerceRecoveryMetrics.java)
- [OutboxPublisherService](/src/main/java/io/github/gseobi/commerce/orchestration/outbox/service/OutboxPublisherService.java)
- [NotificationRetryProcessor](/src/main/java/io/github/gseobi/commerce/orchestration/orchestration/service/NotificationRetryProcessor.java)
- [AdminReprocessingService](/src/main/java/io/github/gseobi/commerce/orchestration/admin/service/AdminReprocessingService.java)
- `docs/runbooks/admin-recovery-runbook.md`

### Test / Verification Evidence

- `CommerceRecoveryMetricsTest`
- `OutboxPublisherServiceTest`
- `NotificationRetryProcessorTest`
- `AdminReprocessingServiceTest`

### Known Limitation / Boundary

- Prometheus/Grafana dashboard와 alert rule은 구현하지 않았습니다.

### Review Summary

- 관측성은 화면보다 먼저 낮은 cardinality metric, 구조화 로그, 감사 기록, 복구 절차가 정해져야 합니다.

## Q12. Prometheus/Grafana dashboard가 없는데 observability를 어떻게 설명할 것인가?

### Technical Explanation

dashboard는 Future Scope로 분리하고, 현재는 관측 신호를 구현했다고 설명합니다. outbox publish, notification retry, admin recovery 결과 counter와 structured log, audit detail, runbook이 있습니다.

### Implementation Evidence

- [CommerceRecoveryMetrics](/src/main/java/io/github/gseobi/commerce/orchestration/common/metrics/CommerceRecoveryMetrics.java)
- `docs/operations/observability-alert-candidates.md`
- `docs/runbooks/admin-recovery-runbook.md`

### Test / Verification Evidence

- [CommerceRecoveryMetricsTest](/src/test/java/io/github/gseobi/commerce/orchestration/common/metrics/CommerceRecoveryMetricsTest.java)
- `docs/verification-matrix.md`
- `docs/test-report.md`

### Known Limitation / Boundary

- dashboard와 alert rule이 구현된 것처럼 말하면 안 됩니다.

### Review Summary

- 현재 구현은 dashboard 완성이 아니라 dashboard로 올릴 수 있는 bounded signal을 먼저 만든 상태입니다.

## Q13. Spring Modulith boundary 검증은 무엇을 증명하나요?

### Technical Explanation

Spring Modulith 검증은 module 간 의존 방향과 공개 API 경계를 지키는지 확인합니다. orchestration이 여러 domain을 조합하더라도 내부 repository를 마구 침범하지 않게 하는 기준입니다.

### Implementation Evidence

- `package-info.java`
- `*.api` named interfaces
- [ModulithArchitectureTest](/src/test/java/io/github/gseobi/commerce/orchestration/architecture/ModulithArchitectureTest.java)

### Test / Verification Evidence

- [ModulithArchitectureTest#verifiesModularStructure](/src/test/java/io/github/gseobi/commerce/orchestration/architecture/ModulithArchitectureTest.java)

### Known Limitation / Boundary

- Modulith 검증은 비즈니스 성공을 증명하지 않고 module dependency rule을 검증합니다.

### Review Summary

- Modulith 검증은 orchestration이 커져도 domain boundary를 코드 레벨에서 계속 지키는지 확인하는 안전장치입니다.

## Q14. Testcontainers integration test는 무엇을 증명하나요?

### Technical Explanation

Testcontainers integration test는 PostgreSQL, Kafka, Flyway migration, JPA validate, outbox publish 같은 인프라 경계가 실제 환경에 가깝게 동작하는지 확인합니다. H2 단위 테스트만으로는 잡기 어려운 schema, transaction, Kafka publish 흐름을 검증합니다.

### Implementation Evidence

- `TestcontainersIntegrationSupport`
- Flyway migration files
- Kafka outbox integration flow

### Test / Verification Evidence

- `OrderOutboxHappyPathIntegrationTest`
- `OutboxRetryDeadLetterIntegrationTest`
- [NotificationRetryProcessorIntegrationTest](/src/test/java/io/github/gseobi/commerce/orchestration/integration/NotificationRetryProcessorIntegrationTest.java)
- `IntegrationSchemaValidationSmokeTest`

### Known Limitation / Boundary

- 실제 외부 payment provider network round-trip을 검증하는 것은 아닙니다.

### Review Summary

- Testcontainers는 이 프로젝트의 상태 전이와 outbox 흐름이 실제 DB/Kafka 경계에서도 깨지지 않는지 확인하는 기준선입니다.

## Q15. 이 프로젝트가 실제 production commerce system과 다른 점은 무엇인가요?

### Technical Explanation

이 프로젝트는 production commerce의 모든 기능을 구현한 것이 아니라, 주문 이후 reliability 문제를 포트폴리오 범위에서 설명하는 backend입니다. 실제 운영이라면 provider callback, full timeout confirmation, provider별 보안 검증, dashboard/alert, stale processing recovery, consumer 기반 후속 처리, real user store가 더 필요합니다.

### Implementation Evidence

- `docs/verification-matrix.md`
- `docs/verification/claim-audit.md`
- `docs/flows/payment-timeout-confirmation-flow.md`
- `docs/flows/provider-callback-flow-review.md`

### Test / Verification Evidence

- `docs/test-report.md`
- `./gradlew test`
- `./gradlew integrationTest`

### Known Limitation / Boundary

- provider callback API, full WebClient confirmation API, Prometheus/Grafana dashboard, Kafka consumer 기반 상태 전이는 현재 구현 범위가 아닙니다.

### Review Summary

- 이 프로젝트는 production 전체 복제가 아니라 commerce 후속 거래 흐름의 상태, 실패, 복구, 검증 근거를 선명하게 보여주는 데 집중했습니다.
