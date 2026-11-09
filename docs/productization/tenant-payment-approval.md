# HB3c-4 Tenant Payment Approval / 검증 결과 적용

- 기준: `8bdebb3` (HB3c-3)
- 범위: 검증 결과를 주문/결제/감사에 원자적으로 반영하는 내부 경계.
  실제 PG 검증 어댑터와 호출자는 제공하지 않습니다.

## Trust Boundary

- `TenantPaymentApproval`은 내부 caller의 ID를 받되 승인 상태·금액·통화·거래 ID는 caller 입력으로 받지 않습니다.
  동일 owner의 `PAYMENT_PENDING` 주문과 일치하는 `READY` 결제를 먼저 확인합니다.
- 별도 `TenantPaymentApprovalVerifier` SPI는 신뢰할 수 있는 provider 승인과 merchant/payment 결합을 검증해야 합니다.
  adapter가 후보 값을 그대로 복사하는 것은 검증이 아닙니다. 기존 legacy/mock provider를 이 SPI에 연결하지 않습니다.
- 운영 verifier가 없거나 선택할 수 없거나, 결과가 비어 있거나 ID/금액/통화가 일치하지 않으면 쓰지 않습니다.
  테스트에서만 verifier를 대체하여 결과를 주입합니다. 성공 테스트는 실제 provider 검증의 근거가 아닙니다.
- 검증은 쓰기 트랜잭션 밖에서 수행합니다. 적용 시 주문 owner/state/금액/통화와 결제 owner/order/state/금액을
  조건부 UPDATE에서 재검사합니다. 잠금 순서는 주문→결제입니다.
- 주문 `PAID`, 결제 `APPROVED` 및 provider 거래 ID, `TENANT_PAYMENT_APPROVED` 감사가 함께 커밋됩니다.
  중복 거래 ID는 기존 V4 unique index로 차단합니다. 중복 호출은 기존 결과 재전송이 아닌 상태 오류이며 감사 중복 없음.
- 오류 시 상태와 기록은 함께 롤백됩니다. 감사에는 provider ID나 고객 정보가 들어가지 않습니다.
  HTTP/정산/알림/outbox/worker 경로에 연결하지 않습니다.

## Next Gates And Completion

- 실제 PG를 연결하기 전에 merchant provider account와 immutable request binding, 권위 있는 상태 확인 방식,
  인증/서명·재전송·타임아웃 대사 정책을 확정하고 검증해야 합니다.
  현재 V4 거래 ID unique 범위는 전역이므로 provider 간 ID namespace 정책도 계약에 포함해야 합니다.
- 후속 정산/알림 쓰기 역시 검증된 승인 및 권한과 결합하고 외부 실행의 중복 방지를 검증해야 합니다.
- [기존 분모](tenant-payment-preparation.md) 기준 HB3c 완결 1/6 (16.7%), 부분 5/6입니다.
  결제 승인 적용은 구현했지만 운영 검증/승인 흐름은 미완료입니다. HB3 2/5 (40%), HB1~6 2/6 (33.3%), P0~P6 0/7 완결.
