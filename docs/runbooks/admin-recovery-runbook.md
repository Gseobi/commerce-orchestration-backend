# Admin Recovery Runbook

## 1. 목적

이 문서는 notification retry, outbox dead-letter, admin recovery를 운영자가 어떤 순서로 확인하고 복구할지 정리합니다.

현재 범위는 자동화된 운영 시스템이 아니라 포트폴리오 프로젝트의 운영 복구 설계 기준입니다. Micrometer custom metric, key-value style structured log, SQL 점검, admin API를 조합해 실패 지점과 복구 결과를 확인하는 데 초점을 둡니다.

운영 복구 흐름의 전체 연결 관계는 아래 다이어그램을 함께 참고합니다.

![Observability recovery architecture](/docs/diagrams/png/commerce_orchestration_observability_recovery_architecture.png)

alert 후보와 metric naming/tag 기준은 [Observability Alert Candidates & Metric Naming](/docs/operations/observability-alert-candidates.md)을 참고합니다. 현재 문서 범위에는 Prometheus/Grafana dashboard나 alert rule 구현이 포함되지 않습니다.

## 2. 공통 확인 순서

1. 장애 증상을 확인합니다.
   - 주문이 `FAILED`에 머무는지
   - notification retry가 계속 실패하는지
   - outbox event가 `RETRY_WAIT`, `DEAD_LETTER`, `PROCESSING`에 머무는지
2. metric을 확인합니다.
   - `commerce.notification.retry.*`
   - `commerce.outbox.publish.*`
   - `commerce.outbox.dead_letter.count`
   - `commerce.admin.recovery.*`
3. structured log event를 확인합니다.
   - `notification_retry_*`
   - `outbox_publish_*`
   - `admin_*`
4. SQL로 대상 event 상태를 확인합니다.
5. admin API를 실행합니다.
6. audit log와 상태 전이를 확인합니다.

Admin notification retry, notification ignore, outbox dead-letter retry API는 선택적 request body로 `operatorId`, `reason`을 받을 수 있습니다. 기존 no-body 호출도 계속 지원합니다. 값이 없거나 blank이면 audit detail에는 `operatorId=unknown`, `reason=not-provided`가 기록됩니다.

```json
{
  "operatorId": "ops-admin",
  "reason": "manual recovery after dependency restored"
}
```

`operatorId`와 `reason`은 audit detail에만 안전하게 남기고 metric tag나 structured log field에는 사용하지 않습니다. 긴 값은 저장 길이에 맞춰 잘립니다.

## 3. Notification retry due batch

### 증상

- `RETRY_SCHEDULED` notification event가 due 시간이 지났는데 처리되지 않습니다.
- due batch 응답의 `skippedCount` 또는 `failedCount`가 증가합니다.
- 주문이 notification 실패 이후 `FAILED` 상태에 머뭅니다.

### 확인 metric

- `commerce.notification.retry.batch.started`
- `commerce.notification.retry.success`
- `commerce.notification.retry.failure`
- `commerce.notification.retry.skipped`
- `commerce.notification.retry.manual_required`

### 확인 log event

- `notification_retry_batch_started`
- `notification_retry_claim_skipped`
- `notification_retry_succeeded`
- `notification_retry_rescheduled`
- `notification_retry_manual_required`
- `notification_retry_batch_completed`

### 확인 SQL

```sql
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from notification_events
where status = 'RETRY_SCHEDULED'
  and next_attempt_at <= current_timestamp
order by next_attempt_at asc, id asc;
```

### 실행 API

```http
POST /api/admin/notification-events/retry-due
Authorization: Bearer <admin-token>
```

### 성공 기준

- batch 응답의 `successCount`가 증가합니다.
- 대상 notification event가 `SENT`로 전환됩니다.
- 관련 order가 `COMPLETED`로 복구됩니다.
- `audit_logs`에 notification retry 성공 기록이 남습니다.

### 실패 시 다음 조치

- `skippedCount`가 증가하면 같은 event가 이미 `PROCESSING`으로 선점됐는지 확인합니다.
- `failedCount`가 증가하면 `failure_code`, `retry_count`, `next_attempt_at`을 확인합니다.
- `MANUAL_INTERVENTION_REQUIRED`로 전환된 경우 아래 manual intervention 절차로 이동합니다.

## 4. Notification manual intervention

### 상태 확인

```sql
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from notification_events
where status = 'MANUAL_INTERVENTION_REQUIRED'
order by id desc;
```

### retry와 ignore 판단 기준

- retry: 알림 실패가 일시적이고 다시 성공 처리할 수 있다고 판단되는 경우
- ignore: 알림 실패가 주문 완료를 막지 않아도 되는 실패로 판단되는 경우

현재 API는 운영자 승인 workflow를 별도로 구현하지 않습니다. 판단 근거는 event 상태, failure code, audit log, 주문 상태를 함께 보고 결정합니다.

### admin retry API

```http
POST /api/admin/notification-events/{notificationEventId}/retry
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "operatorId": "ops-admin",
  "reason": "manual retry after notification channel recovery"
}
```

body 없이 호출해도 기존과 동일하게 동작합니다.

성공 기준:

- 응답의 `action`이 `RETRY`
- 응답의 `result`가 `SUCCESS`
- `currentStatus`가 `SENT`
- `orderStatus`가 `COMPLETED`

### admin ignore API

