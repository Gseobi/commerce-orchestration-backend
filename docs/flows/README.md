# Flow Notes

현재 order lifecycle은 아래 순서를 따릅니다.
이 문서는 텍스트 요약본이며, 추후 flow diagram이 들어와도 현재 설명 구조가 그대로 이어지도록 정리합니다.

처음 보는 경우에는 [Architecture Notes](docs/architecture/README.md)로 구조를 먼저 본 뒤 이 문서를 읽는 편이 자연스럽습니다.

1. JWT 발급
2. 주문 생성
3. orchestration 시작
4. payment 승인
5. settlement 요청 + outbox event 생성
6. notification 요청 + outbox event 생성
7. 최종 완료 또는 실패 분기 처리

## 추후 보강 예정 flow diagram

- order orchestration flow
  happy path 기준의 상태 전이와 step 기록 흐름
- outbox retry / dead-letter flow
  `READY -> RETRY_WAIT -> PUBLISHED / DEAD_LETTER` 전이와 retry metadata 갱신 흐름
- notification retry / manual intervention flow
  `AUTO_RETRY`, `MANUAL_INTERVENTION`, `IGNORE` 정책 분기와 admin recovery 흐름

파일 네이밍과 설명 기준은 [Diagram Guide](docs/diagrams/README.md)를 따릅니다.

## 현재 다이어그램 자산

### Order Orchestration Flow

![Order orchestration flow](docs/diagrams/png/commerce_orchestration_order_flow.png)

- [draw.io 원본](docs/diagrams/source/commerce_orchestration_order_flow.drawio)
- [PNG 이미지](docs/diagrams/png/commerce_orchestration_order_flow.png)
- [PDF 문서](docs/diagrams/pdf/commerce_orchestration_order_flow.pdf)

### Outbox Retry / Dead-Letter Flow

![Outbox retry / dead-letter flow](docs/diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png)

- [draw.io 원본](docs/diagrams/source/commerce_orchestration_outbox_retry_dead_letter.drawio)
- [PNG 이미지](docs/diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png)
- [PDF 문서](docs/diagrams/pdf/commerce_orchestration_outbox_retry_dead_letter.pdf)

## Happy Path

1. `POST /api/orders`로 주문을 `CREATED` 상태로 저장합니다.
2. orchestration 시작 시 payment step을 `READY`로 기록합니다.
3. payment 승인 후 주문은 `PAID`가 됩니다.
4. settlement 요청 후 주문은 `SETTLEMENT_REQUESTED`가 됩니다.
5. notification 요청 후 주문은 `NOTIFICATION_REQUESTED`가 됩니다.
6. 마지막으로 주문은 `COMPLETED`가 됩니다.
7. outbox event는 scheduler 또는 명시적 publish 호출을 통해 `PUBLISHED`가 됩니다.

## Failure Path

### payment 실패

- 주문은 `FAILED`로 전이됩니다.
- payment 실패 step과 audit log를 남깁니다.

### settlement 실패

- 주문은 먼저 `FAILED`로 전이됩니다.
- 가장 최근 승인 결제를 취소하는 보상을 수행합니다.
- 주문은 최종적으로 `CANCELLED`가 됩니다.
- compensation step은 `SUCCESS`로 남습니다.

### notification 실패

- 주문은 `FAILED`로 전이됩니다.
- notification 실패 step을 기록합니다.
- compensation step은 `READY`로 남습니다.
- 현재 의미는 `manual intervention or retry required`입니다.

### notification ignore 가능 실패

- notification event는 `IGNORED`로 남습니다.
- 주문 완료 자체는 유지됩니다.
- 현재 범위에서는 비핵심 알림 실패를 주문 성공과 분리하기 위한 정책입니다.

## Outbox 상태 흐름

- `READY`
- `RETRY_WAIT`
- `PUBLISHED`
- `DEAD_LETTER`

publish 실패 시 `retryCount`, `nextAttemptAt`, `failureCode`, `failureReason`이 갱신됩니다.  
최대 재시도 횟수를 넘기면 `DEAD_LETTER`로 이동합니다.

## Admin Reprocessing

- notification 재처리는 실패한 notification event만 다시 처리합니다.
- 성공 시 주문 상태를 `COMPLETED`로 복구합니다.
- outbox 재처리는 `DEAD_LETTER` 이벤트만 즉시 재발행 대상으로 받습니다.
