# Design Notes

이 문서는 commerce-orchestration-backend가 커머스 주문 이후 후속 처리 흐름을 왜 orchestration, explicit state transition, failure branching, compensation, retry/dead-letter 구조로 나누었는지 정리합니다.

핵심 관점은 단순 주문 API 구현이 아니라, 결제·정산·알림·이벤트 발행 중 일부만 실패했을 때 거래 흐름의 정합성과 운영 복구 가능성을 어떻게 확보할 것인가입니다.
  
실제 검증 범위는 [Test Report](/docs/test-report.md)를 기준으로 확인합니다.  

## 1. 왜 현재 구조를 택했는가

이 프로젝트는 주문 이후의 후속 작업을 여러 controller로 분산시키기보다,  
`CommerceOrchestrationService`가 흐름을 제어하고 각 domain service가 자기 상태와 저장소를 소유하는 방향을 택합니다.

핵심 의도는 아래와 같습니다.

- 비즈니스 외부 진입은 `OrderController`에 집중합니다.
- orchestration은 흐름만 제어하고, payment / settlement / notification의 저장 상세는 각 domain이 소유합니다.
- repository를 직접 외부 공개하기보다 service 경계를 통해 협력합니다.
- 주문 상태, orchestration step, outbox event, audit log를 함께 남겨 운영 가시성을 확보합니다.

## 2. 현재 구조 요약

### Order
- 주문 생성과 상태 전이의 소유자는 `OrderService`입니다.
- 외부 비즈니스 API는 `OrderController -> OrderFacade`를 통해 진입합니다.
- 주문 상세 조회도 다른 domain repository를 직접 읽지 않고 domain application service를 통해 상태를 모읍니다.

### Orchestration
- `CommerceOrchestrationService`는 happy path와 실패 분기를 제어합니다.
- orchestration이 직접 사용하는 repository는 `OrchestrationStepRepository`입니다.
- payment / settlement / notification / outbox / audit는 공개된 application 계약을 통해 협력합니다.

### Payment
- `PaymentService`가 `PaymentRepository`를 내부적으로 사용합니다.
- `PaymentProviderClient`는 `MockPaymentProviderClient`, `ExternalPaymentProviderClient`로 분리되어 있습니다.
- 현재 external 구현은 연동 골격과 기본 예외 매핑까지 포함합니다.
- `paymentRequestId` 기준 기존 payment를 먼저 조회해 동일 approve replay에서 provider 중복 호출을 막습니다.

### Settlement / Notification
- `SettlementService`, `NotificationService`는 각자 자기 요청 결과를 저장합니다.
- settlement와 notification은 동일 보상 정책으로 취급하지 않습니다.

### Outbox / Audit
- `OutboxService`는 이벤트 append/read를 담당합니다.
- `OutboxPublisherService`는 scheduler 또는 명시적 호출로 publish 상태 전이를 수행합니다.
- Kafka publish 구현은 `OutboxEventPublisher` adapter 구현체가 담당합니다.
- `AuditService`는 replay, rejection, failure branch를 기록합니다.

## 3. Compensation 정책

### Settlement failure
- settlement 요청 실패 시 주문을 먼저 `FAILED`로 기록합니다.
- 이후 가장 최근 승인 결제를 취소하는 보상을 수행합니다.
- 최종 주문 상태는 `CANCELLED`로 닫습니다.
- orchestration step에는 `SETTLEMENT FAILED`, `COMPENSATION SUCCESS`가 남습니다.

### Notification failure
- notification 실패 시 주문은 `FAILED`로 남깁니다.
- payment / settlement는 되돌리지 않습니다.
- compensation step은 `READY` 상태로 남기며, 현재 의미는 재시도 또는 운영자 개입 필요입니다.

## 4. Notification 운영 정책

현재 notification 실패는 아래 세 가지로 분류합니다.

- `AUTO_RETRY`  
  일시적 실패로 간주하며 `RETRY_SCHEDULED` 상태와 `nextAttemptAt`을 남깁니다.
- `MANUAL_INTERVENTION`  
  운영자 판단이 필요한 실패로 간주하며 `MANUAL_INTERVENTION_REQUIRED` 상태를 남깁니다.
- `IGNORE`  
  주문 완료를 막지 않아도 되는 실패로 간주하며 `IGNORED` 상태로 남깁니다.

현재는 `RETRY_SCHEDULED` 이벤트를 due 시점에 조회해 재처리하는 `NotificationRetryProcessor` 경로까지 포함합니다.

due retry event는 처리 전에 `PROCESSING`으로 claim합니다. claim에 실패한 event는 다른 실행자가 선점했거나 더 이상 처리 대상이 아니므로 skipped로 집계합니다.

`NotificationRetryScheduler`가 property-gated 방식으로 `NotificationRetryProcessor`를 호출하며, 기본값은 비활성화입니다.  
운영자는 `POST /api/admin/notification-events/retry-due`로 동일 trigger를 수동 실행할 수 있으며, 응답은 batch 처리 결과 요약과 처리된 notification event id 목록을 포함합니다.  
따라서 현재 구현 범위는 "자동 재시도 대상 조회와 상태 전이 로직 + 선택적 scheduled trigger + admin-triggered batch endpoint"입니다.

