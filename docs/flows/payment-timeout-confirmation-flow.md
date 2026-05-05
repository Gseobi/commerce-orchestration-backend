# Payment Timeout Confirmation Flow

## Purpose / 목적

이 문서는 `ExternalPaymentProviderClient`의 WebClient 요청이 timeout 되었을 때 외부 결제 provider의 실제 승인 상태가 불명확해지는 문제를 다루기 위한 설계 노트입니다.

현재 production code에는 WebClient timeout 이후 provider confirmation flow가 구현되어 있지 않습니다. 이 문서는 후속 구현 후보를 명확히 하기 위한 Future Scope 문서이며, 실제 외부 provider 연동에서 발생할 수 있는 uncertain payment state를 어떻게 확인하고 감사 가능하게 처리할지 정리합니다.

목표는 timeout 이후 결제가 승인되었을 수도, 거절되었을 수도, provider에 도달하지 않았을 수도 있는 상황에서 중복 승인, 잘못된 취소, order/payment 상태 불일치를 피하는 기준을 세우는 것입니다.

## Current Implementation Boundary / 현재 구현 경계

현재 payment 구현은 아래 범위까지 포함합니다.

- `PaymentProviderClient`는 `approve`, `cancel` 계약을 제공합니다.
- `MockPaymentProviderClient`는 기본 payment provider 구현이며, description 기반 실패 시뮬레이션을 제공합니다.
- `ExternalPaymentProviderClient`는 WebClient 기반 external adapter입니다.
- `ExternalPaymentProviderClient`는 `approvePath`, `cancelPath`, `baseUrl`, `apiKey`, `connectTimeout`, `readTimeout` 설정을 사용합니다.
- `ExternalPaymentProviderClient`는 provider 응답 status를 `PaymentStatus.APPROVED`, `PaymentStatus.CANCELLED`, `PaymentStatus.FAILED`로 매핑합니다.
- `PaymentService#approve`는 `paymentRequestId`로 기존 payment를 먼저 조회하고, 기존 row가 있으면 provider approve를 다시 호출하지 않습니다.
- `PaymentRepository#findByProviderTransactionId`와 `payments.provider_transaction_id` 컬럼은 callback/idempotency 확장을 위한 준비 지점입니다.

현재 구현하지 않은 범위는 아래와 같습니다.

- WebClient timeout 이후 provider confirmation 요청
- `PaymentProviderClient`의 confirmation 계약
- `PaymentStatus.CONFIRMATION_REQUIRED` 같은 timeout 전용 상태
- provider callback API
- provider confirmation endpoint의 OpenAPI path
- confirmation 결과 기반 settlement 재개 또는 admin recovery API
- confirmation flow 자동 테스트

따라서 이 문서의 `CONFIRMATION_REQUIRED`, `CONFIRMATION_FAILED`, confirmation result category는 모두 proposed future state/category이며, 현재 enum이나 API에 존재하는 값이 아닙니다.

## Problem Scenario / 문제 상황

외부 결제 provider 승인 요청은 아래처럼 진행됩니다.

```text
Backend -> external payment provider approval request
Backend times out while waiting for provider response
```

이때 provider의 실제 상태는 하나로 단정할 수 없습니다.

1. provider가 결제를 승인했지만 backend 응답 대기 시간이 초과됨
2. provider가 결제를 거절했지만 backend 응답 대기 시간이 초과됨
3. 요청이 provider에 도달하지 않음
4. provider가 아직 처리 중임

timeout을 단순 실패로 보고 무조건 재시도하면 중복 승인 위험이 있습니다. 반대로 무조건 취소하면 이미 승인된 거래를 잘못 취소하거나, provider에 존재하지 않는 거래에 대해 불필요한 보상 요청을 보낼 수 있습니다. 따라서 timeout은 `FAILED`로 단정하기보다 `UNKNOWN` 상태로 다루고 confirmation으로 실제 provider state를 확인해야 합니다.

## Design Goal / 설계 목표

- 중복 payment approval을 피합니다.
- provider가 승인한 결제를 잘못 취소하지 않습니다.
- order/payment 상태 일관성을 유지합니다.
- `paymentRequestId`를 provider approve와 confirmation의 primary idempotency key로 유지합니다.
- WebClient timeout은 confirmed failure가 아니라 `UNKNOWN`으로 취급합니다.
- confirmation 시도와 결과를 audit log와 bounded structured log로 남깁니다.
- compensation decision은 confirmation 결과 이후 명시적으로 수행합니다.
- admin/manual recovery는 전체 orchestration 재실행이 아니라 unknown payment 단위 확인/복구로 제한합니다.

## Proposed Flow / 제안 흐름

