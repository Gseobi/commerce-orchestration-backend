# Verification Matrix

이 문서는 README/docs의 구현 주장과 실제 코드/테스트 위치를 대조하기 위한 문서입니다.
긴 Markdown table 대신 capability별 block으로 유지해 raw diff에서 검토하기 쉽게 정리합니다.

Status 값은 다음 의미로 사용합니다.

- `Verified`: 구현이 있고 현재 테스트 커버리지로 확인합니다.
- `Implemented`: 구현은 있으나 이 문서에서 별도 end-to-end 검증으로 주장하지 않습니다.
- `Future Scope`: 후속 구현 또는 문서화 대상으로 남긴 범위입니다.
- `Mismatch Fixed`: 문서 감사에서 기존 문구와 실제 구현 차이를 정리한 항목입니다.
- `Not Implemented`: 현재 코드에 구현되어 있지 않습니다.

## Order / Auth APIs

### Order create API

- Status: Verified
- Implementation: `OrderController#createOrder`, `OrderFacadeService`, `OrderService`
- Tests: `OrderFlowIntegrationTest#createOrder_and_flow_endpoint_work_withJwt`
- Docs: `README.md`, `docs/flows/README.md`
- Notes: `POST /api/orders`는 JWT 인증이 필요합니다.

### Order detail API

- Status: Verified
- Implementation: `OrderController#getOrder`, `OrderFacadeService#getOrderDetail`
- Tests: `OrderFlowIntegrationTest#createOrder_and_flow_endpoint_work_withJwt`
- Tests: `AdminReprocessingIntegrationTest`
- Docs: `README.md`, `docs/flows/README.md`
- Notes: `GET /api/orders/{orderId}` 응답은 payment/settlement/notification 상태 요약을 포함합니다.

### Order orchestration flow

- Status: Verified
- Implementation: `OrderController#orchestrate`, `CommerceOrchestrationService`
- Tests: `OrderFlowIntegrationTest#orchestrate_happyPath_and_duplicateCall_isIdempotent`
- Tests: `OrderOutboxHappyPathIntegrationTest`
- Docs: `README.md`, `docs/architecture/README.md`, `docs/flows/README.md`
- Notes: `POST /api/orders/{orderId}/orchestrate`가 payment, settlement, notification, outbox append를 조율합니다.

### Demo JWT token issuance

- Status: Verified
- Implementation: `AuthController#issueToken`, `JwtTokenProvider`
- Tests: `OrderFlowIntegrationTest`, `AdminReprocessingIntegrationTest`
- Tests: `NotificationRetryProcessorIntegrationTest` token setup
- Docs: `README.md`, `docs/troubleshooting.md`
- Notes: 데모용 access token 발급이며 refresh token/user store는 없습니다.

## Payment / Settlement

### Payment approval idempotency

- Status: Verified
- Implementation: `PaymentService#approve`, `PaymentRepository#findByPaymentRequestId`
- Tests: `PaymentServiceTest#approve_idempotent_replay_reuses_existing_payment_without_provider_call`
- Docs: `README.md`, `docs/architecture/README.md`, `docs/design-notes.md`
- Notes: 같은 `paymentRequestId` replay는 provider approve를 다시 호출하지 않습니다.

### Settlement failure compensation

- Status: Verified
- Implementation: `CommerceOrchestrationService#handleSettlementFailure`
- Implementation: `PaymentService#cancelLatestApprovedPayment`
- Tests: `OrderFlowIntegrationTest#orchestrate_settlementFailure_recordsCompensation`
- Docs: `README.md`, `docs/flows/README.md`, `docs/design-notes.md`
- Notes: settlement 실패 시 payment cancel compensation 후 주문을 `CANCELLED`로 닫습니다.

## Notification / Recovery

### Notification failure policy

- Status: Verified
- Implementation: `NotificationService`, `CommerceOrchestrationService#handleNotificationFailure`
- Tests: `OrderFlowIntegrationTest`, `NotificationRecoveryIntegrationTest`
- Docs: `README.md`, `docs/flows/README.md`, `docs/design-notes.md`
- Notes: `AUTO_RETRY`, `MANUAL_INTERVENTION`, `IGNORE` 분기를 구분합니다.

### Notification retry due processor

- Status: Verified
- Implementation: `NotificationRetryProcessor#processDueRetryEvents`
- Implementation: `NotificationRetryOperations`
- Tests: `NotificationRetryProcessorTest`, `NotificationRetryProcessorIntegrationTest`
- Docs: `README.md`, `docs/design-notes.md`, `docs/troubleshooting.md`
- Notes: due `RETRY_SCHEDULED` event만 claim 후 처리합니다.

### Admin API security

- Status: Verified
- Implementation: `SecurityConfig` `/api/admin/**.hasRole("ADMIN")`
- Tests: `AdminReprocessingIntegrationTest#adminRetryOutboxDeadLetter_requiresAdminRole_andRepublishesEvent`
- Tests: `AdminNotificationRetryControllerTest`
- Docs: `README.md`, `docs/runbooks/admin-recovery-runbook.md`
- Notes: 일반 USER의 admin outbox retry 접근은 `403`으로 검증됩니다.

