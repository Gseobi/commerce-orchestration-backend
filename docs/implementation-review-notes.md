# Implementation Review Notes

이 문서는 Codex를 활용해 구현한 notification retry recovery trigger 결과물을 직접 검토하면서, 설계 의도와 실제 코드 구조가 일치하는지 확인한 기록입니다.

검토 초점은 기능 설명 자체보다, 왜 scheduler와 admin-triggered retry endpoint를 trigger 역할로만 두었는지, 그리고 왜 `notification.api` 포트를 통해 Spring Modulith boundary를 지키도록 설계했는지에 있습니다.

## 1. Review Scope

이번 review에서 확인한 범위는 아래와 같습니다.

- notification retry scheduler
- admin-triggered notification retry endpoint
- `NotificationRetrySchedulerTrigger` port
- `NotificationRetryProcessor` delegation
- Spring Modulith module boundary
- README/docs 반영 여부

확인 결과, 현재 구현은 scheduler와 admin endpoint를 모두 "retry 실행을 시작하는 trigger"로 두고, 실제 due retry 처리 로직은 기존 processor 계층으로 위임하는 구조를 유지하고 있습니다. 또한 README에는 recovery 동작 설명이 이미 반영되어 있었고, 이번 문서 추가로 review 기록까지 연결할 수 있게 했습니다.

## 2. Retry Trigger Responsibility

현재 구조에서 `NotificationRetryScheduler`와 `AdminNotificationRetryController`는 retry 비즈니스 로직을 직접 수행하지 않습니다.

- 두 trigger는 모두 `NotificationRetrySchedulerTrigger` 포트를 호출합니다.
- retry 대상 조회, 상태 변경, repository 접근은 기존 processor/service 계층에 유지합니다.
- trigger 계층은 "언제 실행할 것인가"만 담당하고, "어떻게 처리할 것인가"는 기존 retry processor가 담당합니다.

이 분리는 의도적입니다. Scheduler는 fixed delay 시점에 batch를 시작하는 역할만 수행하고, Admin endpoint는 운영자가 같은 batch를 수동으로 시작할 수 있게만 합니다. 반대로 due 대상 조회, retry 성공 처리, 재스케줄, manual intervention 전환, order recovery, audit 기록은 `NotificationRetryProcessor` 내부에 남아 있습니다.

이렇게 두면 실행 경로가 둘로 늘어나도 retry 규칙은 한 곳에서만 바뀝니다. Scheduler와 Admin API가 각각 자체 로직을 가지기 시작하면 처리 규칙이 쉽게 벌어지고, 동일한 due retry를 서로 다른 방식으로 해석할 위험이 생깁니다. 현재 구조는 그 중복을 피합니다.

## 3. Modulith Boundary Decision

`notification.scheduler`에서 `orchestration.service.NotificationRetryProcessor`를 직접 참조하면 module cycle이 발생할 수 있습니다.

- `notification` 모듈은 현재 `common`, `common::error` 정도만 allowed dependency로 두고 있습니다.
- `orchestration` 모듈은 `notification::api`에 의존하도록 열려 있습니다.
- 따라서 `notification` 내부 구현이 `orchestration.service` 같은 다른 모듈의 내부 구현을 직접 참조하는 방식은 Modulith boundary 관점에서 좋지 않습니다.

이를 피하기 위해 `notification.api.NotificationRetrySchedulerTrigger` 포트를 추가했습니다.

- `notification` 모듈 내부 trigger는 `notification.api`에 정의된 contract에만 의존합니다.
- 실제 구현은 `orchestration.service.NotificationRetryProcessor`가 제공합니다.
- 이 구조는 Spring Modulith의 module boundary 검증과도 맞습니다.

정리하면, trigger의 소유권은 `notification` 쪽에 두되 실제 처리 구현은 `orchestration`이 제공하는 구조입니다. 이 방향은 "trigger는 notification recovery entry point지만, orchestration 전반의 상태 복구 판단은 orchestration processor가 책임진다"는 현재 설계와도 일치합니다.

## 4. Recovery Paths

