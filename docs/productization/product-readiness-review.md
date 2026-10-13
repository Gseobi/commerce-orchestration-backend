# Product Readiness Review / 남은 설계와 트레이드오프

- 점검일: 2026-10-13, base `e3e4425` + HB2 작업 단위
- 기준: 코드/테스트/roadmap 대조. 출시 승인·법률 검토·비용 견적·부하 검증은 아닙니다.
- P0는 In Progress, P1~P6는 Planned. 목표 기능 전체가 제품화된 상태는 아닙니다.

## Release Blockers / 공통 출시 차단 조건

1. tenant 거래 전체 경로의 격리: 생성→결제→정산→알림→outbox→worker→admin→audit.
2. 실제 IdP 계약 검증과 membership 등록/권한 변경/회수 감사, production demo/mock 차단.
3. 첫 실제 PG/알림 제공자의 sandbox·실환경 계약, unknown payment와 실패 보상/환불의 안전한 운영 경로.
4. 데이터 backup/restore, migration lock/rollback 리허설, 실패 탐지·운영자 대응과 retention/개인정보 정책.
5. 최소 판매 모델, 재고 동시성, 비용/usage 제한 및 정량 부하 baseline.

물리 상품 배송을 출시 범위로 선택하면 첫 실제 carrier 접수/취소/추적 계약도 blocker입니다.
shipping을 MVP에서 빼려면 배송 지원을 제품 약속으로 표시하지 않아야 합니다.

## Module Review / 모듈별 남은 설계

| 모듈·단계 | 남은 작업 / exit evidence | 트레이드오프 / 결정 필요 |
|---|---|---|
| Identity/security HB4 | IdP 선택, dev access token·ID token 거부, JWKS 장애/rotation, issuer 변경 절차 | managed의 운영 부담 vs 계정/기능 비용, self-hosted의 제어권 vs 패치/backup 책임. 제공자 SDK를 domain에 넣지 않음 |
| Merchant/membership P0 | provisioning, 권한 변경/revoke 감사, 마지막 운영자 보호, store 설정 관리 | invite/self-service 편의 vs 권한 상승 방어. 첫 관리자 bootstrap과 actor 검증 책임을 먼저 정의 |
| Ownership HB2/HB3 | V9 upgrade·legacy guard 다음 owner-aware query/worker/복구, event envelope version | shared DB의 비용 vs 누락 predicate 위험. composite FK + 앱 권한 확인; RLS 별도 검토. owner immutable로 merchant 이동은 자동 허용하지 않음 |
| Order HB3/P1 | tenant/store 필수 생성, 가격·통화 snapshot, order line, 취소 규칙 | 동기 orchestration의 단순성 vs 외부 latency/장기 transaction. business state 소유는 order, provider 세부는 adapter |
| Orchestration HB3/HB5 | owner 전파, durable step/실패 보상 상태, typed 실패 category/result | 순차 흐름의 단순성 vs 재시작 가능한 saga/hold. error 이름 prefix·문자열 상태에 대한 의미 결합을 줄이되 일괄 비동기화를 먼저 하지 않음 |
| Catalog P1 | SKU/옵션/가격/판매 상태·버전, merchant별 조회/수정 | 조합 옵션의 유연성 vs 검증 복잡도. 주문 가격은 최신 catalog 값 대신 당시 snapshot |
| Inventory P1 | ledger/reservation/release/expiry, 부족 임계치·알림, 동시 oversell 테스트 | 비관적 lock의 대기 비용 vs optimistic conflict 재시도. 단순 quantity 수정으로 ledger를 대체하지 않음 |
| Payment HB5/P3 | durable intent, 최초 동시 승인/PSP idempotency, amount/currency 대조, callback/confirmation/부분 환불 | 높은 성공률 재시도 vs unknown 상태에서 중복 결제. 자동 재승인 대신 확인/hold; PG capability를 계약으로 분리 |
| Settlement HB5 | demo record 이후 실제 정산 책임 범위와 compensation 실패 상태 | 결제 취소를 즉시 정산 rollback으로 단정하지 않음. PG settlement와 사업자 정산 원장의 책임을 선택해야 함 |
| Notification HB6/P2 | 첫 channel adapter, template/recipient, receipt, fallback/quiet-hours | 채널 다변화 vs 단가/PII/중복 발송. 첫 채널 1개부터 기존 retry 상태 재사용 |
| Outbox HB3/HB6 | versioned tenant envelope, owner-aware candidates/claim, 소비 측 dedupe | at-least-once의 복구성 vs 중복. Kafka send 성공이 소비자의 business 완료나 exactly-once 보장은 아님 |
| Recovery/admin HB5/HB6/P4 | tenant 권한, lease heartbeat/fencing, compensation queue, 승인/재처리 이력 | 자동 회수의 속도 vs 살아 있는 worker 중복 효과. lease 만료만으로 환불/외부 효과를 다시 실행하지 않음 |
| Shipping P5 | shipment/address snapshot, carrier port, 접수/취소/tracking event | polling 단순성/비용 vs callback signature·순서 역전. 주소/전화번호 로그 최소화, 실제 carrier 계약 별도 승인 |
| Observability/release P6 | SLO, alert threshold, queue lag/failed payment 관측, restore rehearsal, capacity/cost | 빠른 출시 vs 운영 대응 가능성. high-cardinality tenant metric 대신 안전한 audit/usage 집계 경계 |
| Common/contracts | owner/value snapshot·금액/통화 불변식, module 경계 테스트, schema 호환성 | 작은 공유 값 타입 vs common mega-module. JWT/provider/entity는 module public 계약으로 유출하지 않음 |
| Credential/adapters P0/P3/P5 | merchant별 secret reference·rotation·권한, timeout/error normalization, 계약 테스트 | 자체 port의 제어권 vs SDK 유지보수 비용. 실 secret의 일반 DB/Git/log 저장 금지, 신규 SDK·권한은 사전 승인 |

