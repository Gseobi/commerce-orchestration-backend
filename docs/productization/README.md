# Productization Roadmap

## 1. Purpose

이 문서는 기존 포트폴리오 프로젝트를 소규모 사업자와 개인 사업자가 사용할 수 있는 커머스 운영 백엔드로 확장하기 위한 기준 문서입니다.

현재 저장소는 주문 이후 payment, settlement, notification, outbox 흐름의 신뢰성과 복구 가능성을 보여주는 기반입니다. 상품 판매에 필요한 전체 제품 기능이 이미 구현되어 있다는 의미는 아닙니다. 아래 항목은 `Current`, `Planned`, `Deferred`로 구분하며, 코드와 테스트가 없는 기능을 구현 완료로 표현하지 않습니다.

## 2. Product Goal

목표 사용자는 자체 개발 인력이 적고 여러 외부 서비스를 직접 통합하기 어려운 소규모 커머스 사업자입니다. 제품은 다음 능력을 낮은 운영 비용으로 제공하는 것을 목표로 합니다.

- 주문 접수와 상태 변화 알림
- 재고 가시화, 부족 감지, 임계치 알림
- PG사별 결제 승인, 취소, 환불, callback/confirmation 연동
- 사업자별 영업 시간과 복구 정책에 따른 자동 또는 수동 복구
- 택배 접수, 송장 관리, 배송 상태 추적
- 운영자 감사 이력, 장애 탐지, 재처리와 dead-letter 복구

## 3. Current Baseline

### Current and verified

- 주문 생성과 주문 후 orchestration 진입점
- 결제 승인 멱등성 및 mock provider timeout unknown 상태 기록
- 정산 실패 시 결제 취소 보상
- 알림 실패 정책과 자동/수동 복구
- outbox publish, retry, dead-letter, admin recovery
- metrics, structured logs, audit 기록
- Spring Modulith 경계 검증
- PostgreSQL, Kafka, Testcontainers 기반 통합 테스트 구조
- merchant/store core model, timezone, 기본 수동 복구 정책 snapshot
- merchant-scoped store repository query와 cross-tenant 조회 차단
- merchant membership의 VIEWER/OPERATOR 권한 확인 및 revoke/정지 사업자 접근 차단
- OIDC Resource Server의 RS256/JWKS 검증, issuer/audience/claim 검증, canonical actor identity
- disabled/demo/oidc 분리 및 OIDC에서 legacy business/admin API 접근 차단

근거는 [Verification Matrix](../verification-matrix.md)와 [Claim Audit](../verification/claim-audit.md)에서 관리합니다.

### Not implemented

- HTTP tenant context와 기존 주문/결제/알림/outbox 데이터의 tenant 격리
- 상품, 옵션, SKU, 주문 라인
- 재고 원장, 예약, 차감, 해제, 부족 알림
- 실제 PG credential 관리와 provider별 production adapter
- 실제 환불/부분 환불과 provider callback 처리
- 사업자별 영업 시간과 복구 정책
- 배송, 택배사 접수, 송장, 배송 추적
- production user store, HTTP authorization 연동, membership 관리 API
- dashboard/alert rule과 장기 `PROCESSING` 자동 복구

## 4. Target Module Map

Spring Modulith 기반 modular monolith를 유지합니다. 외부 PG, 알림 채널, 택배사 연동은 domain/application module이 아니라 infrastructure adapter로 격리합니다.

```text
merchant     product/catalog     inventory
    |               |                |
    +---------------+----------------+
                    |
                  order
                    |
              orchestration
       +------------+-------------+
       |            |             |
    payment     notification    shipping
       |            |             |
       +------------+-------------+
                    |
             outbox / audit

external adapters: PG providers, message channels, carriers
```

### Dependency rules

- `merchant`는 사업자와 store 설정의 source of truth입니다.
- `catalog`는 판매 가능한 상품/SKU 정보를 소유합니다.
- `inventory`는 stock ledger와 reservation을 소유하며 order entity가 수량을 직접 변경하지 않습니다.
- `order`는 주문과 주문 라인을 소유하고 외부 provider 세부 타입을 알지 않습니다.
- `payment`, `notification`, `shipping`은 provider-neutral port를 노출합니다.
- `orchestration`은 use case 순서와 보상만 조율하고 adapter 구현을 직접 참조하지 않습니다.
- module 간 호출은 공개 API/named interface 또는 event contract를 사용합니다.
- tenant identifier는 영속 데이터와 비동기 event에서 누락하지 않습니다.

## 5. Delivery Sequence

### P0 - Product foundation

Status: `In Progress`

Current progress:

- merchant/store core model과 Flyway V7 schema
- 내부 `MerchantApplication` contract와 merchant-scoped store 조회
- merchant 간 store 조회 격리 integration test
- 내부 membership role/permission 검증, revoke 및 SUSPENDED 접근 차단

아직 구현하지 않은 범위는 HTTP tenant context, production identity 및 membership 관리 API,
기존 주문/결제/알림/outbox의 tenant ownership입니다.

1. merchant/store 모델, 활성 상태, timezone, 기본 운영 정책
2. production identity/RBAC 설계와 merchant membership
3. tenant context 전파와 repository/API 데이터 격리 테스트
4. secret reference 모델과 credential 저장 경계 정의

Exit criteria:

- 다른 제품 모듈이 `merchantId`를 안정적으로 참조할 수 있습니다.
- 서로 다른 merchant 데이터가 API와 repository에서 섞이지 않는 테스트가 있습니다.
- 실제 secret 값은 DB 일반 컬럼, 로그, Git에 저장하지 않습니다.

