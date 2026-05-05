# WebClient Timeout Confirmation Implementation Review

## Purpose / 목적

이 문서는 WebClient approve 요청 timeout 이후 provider 결제 상태가 불명확해지는 문제를
다음 partition에서 구현할 수 있는지 검토하기 위한 implementation review입니다.

현재 목표는 production code를 바로 변경하는 것이 아니라, 개인 포트폴리오 프로젝트에서
실제 PG 계약 없이도 안전하고 검증 가능한 최소 구현 범위를 정하는 것입니다.
따라서 이 문서는 구현된 기능을 주장하지 않으며, 다음 partition에서 선택할 수 있는
scope, risk, test 기준을 분리합니다.

## Current Implementation / 현재 구현

현재 payment/provider 구현은 아래 상태입니다.

- `PaymentProviderClient`는 `approve`, `cancel` 계약만 제공합니다.
- `MockPaymentProviderClient`는 description token 기반 실패 시뮬레이션을 제공합니다.
- `ExternalPaymentProviderClient`는 WebClient 기반 external adapter입니다.
- external adapter는 `baseUrl`, `apiKey`, `approvePath`, `cancelPath`, `connectTimeout`, `readTimeout`를 사용합니다.
- provider 응답 status는 `APPROVED`, `CANCELLED`, 그 외 `FAILED`로 매핑됩니다.
- WebClient response error와 timeout은 `BusinessException(ErrorCode.PAYMENT_FAILED, ...)`로 매핑됩니다.
- `PaymentService#approve`는 `paymentRequestId`로 기존 payment를 먼저 조회합니다.
- 기존 payment가 있으면 provider approve를 다시 호출하지 않고 기존 payment 기준 응답을 반환합니다.
- `PaymentStatus`는 현재 `READY`, `APPROVED`, `FAILED`, `CANCELLED`만 갖습니다.
- `Payment`에는 `paymentRequestId`, `providerReference`, `providerTransactionId`, `version` 필드가 있습니다.
- `PaymentRepository#findByProviderTransactionId`는 callback/idempotency 확장 지점입니다.

현재 구현하지 않은 범위는 아래와 같습니다.

- timeout 전용 result category
- confirmation provider 계약
- `CONFIRMATION_REQUIRED` 같은 payment 상태
- confirmation retry worker 또는 admin API
- provider callback endpoint
- 실제 PG 계약 기반 confirmation integration
- OpenAPI confirmation path
- automated timeout confirmation test

## Problem / 문제

WebClient approve 요청이 timeout 되면 provider의 실제 처리 결과를 알 수 없습니다.

가능한 상태는 아래처럼 나뉩니다.

- provider가 승인했지만 backend가 응답을 받기 전에 timeout
- provider가 거절했지만 backend가 응답을 받기 전에 timeout
- 요청이 provider에 도달하지 않음
- provider가 아직 처리 중
- provider 또는 network가 일시 장애 상태

현재처럼 timeout을 `PAYMENT_FAILED`로만 매핑하면 구현은 단순하지만,
승인 여부가 불명확한 거래를 확정 실패로 단정하는 한계가 있습니다.
반대로 approve를 단순 재시도하면 중복 승인 위험이 있습니다.

따라서 timeout은 confirmed failure가 아니라 unknown result로 분리할지 검토해야 합니다.

## Mock/Dummy Provider Constraint / 개인 포트폴리오 제약

이 프로젝트에는 실제 PG 계약, provider confirmation API 문서, signature rule,
event sequence rule이 없습니다.

따라서 다음 구현에서 사용할 수 있는 provider integration은 아래 범위로 제한해야 합니다.

- mock provider
- dummy provider
- local test provider
- Testcontainers 또는 WireMock-like simulated behavior
- deterministic timeout, 5xx, unknown response scenario

문서와 코드 어디에서도 real payment provider production integration을 구현한 것처럼
표현하지 않아야 합니다.
구현하더라도 “실제 PG 연동”이 아니라 “timeout/unknown 상태 모델과 복구 분기 검증”에
초점을 둬야 합니다.

## Implementation Options / 구현 선택지

검토 대상은 세 가지입니다.

- Option A: Keep as Future Scope
- Option B: Minimal Mock/Dummy Confirmation Flow
- Option C: Full Provider Callback + Confirmation Model

## Option A - Keep as Future Scope

production code를 변경하지 않고 현재 design note만 유지합니다.

장점:

- behavior change가 없습니다.
- 현재 portfolio baseline을 흔들지 않습니다.
- OpenAPI, DB schema, Modulith boundary, orchestration flow 영향이 없습니다.

단점:

- WebClient timeout을 unknown으로 분리하는 구현 역량은 보여주지 못합니다.
- `ExternalPaymentProviderClient`의 timeout 설정은 있지만, timeout 이후 상태 모델은 계속 문서 수준에 머뭅니다.
- 다음 구현 방향이 계속 추상적으로 남습니다.