### Admin notification retry

- Status: Verified
- Implementation: `AdminController#retryNotification`, `AdminReprocessingService#retryNotification`
- Implementation: `AdminRecoveryContext`
- Tests: `AdminReprocessingIntegrationTest#adminRetryNotification_completesFailedOrder`
- Tests: `NotificationRecoveryIntegrationTest`, `AdminReprocessingServiceTest`
- Docs: `README.md`, `docs/runbooks/admin-recovery-runbook.md`
- Notes: 실패 notification 단위만 `SENT`로 복구하고 order를 `COMPLETED`로 복구합니다.
- Notes: Optional `operatorId`/`reason`은 audit detail에 기록됩니다.

### Admin notification ignore

- Status: Verified
- Implementation: `AdminController#ignoreNotification`, `AdminReprocessingService#ignoreNotification`
- Implementation: `AdminRecoveryContext`
- Tests: `NotificationRecoveryIntegrationTest#notificationManualPolicy_requiresManualInterventionAndAllowsAdminIgnore`
- Tests: `AdminReprocessingServiceTest`
- Docs: `README.md`, `docs/runbooks/admin-recovery-runbook.md`
- Notes: manual intervention 대상 notification을 `IGNORED`로 정리합니다.
- Notes: No-body 호출은 계속 지원됩니다.

### Admin notification retry-due HTTP trigger

- Status: Verified
- Implementation: `AdminNotificationRetryController#retryDueNotificationEvents`
- Implementation: `NotificationRetrySchedulerTrigger`
- Tests: `AdminNotificationRetryControllerTest`
- Tests: `NotificationRetryProcessorIntegrationTest#retryDueNotificationEvents_returnsBatchResultSummary`
- Docs: `README.md`, `docs/design-notes.md`, `docs/runbooks/admin-recovery-runbook.md`
- Docs: `docs/test-report.md`
- Notes: `POST /api/admin/notification-events/retry-due`는 `ApiResponse` envelope 없이 batch result를 직접 반환합니다.

### Admin outbox dead-letter retry

- Status: Verified
- Implementation: `AdminController#retryOutboxDeadLetter`
- Implementation: `AdminReprocessingService#retryOutboxDeadLetter`
- Implementation: `OutboxAdminApplication`, `AdminRecoveryContext`
- Tests: `AdminReprocessingIntegrationTest`, `OutboxRetryDeadLetterIntegrationTest`
- Tests: `AdminReprocessingServiceTest`
- Docs: `README.md`, `docs/runbooks/admin-recovery-runbook.md`, `docs/sql/outbox-operations.sql`
- Notes: `DEAD_LETTER` outbox event만 admin retry 대상입니다.
- Notes: Optional `operatorId`/`reason`은 audit detail에 기록됩니다.

## Outbox / Reliability

### Outbox publish adapter

- Status: Verified
- Implementation: `OutboxEventPublisher`, `KafkaOutboxEventPublisher`, `OutboxPublisherService`
- Tests: `OutboxPublisherServiceTest`, `OrderOutboxHappyPathIntegrationTest`
- Docs: `README.md`, `docs/architecture/README.md`, `docs/flows/README.md`
- Notes: 상태 전이는 service가, Kafka send는 infrastructure adapter가 담당합니다.

### Outbox processing claim

- Status: Verified
- Implementation: `OutboxEventRepository#claimPublishableEvent`
- Implementation: `OutboxPublisherService#publishReadyEvents`
- Tests: `OutboxPublisherServiceTest#publishReadyEvents_skipsAlreadyProcessingEventWhenClaimFails`
- Docs: `README.md`, `docs/architecture/README.md`, `docs/flows/README.md`
- Notes: `READY` / `RETRY_WAIT` event를 `PROCESSING`으로 선점한 실행자만 publish합니다.

## Observability

### Metrics and structured logging

- Status: Verified
- Implementation: `CommerceRecoveryMetrics`, `OutboxPublisherService`
- Implementation: `NotificationRetryProcessor`, `AdminReprocessingService`
- Tests: `CommerceRecoveryMetricsTest`, `OutboxPublisherServiceTest`
- Tests: `NotificationRetryProcessorTest`, `AdminReprocessingServiceTest`
- Docs: `README.md`, `docs/architecture/README.md`
- Docs: `docs/runbooks/admin-recovery-runbook.md`
- Docs: `docs/operations/observability-alert-candidates.md`
- Notes: Custom metric과 structured log event만 구현되어 있으며 dashboard/alert rule은 없습니다.

### Observability alert candidates

- Status: Future Scope
- Implementation: 없음
- Tests: 없음
- Docs: `docs/operations/observability-alert-candidates.md`
- Notes: 현재 metric/log/audit 신호를 기준으로 alert 후보를 문서화했습니다.
- Notes: alert rule configuration은 구현하지 않았습니다.

### Prometheus/Grafana dashboard

