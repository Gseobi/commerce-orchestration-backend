# Orchestration Reliability Decision Record

## 1. Problem

주문 이후 payment, settlement, notification, outbox publish는 서로 다른 실패 의미를 갖습니다.

결제 승인 실패는 거래 시작 자체의 실패이고, 정산 실패는 이미 승인된 결제를 되돌릴지 결정해야 하는 정합성 문제입니다. 알림 실패는 거래 자체를 되돌릴 이유가 아닐 수 있으며, outbox publish 실패는 내부 거래 상태와 외부 이벤트 발행 사이의 불일치 문제입니다.

이 프로젝트의 문제는 후속 처리를 단순 service 호출로 흩뿌리지 않고, 어느 단계까지 성공했는지, 어떤 단계에서 실패했는지, 어떤 단위로 재처리할 수 있는지를 코드와 테스트로 설명 가능하게 만드는 것입니다.

## 2. Decision Summary

- 주문 이후 흐름은 `CommerceOrchestrationService`가 중심에서 조율합니다.
- 주문 상태와 orchestration step을 명시적으로 남깁니다.
- settlement 실패는 payment cancel compensation으로 처리합니다.
- notification 실패는 retry, manual intervention, ignore 정책으로 분리합니다.
- payment approve replay는 `paymentRequestId` 기준으로 provider 중복 호출을 막습니다.
- notification retry와 outbox publish는 처리 전 `PROCESSING` claim을 획득한 실행자만 처리합니다.
- outbox 상태 전이와 Kafka publish 구현은 `OutboxPublisherService`와 `OutboxEventPublisher` adapter로 분리합니다.
- 관측성은 dashboard 구현보다 먼저 metric, structured log, audit, runbook 기준을 둡니다.

## 3. Why Orchestration Service

주문 이후 흐름은 payment, settlement, notification, outbox가 순서와 실패 정책을 공유합니다. 각 도메인 service가 다음 단계를 직접 호출하면 실패 분기와 종료 조건이 여러 위치로 흩어집니다.

`CommerceOrchestrationService`는 order lifecycle을 중심으로 payment approve, settlement request, notification request, outbox append, failure branch를 한 흐름에서 제어합니다. 각 도메인은 자기 저장소와 내부 정책을 소유하고, orchestration은 공개 application contract를 통해 조합합니다.

## 4. Why Explicit State Transition

거래 흐름에서 중요한 것은 최종 성공 여부만이 아니라 중간 상태입니다.

이 프로젝트는 order status, orchestration step, payment status, notification event status, outbox event status, audit log를 통해 "어디까지 갔는가"를 남깁니다. 이 기록은 운영 복구와 테스트 검증의 기준이 됩니다.

## 5. Why Settlement Compensation Differs From Notification Recovery

settlement 실패는 결제 승인 이후 거래 정합성에 영향을 줍니다. 현재 구현은 settlement 실패 시 `PaymentService#cancelLatestApprovedPayment`로 payment cancel compensation을 수행하고 주문을 `CANCELLED`로 닫습니다.

notification 실패는 거래 자체를 반드시 되돌릴 실패가 아닙니다. 그래서 `AUTO_RETRY`, `MANUAL_INTERVENTION`, `IGNORE` 정책으로 나누고, retry 대상은 `NotificationRetryProcessor`와 admin trigger로 복구할 수 있게 둡니다.

## 6. Why Payment Idempotency Uses paymentRequestId

결제 approve는 client retry, orchestration replay, timeout 이후 재호출 가능성이 있습니다. provider approve를 다시 호출하면 중복 승인 위험이 생깁니다.

현재 orchestration은 `"ORDER-" + orderId + "-PAYMENT-APPROVE"` 형태의 deterministic `paymentRequestId`를 사용합니다. `PaymentService#approve`는 먼저 `PaymentRepository#findByPaymentRequestId`로 기존 payment를 조회하고, 기존 row가 있으면 provider를 다시 호출하지 않고 기존 payment 기준 응답을 반환합니다.

## 7. Why PROCESSING Claim Is Used For Retry/Publish

notification retry와 outbox publish는 scheduler, admin trigger, multiple worker가 같은 event를 동시에 잡을 수 있는 영역입니다.

현재 구현은 처리 직전 repository conditional update로 대상 event를 `PROCESSING`으로 선점합니다. claim 성공 실행자만 retry 또는 publish를 수행하고, claim 실패는 skipped로 집계합니다. 이 방식은 별도 분산 락 없이 DB 상태 전이로 중복 실행 가능성을 줄이는 선택입니다.

## 8. Why Outbox Publisher Adapter Was Split

outbox service가 KafkaTemplate 세부 구현까지 직접 알면 상태 전이 정책과 transport 구현이 결합됩니다.

현재 `OutboxPublisherService`는 publish 대상 조회, `PROCESSING` claim, `PUBLISHED` / `RETRY_WAIT` / `DEAD_LETTER` 전이를 담당합니다. 실제 Kafka send, timeout, failure message truncation은 `OutboxEventPublisher` 구현체인 `KafkaOutboxEventPublisher`가 담당합니다.

이 분리는 outbox reliability 정책을 unit test에서 `OutboxEventPublisher` mock으로 검증할 수 있게 만들고, Kafka adapter 교체 가능성도 남깁니다.

## 9. Why Observability Is Metric/Log/Runbook First

현재 프로젝트의 관측성 목표는 운영자가 retry/dead-letter 증가, claim skipped, admin recovery 결과를 확인할 수 있는 신호를 남기는 것입니다.

