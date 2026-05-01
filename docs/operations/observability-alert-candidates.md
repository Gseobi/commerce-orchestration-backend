# Observability Alert Candidates & Metric Naming

## Purpose / 목적

이 문서는 현재 구현된 metric, structured log, audit, DB state 신호를 운영자가 어떻게 해석할 수 있는지 정리합니다.

이 문서는 Prometheus/Grafana dashboard 구현 문서가 아닙니다. alert rule configuration도 아닙니다. 현재 코드에 존재하는 관측 신호와, 후속 운영 환경에서 합리적으로 둘 수 있는 alert/dashboard 후보를 분리해 설명하는 operational design 문서입니다.

목표는 포트폴리오 검토자가 "운영에서 무엇을 봐야 하는가", "어떤 실패 조건이 중요해지는가", "metric name과 tag를 어떻게 설계해야 하는가"를 코드와 문서 범위 안에서 확인할 수 있게 하는 것입니다.

## Current Implementation Boundary / 현재 구현 경계

현재 구현된 관측 신호는 아래 범위입니다.

- `CommerceRecoveryMetrics`가 Micrometer `Counter`를 사용해 outbox publish, notification retry, admin recovery 결과를 기록합니다.
- `OutboxPublisherService`, `NotificationRetryProcessor`, `AdminReprocessingService`가 key-value style structured log event를 남깁니다.
- admin recovery는 `AuditRecorder`를 통해 audit log에 action, target, previous/current status, result, operator/reason context를 남깁니다.
- notification retry와 outbox publish는 DB state와 retry/dead-letter 컬럼을 통해 운영자가 상태를 확인할 수 있습니다.
- `/actuator/health`는 OpenAPI에 공개 health endpoint로 문서화되어 있습니다.
- `RequestTraceFilter`는 `X-Trace-Id` header를 생성/전파하고 MDC에 `traceId`를 넣습니다.
- `CommerceRecoveryMetricsTest`, `OutboxPublisherServiceTest`, `NotificationRetryProcessorTest`, `AdminReprocessingServiceTest`가 metric counter와 tag normalization을 검증합니다.

현재 구현하지 않은 범위는 아래와 같습니다.

- Prometheus/Grafana dashboard
- alert rule configuration
- distributed tracing backend integration
- stale `PROCESSING` automatic recovery job
- provider callback observability
- WebClient timeout confirmation observability
- Kafka consumer-based state transition observability

## Current Signals / 현재 관측 신호

### Notification retry

Current metrics:

- `commerce.notification.retry.batch.started`
- `commerce.notification.retry.success`
- `commerce.notification.retry.failure`
- `commerce.notification.retry.skipped`
- `commerce.notification.retry.manual_required`

Current log events:

- `notification_retry_batch_started`
- `notification_retry_claim_skipped`
- `notification_retry_succeeded`
- `notification_retry_rescheduled`
- `notification_retry_manual_required`
- `notification_retry_batch_completed`

Operational meaning:

- `failure` 증가: retry가 다시 실패했거나 manual intervention으로 넘어가는 흐름입니다.
- `skipped` 증가: claim 경쟁, 이미 처리된 event, 상태 변경으로 인해 현재 worker가 처리하지 않은 흐름입니다.
- `manual_required` 증가: 자동 복구가 끝났고 운영자 판단이 필요한 상태입니다.

### Outbox publish / dead-letter

Current metrics:

- `commerce.outbox.publish.attempts`
- `commerce.outbox.publish.success`
- `commerce.outbox.publish.failure`
- `commerce.outbox.publish.skipped`
- `commerce.outbox.dead_letter.count`

Current log events:

- `outbox_publish_batch_started`
- `outbox_publish_claim_skipped`
- `outbox_publish_batch_completed`
- `outbox_publish_completed`
- `outbox_publish_retry_scheduled`
- `outbox_publish_dead_lettered`
- `outbox_publish_unexpected_failure`

Operational meaning:

