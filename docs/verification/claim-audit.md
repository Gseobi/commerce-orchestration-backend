# Claim Audit

This document maps major portfolio claims to implementation, tests, and documentation evidence.

Status values:

- `Verified`: implementation exists and direct test or validation evidence supports the claim.
- `Implemented`: implementation exists, but this audit does not claim direct test validation for the full claim.
- `Partial`: only part of the claim is supported by current implementation.
- `Future Scope`: intentionally left for later work.
- `Not Implemented`: not present in current implementation.

| Claim | Evidence | Status | Notes |
|---|---|---|---|
| Spring Modulith boundaries are preserved | `package-info.java` module rules, `*.api` named interfaces, `ModulithArchitectureTest#verifiesModularStructure`, `docs/architecture/README.md` | Verified | `ApplicationModules.verify()` is part of the test suite. |
| Order orchestration handles order-payment-settlement-notification flow | `CommerceOrchestrationService`, `OrderController`, `OrderFlowIntegrationTest`, `OrderOutboxHappyPathIntegrationTest`, `docs/flows/README.md` | Verified | The flow records order state, orchestration steps, notification events, and outbox events. |
| Payment approval idempotency is implemented | `PaymentService#approve`, `PaymentRepository#findByPaymentRequestId`, `PaymentServiceTest#approve_idempotent_replay_reuses_existing_payment_without_provider_call` | Verified | Replayed `paymentRequestId` does not call the provider again. |
| Settlement failure compensation is implemented | `CommerceOrchestrationService#handleSettlementFailure`, `PaymentService#cancelLatestApprovedPayment`, `OrderFlowIntegrationTest#orchestrate_settlementFailure_recordsCompensation` | Verified | Settlement failure triggers payment cancel compensation and closes the order as cancelled. |
| Notification failure policy branches into retry/manual/ignore behavior | `NotificationService`, `CommerceOrchestrationService#handleNotificationFailure`, `OrderFlowIntegrationTest`, `NotificationRecoveryIntegrationTest`, `docs/design-notes.md` | Verified | Policies are represented as retry scheduling, manual intervention, or ignore behavior. |
| Notification retry processor supports due event batch processing | `NotificationRetryProcessor#processDueRetryEvents`, `NotificationRetryOperations`, `NotificationRetryProcessorTest`, `NotificationRetryProcessorIntegrationTest` | Verified | Due events are claimed and processed; future events are skipped. |
| Admin notification retry supports optional operator/reason audit context | `AdminController#retryNotification`, `AdminRecoveryRequest`, `AdminRecoveryContext`, `AdminReprocessingService`, `AdminReprocessingServiceTest`, `AdminReprocessingIntegrationTest` | Verified | Audit detail includes bounded `operatorId` and `reason` when provided. |
| Admin notification ignore supports optional operator/reason audit context | `AdminController#ignoreNotification`, `AdminRecoveryContext`, `AdminReprocessingServiceTest`, `NotificationRecoveryIntegrationTest`, `docs/runbooks/admin-recovery-runbook.md` | Verified | Optional context is recorded in audit detail; no-body default context remains valid. |
| Admin outbox dead-letter retry supports optional operator/reason audit context | `AdminController#retryOutboxDeadLetter`, `AdminRecoveryContext`, `OutboxAdminApplication`, `AdminReprocessingServiceTest`, `AdminReprocessingIntegrationTest` | Verified | `DEAD_LETTER` outbox events can be retried with optional audit context. |
| Existing no-body admin recovery calls remain backward compatible | `@RequestBody(required = false)` in `AdminController`, `AdminRecoveryContext.defaults()`, `AdminReprocessingServiceTest`, `AdminReprocessingIntegrationTest` | Verified | Missing body uses `operatorId=unknown` and `reason=not-provided`. |
| OpenAPI documents implemented APIs only | `docs/openapi/openapi.yaml`, controller mapping grep, `docs/openapi/README.md`, `docs/verification-matrix.md` | Verified | Paths match implemented controllers and exclude future-scope APIs. |
| ApiDog import was manually verified | `docs/openapi/README.md`, `docs/test-report.md`, developer manual import confirmation after OpenAPI partition | Verified | This is manual evidence, not CI automation. |
| Metrics and structured logs exist for operation/debugging signals | `CommerceRecoveryMetrics`, `OutboxPublisherService`, `NotificationRetryProcessor`, `AdminReprocessingService`, `CommerceRecoveryMetricsTest`, `OutboxPublisherServiceTest`, `NotificationRetryProcessorTest` | Verified | Metrics avoid high-cardinality business identifiers as tags. |
| Prometheus/Grafana dashboard is not implemented | `docs/test-report.md`, `docs/runbooks/admin-recovery-runbook.md`, `docs/openapi/openapi.yaml` scope notes | Not Implemented | Micrometer metrics exist, but dashboards and alert rules are not implemented. |
| Kafka consumer-based state transition is not implemented | `OutboxPublisherService`, `KafkaOutboxEventPublisher`, `docs/implementation-review-notes.md`, `docs/troubleshooting.md` | Not Implemented | Current outbox state transition is based on publisher send result. |
| Provider callback flow is not implemented | `PaymentProviderClient`, `ExternalPaymentProviderClient`, `README.md`, `docs/design-notes.md` | Not Implemented | `providerTransactionId` is an extension point; callback API does not exist. |
| WebClient timeout confirmation flow is not implemented | `ExternalPaymentProviderClient`, `docs/design-notes.md`, `docs/test-report.md` | Future Scope | Timeout settings exist, but post-timeout confirmation flow is not implemented. |
| Stale PROCESSING automatic recovery job is not implemented | `docs/runbooks/admin-recovery-runbook.md`, `docs/sql/README.md` | Not Implemented | Current support is SQL/runbook inspection, not automatic recovery. |
| Refresh token/key rotation/real user store is not implemented | `AuthController`, `JwtTokenProvider`, `README.md`, `docs/openapi/openapi.yaml` | Not Implemented | `/api/auth/token` is demo-only access token issuance. |
| Docker Compose local environment is preserved | `compose.yaml`, `.env.example`, `application-local.yaml`, `README.md`, `docs/troubleshooting.md` | Implemented | Provides PostgreSQL, Kafka, Kafka UI, and app connection settings. |
| CI test workflow is preserved | `.github/workflows/ci.yml`, `docs/test-report.md`, `docs/verification-matrix.md` | Implemented | Workflow keeps compile/test and integration test jobs; this audit does not claim a fresh remote CI run. |
