# Claim Audit

This document maps major portfolio claims to implementation, tests, and documentation evidence.

Status values:

- `Verified`: implementation exists and direct test or validation evidence supports the claim.
- `Implemented`: implementation exists, but this audit does not claim direct test validation for the full claim.
- `Partial`: only part of the claim is supported by current implementation.
- `Future Scope`: intentionally left for later work.
- `Not Implemented`: not present in current implementation.

## Evidence Ladder

Evidence Ladder는 README/docs claim이 코드보다 앞서가지 않도록 아래 순서로 점검합니다.

1. Claim: README/docs에서 말하는 주장
2. Reason: 이 선택을 한 이유
3. Implementation Evidence: 실제 코드 위치
4. Test Evidence: 테스트 위치
5. Boundary: 현재 구현하지 않은 범위

### Orchestration 중심 흐름 제어

1. Claim: 주문 이후 payment, settlement, notification, outbox 흐름은 orchestration service 중심으로 제어합니다.
2. Reason: 각 단계의 실패 의미와 복구 정책이 달라서 흐름 제어와 failure branch를 한 위치에서 읽을 수 있어야 합니다.
3. Implementation Evidence: `CommerceOrchestrationService`, `OrderController#orchestrate`, `PaymentApplication`, `SettlementApplication`, `NotificationApplication`, `OutboxApplication`
4. Test Evidence: `OrderFlowIntegrationTest`, `OrderOutboxHappyPathIntegrationTest`, `ModulithArchitectureTest#verifiesModularStructure`
5. Boundary: Kafka consumer 기반 choreography나 event-sourced workflow engine은 구현하지 않았습니다.

### settlement compensation과 notification recovery 분리

1. Claim: settlement 실패와 notification 실패는 같은 rollback 정책으로 처리하지 않습니다.
2. Reason: settlement 실패는 결제 승인 이후 거래 정합성 문제이고, notification 실패는 retry/manual/ignore로 복구 가능한 후속 처리 실패일 수 있습니다.
3. Implementation Evidence: `CommerceOrchestrationService#handleSettlementFailure`, `PaymentService#cancelLatestApprovedPayment`, `CommerceOrchestrationService#handleNotificationFailure`, `NotificationHandlingPolicy`
4. Test Evidence: `OrderFlowIntegrationTest#orchestrate_settlementFailure_recordsCompensation`, `NotificationRecoveryIntegrationTest`
5. Boundary: 실제 settlement provider와 notification channel별 운영 정책은 mock 중심이며 외부 provider round-trip을 검증하지 않습니다.

### paymentRequestId 기반 idempotency

1. Claim: 같은 `paymentRequestId` approve replay는 provider를 다시 호출하지 않습니다.
2. Reason: orchestration replay나 timeout 이후 재호출이 중복 결제 승인으로 이어지는 것을 막아야 합니다.
3. Implementation Evidence: `CommerceOrchestrationService#paymentRequestId`, `PaymentService#approve`, `PaymentRepository#findByPaymentRequestId`
4. Test Evidence: `PaymentServiceTest#approve_idempotent_replay_reuses_existing_payment_without_provider_call`, `PaymentServiceTest#approve_idempotent_replay_reusesConfirmationRequiredPayment_without_provider_call`
5. Boundary: provider callback idempotency endpoint/table은 구현하지 않았습니다.

### mock/dummy timeout unknown state 기록

1. Claim: mock/dummy timeout unknown scenario는 `CONFIRMATION_REQUIRED` payment로 기록합니다.
2. Reason: timeout은 provider 승인 여부를 확정할 수 없으므로 단순 성공 또는 단순 실패로 과장하지 않아야 합니다.
3. Implementation Evidence: `MockPaymentProviderClient`, `PaymentStatus.CONFIRMATION_REQUIRED`, `PaymentService#approve`
4. Test Evidence: `MockPaymentProviderClientTest#approve_timeoutUnknownToken_returnsConfirmationRequired`, `PaymentServiceTest#approve_timeoutUnknown_savesConfirmationRequired_andDoesNotTreatAsSuccess`, `OrderFlowIntegrationTest#orchestrate_paymentTimeoutUnknown_recordsConfirmationRequiredPayment`
5. Boundary: external provider confirmation request, admin confirmation API, confirmation OpenAPI path는 구현하지 않았습니다.

### Outbox publish claim + publisher adapter 분리

1. Claim: Outbox publish는 `PROCESSING` claim 이후 publisher adapter를 통해 발행합니다.
2. Reason: 중복 publish 가능성을 줄이고, retry/dead-letter 상태 전이 정책과 Kafka send 구현을 분리해야 합니다.
3. Implementation Evidence: `OutboxEventRepository#claimPublishableEvent`, `OutboxPublisherService#publishReadyEvents`, `OutboxEventPublisher`, `KafkaOutboxEventPublisher`
4. Test Evidence: `OutboxPublisherServiceTest`, `OrderOutboxHappyPathIntegrationTest`, `OutboxRetryDeadLetterIntegrationTest`
5. Boundary: Kafka consumer 기반 상태 전이와 stale `PROCESSING` automatic recovery job은 구현하지 않았습니다.

