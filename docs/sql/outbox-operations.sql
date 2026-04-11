-- 운영 상태 확인
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
       failure_reason,
       created_at
from outbox_events
order by id desc;

-- 재발행 대상 조회
select id,
       order_id,
       topic,
       event_type,
       status,
       retry_count,
       next_attempt_at,
       failure_code,
       failure_reason
from outbox_events
where status in ('READY', 'RETRY_WAIT')
  and next_attempt_at <= current_timestamp
order by next_attempt_at asc, id asc
limit 100;

-- dead-letter 이벤트 조회
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

-- 주문 단위 outbox 추적
select id,
       order_id,
       status,
       retry_count,
       next_attempt_at,
       published_at,
       dead_lettered_at,
       failure_code,
       failure_reason
from outbox_events
where order_id = :order_id
order by id asc;

-- 수동 재처리 예시: dead-letter를 다시 READY로 전환
update outbox_events
set status = 'READY',
    retry_count = 0,
    next_attempt_at = current_timestamp,
    last_attempt_at = null,
    dead_lettered_at = null,
    failure_code = null,
    failure_reason = null
where id = :outbox_event_id
  and status = 'DEAD_LETTER';

-- admin API 재처리 전 확인
select id,
       order_id,
       status,
       retry_count,
       dead_lettered_at,
       failure_code,
       failure_reason
from outbox_events
where id = :outbox_event_id;

-- 수동 재처리 예시: 특정 주문의 재시도 대기 이벤트를 즉시 실행 대상으로 전환
update outbox_events
set status = 'READY',
    next_attempt_at = current_timestamp
where order_id = :order_id
  and status = 'RETRY_WAIT';
