-- notification 이벤트 운영 상태 확인
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason,
       created_at
from notification_events
order by id desc;

-- admin 재처리 후보 조회
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at,
       failure_code,
       failure_reason
from notification_events
where status in ('RETRY_SCHEDULED', 'MANUAL_INTERVENTION_REQUIRED', 'FAILED')
order by id desc;

-- 자동 재시도 대상 조회
select id,
       order_id,
       status,
       handling_policy,
       retry_count,
       next_attempt_at
from notification_events
where status = 'RETRY_SCHEDULED'
  and next_attempt_at <= current_timestamp
order by next_attempt_at asc, id asc;

-- 수동 무시 처리 예시
update notification_events
set status = 'IGNORED',
    handling_policy = 'IGNORE',
    next_attempt_at = null,
    last_attempt_at = current_timestamp
where id = :notification_event_id
  and status in ('RETRY_SCHEDULED', 'MANUAL_INTERVENTION_REQUIRED', 'FAILED');
