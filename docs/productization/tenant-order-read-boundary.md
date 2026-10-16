# HB3a Tenant Order Read Boundary / 내부 조회 경계

- Date: 2026-10-17, base `8405a88`
- Scope: HB3의 첫 delivery unit. 내부 주문 header 조회만 구현하며 HB3 전체 완료가 아닙니다.

## Contract / 구현 범위

`order::api`의 `TenantOrderQuery.getOrder(merchantId, orderId)`는 두 양수 ID를 모두 요구합니다.
호출자는 사전에 신뢰된 인증 identity로 membership/merchant 상태와 READ 권한을 확인해야 합니다.
이 port는 인증·권한 증명이나 HTTP endpoint가 아니며 body/header의 merchant ID를 그대로 전달해서는 안 됩니다.
현재 HTTP/controller/orchestration/worker에서 이 port를 호출하지 않습니다.

- `TENANT + merchant_id + id`를 한 query에서 제한하고 foreign/legacy/missing은 모두 `ORDER_NOT_FOUND`.
- null/0/음수 ID는 DB 조회 이전 거부. owner 없는 overload/global fallback 없음.
- immutable `TenantOrderView`: merchantId/orderId/status/totalAmount/currency만 반환.
  customerId/description, payment/settlement/notification 상세는 반환하지 않음.
- 기존 `Order`의 LEGACY restriction과 writer/workflow/worker/admin 경계 유지.
- 새 dependency, migration, 설정, HTTP/OpenAPI path, metric/log 추가 없음.
- TENANT fixture는 격리된 Testcontainers JDBC로만 생성. 운영 tenant writer는 여전히 비활성.

## Design Trade-off / 선택과 한계

기존 LEGACY JPA entity를 재사용해 TENANT entity를 로딩하거나 restriction을 제거하지 않습니다.
별도 Spring Data `Repository`는 owner 필수 native scalar projection 하나만 선언합니다.
`JpaRepository`를 상속하지 않아 이 repository에 global findAll/findById/save를 제공하지 않습니다.
native query에는 Hibernate restriction이 자동 적용되지 않으므로 scope/owner 조건은 SQL에 명시하고
실제 PostgreSQL negative test로 고정합니다. DB 권한/RLS를 제공하는 설계는 아닙니다.

장점은 legacy 경로를 유지하며 tenant 조회를 독립적으로 검증한다는 점입니다.
비용은 native SQL/alias와 immutable view mapping을 별도 유지해야 한다는 점입니다.
읽기 snapshot은 이후 mutation 권한을 보장하지 않습니다. 미래 변경 작업은 owner를 다시 제한하고,
입구의 membership 확인 및 동시 revoke 정책을 별도로 구현해야 합니다.
고객정보를 제외한 header라도 금액/상태는 업무 데이터이므로 외부 노출 시 권한 검사를 생략할 수 없습니다.

## Verification / 검증 시나리오

- Unit: 정상 mapping, not-found와 fallback 없음, invalid ID 6개 입력 조합에서 repository 미호출.
- PostgreSQL: 정상 snapshot/row 불변, cross-merchant·미존재 주문·미존재 merchant 동일 오류,
  LEGACY 제외, 교차 반복 조회 시 owner context 누출 없음, 조회 이후에도 legacy workflow의 TENANT 읽기/변경 차단.
- 전체 unit/integration/Modulith 회귀와 Compose health, PR/main CI는 delivery 보고서에 실제 결과 기록.

## Remaining HB3 / 후속 단위

1. HB3b: owner 필수 주문 생성/상태 변경 내부 계약과 store 소속·정책 snapshot 결정.
   child module이 준비되기 전 HTTP writer·기존 legacy orchestration 연결 금지.
2. HB3c: payment/settlement/notification/audit/step owner 계약과 부모 검증, 외부 호출 이전 owner 검증.
3. HB3d: versioned tenant outbox envelope와 row/payload owner 일치, scheduler/claim/recovery owner 경계.
4. HB3e: 인증·membership을 연결한 HTTP vertical slice와 전체 tenant negative/replay/동시성 테스트.

위 분할은 순서 설계이며 b~e 구현 완료가 아닙니다. HB4 IdP 선택/provisioning, HB5 금전 신뢰성,
HB6 채널/lease/운영, P1~P6 제품 모듈도 남습니다.
[전체 남은 설계와 출시 조건](product-readiness-review.md)을 함께 참고합니다.
