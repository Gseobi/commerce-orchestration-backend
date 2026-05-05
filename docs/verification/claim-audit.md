# Claim Audit

This document maps major portfolio claims to implementation, tests, and documentation evidence.

Status values:

- `Verified`: implementation exists and direct test or validation evidence supports the claim.
- `Implemented`: implementation exists, but this audit does not claim direct test validation for the full claim.
- `Partial`: only part of the claim is supported by current implementation.
- `Future Scope`: intentionally left for later work.
- `Not Implemented`: not present in current implementation.

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