### metric/log/runbook 기반 recovery observability

1. Claim: recovery observability는 metric, structured log, audit, runbook 기반으로 구현되어 있습니다.
2. Reason: 운영자는 retry/dead-letter 증가, skipped claim, admin recovery 결과를 낮은 cardinality 신호로 확인해야 합니다.
3. Implementation Evidence: `CommerceRecoveryMetrics`, `OutboxPublisherService`, `NotificationRetryProcessor`, `AdminReprocessingService`, `docs/runbooks/admin-recovery-runbook.md`
4. Test Evidence: `CommerceRecoveryMetricsTest`, `OutboxPublisherServiceTest`, `NotificationRetryProcessorTest`, `AdminReprocessingServiceTest`
5. Boundary: Prometheus/Grafana dashboard, alert rule, distributed tracing backend은 구현하지 않았습니다.

## Verified Claims

### Spring Modulith boundaries are preserved

- Status: Verified
- Evidence: `package-info.java` module rules, `*.api` named interfaces
- Evidence: `ModulithArchitectureTest#verifiesModularStructure`
- Evidence: `docs/architecture/README.md`
- Notes: `ApplicationModules.verify()` is part of the test suite.

### Order orchestration handles order-payment-settlement-notification flow

- Status: Verified
- Evidence: `CommerceOrchestrationService`, `OrderController`
- Evidence: `OrderFlowIntegrationTest`, `OrderOutboxHappyPathIntegrationTest`
- Evidence: `docs/flows/README.md`
- Notes: The flow records order state, orchestration steps, notification events, and outbox events.

### Payment approval idempotency is implemented

- Status: Verified
- Evidence: `PaymentService#approve`, `PaymentRepository#findByPaymentRequestId`
- Evidence: `PaymentServiceTest#approve_idempotent_replay_reuses_existing_payment_without_provider_call`
- Evidence: `PaymentServiceTest#approve_idempotent_replay_reusesConfirmationRequiredPayment_without_provider_call`
- Notes: Replayed `paymentRequestId` does not call the provider again.

### Mock provider timeout unknown state is implemented

- Status: Verified
- Evidence: `MockPaymentProviderClient`, `PaymentStatus.CONFIRMATION_REQUIRED`
- Evidence: `PaymentServiceTest#approve_timeoutUnknown_savesConfirmationRequired_andDoesNotTreatAsSuccess`
- Evidence: `MockPaymentProviderClientTest#approve_timeoutUnknownToken_returnsConfirmationRequired`
- Evidence: `OrderFlowIntegrationTest#orchestrate_paymentTimeoutUnknown_recordsConfirmationRequiredPayment`
- Notes: This is a mock/dummy scenario, not real payment provider contract integration.
- Notes: The order is not treated as successful; settlement does not start.

### Settlement failure compensation is implemented

- Status: Verified
- Evidence: `CommerceOrchestrationService#handleSettlementFailure`
- Evidence: `PaymentService#cancelLatestApprovedPayment`
- Evidence: `OrderFlowIntegrationTest#orchestrate_settlementFailure_recordsCompensation`
- Notes: Settlement failure triggers payment cancel compensation and closes the order as cancelled.

### Notification failure policy branches into retry/manual/ignore behavior

- Status: Verified
- Evidence: `NotificationService`, `CommerceOrchestrationService#handleNotificationFailure`
- Evidence: `OrderFlowIntegrationTest`, `NotificationRecoveryIntegrationTest`
- Evidence: `docs/design-notes.md`
- Notes: Policies are represented as retry scheduling, manual intervention, or ignore behavior.

### Notification retry processor supports due event batch processing

- Status: Verified
- Evidence: `NotificationRetryProcessor#processDueRetryEvents`, `NotificationRetryOperations`
- Evidence: `NotificationRetryProcessorTest`, `NotificationRetryProcessorIntegrationTest`
- Notes: Due events are claimed and processed; future events are skipped.

### Admin notification retry supports optional operator/reason audit context

- Status: Verified
- Evidence: `AdminController#retryNotification`, `AdminRecoveryRequest`
- Evidence: `AdminRecoveryContext`, `AdminReprocessingService`
- Evidence: `AdminReprocessingServiceTest`, `AdminReprocessingIntegrationTest`
- Notes: Audit detail includes bounded `operatorId` and `reason` when provided.

### Admin notification ignore supports optional operator/reason audit context

- Status: Verified
- Evidence: `AdminController#ignoreNotification`, `AdminRecoveryContext`
- Evidence: `AdminReprocessingServiceTest`, `NotificationRecoveryIntegrationTest`
- Evidence: `docs/runbooks/admin-recovery-runbook.md`
- Notes: Optional context is recorded in audit detail; no-body default context remains valid.

### Admin outbox dead-letter retry supports optional operator/reason audit context

- Status: Verified
- Evidence: `AdminController#retryOutboxDeadLetter`, `AdminRecoveryContext`
- Evidence: `OutboxAdminApplication`, `AdminReprocessingServiceTest`
- Evidence: `AdminReprocessingIntegrationTest`
- Notes: `DEAD_LETTER` outbox events can be retried with optional audit context.

