# Provider Callback Flow Review

## Purpose / 목적

이 문서는 외부 payment provider callback flow를 지금 구현할지, 후속 범위로 유지할지 검토하기 위한 design review 문서입니다.

현재 production code에는 provider callback HTTP endpoint가 구현되어 있지 않습니다. 이 문서는 callback flow를 구현된 기능처럼 주장하지 않기 위해 현재 구현 경계와 후속 구현 시 필요한 설계 결정을 분리합니다.

검토 결론은 현재 포트폴리오 단계에서는 provider callback을 바로 구현하지 않고 Future Scope / Extension Point로 유지하는 것입니다. 실제 provider 연동 강조가 다음 목표가 될 때, 먼저 WebClient timeout confirmation state model을 확정한 뒤 callback flow를 구현하는 편이 적절합니다.

## Current Implementation Boundary / 현재 구현 경계

현재 payment/order/orchestration 구현은 아래 범위까지 포함합니다.

- `CommerceOrchestrationService`는 주문 이후 payment, settlement, notification, outbox 흐름을 조율합니다.
- `CommerceOrchestrationService#paymentRequestId`는 `"ORDER-" + orderId + "-PAYMENT-APPROVE"` 형식의 deterministic key를 생성합니다.
- `PaymentApplication#approve`와 `PaymentService#approve`는 `paymentRequestId`를 인자로 받습니다.
- `PaymentService#approve`는 `PaymentRepository#findByPaymentRequestId`로 기존 payment를 먼저 조회해 같은 approve replay에서 provider approve를 다시 호출하지 않습니다.
- `PaymentProviderClient`는 현재 `approve`, `cancel` 계약만 제공합니다.
- `MockPaymentProviderClient`와 `ExternalPaymentProviderClient`가 `PaymentProviderClient`를 구현합니다.
- `ExternalPaymentProviderClient`는 WebClient 기반 approve/cancel adapter이며, provider 응답 status를 `PaymentStatus`로 매핑합니다.
- `PaymentStatus`의 현재 값은 `READY`, `APPROVED`, `FAILED`, `CANCELLED`입니다.
- `Payment`에는 `paymentRequestId`, `providerReference`, `providerTransactionId`, `version` 필드가 있습니다.
- `PaymentRepository#findByProviderTransactionId`는 provider callback/idempotency 확장을 위한 조회 포트입니다.
- payment 실패는 `CommerceOrchestrationService#handlePaymentFailure`에서 order 실패, orchestration step, audit 기록으로 이어집니다.
- settlement 실패는 `PaymentService#cancelLatestApprovedPayment`를 통한 payment cancel compensation으로 연결됩니다.

현재 구현하지 않은 범위는 아래와 같습니다.

- provider callback HTTP endpoint
- callback request DTO
- provider signature/shared secret 검증
- callback event id 저장 또는 중복 처리 테이블
- callback 기반 payment 상태 전이 service
- callback으로 settlement를 재개하는 흐름
- callback 관련 audit/outbox/metric event
- callback API OpenAPI path
- callback automated test

따라서 provider callback flow는 현재 Not Implemented / Future Scope입니다.

## What Provider Callback Would Add / Callback이 추가하는 가치

Provider callback은 동기 approve 응답만으로는 다루기 어려운 외부 provider 상태 변화를 backend가 비동기적으로 반영할 수 있게 합니다.

추가되는 가치는 아래와 같습니다.

- provider가 payment 승인/거절을 비동기로 확정하는 모델 지원
- WebClient timeout 이후 `UNKNOWN` 상태를 provider notification으로 해소
- provider 상태와 내부 payment/order 상태 reconciliation
- 늦게 도착한 승인/거절 이벤트 처리
- providerTransactionId 기반 외부 거래 추적성 강화
- 운영자가 수동으로 확인하기 전 provider가 보내는 상태 변경을 자동으로 기록

하지만 callback은 단순 endpoint 추가가 아닙니다. 보안 검증, 중복/순서 역전 처리, terminal state 충돌, settlement 재개 여부, OpenAPI와 테스트 범위까지 함께 커지므로 현재 구현 범위를 크게 넓힙니다.

## Required Design Decisions / 필요한 설계 결정

후속 구현 전에는 아래 결정을 먼저 고정해야 합니다.

- callback endpoint path
- provider authentication 방식
- signature verification 또는 shared secret verification 기준
- 허용할 callback event type
- provider status와 내부 `PaymentStatus` 매핑
- provider event id 저장 여부
- `paymentRequestId`, `providerTransactionId`, provider event id 중 idempotency 기준
- duplicate callback 처리 방식
- out-of-order callback 처리 방식
- 알 수 없는 `paymentRequestId` 또는 `providerTransactionId` callback 처리 방식
- payment/order가 이미 `APPROVED`, `FAILED`, `CANCELLED`, `COMPLETED` 같은 terminal state일 때의 정책
- callback `APPROVED`가 settlement를 직접 trigger할지, payment 상태만 갱신하고 별도 recovery flow가 이어갈지
- callback 처리 결과를 outbox event로 발행할지
- callback 처리 실패의 retry/dead-letter 정책
- invalid signature, malformed payload, inconsistent amount/orderId를 audit에 어떻게 남길지
- admin recovery가 invalid/ambiguous callback을 어떻게 확인하거나 무시할지

