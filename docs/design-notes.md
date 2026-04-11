# Design Notes

## 현재 설계 의도

- 주문 생성 이후 후속 처리 흐름을 명시적으로 제어하는 orchestration 중심 구조를 먼저 만듭니다.
- 각 domain은 자체 상태를 가지되, 흐름 제어는 `CommerceOrchestrationService`로 모읍니다.
- 실제 외부 연동보다 상태 전이, step 기록, outbox 확장 포인트를 먼저 보이게 합니다.

## 초기 상태 전이

- `CREATED`
- `PAYMENT_PENDING`
- `PAID`
- `SETTLEMENT_REQUESTED`
- `NOTIFICATION_REQUESTED`
- 추후 `COMPLETED`, `FAILED`, `CANCELLED` 강화

## TODO

- payment provider 실제 연동
- retry / backoff 정책
- idempotency key 기반 중복 처리 방지
- compensation 로직 구체화
- outbox publisher / Kafka consumer 연결
- admin / internal API 추가