```text
1. CommerceOrchestrationService가 deterministic paymentRequestId로 payment approve를 요청합니다.
2. PaymentService는 paymentRequestId로 기존 payment를 조회합니다.
3. 기존 payment가 없으면 PaymentProviderClient.approve를 호출합니다.
4. provider가 success를 반환하면 payment를 APPROVED로 저장하고 settlement 흐름을 계속 진행합니다.
5. provider가 business failure를 반환하면 payment를 FAILED로 저장하고 payment failure branch로 종료합니다.
6. WebClient timeout이 발생하면 payment를 CONFIRMATION_REQUIRED 같은 future state로 저장합니다.
7. provider confirmation을 paymentRequestId 또는 providerTransactionId 기준으로 요청합니다.
8. confirmation 결과가 APPROVED이면 기존 payment를 APPROVED로 확정하고 settlement를 진행합니다.
9. confirmation 결과가 REJECTED/FAILED 또는 NOT_FOUND이면 failure branch로 처리합니다.
10. confirmation 결과가 UNKNOWN/STILL_PROCESSING이면 retry schedule 또는 admin intervention 대상으로 남깁니다.
11. confirmation 자체가 반복 timeout 또는 provider error를 반환하면 bounded retry 후 manual intervention으로 전환합니다.
```

이 흐름은 현재 구현이 아니라 설계안입니다. 특히 6번 이후의 상태, confirmation client, retry scheduler, admin API, OpenAPI path는 후속 구현 범위입니다.

## State Transition Policy / 상태 전이 정책

현재 `PaymentStatus` 값은 아래와 같습니다.

- `READY`
- `APPROVED`
- `FAILED`
- `CANCELLED`

현재 payment approve 구현은 provider 결과를 기준으로 `APPROVED` 또는 `FAILED` payment를 저장하고, cancel compensation 시 `CANCELLED`로 변경합니다.

후속 구현에서 고려할 proposed future state는 아래와 같습니다.

- `CONFIRMATION_REQUIRED`: approve 요청 timeout 이후 provider 상태 확인이 필요한 상태
- `CONFIRMATION_IN_PROGRESS`: confirmation worker 또는 admin action이 확인 중인 상태
- `CONFIRMATION_FAILED`: confirmation 자체가 retry 한도를 넘었거나 수동 개입이 필요한 상태

상태 전이 기준은 아래처럼 분리합니다.

```text
READY
  -> APPROVED
  -> FAILED
  -> CONFIRMATION_REQUIRED (future)

CONFIRMATION_REQUIRED
  -> CONFIRMATION_IN_PROGRESS (future)
  -> APPROVED
  -> FAILED
  -> CONFIRMATION_FAILED (future)

APPROVED
  -> CANCELLED (compensation)
```

`CONFIRMATION_REQUIRED` 상태의 payment는 order를 성공 또는 실패로 확정하지 않습니다. settlement 시작 여부도 confirmation 결과가 `APPROVED`로 확정된 뒤 결정합니다.

## Idempotency Policy / 멱등성 정책

`paymentRequestId`는 현재 구현에서도 approve replay 방어의 primary key입니다. timeout confirmation flow에서도 같은 기준을 유지합니다.

- approve 요청과 confirmation 요청은 같은 `paymentRequestId`를 기준으로 연결합니다.
- provider가 `providerTransactionId`를 반환한 경우 confirmation은 `paymentRequestId`와 `providerTransactionId`를 함께 검증합니다.
- confirmation 결과는 기존 payment row를 갱신해야 하며 두 번째 payment row를 만들지 않습니다.
- 동일 confirmation 응답이 여러 번 도착해도 이미 확정된 상태를 중복 전이하지 않습니다.
- admin retry는 전체 orchestration을 재실행하지 않고, `CONFIRMATION_REQUIRED` payment의 확인 작업만 수행합니다.
- provider confirmation API가 idempotent하지 않다면 client adapter에서 retry 정책을 더 보수적으로 둡니다.

## Confirmation Result Handling / confirmation 결과 처리

아래 값들은 design category이며, 현재 enum 값이 아닙니다.

| Confirmation result | 처리 기준 |
|---|---|
| `APPROVED` | payment를 `APPROVED`로 확정하고 settlement 흐름을 진행합니다. 이미 settlement가 시작된 경우 중복 실행하지 않습니다. |
| `REJECTED` / `FAILED` | payment를 `FAILED`로 확정하고 payment failure branch로 종료합니다. |
| `NOT_FOUND` | provider가 요청을 받지 못했을 가능성으로 보고 failure branch 또는 제한적 approve 재시도를 검토합니다. 재시도는 반드시 같은 `paymentRequestId`를 사용합니다. |
| `UNKNOWN` / `STILL_PROCESSING` | retry schedule을 남기고 order/payment 확정을 보류합니다. retry 한도 초과 시 manual intervention으로 전환합니다. |
| `PROVIDER_ERROR` | 일시적 provider 장애로 분류하고 bounded retry를 수행합니다. |
| `TIMEOUT_AGAIN` | confirmation 자체가 timeout 된 상태입니다. approve 재호출이 아니라 confirmation retry 또는 admin intervention 대상으로 남깁니다. |

