# HB3c-3 Tenant Downstream Status / 정산·알림 조회 경계

- 기준: `41e6c97` (HB3c-2)
- 범위: 정산과 알림의 tenant 소유권을 검증하는 내부 상태 조회. writer/dispatch는 범위 밖입니다.

## Contract And Review

- `TenantDownstreamStatusQuery`는 먼저 `TenantOrderQuery`로 부모를 검증합니다.
  다른 사업자·없는 주문·LEGACY 주문은 동일한 `ORDER_NOT_FOUND`이며 하위 조회는 실행하지 않습니다.
- 확인된 주문의 owner/order를 `TenantSettlementStatusQuery`, `TenantNotificationStatusQuery`에 전달합니다.
  각 모듈은 자체 테이블에서 `TENANT + merchant_id + order_id`를 모두 검사합니다.
- 응답은 각 테이블에서 ID가 가장 큰 행의 상태 하나 또는 빈 값입니다. 알림 수신자·채널·payload·실패 상세,
  정산 메모는 조회하지 않습니다. 빈 값은 하위 행 없음이며 성공이나 실패를 뜻하지 않습니다.
- 내부 호출자의 선행 READ 권한 검사가 필요합니다. 소유권 조회는 membership 검사나 RLS가 아닙니다.
  두 하위 조회는 READ COMMITTED 기본 동작을 따르며 단일 시점의 결합 상태를 보장하지 않습니다.
- 조회는 상태 변경, child row 생성, PG/정산/알림 호출이나 outbox 발행을 하지 않습니다.
  `READY` 결제와 `PAYMENT_PENDING` 주문은 결제 확인의 근거가 아닙니다.
- 기존 LEGACY ORM과 worker, V9 FK, migration, HTTP/OpenAPI는 변경하지 않습니다.
  TENANT 정산·알림 테스트 행은 격리 PostgreSQL에만 직접 생성합니다.

## Completion

[기존 분모](tenant-payment-preparation.md)를 유지합니다. HB3c는 완결 1/6 (16.7%),
부분 구현 5/6 (결제·감사·단계의 결제 준비 범위, 정산·알림의 상태 조회 범위)입니다.
HB3는 2/5 (40%), HB1~6은 2/6 (33.3%), P0~P6 완결은 0/7입니다.
부분 구현은 완결 수에 포함하지 않습니다.

다음 조건은 신뢰할 수 있는 결제 승인 확인 및 상태 전이입니다. 그 결과에 근거하는 정산·알림 writer,
외부 효과의 중복 방지, 이벤트/worker 및 권한 적용은 별도 단위로 남습니다.
