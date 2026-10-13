# HB2 Tenant Ownership Migration / 확정 설계

- Date: 2026-10-13, base `e3e4425`
- 사용자 결정: 기존 데이터 보존/legacy 격리, 신규 SaaS 거래 tenant 필수.
- 이 unit은 expand migration + legacy runtime guard입니다. SaaS 거래 생성·HB3 전체 owner 전파는 미구현입니다.

## Decision / 선택

7개 거래 테이블에 `ownership_mode` (LEGACY/TENANT), `merchant_id`를 추가합니다.
기존 row와 현재 demo writer는 LEGACY/NULL이며 임의 tenant 배정·내용 변경·삭제를 하지 않습니다.
TENANT row는 양수 merchant FK가 필수이고, business child/audit는 tenant order에 같은 owner로 연결해야 합니다.
신규 SaaS writer는 HB3에서 TENANT를 명시해야 하며 default LEGACY가 tenant 누락을 막아주는 것은 아닙니다.

- orders의 `(merchant_id,id)`와 `(ownership_mode,id)` unique를 부모 참조점으로 사용.
- child의 merchant/order composite FK로 TENANT 간 owner 불일치 및 legacy 부모 연결 거부.
- scope/order FK는 `NOT VALID`: 기존 orphan을 보존하고 신규 orphan·LEGACY→TENANT parent 참조는 거부.
  기존 이력이 모두 정상이라는 뜻이 아니며 정합성 조사 후 VALIDATE를 별도 진행해야 합니다.
- ownership는 INSERT 후 immutable DB trigger로 보호. 임의 owner 이동/legacy 자동 전환 없음.
- audit의 legacy `order_id=NULL`은 보존. TENANT business audit에는 order 필수이며 platform/membership audit는 후속 별도 설계.

## Runtime Guard / 현재 애플리케이션 경계

공유 `LegacyCommerceEntity`는 두 ownership 필드를 읽기 전용으로 매핑하고 새 entity는 LEGACY로 생성합니다.
각 기존 entity의 `@SQLRestriction`으로 ORM 조회를 LEGACY로 제한하고 outbox/notification bulk claim에도 명시 predicate를 넣습니다.
기존 API/worker/복구는 TENANT row를 조회·claim하지 않습니다. 이는 일반 multi-tenant query 구현이 아니라
기존 global runtime의 차단 장치입니다. native SQL/직접 DB 권한을 격리하는 RLS는 아닙니다.
HB3에서 restriction을 무조건 제거하지 말고 owner 필수 계약/queries/worker/복구 negative tests로 대체해야 합니다.
DB FK는 이미 발생한 외부 PG/채널 효과를 rollback하지 않습니다. HB3은 외부 호출 전에 owner/권한을 확인하고,
HB5는 durable intent/provider idempotency/unknown 결과 확인을 별도로 검증해야 합니다.

## Upgrade / 적용과 트레이드오프

- V1~V8 변경 없이 V9 forward migration. 공유/운영 DB에서 자동 실행·초기화하지 않음.
- ALTER/unique index/FK/trigger는 table lock·IO 비용이 있습니다. 데이터 크기/유지보수 창을 확인한 후 배포합니다.
- 새 owner FK에 따른 parent 삭제/변경 거부는 의도된 보호이며 finance history 삭제 정책을 별도 설계합니다.
- ORM 필터는 모든 신규 DB writer를 통제하지 않으므로 HB3 전 TENANT production 쓰기를 허용하지 않습니다.
- 구 binary는 필터가 없어 TENANT row를 읽을 수 있습니다. old pods를 격리한 뒤 신규 tenant 쓰기를 시작해야 합니다.
- tenant 쓰기 이후 old binary rollback은 금지하고 검증된 forward-fix를 기본으로 합니다.
- 기존 legacy orphan/non-order audit를 보존하는 비용으로 historical FK validation은 남습니다.
- 테스트는 별도 PostgreSQL Testcontainers와 무작위 upgrade schema에서만 데이터를 생성·삭제합니다.

## Verification / 완료 조건

V8 seed 7테이블 내용/row 수 보존, null audit/orphan 보존, V9 새 DB+Hibernate validate,
TENANT owner 누락/불명 merchant/parent mismatch/legacy parent 거부, owner 변경 거부,
7개 legacy repository와 outbox/notification worker의 TENANT 조회·claim 차단, 전체 183개 회귀 보존.
H2 unit profile은 mapping만 검증하며 PostgreSQL 제약/trigger 검증의 대체물이 아닙니다.

[Hibernate SQLRestriction](https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/annotations/SQLRestriction.html)은
비활성화·parameter화할 수 없는 정적 restriction입니다. 그래서 tenant별 동적 권한으로 사용하지 않습니다.