`NOT_FOUND`는 provider별 의미가 다를 수 있습니다. 어떤 provider에서는 "요청 미수신"일 수 있고, 다른 provider에서는 eventual consistency 지연일 수 있으므로 provider-specific mapping을 별도 adapter 정책으로 분리해야 합니다.

## Audit & Observability / 감사와 관측 기준

confirmation flow는 payment state를 확정하는 운영상 중요한 분기이므로 audit log와 observability 기준을 함께 둡니다.

- confirmation requested, succeeded, failed, retried, manual intervention 전환을 audit log로 남깁니다.
- audit detail에는 action, payment id, previous status, current status, result, operator, reason을 가능한 범위에서 남깁니다.
- provider raw payload, API key, authorization header, token은 log/audit에 남기지 않습니다.
- structured log에는 `event=payment_confirmation_requested`, `result`, `status`, `failureType`처럼 bounded field를 사용합니다.
- metric tag에는 `result`, `status`, `failureType`, `provider`처럼 낮은 cardinality 값만 사용합니다.
- metric tag에 `orderId`, `paymentRequestId`, `providerTransactionId`, raw exception message를 넣지 않습니다.

## Failure Cases / 실패 케이스

- 첫 approve 요청이 timeout 되었지만 provider는 이미 승인한 경우
- 첫 approve 요청이 timeout 되었고 provider가 요청을 받지 못한 경우
- 첫 approve 요청이 timeout 되었고 provider가 아직 처리 중인 경우
- confirmation API도 timeout 되는 경우
- confirmation 결과가 local payment amount/orderId와 맞지 않는 경우
- confirmation이 `APPROVED`를 반환했지만 payment가 이미 `FAILED` 또는 `CANCELLED`로 전이된 경우
- 같은 `paymentRequestId`에 대한 confirmation이 중복 실행되는 경우
- admin retry가 unknown state에서 동시에 실행되는 경우
- confirmation approved 이후 settlement가 이미 시작되었거나 완료된 경우
- provider callback과 confirmation result가 서로 다른 순서로 도착하는 경우

## Test Strategy / 테스트 전략

후속 구현 전에는 아래 테스트가 필요합니다.

- confirmation result mapping unit test
- WebClient timeout -> `CONFIRMATION_REQUIRED` 저장 unit/integration test
- 동일 `paymentRequestId` approve replay가 provider approve를 중복 호출하지 않는 regression test
- confirmation `APPROVED` -> payment `APPROVED` 확정 -> settlement 진행 test
- confirmation `FAILED` / `REJECTED` / `NOT_FOUND` -> payment failure branch test
- confirmation `UNKNOWN` / `STILL_PROCESSING` -> retry schedule 또는 manual intervention 전환 test
- confirmation retry claim 또는 conditional update concurrency test
- duplicate confirmation response idempotency test
- admin confirmation retry가 전체 orchestration을 재실행하지 않는 test
- audit detail에 bounded operator/reason/result가 남는 test
- metrics tag에 high-cardinality id가 들어가지 않는 test
- provider callback flow가 추가되는 경우 callback과 confirmation 결과 충돌 test
- confirmation API 구현 후 OpenAPI path와 Controller mapping consistency check

## Current Status / 현재 상태

Status: Design Note / Future Scope

- production code에는 아직 구현되어 있지 않습니다.
- `PaymentProviderClient`에는 confirmation method가 없습니다.
- `PaymentStatus`에는 confirmation 전용 상태가 없습니다.
- OpenAPI paths에는 confirmation API가 없습니다.
- 자동 테스트로 검증된 상태가 아닙니다.
- 다음 구현 후보를 설명하기 위한 설계 문서입니다.
- 구현 범위 검토는 [WebClient Timeout Confirmation Implementation Review](/docs/implementation-reviews/webclient-timeout-confirmation-implementation-review.md)에 정리했습니다.
- 현재 권장 후보는 실제 PG 계약이 아닌 mock/dummy provider 기반 minimal confirmation flow입니다.

## Future Implementation Scope / 후속 구현 범위

- provider별 confirmation API contract 조사
- `PaymentProviderClient` confirmation 계약 추가
- `ExternalPaymentProviderClient` confirmation adapter 추가
- timeout을 `FAILED`가 아닌 confirmation-required state로 남기는 payment state model 확장
- confirmation retry/backoff policy와 DB conditional update claim 설계
- admin confirmation retry/ignore/manual resolution API 설계
- audit log detail과 bounded metric/log event 추가
- provider callback flow와 confirmation flow의 충돌 해결 정책 설계
- 구현 후 `docs/openapi/openapi.yaml`에 실제 Controller endpoint만 추가
- 구현 후 `docs/verification-matrix.md`, `docs/test-report.md`, `docs/openapi/README.md` 갱신