## HB Status / 보강 단위 추적

| 단위 | 상태 | 남은 실제 개발 |
|---|---|---|
| HB1 | Complete, PR #8 | tenant event schema는 별도 HB3 |
| HB2 | 이번 단위: V9 + legacy runtime isolation | historical scope FK VALIDATE, 큰 DB lock/upgrade 리허설 |
| HB3 | Planned | tenant-aware 모든 계약/queries/worker/admin/audit + write endpoint 공개 gate |
| HB4 | Planned, IdP 비교만 완료 | 제공자/계정 승인, 실제 연결, provisioning 감사 |
| HB5 | Planned | payment intent/confirmation/callback/실패 보상/환불 |
| HB6 | Planned | 실제 알림 채널/tenant 운영 정책/lease/observability |

HB3은 schema 컬럼이 있다는 이유만으로 완료하지 않습니다. 기존 demo writer는 LEGACY로 남고
신규 SaaS writer를 허용하기 전 모든 downstream 경로를 검증해야 합니다.
HB4는 HB3과 설계 병행할 수 있지만 IdP를 선정하지 않은 상태에서 계정/유료 계약/설치를 자동 수행하지 않습니다.

## MVP Boundary / 제품 범위 결정

최소 출시 후보는 **tenant 격리 주문 모델 + catalog/재고 + 실제 PG 한 개 + 실제 알림 채널 한 개 + 운영 복구**입니다.
이는 계획 제안이며 실제 MVP 범위 승인이나 상용 출시 선언은 아닙니다.
다중 PG 자동 추가·shipping·고급 정산·자동 부분 환불은 초기 범위에서 제외할 수 있으나
제외 시 제품 설명/계약/API에서도 지원을 약속하지 않아야 합니다.

추가로 결정할 항목:

- 목표 merchant 수/peak 주문량, 가용성 목표, 월 인프라+인증+채널 예산.
- 주문 가격/세금/통화/환불·취소 모델, 실제 PG 책임과 사업자 정산 범위.
- 유료 요금제/usage 한도/남용 방어, support 범위, data export/retention/deletion.
- 지역/개인정보·상거래/PG·택배사 계약 요건은 실제 출시 지역과 제공자 확정 후 검토.
- 물리 상품 배송 포함 여부와 첫 channel/provider 우선순위.

현재 성능 수치는 테스트 실행시간이며 처리량/SLO/비용 증거가 아닙니다.
각 exit evidence는 positive/negative, duplicate/concurrent, failure/recovery, tenant A→B 차단과
production profile 설정을 포함해 별도 delivery report로 남겨야 합니다.

연결 문서: [Roadmap](README.md), [SaaS Hardening](saas-hardening-plan.md),
[HB2 확정 설계](tenant-ownership-migration.md).