- `publish.failure` 증가: Kafka publish 실패 또는 publisher adapter failure가 발생한 흐름입니다.
- `dead_letter.count` 증가: retry 한도를 초과한 outbox event가 운영자 재처리 대상이 된 흐름입니다.
- `publish.skipped` 증가: claim 경쟁 또는 이미 처리된 outbox event로 인해 현재 worker가 처리하지 않은 흐름입니다.

### Admin recovery

Current metrics:

- `commerce.admin.recovery.requests`
- `commerce.admin.recovery.success`
- `commerce.admin.recovery.failure`

Current log events:

- `admin_notification_retry_requested`
- `admin_notification_retry_completed`
- `admin_notification_ignore_requested`
- `admin_notification_ignore_completed`
- `admin_outbox_retry_requested`
- `admin_outbox_retry_completed`
- `admin_recovery_failed`

Current audit events:

- `ADMIN_NOTIFICATION_RETRIED`
- `ADMIN_NOTIFICATION_IGNORED`
- `ADMIN_OUTBOX_RETRIED`

Operational meaning:

- `requests`는 운영자가 수동 복구를 시도한 빈도입니다.
- `success`는 하위 처리 단위 복구가 성공했음을 뜻합니다.
- `failure`는 복구 API가 실패했거나 대상 상태가 복구 조건과 맞지 않는 흐름을 의미합니다.
- audit log는 admin recovery traceability의 durable source입니다.

### Payment/settlement failure branch

Current durable signals:

- order status
- payment status
- settlement record
- orchestration step
- audit log

Current limitation:

- payment approval failure와 settlement compensation에 대한 별도 Micrometer counter는 없습니다.
- 따라서 이 영역의 alert는 현재 metric 기반이 아니라 DB/audit/step 조회 또는 future metric candidate로 다뤄야 합니다.

### Health check / Actuator

Current signal:

- `/actuator/health`

Operational meaning:

- application liveness/readiness의 기본 진입점입니다.
- 현재 문서 범위에서는 health endpoint가 존재한다는 사실만 다루며, 외부 uptime monitor나 alert rule은 구현하지 않았습니다.

### Trace ID

Current signal:

- `RequestTraceFilter`
- `X-Trace-Id` response header
- MDC `traceId`

Operational meaning:

- request 단위 log correlation을 돕는 request trace id입니다.
- OpenTelemetry, Zipkin, Jaeger 같은 distributed tracing backend는 구현되어 있지 않습니다.

## Metric Naming Policy / Metric naming 기준

현재 metric name은 dot-separated lower-case style을 사용합니다.

Current metric names:

```text
commerce.outbox.publish.attempts
commerce.outbox.publish.success
commerce.outbox.publish.failure
commerce.outbox.publish.skipped
commerce.outbox.dead_letter.count
commerce.notification.retry.batch.started
commerce.notification.retry.success
commerce.notification.retry.failure
commerce.notification.retry.skipped
commerce.notification.retry.manual_required
commerce.admin.recovery.requests
commerce.admin.recovery.success
commerce.admin.recovery.failure
```

향후 metric naming 기준은 아래처럼 유지합니다.

- lower-case를 사용합니다.
- dot-separated namespace를 사용합니다.
- prefix는 `commerce`로 시작합니다.
- domain 또는 area를 두 번째 segment로 둡니다.
- action/result를 뒤쪽 segment로 둡니다.
- dynamic ID를 metric name에 넣지 않습니다.
- 동일 의미의 result는 metric name 또는 bounded tag 중 하나로 일관되게 표현합니다.

Recommended future examples:

```text
commerce.payment.approval.failure
commerce.settlement.compensation.success
commerce.settlement.compensation.failure
commerce.notification.retry.results
commerce.outbox.publish.results
commerce.admin.recovery.results
```

위 future examples는 현재 구현된 metric이 아닙니다.

## Tag Policy / Metric tag 기준

현재 metric tag는 낮은 cardinality의 값만 사용합니다.

Current tags:

