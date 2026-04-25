# Flow Notes

주문 이후 주요 처리 흐름과 실패 분기를 정리한 문서입니다.

이 프로젝트에서 중요한 흐름은 주문 생성 자체가 아니라, 주문 이후 payment · settlement · notification · outbox publish 단계에서 일부 후속 처리만 실패했을 때 상태를 어떻게 남기고 복구할 것인가입니다.

## 1. Current Lifecycle

1. JWT 발급
2. 주문 생성
3. orchestration 시작
4. payment 승인
5. settlement 요청 + outbox event 생성
6. notification 요청 + outbox event 생성
7. 완료 또는 실패 분기
8. 실패 유형에 따라 compensation / retry / manual intervention / ignore 처리

## 2. Flow Intent

- payment 성공 이후 order를 `PAID`로 전이합니다.
- payment approve는 `paymentRequestId` 기준으로 provider 중복 호출을 방지합니다.
- settlement 성공 이후 order를 `SETTLEMENT_REQUESTED`로 전이하고 후속 outbox event를 남깁니다.
- notification 성공 이후 order를 `NOTIFICATION_REQUESTED` 또는 `COMPLETED` 흐름으로 전이하고 후속 outbox event를 남깁니다.
- settlement 실패는 거래 정합성에 영향을 주는 실패로 보고 payment cancel compensation까지 연결합니다.
- notification 실패는 payment / settlement를 되돌리지 않고, 실패 정책에 따라 retry / manual intervention / ignore로 분기합니다.
- outbox publish 실패는 비즈니스 처리 자체와 분리해 retry / dead-letter 상태로 추적합니다.
- notification retry와 outbox publish는 처리 전 `PROCESSING` 상태 claim을 수행합니다.

## 3. Current Assets

### Order Orchestration Flow

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_order_flow.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_order_flow.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_order_flow.pdf)

### Outbox Retry / Dead-Letter Flow

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_outbox_retry_dead_letter.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_outbox_retry_dead_letter.pdf)

### Notification Retry / Manual Intervention Flow

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_notification_recovery_flow.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_notification_recovery_flow.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_notification_recovery_flow.pdf)

notification 실패 시 `handling_policy` 기준으로 `AUTO_RETRY`, `MANUAL_INTERVENTION`, `IGNORE`로 분기합니다.

- `AUTO_RETRY`는 `RETRY_SCHEDULED`와 `nextAttemptAt` 중심으로 다음 시도를 예약하고, due 시점이 된 이벤트는 `NotificationRetryProcessor`를 통해 재처리할 수 있습니다.
- processor는 성공 시 notification event를 `SENT`로 전환하고 order를 `COMPLETED`로 복구합니다.
- 반복 실패 시 backoff 재스케줄 또는 `MANUAL_INTERVENTION_REQUIRED` 전환을 수행합니다.
- `MANUAL_INTERVENTION`은 운영자 확인 후 `retry` 또는 `ignore` API로 개입합니다.
- 명시적 ignore 처리 시 주문은 `COMPLETED`로 복구됩니다.
- 실패/보류 상태는 audit log 또는 orchestration step 기록 포인트로 남습니다.

### Reliability State Transitions

#### Payment approve

```text
paymentRequestId 없음
  -> provider approve
  -> payment 저장
  -> PaymentResponse 반환

동일 paymentRequestId 있음
  -> provider 재호출 없음
  -> 기존 payment 기준 PaymentResponse 반환
```

orchestration replay에서 같은 주문은 `"ORDER-" + orderId + "-PAYMENT-APPROVE"` key를 다시 사용합니다.

#### Notification retry

```text
RETRY_SCHEDULED
  -> PROCESSING
  -> SENT / RETRY_SCHEDULED / MANUAL_INTERVENTION_REQUIRED
```

due retry 후보를 조회한 뒤 조건부 update로 `PROCESSING` claim을 시도합니다. claim 결과가 `0`이면 다른 실행자가 이미 처리 중이거나 대상 상태가 아니므로 `skippedCount`만 증가시키고 order 조회나 retry 처리는 수행하지 않습니다.

#### Outbox publish

```text
READY / RETRY_WAIT
  -> PROCESSING
  -> PUBLISHED / RETRY_WAIT / DEAD_LETTER
```

publish 후보를 조회한 뒤 조건부 update로 `PROCESSING` claim을 획득한 이벤트만 `OutboxEventPublisher`에 넘깁니다. Kafka 발행 성공 시 `PUBLISHED`, 실패 시 retry 정책에 따라 `RETRY_WAIT` 또는 `DEAD_LETTER`로 전이합니다.

## 4. Covered Flow

- order create / detail / orchestration 시작
- payment success / failure
- payment idempotent replay
- settlement failure compensation
- notification policy 분기
- admin retry / ignore / reprocessing
- outbox retry / dead-letter 전환
- outbox publish claim
- notification retry / manual intervention flow
- notification retry processor due event 처리
- notification retry claim / skipped count 집계

## 5. Why This Matters

커머스 주문 이후 후속 처리는 한 번에 모두 성공한다는 가정으로 설계하기 어렵습니다.

이 문서는 결제, 정산, 알림, 이벤트 발행이 서로 다른 실패 의미를 가진다는 전제에서, 어떤 실패를 되돌릴지, 어떤 실패를 재시도할지, 어떤 실패를 운영자 개입 대상으로 남길지를 흐름 단위로 보여줍니다.
