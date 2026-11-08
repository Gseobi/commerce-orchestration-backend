# HB3b Tenant Order Commands / 내부 주문 생성·취소

- 기준: `fda2d99` (HB3a), 2026-11-08
- 범위: Java 내부 계약과 V10. HTTP, OpenAPI, PG, 이벤트 발행 및 하위 거래 모듈은 변경하지 않습니다.

## Contract

- `TenantOrderCommands.create(merchantId, command)`: 양수 owner/store, 고객 식별자, 양수 금액 및 통화 코드 형식 필수.
  `numeric(18,2)`에 맞지 않는 금액을 반올림하지 않습니다. 통화 코드 형식만 검사하며 PG 지원 통화나 가격 정책 검증은 아닙니다.
- `merchant::api`의 `OrderStoreAccess`가 한 SQL에서 같은 owner의 활성 사업자와 활성 스토어를 확인합니다.
  없음/다른 owner/비활성은 모두 `STORE_NOT_FOUND`. `FOR SHARE`로 두 행을 호출자의 쓰기 트랜잭션 종료까지 보호합니다.
  주문 모듈은 merchant repository/entity를 참조하지 않습니다.
- 주문은 `TENANT / CREATED`로 저장합니다. 스토어 ID·timezone·recovery policy는 생성 당시 값이며 변경하지 않습니다.
  현재 유효 정책은 `MANUAL_REVIEW`뿐입니다. 스냅샷 저장은 복구 실행을 의미하지 않습니다.
- `cancelCreated(merchantId, orderId)`는 `TENANT + owner + order + snapshot + CREATED` 조건의 원자적 갱신입니다.
  소유 주문이 이미 `CANCELLED`면 추가 변경 없이 반환합니다. 다른 상태는 `INVALID_ORDER_STATE`,
  foreign/legacy/missing은 `ORDER_NOT_FOUND`. 결제 시작 이후의 취소/환불은 처리하지 않습니다.
- 반환값은 HB3a의 고객정보 없는 `TenantOrderView`입니다. 내부 caller는 신뢰된 identity와 WRITE 권한을 먼저 검증해야 합니다.
  포트 자체는 membership 검사나 동시 revoke 보장을 하지 않으며, 현재 HTTP/legacy orchestration/worker 호출자는 없습니다.

## Migration And Review

| 검토한 위험 | 반영한 보완 |
|---|---|
| 상태 검증 직후 store 또는 merchant 비활성화 | 같은 DB 트랜잭션에서 두 행을 잠그고 INSERT. 두 잠금과 롤백을 별도 DB 연결로 검증 |
| 다른 owner의 store 참조 또는 정책 위변조 | `(merchant_id, store_id)` 복합 FK, LEGACY/TENANT 스냅샷 제약, UPDATE 불변 트리거 |
| 과거 V9 TENANT 데이터의 스냅샷을 알 수 없음 | V10 CHECK는 `NOT VALID`. 기존 내용 그대로 유지, 새 쓰기에 강제. snapshot 없는 기존 주문에 취소 상태 전이를 적용하지 않음 |
| 신규 제약이 HB2 검증을 가릴 위험 | V8→V9 업그레이드는 정확히 V9에서 검증하고, V9→V10 보존 테스트를 별도 추가. 신규 TENANT fixture에는 snapshot을 명시 |
| 주문 재호출/동시 처리 | 취소는 원자적 상태 조건과 no-op replay. 생성은 idempotent하지 않아 자동 재호출 금지; HTTP 공개 전 요청 키와 replay 정책 필요 |

V10은 기존 migration을 수정하거나 임의 backfill/삭제를 하지 않습니다. legacy 주문에는 세 컬럼 모두 NULL입니다.
기존 snapshot 없는 TENANT 행이 있다면 추후 승인된 정합성 정책 없이는 CHECK를 `VALIDATE`할 수 없습니다.
복합 FK 때문에 주문이 참조하는 스토어의 물리 삭제·소유자 변경이 제한됩니다.
대형 DB의 DDL lock·index 비용과 잠금 경합은 배포 전에 별도로 검증해야 합니다.

## Next Gates

- HB3c: 결제/정산/알림/audit/step owner 전파. 외부 효과 이전에 owner와 상태를 원자적으로 확인하는 계약 필요.
  [HB3c-1](tenant-payment-preparation.md)에서 취소와 경쟁하는 조건부 claim과 내부 READY 기록을 추가했습니다.
  외부 결제 호출은 아직 연결하지 않습니다.
- HB3d/e: event/worker/recovery 및 인증 HTTP 연결. 생성 요청 중복 방지, membership 변경 경쟁, 전체 tenant negative test.
- 실제 IdP/PG/알림 연결 및 운영 배포는 이번 범위가 아닙니다.