- `eventType`
- `topic`
- `failureCode`
- `reason`
- `target`
- `action`

Allowed bounded tags:

- `action`
- `result`
- `status`
- `failureType`
- `failureCode`
- `policy`
- `channel`, 값 집합이 제한될 때만 허용
- `target`
- `eventType`, 값 집합이 제한될 때만 허용
- `topic`, topic set이 운영상 제한될 때만 허용

Forbidden high-cardinality or unsafe tags:

- `orderId`
- `paymentId`
- `paymentRequestId`
- `providerTransactionId`
- `notificationEventId`
- `outboxEventId`
- `operatorId`
- `reason`
- raw exception message
- token
- Authorization header
- API key
- raw payload
- raw provider payload
- signature or secret

금지 이유:

- high-cardinality tag는 time series 폭증을 일으킵니다.
- 운영 dashboard와 alert가 불안정해집니다.
- `operatorId`, `reason`, token, payload, secret 계열 값은 privacy/security risk가 있습니다.
- raw exception message는 값 집합이 불안정해 alert grouping을 어렵게 만듭니다.

## Structured Logging Policy / Structured log 기준

Structured log는 장애 분석에 필요한 문맥을 남기되 metric tag보다 넓은 정보를 담을 수 있습니다.

Recommended keys:

- `event`
- `action`
- `result`
- `targetType`
- `targetId`
- `previousStatus`
- `currentStatus`
- `failureType`
- `failureCode`
- `retryCount`
- `nextAttemptAt`
- `eventType`
- `topic`
- `traceId`

현재 log에는 `notificationEventId`, `outboxEventId`, `orderId` 같은 식별자가 포함될 수 있습니다. ID는 metric tag보다 log field에 두는 편이 안전하지만, 필요한 경우에만 사용해야 합니다.

Sensitive or unsafe fields to avoid:

- token
- Authorization header
- API key
- raw provider payload
- raw personal data
- raw secret/signature
- full request body

Audit log는 admin recovery traceability의 durable source입니다. 운영자 입력인 `operatorId`, `reason`은 metric tag나 일반 structured log field가 아니라 bounded audit detail에 남기는 현재 정책을 유지합니다.

## Alert Candidates / Alert 후보

아래 표는 alert rule 구현이 아니라 운영 후보 목록입니다.

| Area | Signal | Candidate Condition | Severity | Suggested Action | Status |
|---|---|---|---|---|---|
| Notification retry | `commerce.notification.retry.failure` | 짧은 시간 동안 failure가 평소보다 급증 | Warning | notification channel 상태와 `notification_retry_rescheduled` log 확인 | Current Signal / Candidate |
| Notification retry | `commerce.notification.retry.manual_required` | manual-required count 증가 | Warning | [Admin Recovery Runbook](/docs/runbooks/admin-recovery-runbook.md)의 manual intervention 절차 실행 | Current Signal / Candidate |
| Notification retry | `commerce.notification.retry.skipped` | claim skipped가 반복 증가 | Info/Warning | 중복 실행, scheduler/admin 동시 실행, `PROCESSING` 장기 체류 여부 확인 | Current Signal / Candidate |
| Notification retry-due batch | batch response `processedCount`, `successCount`, `failedCount`, `skippedCount` | processedCount는 높지만 successCount가 낮음 | Warning | retry-due API 응답과 notification event 상태 확인 | Current Signal / Candidate |
| Outbox publish | `commerce.outbox.publish.failure` | publish failure가 반복 증가 | Warning | Kafka broker/topic 상태, `outbox_publish_retry_scheduled` log 확인 | Current Signal / Candidate |
| Outbox dead-letter | `commerce.outbox.dead_letter.count` | dead-letter count 증가 | Critical | dead-letter SQL 조회 후 admin outbox retry 절차 실행 | Current Signal / Candidate |
| Outbox publish | `commerce.outbox.publish.skipped` | claim skipped가 급증 | Info/Warning | 다중 worker 경쟁 또는 stuck `PROCESSING` 상태 확인 | Current Signal / Candidate |
| Admin recovery | `commerce.admin.recovery.failure` | admin recovery failure 증가 | Warning | target/action/failureCode 확인 후 대상 상태와 권한 확인 | Current Signal / Candidate |
| Admin recovery | `commerce.admin.recovery.requests` with `action=IGNORE` | ignore action 사용량이 평소보다 증가 | Review | notification 실패가 반복되는지, ignore 기준이 남용되는지 audit 확인 | Current Signal / Candidate |
| Payment approval | payment failure branch audit/step | payment failure가 급증 | Warning | provider/mock failure token, external provider 상태 확인 | Future Metric Candidate |
| Settlement compensation | compensation step/audit | settlement failure compensation이 급증 | Critical | settlement dependency 상태와 payment cancel 결과 확인 | Future Metric Candidate |
| Health | `/actuator/health` | health endpoint down | Critical | application, DB, Kafka, container 상태 확인 | Current Signal / Candidate |
| CI verification | GitHub Actions test job result | compile/test/integrationTest 실패 | Warning/Critical | 실패 test class/method와 Testcontainers 로그 확인 | Current Signal / Candidate |