## State Transition Impact / 상태 전이 영향

현재 `PaymentStatus` 값은 아래 네 가지입니다.

- `READY`
- `APPROVED`
- `FAILED`
- `CANCELLED`

Provider callback을 구현하면 callback event가 payment 상태 전이의 입력이 됩니다. 가능한 proposed flow는 아래와 같습니다.

```text
callback APPROVED
  -> payment APPROVED
  -> order payment-confirmed 상태 확정
  -> settlement 진행 또는 settlement recovery candidate 생성

callback REJECTED / FAILED
  -> payment FAILED
  -> order failure branch

duplicate callback
  -> 상태 변경 없음
  -> duplicate accepted/ignored audit 기록

callback for unknown paymentRequestId/providerTransactionId
  -> reject 또는 ignored audit
  -> provider reconciliation 대상

callback while confirmation is required
  -> UNKNOWN 상태 해소
  -> APPROVED 또는 FAILED로 확정
```

`CONFIRMATION_REQUIRED`는 mock/dummy timeout unknown scenario를 남기기 위해 현재 enum에 존재합니다.
`CONFIRMATION_IN_PROGRESS` 같은 full confirmation workflow 상태는 아직 존재하지 않습니다.
callback flow는 timeout confirmation state model이 더 확정된 뒤 연결하는 것이 자연스럽습니다.

Callback이 settlement를 직접 시작할지 여부는 특히 중요합니다.
직접 settlement를 시작하면 provider callback이 orchestration의 새 진입점이 되므로
중복 settlement, outbox 중복 발행, admin recovery와의 충돌을 막는 conditional state transition이 필요합니다.
반대로 payment 상태만 갱신하면 구현은 단순하지만,
이후 settlement 재개를 담당할 recovery trigger가 별도로 필요합니다.

## Idempotency Policy / 멱등성 정책

현재 내부 approve idempotency 기준은 `paymentRequestId`입니다. callback flow에서도 `paymentRequestId`는 내부 payment를 찾는 primary idempotency key로 유지하는 편이 적절합니다.

후속 구현 시 권장 기준은 아래와 같습니다.

- `paymentRequestId`로 내부 payment를 찾습니다.
- provider가 제공하는 `providerTransactionId`로 외부 거래를 보조 검증합니다.
- provider event id가 있다면 callback 중복 처리의 primary key로 저장합니다.
- 같은 provider event id는 한 번만 처리합니다.
- 같은 `paymentRequestId`에 대해 이미 terminal state가 확정된 경우 상태 전이를 조건부로 제한합니다.
- duplicate callback은 settlement, notification, outbox publish를 다시 trigger하지 않습니다.
- idempotency는 in-memory flag가 아니라 repository constraint, callback event table, conditional update 중 하나로 보장해야 합니다.
- out-of-order callback은 현재 payment status와 provider event timestamp 또는 sequence를 기준으로 accept/ignore를 결정합니다.

이 Partition에서는 schema constraint, callback event table, conditional update를 추가하지 않습니다.

## Security & Validation / 보안과 검증

Provider callback은 외부에서 들어오는 요청이므로 일반 내부 admin recovery보다 엄격한 검증이 필요합니다.

- provider signature verification 또는 shared secret verification이 필요합니다.
- callback timestamp와 replay window 검증을 고려합니다.
- malformed payload는 payment 상태를 변경하지 않고 거절합니다.
- amount, currency, `paymentRequestId`, `providerTransactionId`가 내부 payment와 일치하는지 검증합니다.
- 알 수 없는 provider event type은 ignored 또는 rejected로 분류합니다.
- raw callback payload를 그대로 audit/log에 저장하지 않습니다.
- signature, shared secret, API key, authorization header는 log/audit/docs에 남기지 않습니다.
- OpenAPI example에는 실제 secret 또는 production signature 값을 넣지 않습니다.

## Audit & Observability / 감사와 관측 기준

Callback flow가 구현되면 아래 event를 audit/log/metric 기준으로 분리해야 합니다.

- callback received
- callback accepted
- callback rejected
- callback ignored
- duplicate callback ignored
- invalid signature rejected
- malformed payload rejected
- inconsistent payment data rejected
- payment state transition succeeded
- payment state transition skipped
- callback processing failed

Audit detail에는 action, provider, event type, previous status, current status, result, reason을 가능한 범위에서 남깁니다. 필요한 경우 `paymentId` 같은 내부 식별자는 audit detail에 둘 수 있지만, metric tag로 사용하지 않습니다.

Metric tag는 bounded value만 사용합니다.

