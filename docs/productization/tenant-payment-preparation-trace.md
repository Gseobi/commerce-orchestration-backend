# HB3c-2 Tenant Payment Preparation Trace / 결제 준비 추적

- 기준: `9b0cff3` (HB3c-1)
- 범위: 결제 준비 성공에 대한 tenant 감사·단계 기록. 외부 실행이나 실패 이력 저장은 범위 밖입니다.

## Contract

- 내부 coordinator가 주문 선점 결과의 owner/order를 `TenantOrderAuditRecorder`와 내부 단계 writer에 전달합니다.
  주문 선점, READY 결제, 단계, 감사 기록은 모두 같은 트랜잭션에서 커밋됩니다.
- 단계는 `PAYMENT_PREPARATION / SUCCESS`, 감사 동작은 `TENANT_PAYMENT_PREPARED`입니다.
  이는 준비 성공만 의미하며 PG 승인 또는 결제 완료가 아닙니다.
- V9 복합 FK가 부모와 동일한 tenant 소유권을 강제합니다. 두 writer 모두 `MANDATORY`이며 권한 검사 대신 사용하지 않습니다.
- 감사 상세는 고정 문구이며 호출자가 입력한 텍스트, 고객 정보, 결제 요청/제공자 식별자를 기록하지 않습니다.
- 동일 주문 재시도와 취소 경쟁은 기존 상태 선점으로 차단합니다.
  결제·단계·감사 중 하나라도 저장에 실패하면 전부 롤백되며 성공 이력이 남지 않습니다.
  실패 시도 이력, 운영자 신원 및 tenant 조회 API는 추가하지 않습니다.
- 기존 LEGACY 엔티티와 처리 경로, migration, HTTP/OpenAPI에는 변경이 없습니다.

## Completion

[이전 집계](tenant-payment-preparation.md)의 분모를 유지합니다. HB3c는 완결 1/6 (16.7%)이며,
결제·감사·단계는 결제 준비 경로에서만 부분 구현됐습니다. 정산·알림은 미구현입니다.
HB3는 2/5 (40.0%), HB1~6은 2/6 (33.3%), 제품화 P0~P6의 완결 단계는 0/7입니다.
완결 수가 그대로인 것은 부분 구현을 분자에 넣지 않기 때문입니다.

다음은 정산·알림 owner 계약입니다. 실제 결제 확인에 근거한 상태·권한 계약과 외부 호출 안전성이 갖춰지기 전에는
결제 준비 결과로 정산이나 알림을 실행하지 않습니다.