Alert threshold는 운영 환경의 traffic baseline이 생긴 뒤 정해야 합니다. 현재 문서는 threshold나 Prometheus rule을 구현하지 않습니다.

## Dashboard Candidates / Dashboard 후보

아래 dashboard는 Future Scope입니다. 현재 Grafana dashboard는 구현되어 있지 않습니다.

- Notification retry trend
  - success/failure/skipped/manual_required counter
  - retry due batch 처리량
- Outbox publish trend
  - attempts/success/failure/skipped/dead-letter counter
  - failureCode별 분포
- Admin recovery summary
  - target/action별 requests/success/failure
  - ignore action 증가 추이
- Payment/settlement failure summary
  - payment failure branch count
  - settlement compensation count
  - 현재는 future metric candidate
- Health/test status summary
  - `/actuator/health`
  - CI unit/integration test result

## Runbook Linkage / Runbook 연결

Alert candidate는 반드시 운영자가 실행할 수 있는 runbook action과 연결되어야 합니다.

Primary runbook:

- [Admin Recovery Runbook](/docs/runbooks/admin-recovery-runbook.md)

연결 기준:

- notification retry alert는 retry-due batch, manual intervention, retry/ignore API 절차로 연결합니다.
- outbox dead-letter alert는 `DEAD_LETTER` 조회 SQL과 admin outbox retry API로 연결합니다.
- `PROCESSING` 장기 체류 의심은 자동 reset이 아니라 SQL 확인과 운영자 판단으로 연결합니다.
- admin recovery failure alert는 대상 event 상태, 권한, audit detail 확인으로 연결합니다.
- payment/settlement alert 후보는 현재 metric이 아니라 order/payment/settlement/orchestration step/audit 조회로 연결합니다.

## What Is Not Implemented / 구현되지 않은 것

아래 항목은 현재 구현되어 있지 않습니다.

- Prometheus dashboard
- Grafana dashboard
- concrete alert rule configuration
- distributed tracing backend integration
- stale `PROCESSING` automatic recovery job
- provider callback observability
- WebClient timeout confirmation observability
- Kafka consumer-based state transition
- notification channel-specific retry policy metric
- payment provider production monitoring

현재 구현된 것은 Micrometer counter, structured log, audit log, DB state, health endpoint, request trace id 기반의 운영 관측성 보조 신호입니다.

## Future Scope / 후속 범위

- Prometheus scrape/export 환경 정리
- Grafana dashboard 작성
- alert rule configuration 작성
- alert threshold baseline 수집
- payment approval failure metric 추가
- settlement compensation metric 추가
- stale `PROCESSING` detection job 또는 admin reset API 설계
- distributed tracing backend 연동 검토
- provider callback/timeout confirmation 구현 이후 해당 flow metric/log 추가
- notification channel별 bounded tag 정책 확정