그래서 `CommerceRecoveryMetrics`, structured log, audit detail, SQL/runbook을 먼저 둡니다. Prometheus/Grafana dashboard와 alert rule은 구현하지 않았고 Future Scope로 남깁니다. metric tag에는 `orderId`, `paymentRequestId`, operatorId, reason 같은 high-cardinality 값을 넣지 않습니다.

## 10. Alternatives Considered

- 각 domain service가 다음 단계를 직접 호출하는 방식
  - 단순하지만 실패 분기와 보상 정책이 흩어집니다.
- settlement와 notification 실패를 모두 transaction rollback으로 처리하는 방식
  - 알림 실패처럼 거래 완료를 막지 않아도 되는 실패까지 과도하게 되돌릴 수 있습니다.
- retry/publish 중복 제어를 in-memory flag로 처리하는 방식
  - 여러 instance나 재시작 상황에서 검증 근거가 약합니다.
- outbox service가 KafkaTemplate을 직접 사용하는 방식
  - 상태 전이 정책과 transport 구현이 결합되어 테스트와 교체가 어려워집니다.
- dashboard부터 구축하는 방식
  - 현재 portfolio 범위에서는 metric/log/audit/runbook 신호를 먼저 고정하는 편이 구현 범위를 과장하지 않습니다.

## 11. Trade-offs

- orchestration service는 흐름을 읽기 쉽게 만들지만, 너무 많은 도메인 규칙을 흡수하지 않도록 `*.api` contract와 Modulith boundary를 지켜야 합니다.
- explicit state transition은 테이블과 테스트가 늘어나지만, 실패 복구와 기술 검토의 근거가 됩니다.
- `paymentRequestId` idempotency는 approve replay 제어에는 직접적이지만, provider callback idempotency까지 완성한 것은 아닙니다.
- `PROCESSING` claim은 중복 실행 가능성을 줄이지만, 오래 머무는 `PROCESSING` automatic recovery job은 아직 없습니다.
- outbox adapter 분리는 테스트 가능성을 높이지만, Kafka consumer 기반 상태 전이까지 구현한 것은 아닙니다.

## 12. Implementation Evidence

- `CommerceOrchestrationService`
- `PaymentService#approve`
- `PaymentRepository#findByPaymentRequestId`
- `PaymentStatus.CONFIRMATION_REQUIRED`
- `CommerceOrchestrationService#handleSettlementFailure`
- `NotificationService`
- `NotificationRetryProcessor#processDueRetryEvents`
- `NotificationEventRepository#claimRetryScheduledEvent`
- `OutboxPublisherService#publishReadyEvents`
- `OutboxEventRepository#claimPublishableEvent`
- `OutboxEventPublisher`
- `KafkaOutboxEventPublisher`
- `AdminReprocessingService`
- `CommerceRecoveryMetrics`
- `ModulithArchitectureTest#verifiesModularStructure`

## 13. Test Evidence

- `PaymentServiceTest#approve_idempotent_replay_reuses_existing_payment_without_provider_call`
- `PaymentServiceTest#approve_idempotent_replay_reusesConfirmationRequiredPayment_without_provider_call`
- `PaymentServiceTest#approve_timeoutUnknown_savesConfirmationRequired_andDoesNotTreatAsSuccess`
- `MockPaymentProviderClientTest#approve_timeoutUnknownToken_returnsConfirmationRequired`
- `OrderFlowIntegrationTest#orchestrate_paymentTimeoutUnknown_recordsConfirmationRequiredPayment`
- `OrderFlowIntegrationTest#orchestrate_settlementFailure_recordsCompensation`
- `NotificationRecoveryIntegrationTest`
- `NotificationRetryProcessorTest`
- `NotificationRetryProcessorIntegrationTest`
- `OutboxPublisherServiceTest#publishReadyEvents_skipsAlreadyProcessingEventWhenClaimFails`
- `OrderOutboxHappyPathIntegrationTest`
- `OutboxRetryDeadLetterIntegrationTest`
- `AdminReprocessingServiceTest`
- `AdminReprocessingIntegrationTest`
- `CommerceRecoveryMetricsTest`
- `ModulithArchitectureTest#verifiesModularStructure`

## 14. Current Boundaries / Future Scope

현재 구현 범위:

- synchronous order orchestration
- payment approve/cancel provider abstraction
- `paymentRequestId` approve replay idempotency
- mock/dummy timeout unknown state를 `CONFIRMATION_REQUIRED` payment로 기록
- settlement failure payment cancel compensation
- notification retry/manual/ignore policy
- notification due retry `PROCESSING` claim
- outbox retry/dead-letter, publish `PROCESSING` claim
- outbox publisher adapter split
- admin notification/outbox recovery
- metric/log/audit/runbook 기반 recovery observability
- Spring Modulith boundary verification
- Testcontainers 기반 PostgreSQL/Kafka integration test

Future Scope 또는 Not Implemented:

- external provider confirmation request
- admin confirmation API
- provider callback API
- provider callback idempotency table or endpoint
- full WebClient timeout confirmation workflow
- Kafka consumer 기반 상태 전이
- stale `PROCESSING` automatic recovery job
- Prometheus/Grafana dashboard and alert rules
- distributed tracing backend

## 15. Technical Discussion Summary

이 프로젝트의 핵심 Review Point는 "기능을 많이 붙였다"가 아니라 "거래 후속 흐름에서 실패 의미가 다른 지점을 상태, 보상, 재처리, 관측성 단위로 분리했고, 그 선택을 코드와 테스트로 증명했다"입니다.

기술 검토에서는 provider callback, full confirmation API, dashboard 같은 미구현 범위를 구현된 것처럼 말하지 않고, 현재 구현한 mock/dummy timeout unknown 기록, idempotency, claim, adapter, admin recovery, Modulith/Testcontainers 검증 범위를 정확히 설명하는 것이 핵심입니다.