현재 recovery path는 자동 복구와 수동 복구 두 경로로 나뉘지만, 실제 retry 실행 contract는 하나로 통일되어 있습니다.

- 자동 복구 경로:
  - `NotificationRetryScheduler`
  - `notification.retry.scheduler.enabled=true`일 때만 활성화
  - `fixed-delay-ms` 설정 기반 실행
- 수동 복구 경로:
  - `POST /api/admin/notification-events/retry-due`
  - 운영자가 scheduler를 기다리지 않고 due retry batch를 트리거할 수 있음

두 경로 모두 동일한 retry trigger port를 사용합니다.

이 판단은 운영 측면에서도 실용적입니다. 정상 운영에서는 scheduler가 due retry를 흡수하고, 장애 점검이나 긴급 복구 시에는 운영자가 같은 처리를 수동으로 즉시 실행할 수 있습니다. 다만 둘 다 동일한 port를 호출하므로, 처리 규칙과 결과 일관성은 유지됩니다.

## 5. Configuration Review

설정은 `application.yaml` 기준으로 아래처럼 확인했습니다.

- 기본 설정은 `notification.retry.scheduler.enabled=false`
- `fixed-delay-ms` 기본값은 `60000`
- scheduler는 명시적으로 활성화하지 않는 한 실행되지 않습니다.

이 기본값은 포트폴리오 프로젝트에서도 운영성 관점에서 안전한 선택입니다. retry batch는 실제 운영 데이터에 영향을 줄 수 있으므로, 환경에서 명시적으로 opt-in 하지 않으면 동작하지 않게 두는 편이 안전합니다. property-gated trigger로 둔 판단은 데모/로컬 환경, 운영 환경, 테스트 환경의 의도를 분리하는 데도 유리합니다.

## 6. Test Review

현재 코드 기준으로 확인한 테스트와 검증 포인트는 아래와 같습니다.

- scheduler bean condition test 추가
  - `NotificationRetrySchedulerTest`에서 enabled 미설정일 때 scheduler bean이 등록되지 않는 것을 확인합니다.
  - `notification.retry.scheduler.enabled=false`일 때 미등록을 확인합니다.
  - `notification.retry.scheduler.enabled=true`일 때 등록을 확인합니다.
- admin endpoint test 추가 여부
  - 현재 코드 기준으로 `AdminNotificationRetryControllerTest`가 추가되어 있습니다.
  - 이 테스트는 `ADMIN` role 사용자로 `POST /api/admin/notification-events/retry-due`를 호출했을 때 `200 OK`와 batch 처리 결과 응답을 확인하고, `NotificationRetrySchedulerTrigger` 호출을 검증합니다.
  - 다만 현재 테스트는 성공 경로 중심이며, 세분화된 권한 실패 시나리오까지 넓게 다루는 상태는 아닙니다.
- compileJava 통과 여부
  - 이번 review 마무리 단계에서 `./gradlew compileJava` 실행 기준 통과를 다시 확인했습니다.
- test 통과 여부
  - 이번 review 마무리 단계에서 `./gradlew test` 실행 기준 통과를 다시 확인했습니다.
- `ModulithArchitectureTest` 통과 여부
  - `ApplicationModules.verify()`를 수행하는 `ModulithArchitectureTest`가 현재 테스트 스위트에 포함되어 있으며, 이번 review 시점에도 통과 기준을 유지하고 있음을 확인했습니다.

즉, 이번 retry recovery trigger 구조는 bean registration 조건, admin-triggered entry point, Modulith architecture verification까지 최소한의 구조 검증을 갖춘 상태입니다.

## 7. Remaining Improvements

현재 구조는 trigger 분리와 module boundary 정리에 초점을 맞춘 구현으로 보는 것이 맞고, 아래 항목은 후속 과제로 남겨두는 편이 적절합니다.

- batch 결과 노출 고도화
  - 현재 admin retry-due endpoint는 `NotificationRetryProcessingResult`를 반환하며 `processedCount`, `successCount`, `failedCount`, `skippedCount`, `processedEventIds`를 포함합니다.
  - 운영자 실행 actor trace, batch id, 상세 실패 리스트는 후속 확장 후보입니다.
