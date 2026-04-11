# Design Notes

## 1. 왜 현재 구조를 택했는가

이 프로젝트는 주문 이후의 후속 작업을 여러 controller로 분산시키기보다,  
`CommerceOrchestrationService`가 흐름을 제어하고 각 domain service가 자기 상태와 저장소를 소유하는 방향을 택합니다.

핵심 의도는 아래와 같습니다.

- 비즈니스 외부 진입은 `OrderController`에 집중한다.
- orchestration은 흐름만 제어하고, payment / settlement / notification의 저장 상세는 각 domain이 소유한다.
- repository 패키지를 외부 공개하기보다 service 경계를 통해 협력한다.
- 주문 상태, orchestration step, outbox event, audit log를 같이 남겨 운영 가시성을 확보한다.

## 2. 현재 구조 요약

### Order

- 주문 생성과 상태 전이의 소유자는 `OrderService`입니다.
- 외부 비즈니스 API는 `OrderController -> OrderFacade`를 통해 진입합니다.
- 주문 상세 조회도 다른 domain repository를 직접 읽지 않고 domain application service를 통해 상태를 모읍니다.

### Orchestration

- `CommerceOrchestrationService`는 happy path와 실패 분기를 제어합니다.
- orchestration이 직접 사용하는 repository는 `OrchestrationStepRepository`만입니다.
- payment / settlement / notification / outbox / audit는 공개된 application 계약을 통해 협력합니다.

### Payment

- `PaymentService`가 `PaymentRepository`를 내부적으로 사용합니다.
- `PaymentProviderClient`는 `MockPaymentProviderClient`, `ExternalPaymentProviderClient`로 분리되어 있습니다.
- 현재 external 구현은 연동 골격과 기본 예외 매핑까지 포함합니다.

### Settlement / Notification

- `SettlementService`, `NotificationService`는 각자 자기 요청 결과를 저장합니다.
- settlement와 notification은 현재 동일 보상 정책으로 취급하지 않습니다.

### Outbox / Audit

- `OutboxService`는 이벤트 append/read를 담당합니다.
- `OutboxPublisherService`는 scheduler 또는 명시적 호출로 publish를 수행합니다.
- `AuditService`는 replay, rejection, failure branch를 기록합니다.

## 3. Compensation 정책이 어디까지 분리되었는가

### Settlement failure

- settlement 요청 실패 시 주문을 먼저 `FAILED`로 기록합니다.
- 이후 가장 최근 승인 결제를 취소하는 보상을 수행합니다.
- 최종 주문 상태는 `CANCELLED`로 닫습니다.
- orchestration step에는 `SETTLEMENT FAILED`, `COMPENSATION SUCCESS`가 남습니다.

### Notification failure

- notification 실패 시 주문은 `FAILED`로 남깁니다.
- payment/settlement를 되돌리지는 않습니다.
- compensation step은 `READY` 상태로 남기고, 현재 의미는 `retry or manual intervention required`입니다.

### 현재 남겨 둔 부분

- notification 채널별 재시도 횟수
- 무시 가능한 실패와 운영자 개입이 필요한 실패의 구분
- admin 재처리 API와 수동 승인 절차

이 부분은 아직 코드에 과장해서 반영하지 않았고 TODO로 유지합니다.

## 4. Outbox 신뢰성 기준

현재 outbox는 아래 상태를 사용합니다.

- `READY`
- `RETRY_WAIT`
- `PUBLISHED`
- `DEAD_LETTER`

현재 정책은 아래와 같습니다.

- publish 대상은 `READY`, `RETRY_WAIT` 중 `nextAttemptAt <= now`인 이벤트입니다.
- 실패 시 `retryCount`, `failureCode`, `failureReason`, `lastAttemptAt`, `nextAttemptAt`을 갱신합니다.
- `maxRetryCount`를 초과하면 `DEAD_LETTER`로 전환합니다.
- 운영 수동 재처리 SQL은 `docs/sql/outbox-operations.sql`에 정리합니다.

## 5. Payment Provider 설계 기준

- 기본 모드는 `mock`입니다.
- `external` 모드는 실제 provider 연동을 붙일 수 있는 구조와 timeout 설정, API key 헤더, 예외 매핑을 제공합니다.
- provider별 retry, error taxonomy, idempotency key 전략은 아직 고정하지 않았습니다.

## 6. DB 스키마 관리 기준

현재 스키마의 소스 오브 트루스는 Flyway migration입니다.

- `V1__init.sql`
  현재 엔티티 기준 초기 스키마
- `V2__outbox_retry_dead_letter.sql`
  outbox retry/dead-letter 확장

애플리케이션은 Flyway 적용 후 JPA `validate`로 매핑 정합성을 확인합니다.

## 7. 현재 남아 있는 TODO

- payment provider 실제 연동 고도화
- notification 운영 정책 구체화
- dead-letter 운영 자동화
- refresh token / key rotation / user store
- admin 레벨 재처리/재검증 API