적합한 경우:

- 다음 작업에서 payment model 변경을 피하고 observability, metrics, docs 정리를 우선할 때
- 현재 테스트 baseline 안정성이 더 중요한 시점

## Option B - Minimal Mock/Dummy Confirmation Flow

다음 partition에서 가장 현실적인 후보입니다.

핵심은 실제 PG 계약을 흉내 내지 않고,
mock/dummy provider로 timeout 이후 unknown 상태를 작게 모델링하고 검증하는 것입니다.

가능한 scope:

- provider approve 결과에 timeout/unknown category를 추가합니다.
- `PaymentStatus.CONFIRMATION_REQUIRED` 추가를 검토합니다.
- `PaymentProviderClient`에 minimal confirmation 계약을 추가합니다.
- mock/dummy provider가 deterministic token으로 timeout/unknown/approved/failed confirmation을 반환하게 합니다.
- `PaymentService#approve`는 timeout/unknown 결과를 `FAILED`로 단정하지 않고 payment row에 남깁니다.
- confirmation은 provider callback endpoint 없이 service/test 수준에서 먼저 검증합니다.
- real PG API path, signature, callback endpoint, OpenAPI path는 추가하지 않습니다.

권장하는 최소 구현 원칙:

- status/model 변경은 작고 명시적으로 둡니다.
- 새로운 상태는 order completion을 확정하지 않습니다.
- 같은 `paymentRequestId`는 계속 primary idempotency key로 유지합니다.
- provider approve 재호출 대신 confirmation만 재시도합니다.
- provider raw payload, API key, token은 log/audit에 남기지 않습니다.
- metric tag가 필요하면 bounded value만 사용합니다.

장점:

- timeout을 failure와 unknown으로 분리하는 핵심 모델을 보여줄 수 있습니다.
- 실제 PG 계약 없이도 deterministic test가 가능합니다.
- provider callback endpoint보다 scope가 작습니다.
- `paymentRequestId` idempotency와 연결하기 쉽습니다.

단점:

- `PaymentStatus` enum 추가와 DB 저장 값 확장이 필요합니다.
- `PaymentProviderResult` 또는 별도 result type 변경이 필요할 수 있습니다.
- orchestration이 confirmation-required payment를 어떻게 종료할지 결정해야 합니다.
- 구현 후 verification matrix, claim audit, test-report 갱신이 필요합니다.

적합한 경우:

- 다음 partition에서 작은 production change와 테스트를 수용할 수 있을 때
- 포트폴리오에 “timeout unknown state handling”을 검증된 범위로 추가하고 싶을 때

## Option C - Full Provider Callback + Confirmation Model

provider callback endpoint, signature verification, callback event idempotency,
out-of-order handling, confirmation reconciliation까지 함께 구현하는 선택지입니다.

필요 범위:

- callback Controller endpoint
- request DTO와 signature/shared-secret 검증
- provider event id 저장 또는 conditional update
- callback status mapping
- confirmation status mapping
- duplicate/out-of-order callback 처리
- terminal state 충돌 정책
- settlement 재개 또는 recovery candidate 정책
- audit/log/metric event
- OpenAPI path와 security 설명
- unit/integration/security tests

장점:

- 실제 payment integration에 가까운 설계 문제를 폭넓게 보여줄 수 있습니다.
- timeout confirmation과 provider callback의 충돌 정책까지 정리할 수 있습니다.

단점:

- 실제 PG 계약이 없으면 dummy provider 설계가 과도하게 커집니다.
- 보안, idempotency, state reconciliation 범위가 현재 portfolio scope를 크게 넓힙니다.
- OpenAPI, DB schema, admin recovery, Modulith boundary, integration test 영향이 큽니다.
- 구현 claim을 정직하게 유지하기 어렵고 review 비용이 큽니다.

판단:

- 지금은 권장하지 않습니다.
- callback은 timeout confirmation model이 작게 검증된 뒤 후속 범위로 유지하는 편이 안전합니다.

## Recommended Next Step / 권장 다음 단계

Recommendation: Option B를 다음 partition의 후보로 진행합니다.

단, 다음 조건을 만족할 때만 구현합니다.

- 실제 PG 계약 구현이라고 표현하지 않습니다.
- mock/dummy provider 기반 deterministic scenario로 제한합니다.
- provider callback endpoint는 추가하지 않습니다.
- OpenAPI YAML은 실제 HTTP endpoint가 생기기 전까지 변경하지 않습니다.
- `CONFIRMATION_REQUIRED` 같은 상태 추가가 전체 order/orchestration flow를 과도하게 흔들지 않는지 먼저 확인합니다.
- timeout 이후 order를 어떤 terminal state로 둘지 명확히 정합니다.

상태/model 변경이 예상보다 커지면 Option A로 되돌리고,
payment/settlement observability 또는 admin recovery 문서 보강을 먼저 진행하는 편이 낫습니다.