- Status: Not Implemented
- Implementation: 없음
- Tests: 없음
- Docs: `docs/operations/observability-alert-candidates.md`, `docs/runbooks/admin-recovery-runbook.md`
- Docs: `docs/diagrams/README.md`, `docs/test-report.md`
- Notes: Micrometer metric은 있으나 dashboard와 alert rule은 없습니다.

### Distributed tracing backend

- Status: Not Implemented
- Implementation: 없음
- Tests: 없음
- Docs: `docs/operations/observability-alert-candidates.md`
- Notes: `X-Trace-Id` request correlation은 있으나 distributed tracing backend integration은 없습니다.

## Architecture / Platform

### Spring Modulith boundary verification

- Status: Verified
- Implementation: `package-info.java` module rules, `*.api` named interfaces
- Tests: `ModulithArchitectureTest#verifiesModularStructure`
- Docs: `README.md`, `docs/architecture/README.md`, `docs/implementation-review-notes.md`
- Notes: `ApplicationModules.verify()`로 module boundary를 검증합니다.

### Docker Compose local environment

- Status: Implemented
- Implementation: `compose.yaml`, `.env.example`, `application-local.yaml`
- Tests: CI workflow의 Docker availability check, local `docker compose up -d` 문서화
- Docs: `README.md`, `docs/troubleshooting.md`
- Notes: PostgreSQL, Kafka, Kafka UI 로컬 실행 구성을 제공합니다.

### CI test workflow

- Status: Implemented
- Implementation: `.github/workflows/ci.yml`
- Tests: GitHub Actions `build-and-test`, `integration-test` jobs
- Docs: `README.md`, `docs/test-report.md`
- Notes: workflow는 compile/test/integrationTest와 report upload를 유지합니다.
- Notes: 현재 원격 실행 결과 자체는 이 표에서 주장하지 않습니다.

### OpenAPI / ApiDog readiness

- Status: Verified
- Implementation: `docs/openapi/openapi.yaml`, `docs/openapi/README.md`
- Tests: YAML syntax parse, ApiDog manual import, `git diff --check`, Gradle verification
- Docs: `README.md`, `docs/README.md`, `docs/openapi/README.md`
- Docs: `docs/verification/claim-audit.md`
- Notes: 구현된 endpoint만 static OpenAPI 3.0.3 spec에 포함합니다.
- Notes: ApiDog import는 개발자가 로컬에서 수동 확인했으며, 자동 검증으로 과장하지 않습니다.
- Notes: Future scope API는 paths에 넣지 않습니다.

### AI-assisted verification workflow

- Status: Implemented
- Implementation: `AGENTS.md`, `docs/ai-assisted-development.md`
- Implementation: `docs/verification/claim-audit.md`
- Tests: claim audit inspection, controller/OpenAPI comparison, Gradle verification
- Docs: `README.md`, `docs/README.md`, `docs/test-report.md`
- Notes: AI Agent는 생산성 도구로 사용하고, 구현 범위와 검증 책임은 개발자 주도 기준으로 문서화합니다.

## Future Scope / Not Implemented

### Provider callback flow

- Status: Future Scope
- Implementation: 없음
- Tests: 없음
- Docs: `docs/flows/provider-callback-flow-review.md`, `README.md`
- Docs: `docs/architecture/README.md`, `docs/design-notes.md`, `docs/troubleshooting.md`
- Notes: Design review document를 추가했습니다.
- Notes: production code에는 구현하지 않았고 OpenAPI paths에도 포함하지 않았습니다.
- Notes: Timeout confirmation model 확정 이후 후속 구현 후보로 유지합니다.

### WebClient timeout confirmation flow

- Status: Future Scope
- Implementation: 없음
- Tests: 없음
- Docs: `docs/flows/payment-timeout-confirmation-flow.md`, `docs/design-notes.md`, `docs/test-report.md`
- Notes: Design document를 추가했습니다.
- Notes: production code에는 구현하지 않았고 OpenAPI paths에도 포함하지 않았습니다.

### Kafka consumer-based state transition

- Status: Not Implemented
- Implementation: 없음
- Tests: 없음
- Docs: `docs/implementation-review-notes.md`, `docs/troubleshooting.md`, `docs/test-report.md`
- Notes: 현재 outbox 상태 전이는 publisher adapter send 결과 기준입니다.

### Stale PROCESSING automatic recovery

- Status: Not Implemented
- Implementation: 없음
- Tests: 없음
- Docs: `docs/runbooks/admin-recovery-runbook.md`, `docs/sql/README.md`
- Notes: 오래 머무는 `PROCESSING` 조회 SQL과 runbook 판단 절차만 있습니다.

### Completed retry-due batch response TODO

- Status: Mismatch Fixed
- Implementation: README TODO
- Tests: `AdminNotificationRetryControllerTest`, `NotificationRetryProcessorIntegrationTest`
- Docs: `README.md`, `docs/test-report.md`, 이 문서
- Notes: `processedCount`, `successCount`, `failedCount`, `skippedCount`, `processedEventIds` 응답은 이미 구현되어 있습니다.
- Notes: README 남은 범위에서 제거했습니다.
