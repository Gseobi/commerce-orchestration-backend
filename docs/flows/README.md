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
- [draw.io 원본](../diagrams/source/commerce_orchestration_order_flow.drawio)
- [PNG 이미지](../diagrams/png/commerce_orchestration_order_flow.png)
- [PDF 문서](../diagrams/pdf/commerce_orchestration_order_flow.pdf)

### Outbox Retry / Dead-Letter Flow
- [draw.io 원본](../diagrams/source/commerce_orchestration_outbox_retry_dead_letter.drawio)
- [PNG 이미지](../diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png)
- [PDF 문서](../diagrams/pdf/commerce_orchestration_outbox_retry_dead_letter.pdf)

## Covered Flow

- order create / detail / orchestration 시작
- payment success / failure
- settlement compensation
- notification policy 분기
- admin retry / ignore / reprocessing

## TODO

- notification retry / manual intervention flow
- table-level flow annotation