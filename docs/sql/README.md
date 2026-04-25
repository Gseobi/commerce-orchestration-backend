# SQL Guide

이 문서는 DB schema source of truth와 운영 점검용 SQL의 역할을 구분하기 위한 문서입니다.

이 프로젝트에서 실제 스키마 변경은 Flyway migration으로 관리하고, 
`docs/sql`은 outbox retry/dead-letter, notification admin operation처럼 운영 확인과 수동 점검이 필요한 상황을 설명하는 참고 SQL로 유지합니다.

## 1. Source of Truth

실제 DB 스키마의 source of truth는 `src/main/resources/db/migration` 아래 Flyway migration입니다.

- `V1__init.sql`  
  현재 엔티티 기준 초기 스키마
- `V2__outbox_retry_dead_letter.sql`  
  outbox retry / backoff / dead-letter 컬럼 및 인덱스 추가
- `V3__notification_admin_policy.sql`  
  notification handling policy, retry metadata, admin 재처리 지원 컬럼
- `V4__payment_idempotency.sql`  
  payment idempotency / provider transaction id / optimistic locking 컬럼과 partial unique index
- `V5__notification_retry_claim.sql`  
  notification retry claim을 위한 version 컬럼
- `V6__outbox_processing_claim.sql`  
  outbox publish claim을 위한 version 컬럼

## 2. docs/sql의 역할

`docs/sql`은 migration 대체 문서가 아니라, 운영 확인과 수동 점검을 돕기 위한 참고 SQL 모음입니다.

즉,
- 스키마 변경은 Flyway migration에서 관리하고
- 조회 / 점검 / 운영 대응 예시는 `docs/sql`에서 확인합니다

## 3. 현재 문서 구성

- [outbox-retry-dead-letter-ddl.sql](outbox-retry-dead-letter-ddl.sql)  
  outbox 변경분만 따로 확인하기 위한 참고용 DDL
- [outbox-operations.sql](outbox-operations.sql)  
  상태 확인, retry 대상 확인, dead-letter 확인, 수동 재처리 예시
- [notification-admin-operations.sql](notification-admin-operations.sql)  
  notification 재처리 후보, 자동 재시도 후보, admin 무시 처리 예시

## 4. 적용 방식

- 애플리케이션 기본 / 로컬 / 통합 테스트 프로필은 Flyway를 자동 실행합니다.
- 단위 테스트용 `test` 프로필은 H2 `create-drop`을 유지하고 Flyway를 비활성화합니다.

## 5. 운영 점검 SQL

### payment_request_id 중복 확인

partial unique index가 있으므로 정상 운영 DB에서는 중복 row가 없어야 합니다. migration 전 데이터 점검이나 장애 조사 시 사용합니다.

```sql
select payment_request_id, count(*) as duplicate_count
from payments
where payment_request_id is not null
group by payment_request_id
having count(*) > 1
order by duplicate_count desc, payment_request_id;
```

### 오래 머무는 PROCESSING notification event 조회

```sql
select id,
       order_id,
       status,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from notification_events
where status = 'PROCESSING'
  and last_attempt_at < now() - interval '10 minutes'
order by last_attempt_at asc, id asc;
```

### 오래 머무는 PROCESSING outbox event 조회

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
  and last_attempt_at < now() - interval '10 minutes'
order by last_attempt_at asc, id asc;
```

### DEAD_LETTER outbox event 조회

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
order by dead_lettered_at desc nulls last, id desc;
```

### due RETRY_SCHEDULED notification event 조회

```sql
select id,
       order_id,
       retry_count,
       next_attempt_at,
       last_attempt_at,
       failure_code,
       failure_reason
from notification_events
where status = 'RETRY_SCHEDULED'
  and next_attempt_at is not null
  and next_attempt_at <= now()
  and retry_count < 3
order by next_attempt_at asc, id asc;
```

### publish 대상 outbox event 조회

```sql
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
  and next_attempt_at <= now()
order by next_attempt_at asc, id asc;
```