## Required Code Changes / 필요한 코드 변경

Option B를 구현한다면 최소 후보는 아래입니다.

- `PaymentStatus`에 `CONFIRMATION_REQUIRED` 추가 검토
- `PaymentProviderResult`에 provider result category 또는 timeout/unknown indicator 추가
- `PaymentProviderClient`에 confirmation 계약 추가 검토
- `MockPaymentProviderClient`에 deterministic timeout/unknown simulation 추가
- `ExternalPaymentProviderClient` timeout mapping을 confirmed failure와 분리
- `PaymentService#approve`에서 unknown result를 payment row로 저장
- confirmation result를 기존 payment row에 반영하는 service method 추가
- 필요 시 `PaymentApplication` public contract 확장
- audit/log/metric 추가 시 high-cardinality tag 금지 유지

주의할 점:

- enum/status 추가는 behavior change이므로 별도 구현 partition에서 full baseline을 실행해야 합니다.
- DB column은 enum string 저장 방식이므로 migration이 필요 없을 수 있지만, 상태 의미 문서는 갱신해야 합니다.
- order/orchestration이 `CONFIRMATION_REQUIRED`를 받았을 때 실패로 닫을지, pending으로 남길지 먼저 결정해야 합니다.

## Required Tests / 필요한 테스트

Option B 구현 시 최소 테스트는 아래입니다.

- mock/dummy approve timeout -> payment `CONFIRMATION_REQUIRED` 저장
- timeout/unknown result는 provider approve 재호출로 해결하지 않음
- 동일 `paymentRequestId` replay는 기존 confirmation-required payment를 반환
- confirmation `APPROVED` -> payment `APPROVED`
- confirmation `FAILED` / `REJECTED` / `NOT_FOUND` -> payment `FAILED`
- confirmation `UNKNOWN` / timeout again -> 상태 유지 또는 retry/manual 대상 유지
- confirmation result가 orderId/amount와 맞지 않으면 상태 전이하지 않음
- `PaymentServiceTest` 기존 idempotency 회귀 유지
- `CommerceOrchestrationService`가 confirmation-required payment를 과장된 success로 처리하지 않는지 검증
- log/metric을 추가한다면 high-cardinality tag 방지 테스트

가능하면 local simulated provider는 test scope 안에서만 둡니다.
새 runtime dependency는 추가하지 않는 편이 좋습니다.

## OpenAPI & Docs Impact / OpenAPI와 문서 영향

이번 review partition에서는 OpenAPI YAML을 변경하지 않습니다.

Option B 구현 후에도 HTTP endpoint가 생기지 않는다면 OpenAPI path를 추가하지 않습니다.
만약 admin confirmation endpoint를 실제로 추가하는 후속 partition이 생기면,
그때 Controller mapping과 함께 OpenAPI를 갱신합니다.

필요한 문서 영향:

- `docs/verification-matrix.md`: 구현 후 Future Scope에서 Verified/Implemented로 이동할지 판단
- `docs/verification/claim-audit.md`: 테스트 근거가 생긴 뒤에만 claim 상태 변경
- `docs/test-report.md`: 구현 테스트 결과 추가
- `docs/flows/payment-timeout-confirmation-flow.md`: proposed state가 실제 구현으로 바뀐 범위만 명확히 갱신
- `README.md`: 구현된 범위만 보수적으로 반영

## Risks / 리스크

- timeout을 `FAILED`에서 `CONFIRMATION_REQUIRED`로 바꾸면 order failure branch가 달라질 수 있습니다.
- payment state가 pending으로 남을 때 admin/recovery 흐름이 비어 있을 수 있습니다.
- confirmation-required 상태가 settlement 시작 전 대기 상태인지, order failure로 닫히는 상태인지 애매해질 수 있습니다.
- 실제 PG 계약이 없으므로 provider status mapping을 일반화해서 과장할 위험이 있습니다.
- WebClient timeout 예외 종류가 `TimeoutException`만으로 충분하지 않을 수 있습니다.
- `PaymentProviderClient` public contract 변경은 mock/external 구현과 테스트를 함께 바꿔야 합니다.
- callback까지 함께 구현하면 scope가 급격히 커집니다.

## Decision / 결정

Decision: 다음 partition에서는 Option B를 구현 후보로 검토합니다.

구현을 시작하기 전 최종 gate는 아래입니다.

- 상태 추가가 `PaymentStatus`와 `PaymentService` 수준의 작은 변경으로 끝나는지 확인합니다.
- orchestration flow가 confirmation-required payment를 어떻게 다룰지 test-first로 고정합니다.
- provider callback endpoint는 제외합니다.
- real PG integration claim은 금지합니다.
- OpenAPI YAML은 실제 endpoint가 없으면 변경하지 않습니다.

이 조건을 만족하지 못하면 Option A로 유지하고,
timeout confirmation은 계속 Future Scope / Design Note로 남깁니다.