- 허용 예시: `result`, `status`, `failureType`, `eventType`, `provider`
- 금지 예시: `orderId`, `paymentRequestId`, `providerTransactionId`, provider event id, raw exception message, token, signature

## OpenAPI Impact / OpenAPI 영향

이번 Partition에서는 `docs/openapi/openapi.yaml`에 provider callback path를 추가하지 않습니다.

후속 구현 시 OpenAPI에는 실제 Controller endpoint가 생긴 뒤에만 callback API를 추가합니다. 그때 필요한 항목은 아래와 같습니다.

- callback endpoint path
- request schema
- response envelope 또는 provider 요구 응답 형식
- signature/authentication 설명
- accepted event type example
- invalid signature / malformed payload / duplicate callback error case
- 실제 secret이 아닌 placeholder example

Provider별 callback은 일반 사용자 API와 성격이 다르므로, OpenAPI에 포함하더라도 demo auth와 혼동되지 않게 별도 security 설명이 필요합니다.

## Test Strategy / 테스트 전략

후속 구현 전에는 아래 테스트가 필요합니다.

- valid callback `APPROVED` -> payment approved 처리
- valid callback `REJECTED` / `FAILED` -> payment failed 처리
- duplicate callback no-op 처리
- callback for unknown `paymentRequestId`
- callback for unknown `providerTransactionId`
- invalid signature rejected
- malformed payload rejected
- amount/currency mismatch rejected
- out-of-order callback ignored 또는 manual intervention 처리
- callback after terminal payment state 처리
- callback resolving timeout/confirmation required state
- callback accepted 후 settlement trigger 중복 방지
- audit detail 기록 검증
- metric tag high-cardinality 방지 검증
- OpenAPI path 추가 후 Controller mapping consistency check

## Implementation Cost / 구현 비용

구현 비용은 moderate to high입니다.

필요한 production 변경 범위는 아래와 같습니다.

- provider callback Controller endpoint
- callback request DTO
- signature/shared secret validator
- callback application service
- provider event type/status mapping
- payment 상태 전이 method
- callback idempotency 저장 구조 또는 conditional update
- unknown/out-of-order/duplicate callback 정책
- audit 기록
- metric/structured log event
- settlement 재개 또는 recovery trigger 연동
- OpenAPI spec 갱신
- unit/integration/security tests
- docs/test-report/verification matrix 갱신

특히 callback이 settlement를 직접 trigger한다면 orchestration의 두 번째 외부 진입점이 생기므로, Modulith boundary와 중복 후속 처리 방어를 더 엄격히 검증해야 합니다.

## Recommendation / 권장 판단

Recommendation: Keep as Future Scope for now.

현재 프로젝트는 이미 synchronous orchestration, payment idempotency, settlement compensation, notification retry/manual intervention, outbox retry/dead-letter, admin recovery, OpenAPI, claim audit까지 포트폴리오 핵심 범위를 갖추고 있습니다.

Provider callback을 지금 구현하면 결제 상태 모델, 보안 검증, out-of-order handling, reconciliation, settlement 재개 정책까지 범위가 커집니다. 이 추가 범위는 실제 payment provider integration을 강조할 때는 가치가 있지만, 현재 Partition에서는 구현 안정성보다 문서 경계와 구현 정직성이 더 중요합니다.

따라서 지금은 provider callback을 구현하지 않고, `providerTransactionId`를 extension point로 유지하며, design review 문서로 필요한 결정과 테스트 범위를 남기는 것이 적절합니다.

후속 구현을 진행한다면 먼저 [Payment Timeout Confirmation Flow](/docs/flows/payment-timeout-confirmation-flow.md)의 timeout/unknown state model을 확정한 뒤 callback이 그 상태를 해소하는 보조 입력인지, 별도 asynchronous payment finalization 진입점인지 결정해야 합니다.

## Current Status / 현재 상태

Status: Design Review / Future Scope

- production code에는 provider callback endpoint가 없습니다.
- `PaymentProviderClient`에는 callback 처리 계약이 없습니다.
- `PaymentStatus`에는 callback/confirmation 전용 상태가 없습니다.
- OpenAPI paths에는 provider callback API가 없습니다.
- 자동 테스트로 검증된 callback flow가 없습니다.
- 현재 권장 판단은 timeout confirmation model 확정 이후의 후속 구현 후보로 유지하는 것입니다.

## Future Implementation Scope / 후속 구현 범위

- provider callback endpoint path 결정
- provider signature/shared secret verification 설계
- callback DTO와 provider status mapping 설계
- callback event id/idempotency 저장 방식 결정
- `paymentRequestId`와 `providerTransactionId` 매핑 검증
- duplicate/out-of-order callback 처리 정책 구현
- terminal payment/order 상태 충돌 정책 구현
- callback이 settlement를 직접 trigger할지 recovery candidate만 남길지 결정
- audit/metric/structured log 추가
- OpenAPI spec 갱신
- callback unit/integration/security test 추가
- timeout confirmation flow와 callback flow 충돌/우선순위 테스트 추가
