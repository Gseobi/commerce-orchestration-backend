# Architecture Boundaries

## Spring Modulith Boundary / 모듈 경계

Spring Modulith boundary는 이 프로젝트의 핵심 원칙입니다.

- 다른 domain module의 Repository를 직접 주입하지 않습니다.
- 모듈 간 협력은 가능한 한 `*.api` public interface를 통해 수행합니다.
- `@NamedInterface("api")`, `allowedDependencies` 규칙을 임의로 깨지 않습니다.
- Modulith verification test가 실패하는 방향의 변경은 허용하지 않습니다.
- 경계 변경이 필요하면 code, test, docs를 함께 수정하고 이유를 명확히 남깁니다.

## Orchestration Responsibility / 조율 책임

`CommerceOrchestrationService`는 주문 이후 payment, settlement, notification, outbox, audit 흐름의 coordinator입니다.
단, 다른 domain의 내부 repository를 직접 소유하거나 침범하지 않습니다.

- Order, Payment, Settlement, Notification, Outbox 상태 전이는 숨은 side effect가 아니라 서비스 책임과 repository update 흐름으로 드러나야 합니다.
- 실패 상태와 복구 가능 상태를 구분합니다.
- retry 가능한 실패와 manual intervention이 필요한 실패를 구분합니다.
- Payment 승인 이후 Settlement 실패 시 Payment Cancel compensation을 고려합니다.
- Notification 실패는 주문 자체 rollback과 분리될 수 있습니다.
- Outbox publish 실패는 business transaction 실패와 분리해 다룹니다.

## Outbox Decoupling / Outbox 분리

Outbox publishing은 Kafka 구현체와 직접 결합하지 않습니다.

권장 의존 방향:

```text
OutboxPublisherService
  -> OutboxEventPublisher
    -> KafkaOutboxEventPublisher
      -> KafkaTemplate
```

`OutboxPublisherService` 책임:

- publish 대상 조회 또는 claim
- 상태 전이
- retry count 증가
- backoff 계산
- dead-letter 전환
- publish 결과 기록

`KafkaOutboxEventPublisher` 책임:

- KafkaTemplate 사용
- topic publish
- Kafka 예외 처리
- infra adapter 역할

Kafka consumer 기반 상태 전이를 추가하려면 별도 설계 문서, 테스트, README 반영이 필요합니다.
암묵적으로 추가하지 않습니다.

## DB Conditional Claim / 동시성 보호

Scheduler, Admin API, 다중 application instance가 같은 event를 동시에 처리할 수 있다고 가정합니다.
핵심 동시성 보호는 Java `synchronized`가 아니라 DB conditional update 기반 claim으로 처리합니다.

예시:

```text
RETRY_SCHEDULED -> PROCESSING
READY / RETRY_WAIT -> PROCESSING
```

- update count가 `1`이면 claim 성공입니다.
- update count가 `0`이면 이미 다른 worker가 처리 중이므로 skip합니다.
- skip은 실패가 아니라 경쟁 상황에서의 정상 방어 결과입니다.
- 이 규칙은 notification retry, outbox publish, admin recovery에서 유지되어야 합니다.

## Docker Compose / 실행 환경

Docker Compose는 어떤 개발 환경에서도 실행 가능한 상태를 유지해야 합니다.

- `compose.yaml`, `.env.example`, application profile 설정을 임의로 깨지 않습니다.
- PostgreSQL, Kafka, Kafka UI, application service 간 연결성을 훼손하지 않습니다.
- 로컬 실행 편의성을 낮추는 변경은 피합니다.
- 환경 변수 이름을 변경하면 README, docs, `.env.example`, CI 설정을 함께 수정합니다.
- code/config/infrastructure 변경 후 Docker 기반 verification이 필요한 경우 PostgreSQL과 Kafka container health를 확인합니다.

## Gradle Dependency Minimality / 의존성 최소화

현재 프로젝트의 기술 기반은 유지합니다.

- Java 21
- Spring Boot
- Spring MVC
- WebClient
- Spring Security
- JPA
- Flyway
- PostgreSQL
- Kafka
- Spring Modulith
- Testcontainers
- Micrometer / Actuator

의존성 추가 전 확인합니다.

- 현재 코드로 해결 가능한가?
- test scope만 필요한가, runtime도 필요한가?
- 기존 Spring Boot dependency management로 관리 가능한가?
- CI와 Docker 환경에 영향이 있는가?
- 문서화가 필요한가?

사용하지 않는 라이브러리, 작은 문제 해결을 위한 대규모 framework, 명시 요청 없는 주요 버전 업그레이드는 추가하지 않습니다.
dependency를 추가했다면 왜 필요한지 작업 요약에 남기고 build/test를 수행합니다.

## Observability / 관측 가능성

Observability는 운영 분석을 돕기 위한 것이며, 상태 테이블과 audit log를 대체하지 않습니다.

Metric tag에는 high-cardinality 값을 넣지 않습니다.

금지 예시:

- orderId
- eventId
- paymentRequestId
- providerTransactionId
- raw exception message
- token
- authorization header

허용 예시:

- result
- status
- action
- failureType
- policy
- channel

Structured log는 장애 분석에 필요한 안전한 값만 남깁니다.
secret, token, API key, authorization header는 절대 log에 남기지 않습니다.

권장 event name 예시:

- `notification_retry_started`
- `notification_retry_succeeded`
- `notification_retry_failed`
- `outbox_publish_started`
- `outbox_publish_dead_lettered`
- `admin_recovery_requested`
- `admin_recovery_succeeded`
- `admin_recovery_failed`