### P1 - Catalog, order line, inventory

Status: `Planned`

1. product, option, SKU, 판매 상태
2. order line과 주문 시점 가격 snapshot
3. stock ledger와 available/reserved quantity
4. 주문 생성 시 reservation, 실패/취소 시 release
5. merchant/SKU별 shortage threshold와 알림 event

Exit criteria:

- 동시 주문에서 overselling을 방지하는 통합 테스트가 있습니다.
- 재시도에도 reservation/차감/해제가 중복 적용되지 않습니다.
- 재고 부족 상태와 알림이 merchant별로 조회/통제됩니다.

### P2 - Notification channels

Status: `Planned`

1. channel-neutral notification port와 template model
2. email/SMS/메신저 adapter 중 한 개의 production candidate
3. merchant별 channel, recipient, quiet-hours 설정
4. delivery receipt, retry, fallback policy

Exit criteria:

- 기존 retry/manual/ignore 상태 모델을 채널 adapter가 재사용합니다.
- provider 오류 코드가 내부 failure category로 정규화됩니다.

### P3 - Payment provider platform

Status: `Planned`

1. provider registry와 capability model
2. merchant별 provider account/credential reference
3. approve, cancel, full/partial refund 계약
4. signed callback 검증, duplicate callback 멱등성
5. timeout confirmation과 reconciliation job
6. 첫 실제 PG adapter는 별도 보안/계약 검토 후 선택

Exit criteria:

- provider 교체가 order/orchestration 코드 변경을 요구하지 않습니다.
- callback 서명, replay, amount/currency 불일치 테스트가 있습니다.
- 환불 요청과 provider 결과가 감사 가능한 상태 전이로 남습니다.

### P4 - Recovery policy

Status: `Planned`

1. merchant timezone, 영업 시간, 휴일/비지타임 정책
2. failure category별 retry, hold, manual review, refund policy
3. policy version snapshot과 결정 근거 audit
4. stale `PROCESSING` lease recovery와 operator queue

Exit criteria:

- 같은 장애도 merchant 정책에 따라 다른 복구 경로를 선택합니다.
- 실행 당시 정책 버전과 결정 이유를 재현할 수 있습니다.
- 자동 환불은 금액/횟수 제한과 수동 승인 경계를 가집니다.

### P5 - Shipping and tracking

Status: `Planned`

1. shipment, parcel, address snapshot, tracking number
2. carrier-neutral registration/cancel/tracking port
3. 첫 택배사 adapter와 callback 또는 polling
4. 배송 상태 event와 고객/사업자 알림

Exit criteria:

- carrier 교체가 order domain을 오염시키지 않습니다.
- callback/polling 중복에도 배송 상태가 역행하지 않습니다.
- 개인정보가 로그와 event payload에 불필요하게 노출되지 않습니다.

### P6 - Operability and release readiness

Status: `Planned`

1. Prometheus alert rules와 운영 dashboard
2. backup/restore, migration rollback, disaster recovery rehearsal
3. rate limit, abuse protection, retention/deletion policy
4. SLO, incident runbook, release checklist
5. 비용 계측과 merchant별 usage limit

Exit criteria:

- 장애 탐지부터 복구까지 runbook으로 반복 검증할 수 있습니다.
- production configuration에서 demo auth/mock provider가 비활성화됩니다.

## 6. Cross-cutting Requirements

- Security: least privilege, secret manager reference, callback signature 검증, PII 최소화
- Reliability: idempotency key, explicit state transition, bounded retry, dead-letter, compensation
- Tenancy: 모든 business aggregate와 event에 tenant ownership을 명시하고 격리를 테스트
- Observability: low-cardinality metric tag, correlation identifier, audit trail
- Data: Flyway forward migration, 금액/통화 불변식, timezone 명시
- Cost: 초기에는 modular monolith와 shared infrastructure를 유지하고 실제 부하 근거 없이 서비스 분리하지 않음
- Compatibility: API/event schema 변경은 migration 및 backward compatibility 전략과 함께 수행

## 7. Architecture Decision Triggers

다음 변화는 구현 전에 ADR 또는 implementation review를 작성합니다.

- 새로운 외부 provider SDK/dependency 추가
- credential 또는 개인정보 저장 방식 변경
- module 간 synchronous dependency 추가
- DB locking/concurrency 전략 변경
- event schema 또는 public API 호환성 변경
- 자동 환불처럼 금전적 부작용이 있는 정책 추가

## 8. Immediate Next Unit

`merchant foundation`과 `merchant membership access`는 main에 병합했습니다.
다음 방향은 사용자 선택에 따라 외부 OIDC/JWT 검증이며, [OIDC Membership Security Adapter 설계](oidc-membership-security-design.md)를 기준으로 진행합니다.
Resource Server 및 demo mode 분리는 [OIDC Resource Server](oidc-resource-server.md)로 구현했습니다.
실제 IdP production 연결과 HTTP membership adapter는 아직 구현 완료가 아닙니다.

다음 구현 단위는 읽기 전용 merchant-scoped store HTTP API와 membership 연결입니다.
Resource Server를 재사용하되 tenant 권한 검증을 완료한 단일 slice만 독립 PR로 노출합니다.
catalog/inventory/payment/shipping은 P0의 tenant foundation이 완료된 이후 시작합니다.

실행 절차는 [Module Delivery Playbook](module-delivery-playbook.md)을 따릅니다.
