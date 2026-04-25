# Architecture Notes

현재 모듈 경계, 의존 방향, 테이블 관계를 정리한 문서입니다.

이 프로젝트의 architecture 핵심은 주문 이후 흐름을 여러 service가 서로 직접 얽히는 구조로 만들지 않고, `CommerceOrchestrationService`가 후속 처리 흐름을 제어하되 각 domain은 자기 상태와 저장소를 소유하도록 나누는 것입니다.

## 1. Architecture Intent

- order는 주문 생성과 상태 전이를 소유합니다.
- orchestration은 payment / settlement / notification / outbox / audit 흐름을 조합하되, 각 domain repository를 직접 주입하지 않습니다.
- payment는 provider 연동 추상화와 승인/취소 기록을 담당합니다.
- settlement는 정산 요청 기록과 실패 지점을 담당합니다.
- notification은 알림 요청, 실패 정책, retry 상태를 담당합니다.
- outbox는 후속 이벤트 저장, publish, retry, dead-letter 전환을 담당합니다.
- audit은 분기, 실패, 재처리 지점을 추적합니다.
- admin은 전체 orchestration 재실행이 아니라 실패한 하위 처리 단위의 명시적 복구 API를 제공합니다.

최근 reliability hardening 이후 architecture 관점의 추가 기준은 아래와 같습니다.

- `PaymentService`는 `paymentRequestId` 기준으로 idempotent replay를 처리합니다.
- `NotificationRetryProcessor`와 outbox publisher는 Java `synchronized`가 아니라 DB 조건부 상태 전이를 통해 처리 권한을 선점합니다.
- `OutboxPublisherService`는 `KafkaTemplate`을 직접 알지 않고 `OutboxEventPublisher` interface에 의존합니다.
- `KafkaOutboxEventPublisher`는 `infrastructure/kafka` adapter로 분리되어 `KafkaTemplate` 발행과 timeout/failure 변환을 담당합니다.

## 2. Dependency Direction

권장 의존 방향은 아래와 같습니다.

- `controller -> facade/service -> repository`
- `orchestration -> domain application contract`
- 다른 domain의 repository를 직접 주입해서 흐름을 제어하지 않습니다.
- repository 패키지를 외부에 공개하는 방식보다 `*.api` 공개 계약을 통해 협력하는 방식을 우선합니다.
- Spring Modulith의 `@NamedInterface("api")`와 `allowedDependencies`를 통해 module boundary를 명시합니다.

## 3. Current Assets

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_overall_architecture.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_overall_architecture.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf)

## 4. Reference Assets

- [reference draw.io](/docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio)
- [reference PNG](/docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png)
- [reference PDF](/docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf)

## 5. Current Scope

- controller
- orchestration
- payment / settlement / notification
- outbox
- audit
- admin
- common

## 6. Table Relation Overview

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_table_relation_overview.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_table_relation_overview.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_table_relation_overview.pdf)

이 다이어그램은 `src/main/resources/db/migration` 아래 Flyway migration을 source of truth로 삼아 현재 테이블 구조를 요약한 logical relation overview입니다.

실제 FK constraint를 새로 추가한 것이 아니라, 대부분 `order_id`를 기준으로 `orders`와 연결되는 운영/상태 추적 구조를 보여줍니다.

## 7. Why This Matters

커머스 거래 흐름에서는 결제, 정산, 알림, 이벤트 발행이 모두 같은 실패 의미를 가지지 않습니다.

따라서 architecture의 목표는 모든 후속 처리를 하나의 거대한 service에 밀어 넣는 것이 아니라, orchestration은 흐름을 제어하고 각 domain은 자기 상태와 복구 기준을 소유하도록 분리하는 것입니다.

이 구조를 통해 실패 지점을 추적하고, 재시도/보상/수동 개입 대상을 하위 처리 단위로 좁힐 수 있습니다.

## 8. Reliability Hardening Structure

### Payment Idempotency

`CommerceOrchestrationService`는 payment approve 호출 시 `"ORDER-" + orderId + "-PAYMENT-APPROVE"` 형식의 deterministic `paymentRequestId`를 넘깁니다.

`PaymentService`는 approve 시작 시 `paymentRequestId`로 기존 payment를 먼저 조회합니다. 기존 row가 있으면 provider를 다시 호출하지 않고 기존 payment 기준 `PaymentResponse`를 반환합니다. 기존 row가 없을 때만 provider approve를 호출하고 payment row를 저장합니다.

`providerTransactionId` 컬럼과 repository 조회 메서드는 외부 provider callback 멱등성 확장을 위한 준비 지점입니다. 현재 callback API와 callback 처리 flow는 구현되어 있지 않습니다.

### Notification / Outbox Claim

Notification retry와 Outbox publish는 둘 다 DB 상태 조건 기반 claim을 사용합니다.

- notification: `RETRY_SCHEDULED -> PROCESSING -> SENT / RETRY_SCHEDULED / MANUAL_INTERVENTION_REQUIRED`
- outbox: `READY / RETRY_WAIT -> PROCESSING -> PUBLISHED / RETRY_WAIT / DEAD_LETTER`

claim은 조건부 update 결과로 판단합니다.

- update count `1`: 처리 권한 획득
- update count `0`: 이미 다른 실행자가 선점했거나 더 이상 대상 상태가 아니므로 skip

Java `synchronized`를 쓰지 않는 이유는 scheduler와 admin/manual retry가 같은 JVM 안에서만 실행된다는 보장이 없기 때문입니다. 다중 인스턴스나 운영자 수동 실행까지 고려하면 프로세스 내부 락보다 DB row 상태 전이가 더 명확한 조정 지점입니다.

### Outbox Publisher Adapter

```text
OutboxPublisherService
  -> OutboxEventPublisher
      -> KafkaOutboxEventPublisher
          -> KafkaTemplate
```

`OutboxPublisherService`는 outbox 상태 전이, retry count, backoff, dead-letter 정책을 담당합니다. Kafka 발행 세부 구현과 failure message truncation은 `KafkaOutboxEventPublisher`가 담당합니다.
