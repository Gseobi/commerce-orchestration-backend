# Flow Notes

현재 코드 기준 order lifecycle flow는 아래 순서를 따릅니다.

1. JWT 발급
2. 주문 생성
3. orchestration 시작
4. payment 승인
5. settlement 요청
6. settlement outbox event 생성
7. notification 요청
8. notification outbox event 생성
9. 최종 완료 또는 실패 분기 처리

## Happy Path

1. `POST /api/orders`로 주문을 `CREATED` 상태로 저장합니다.
2. `POST /api/orders/{orderId}/orchestrate`가 호출되면 payment step을 `READY`로 기록합니다.
3. payment 승인 후 주문은 `PAID`가 됩니다.
4. settlement 요청 후 주문은 `SETTLEMENT_REQUESTED`가 됩니다.
5. notification 요청 후 주문은 `NOTIFICATION_REQUESTED`가 됩니다.
6. 마지막으로 주문은 `COMPLETED`가 됩니다.

## Failure Path

### payment 실패

- 주문은 `FAILED`로 전이됩니다.
- payment 실패 step과 audit log를 남깁니다.

### settlement 실패

- 주문은 먼저 `FAILED`로 기록됩니다.
- 이후 payment 취소 보상을 수행하고 최종적으로 `CANCELLED`로 전이됩니다.
- compensation 성공 step을 남깁니다.

### notification 실패

- 주문은 `FAILED`로 전이됩니다.
- notification 실패 step을 기록합니다.
- compensation 정책이 아직 확정되지 않아 compensation step은 `READY`로 남깁니다.

## 현재 남아 있는 후속 흐름

- outbox `FAILED` 재발행 정책과 backoff
- notification 전용 compensation 구체화
- 실제 payment provider callback / timeout / retry 처리
- Testcontainers 기반 실인프라 검증
