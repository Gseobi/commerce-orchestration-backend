# HB3c-1 Tenant Payment Preparation / 내부 결제 준비

- 기준: `0282fae` (HB3b)
- 범위: 주문 선점과 같은 소유자의 초기 결제 기록. HB3c 전체 완료가 아닙니다.
- 이 문서는 HB3c-1 시점의 기록입니다. 현재 구현과 수치는 [HB3c-2 추적 설계](tenant-payment-preparation-trace.md)를 참고합니다.

## Contract And Review

- 내부 orchestration은 `TenantOrderPaymentClaim`과 `TenantPaymentRecords`만 호출합니다.
  내부 호출자가 제공하는 값은 owner/order ID뿐이며 결제 금액과 저장할 owner/order는 선점된 주문에서 가져옵니다.
- 주문 SQL의 `TENANT + owner + order + store snapshot + CREATED` 조건을 만족할 때만 `PAYMENT_PENDING`으로 갱신합니다.
  다른 사업자·LEGACY·미존재 주문은 `ORDER_NOT_FOUND`, 자기 주문의 상태 불일치·snapshot 누락은 `INVALID_ORDER_STATE`입니다.
- 기존 V9 복합 FK가 부모와 결제 기록의 owner 일치를 강제합니다. 준비는 `TENANT / READY` row만 생성합니다.
  PG 승인, 통화 확정, 요청 키, callback, refund, retry worker는 만들지 않습니다.
- 선점과 결제 INSERT는 단일 트랜잭션으로 묶입니다. 두 하위 포트는 `MANDATORY`로 단독 호출을 차단하되
  이미 존재하는 임의 트랜잭션까지 권한 증명으로 간주하지 않습니다. 내부 coordinator를 통해 호출해야 합니다.
- 취소와 선점은 같은 `CREATED` 조건을 공유합니다. 동시 요청 중 하나만 성공하고 INSERT 실패 시 선점도 롤백됩니다.
  재호출을 기존 결제 반환으로 처리하지 않으며, 중복 호출은 상태 오류입니다.
- 이 내부 서비스는 HTTP, legacy orchestration, scheduler 또는 provider에 연결하지 않습니다.
  미래 호출자는 인증된 WRITE 권한을 선행 검사해야 합니다. 사업자/스토어 상태 재검사와 membership revoke 정책,
  결제 요청 키·통화 일치 및 실제 dispatch/confirmation은 아직 필요합니다.

## Goal Completion / 설계 달성률 집계

같은 수준의 설계 단위에 동일 가중치를 적용하고, 구현·검증이 끝난 항목만 분자에 넣습니다.
부분 구현은 표시하되 완료 수에는 넣지 않습니다. 아래 수치는 공수·일정·출시 준비도 추정이 아닙니다.

| 기준 | 완료 | 진행 중·미완료 |
|---|---:|---|
| HB3c 내부 경계 6개: 주문 선점, 결제, 정산, 알림, audit, step | 1/6 (16.7%) | 주문 선점 완료. 결제는 READY 기록만 구현, 다른 4개는 미구현 |
| HB3a~e 5개 | 2/5 (40.0%) | a 조회, b 생성·취소 완료. c 진행 중, d 이벤트/worker 및 e HTTP 미완료 |
| HB1~6 구현 단위 | 2/6 (33.3%) | HB1 outbox 분리, HB2 V9/legacy guard 완료. HB3 진행 중, HB4~6 미완료. 운영 배포 점검은 별도 |
| 로드맵 P0~P6 전체 단계 | 0/7 (0%) | P0 진행 중. 전체 SaaS 거래 경로 격리와 credential 저장 경계가 남음. P1~P6 완료 없음 |

HB3c 분모는 [HB3 분할 계획](tenant-order-read-boundary.md)의 하위 모듈 5개와
[HB3b 후속 조건](tenant-order-write-boundary.md)의 원자적 주문 선점 1개입니다.
다음 내부 단위는 정산·알림·audit·step으로 owner를 전달하는 계약입니다. 실제 결제 효과는 HB5 검증 전 열지 않습니다.