### 후속 작업
- notification 채널별 재시도 횟수
- 무시 가능한 실패와 운영자 개입 필요 실패의 구분
- 운영자 승인 절차와 이력 보강

## 5. Outbox 신뢰성 기준

현재 outbox는 아래 상태를 사용합니다.

- `READY`
- `PROCESSING`
- `RETRY_WAIT`
- `PUBLISHED`
- `DEAD_LETTER`

현재 정책은 아래와 같습니다.

- publish 대상은 `READY`, `RETRY_WAIT` 중 `nextAttemptAt <= now`인 이벤트입니다.
- publish 전 조건부 update로 `PROCESSING` claim을 획득한 이벤트만 실제 publisher adapter에 전달합니다.
- 실패 시 `retryCount`, `failureCode`, `failureReason`, `lastAttemptAt`, `nextAttemptAt`을 갱신합니다.
- `maxRetryCount`를 초과하면 `DEAD_LETTER`로 전환합니다.
- 운영 수동 재처리 SQL은 `docs/sql/outbox-operations.sql`에 정리합니다.

## 6. DB 상태 기반 claim을 사용한 이유

scheduler와 admin API는 같은 notification event 또는 outbox event를 비슷한 시점에 처리할 수 있습니다. 단일 JVM 내부라면 `synchronized` 같은 프로세스 내부 락이 일부 중복 실행을 줄일 수 있지만, 다중 인스턴스 배포나 운영자 수동 실행까지 고려하면 충분하지 않습니다.

그래서 retry/publish 처리 권한은 DB 조건부 update로 선점합니다.

- 대상 상태와 due 조건을 만족할 때만 `PROCESSING`으로 update합니다.
- update count가 `1`이면 처리 권한을 획득한 것으로 봅니다.
- update count가 `0`이면 다른 실행자가 선점했거나 더 이상 대상 상태가 아니므로 skipped로 봅니다.

Outbox는 이벤트 유실을 줄이는 구조지만, 중복 발행 방어는 별도 claim/idempotency 전략이 필요합니다. 현재 구현은 publish 직전 `PROCESSING` claim으로 중복 publish 가능성을 줄이고, retry/dead-letter 전이는 `OutboxPublisherService`가 담당합니다.

Payment는 `paymentRequestId`를 기준으로 provider 중복 호출을 방지합니다. 외부 provider callback 멱등성은 `providerTransactionId` 컬럼과 repository 조회 메서드까지만 준비되어 있으며, callback API가 구체화될 때 후속 확장할 수 있습니다.

## 7. Admin 재처리 기준

- outbox는 `DEAD_LETTER` 이벤트만 admin 즉시 재발행 대상으로 허용합니다.
- notification은 `RETRY_SCHEDULED`, `MANUAL_INTERVENTION_REQUIRED`, `FAILED` 상태를 admin 재처리 / 무시 대상으로 봅니다.
- admin 재처리는 전체 orchestration 재실행이 아니라 실패한 하위 처리 단위 복구입니다.
- notification 재처리 성공 또는 ignore 처리 후에는 주문을 `COMPLETED`로 복구합니다.

## 8. Payment Provider 설계 기준

- 기본 모드는 `mock`입니다.
- `external` 모드는 실제 provider 연동을 붙일 수 있는 구조와 timeout 설정, API key 헤더, 예외 매핑을 제공합니다.
- provider별 retry와 error taxonomy는 아직 고정하지 않았습니다.
- approve idempotency key는 현재 orchestration에서 생성한 deterministic `paymentRequestId`를 사용합니다.

## 9. DB 스키마 관리 기준

현재 스키마의 source of truth는 Flyway migration입니다.

- `V1__init.sql`  
  현재 엔티티 기준 초기 스키마
- `V2__outbox_retry_dead_letter.sql`  
  outbox retry / dead-letter 확장
- `V3__notification_admin_policy.sql`  
  notification handling policy, retry metadata, admin 재처리 지원 컬럼
- `V4__payment_idempotency.sql`  
  payment idempotency / provider transaction id / optimistic locking 컬럼
- `V5__notification_retry_claim.sql`  
  notification retry claim을 위한 version 컬럼
- `V6__outbox_processing_claim.sql`  
  outbox publish claim을 위한 version 컬럼

애플리케이션은 Flyway 적용 후 JPA `validate`로 매핑 정합성을 확인합니다.

## 10. 현재 남아 있는 TODO

- payment provider 실제 연동 고도화
- notification 운영 정책 세부화
- dead-letter 운영 자동화
- Kafka consumer 기반 상태 전이
- WebClient timeout confirmation flow
- provider callback API와 `providerTransactionId` 기반 callback idempotency
- refresh token / key rotation / user store
- admin 레벨 재처리 / 재검증 API 고도화