### Existing no-body admin recovery calls remain backward compatible

- Status: Verified
- Evidence: `@RequestBody(required = false)` in `AdminController`
- Evidence: `AdminRecoveryContext.defaults()`
- Evidence: `AdminReprocessingServiceTest`, `AdminReprocessingIntegrationTest`
- Notes: Missing body uses `operatorId=unknown` and `reason=not-provided`.

### OpenAPI documents implemented APIs only

- Status: Verified
- Evidence: `docs/openapi/openapi.yaml`, controller mapping grep
- Evidence: `docs/openapi/README.md`, `docs/verification-matrix.md`
- Notes: Paths match implemented controllers and exclude future-scope APIs.

### ApiDog import was manually verified

- Status: Verified
- Evidence: `docs/openapi/README.md`, `docs/test-report.md`
- Evidence: developer manual import confirmation after OpenAPI partition
- Notes: This is manual evidence, not CI automation.

### Metrics and structured logs exist for operation/debugging signals

- Status: Verified
- Evidence: `CommerceRecoveryMetrics`, `OutboxPublisherService`
- Evidence: `NotificationRetryProcessor`, `AdminReprocessingService`
- Evidence: `CommerceRecoveryMetricsTest`, `OutboxPublisherServiceTest`
- Evidence: `NotificationRetryProcessorTest`
- Evidence: `docs/operations/observability-alert-candidates.md`
- Notes: Metrics avoid high-cardinality business identifiers as tags.

## Implemented Claims

### Docker Compose local environment is preserved

- Status: Implemented
- Evidence: `compose.yaml`, `.env.example`, `application-local.yaml`
- Evidence: `README.md`, `docs/troubleshooting.md`
- Notes: Provides PostgreSQL, Kafka, Kafka UI, and app connection settings.

### CI test workflow is preserved

- Status: Implemented
- Evidence: `.github/workflows/ci.yml`, `docs/test-report.md`, `docs/verification-matrix.md`
- Notes: Workflow keeps compile/test and integration test jobs.
- Notes: This audit does not claim a fresh remote CI run.

## Future Scope Claims

### Alert candidates are documented but not implemented as alert rules

- Status: Future Scope
- Evidence: `docs/operations/observability-alert-candidates.md`
- Notes: The document maps current signals to candidate conditions.
- Notes: No alert rule configuration exists.

### Provider callback flow is reviewed but not implemented

- Status: Future Scope
- Evidence: `PaymentProviderClient`, `ExternalPaymentProviderClient`
- Evidence: `docs/flows/provider-callback-flow-review.md`
- Evidence: `README.md`, `docs/design-notes.md`
- Notes: `providerTransactionId` is an extension point.
- Notes: There is no production code, OpenAPI path, or automated test yet.

### Full WebClient timeout confirmation flow is partially implemented

- Status: Partial
- Evidence: `ExternalPaymentProviderClient`
- Evidence: `MockPaymentProviderClient`, `PaymentStatus.CONFIRMATION_REQUIRED`
- Evidence: `docs/flows/payment-timeout-confirmation-flow.md`
- Evidence: `docs/implementation-reviews/webclient-timeout-confirmation-implementation-review.md`
- Evidence: `docs/design-notes.md`, `docs/test-report.md`
- Notes: Mock/dummy timeout unknown state recording is implemented and tested.
- Notes: Real external provider confirmation request, admin confirmation API, OpenAPI path, and callback flow are not implemented.

## Not Implemented Claims

### Prometheus/Grafana dashboard is not implemented

- Status: Not Implemented
- Evidence: `docs/operations/observability-alert-candidates.md`
- Evidence: `docs/test-report.md`, `docs/runbooks/admin-recovery-runbook.md`
- Evidence: `docs/openapi/openapi.yaml` scope notes
- Notes: Micrometer metrics exist, but dashboards and alert rules are not implemented.

### Distributed tracing backend is not implemented

- Status: Not Implemented
- Evidence: `RequestTraceFilter`, `TraceIdHolder`
- Evidence: `docs/operations/observability-alert-candidates.md`
- Notes: Request trace id correlation exists, but there is no distributed tracing backend integration.

### Kafka consumer-based state transition is not implemented

- Status: Not Implemented
- Evidence: `OutboxPublisherService`, `KafkaOutboxEventPublisher`
- Evidence: `docs/implementation-review-notes.md`, `docs/troubleshooting.md`
- Notes: Current outbox state transition is based on publisher send result.

### Stale PROCESSING automatic recovery job is not implemented

- Status: Not Implemented
- Evidence: `docs/runbooks/admin-recovery-runbook.md`, `docs/sql/README.md`
- Notes: Current support is SQL/runbook inspection, not automatic recovery.

### Refresh token/key rotation/real user store is not implemented

- Status: Not Implemented
- Evidence: `AuthController`, `JwtTokenProvider`
- Evidence: `README.md`, `docs/openapi/openapi.yaml`
- Notes: `/api/auth/token` is demo-only access token issuance.