- retry 실행 결과에 대한 audit log 강화
  - 현재 processor 내부 audit는 있으나, batch trigger 단위 실행 결과와 운영자 실행 맥락까지 더 풍부하게 남길 여지가 있습니다.
- admin endpoint 인증/권한 정책 추가
  - 현재 `/api/admin/**`는 `ADMIN` role 보호가 걸려 있지만, retry batch trigger 전용 권한 분리, actor trace, 운영 승인 정책 같은 세분화는 후속 과제로 볼 수 있습니다.
- channel별 notification retry policy 세분화
  - 현재 구조는 공통 retry processor 중심이므로, email, sms, push 등 channel별 재시도 규칙 차등화는 이후 확장 포인트입니다.
- dead-letter event 운영 재처리 자동화
  - outbox dead-letter 운영 재처리는 현재 admin path가 중심이며, 더 자동화된 운영 회수 전략은 별도 과제로 남습니다.
- retry/backoff 정책을 설정 기반으로 외부화
  - 현재 retry 횟수와 backoff는 processor 내부 상수에 가깝기 때문에, 환경별 운영 설정으로 빼는 작업이 남아 있습니다.

## 8. Reliability Hardening Review

최근 hardening 작업에서는 retry trigger 분리 이후 실제 중복 실행 가능성을 줄이는 구현을 추가했습니다.

### Payment idempotency guard

- `PaymentApplication.approve`는 `paymentRequestId`를 인자로 받습니다.
- orchestration은 `"ORDER-" + orderId + "-PAYMENT-APPROVE"` deterministic key를 생성해 전달합니다.
- `PaymentService`는 provider 호출 전에 `findByPaymentRequestId`를 수행합니다.
- 기존 payment가 있으면 provider approve를 다시 호출하지 않고 기존 payment 기준 응답을 반환합니다.
- 실패 provider result도 payment row로 저장하되, 기존 정책처럼 `BusinessException`은 유지합니다.

### Notification retry claim

- `NotificationEventStatus.PROCESSING`과 `NotificationEvent.@Version`을 추가했습니다.
- `NotificationEventRepository.claimRetryScheduledEvent`는 `RETRY_SCHEDULED`, due 조건, retry count 조건을 만족할 때만 `PROCESSING`으로 update합니다.
- update count가 `1`이면 retry 처리 권한을 얻고, `0`이면 skipped로 집계합니다.
- `markRetrySucceeded`, `rescheduleRetry`, `requireManualIntervention`은 claim 이후 `PROCESSING` 이벤트를 다음 상태로 전이하는 경로에서 동작합니다.

### Outbox publisher adapter 분리

- `OutboxPublisherService`의 `KafkaTemplate` 직접 의존을 제거했습니다.
- `OutboxPublisherService`는 `OutboxEventPublisher` interface에 의존하고, publish 결과에 따라 `PUBLISHED`, `RETRY_WAIT`, `DEAD_LETTER` 상태 전이만 담당합니다.
- `KafkaOutboxEventPublisher`는 `infrastructure/kafka` adapter로 `KafkaTemplate.send(...).get(...)`, timeout, failure message truncation을 담당합니다.
- Kafka consumer 기반 상태 전이는 아직 구현하지 않았고, 현재 범위는 publisher adapter 분리까지입니다.

### Outbox PROCESSING claim

- `OutboxStatus.PROCESSING`과 `OutboxEvent.@Version`을 추가했습니다.
- `OutboxEventRepository.claimPublishableEvent`는 `READY` 또는 `RETRY_WAIT`이고 `nextAttemptAt <= now`인 이벤트만 `PROCESSING`으로 선점합니다.
- `publishReadyEvents`는 후보 조회 후 claim에 성공한 이벤트만 publisher adapter에 전달합니다.
- 이미 `PROCESSING`인 이벤트나 claim 실패 이벤트는 중복 publish하지 않습니다.

### Verification

이번 문서 정리 전 기준으로 아래 명령을 실행했고 둘 다 통과했습니다.

- `./gradlew clean test --rerun-tasks`
- `./gradlew clean integrationTest --rerun-tasks --stacktrace`