```http
POST /api/admin/notification-events/{notificationEventId}/ignore
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "operatorId": "ops-admin",
  "reason": "customer accepted missing notification"
}
```

body 없이 호출해도 기존과 동일하게 동작합니다.

성공 기준:

- 응답의 `action`이 `IGNORE`
- 응답의 `result`가 `IGNORED`
- `currentStatus`가 `IGNORED`
- `orderStatus`가 `COMPLETED`

### audit log 확인

```sql
select id,
       order_id,
       event_type,
       detail,
       created_at
from audit_logs
where order_id = :order_id
order by id desc;
```

`detail`에는 `action`, `result`, `previousStatus`, `currentStatus`, target id가 포함됩니다.
admin request body가 전달된 경우 `operatorId`, `reason`도 포함됩니다. 값이 없으면 `unknown`, `not-provided`가 기록됩니다.

## 5. Outbox DEAD_LETTER 재발행

### DEAD_LETTER 조회 SQL

```sql
select id,
       order_id,
       topic,
       event_type,
       retry_count,
       dead_lettered_at,
       failure_code,
       failure_reason
from outbox_events
where status = 'DEAD_LETTER'
order by dead_lettered_at desc, id desc;
```

### admin retry API

```http
POST /api/admin/outbox-events/{outboxEventId}/retry
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "operatorId": "outbox-operator",
  "reason": "Kafka broker recovered"
}
```

body 없이 호출해도 기존과 동일하게 동작합니다.

### 결과별 조치

- `PUBLISHED`
  - 재발행 성공입니다.
  - `commerce.outbox.publish.success`와 `commerce.admin.recovery.success`를 확인합니다.
  - `outbox_publish_completed`, `admin_outbox_retry_completed` log event를 확인합니다.
- `RETRY_WAIT`
  - 재발행 실패 후 다음 retry로 이월됐습니다.
  - `nextAttemptAt`, `retryCount`, `failureCode`를 확인합니다.
  - Kafka broker 상태와 topic 설정을 함께 확인합니다.
- `DEAD_LETTER`
  - 재발행이 다시 실패해 dead-letter 상태로 남았습니다.
  - `commerce.outbox.dead_letter.count`와 `outbox_publish_dead_lettered` log event를 확인합니다.
  - 반복되면 payload 자체가 아니라 topic, event type, failure code, Kafka 상태를 기준으로 원인을 좁힙니다.

### 관련 metric/log/audit 확인

- metric
  - `commerce.outbox.publish.attempts`
  - `commerce.outbox.publish.success`
  - `commerce.outbox.publish.failure`
  - `commerce.outbox.dead_letter.count`
  - `commerce.admin.recovery.requests`
  - `commerce.admin.recovery.success`
  - `commerce.admin.recovery.failure`
- log event
  - `outbox_publish_completed`
  - `outbox_publish_retry_scheduled`
  - `outbox_publish_dead_lettered`
  - `admin_outbox_retry_requested`
  - `admin_outbox_retry_completed`
  - `admin_recovery_failed`
- audit
  - `ADMIN_OUTBOX_RETRIED`

## 6. PROCESSING 상태 장기 체류

### notification PROCESSING 확인 SQL

```sql
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from notification_events
where status = 'PROCESSING'
order by last_attempt_at asc, id asc;
```

### outbox PROCESSING 확인 SQL

```sql
select id,
       order_id,
       topic,
       event_type,
       status,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from outbox_events
where status = 'PROCESSING'
order by last_attempt_at asc, id asc;
```

현재 자동 stale recovery job은 구현하지 않았습니다.

### 수동 판단 기준

- `last_attempt_at` 이후 충분한 시간이 지났는지 확인합니다.
- 같은 event의 claim skipped metric/log가 반복되는지 확인합니다.
- worker 종료, 애플리케이션 재시작, Kafka 장애, DB transaction 중단 가능성을 확인합니다.
- 상태를 직접 수정하기 전에는 관련 order, notification/outbox event, audit log를 함께 확인합니다.

### 후속 확장 후보

- stale `PROCESSING` event 감지 job
- 운영자 승인 기반 stale event reset API
- stale reset metric과 audit event
- alert rule

## 7. 장애 후 사후 점검

### orders 상태 확인

```sql
select id,
       customer_id,
       order_status,
       total_amount,
       currency,
       updated_at
from orders
where id = :order_id;
```

### notification_events 확인

```sql
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from notification_events
where order_id = :order_id
order by id asc;
```

### outbox_events 확인

```sql
select id,
       order_id,
       topic,
       event_type,
       status,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       published_at,
       dead_lettered_at,
       failure_code,
       failure_reason
from outbox_events
where order_id = :order_id
order by id asc;
```

### audit_logs 확인

```sql
select id,
       order_id,
       event_type,
       detail,
       created_at
from audit_logs
where order_id = :order_id
order by id asc;
```

### retry/dead-letter metric 확인

- `commerce.notification.retry.success`
- `commerce.notification.retry.failure`
- `commerce.notification.retry.skipped`
- `commerce.notification.retry.manual_required`
- `commerce.outbox.publish.failure`
- `commerce.outbox.publish.skipped`
- `commerce.outbox.dead_letter.count`
- `commerce.admin.recovery.failure`

## 8. 이번 범위에서 하지 않은 것

- Prometheus/Grafana dashboard
- alert rule
- stale `PROCESSING` automatic recovery job
- Kafka consumer 기반 상태 전이
