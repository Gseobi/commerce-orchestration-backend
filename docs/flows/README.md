# Flow Notes

주문 이후 주요 처리 흐름과 실패 분기를 정리한 문서입니다.

## Current Lifecycle

1. JWT 발급
2. 주문 생성
3. orchestration 시작
4. payment 승인
5. settlement + outbox event 생성
6. notification + outbox event 생성
7. 완료 또는 실패 분기

## Current Assets

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
`AUTO_RETRY`는 `RETRY_SCHEDULED`와 `nextAttemptAt` 중심으로 다음 시도를 예약하고, due 시점이 된 이벤트는 `NotificationRetryProcessor`를 통해 재처리할 수 있습니다.  
processor는 성공 시 notification event를 `SENT`로 전환하고 order를 `COMPLETED`로 복구하며, 반복 실패 시 backoff 재스케줄 또는 `MANUAL_INTERVENTION_REQUIRED` 전환을 수행합니다.  
`MANUAL_INTERVENTION`은 운영자 확인 후 `retry` 또는 `ignore` API로 개입하고, 명시적 ignore 처리 시 주문은 `COMPLETED`로 복구됩니다.  
실패/보류 상태는 audit log 또는 orchestration step 기록 포인트로 남습니다.

## Covered Flow

- order create / detail / orchestration 시작
- payment success / failure
- settlement compensation
- notification policy 분기
- admin retry / ignore / reprocessing
- notification retry / manual intervention flow
- notification retry processor due event 처리
