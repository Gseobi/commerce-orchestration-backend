# commerce-orchestration-backend

주문 생성 이후 결제, 정산, 알림 같은 후속 작업을 하나의 흐름으로 제어하고,  
그 진행 상태와 실패 분기를 코드와 데이터로 추적할 수 있게 만드는 Spring Boot 기반 backend입니다.

이 프로젝트는 API 개수를 늘리는 것보다 아래 두 가지를 우선합니다.

- `CommerceOrchestrationService`를 중심으로 order lifecycle을 명시적으로 제어하는 것
- 운영 관점에서 `order status`, `orchestration step`, `outbox event`, `audit log`를 함께 남기는 것

## 1. 현재 기준선

- 비즈니스 외부 진입점은 `OrderController`입니다.
- 인증용 `AuthController`는 데모 JWT 발급 보조 엔드포인트만 제공합니다.
- `CommerceOrchestrationService`는 흐름 제어만 담당합니다.
- `payment`, `settlement`, `notification`, `outbox`, `audit`는 각자 자기 repository를 내부 service가 소유합니다.
- 결제 provider는 `PaymentProviderClient` 인터페이스 아래 `mock` / `external` 구현으로 분리되어 있습니다.
- outbox는 `READY -> RETRY_WAIT -> PUBLISHED / DEAD_LETTER` 상태를 사용합니다.
- settlement 실패와 notification 실패는 동일 보상 정책으로 처리하지 않습니다.
- admin은 `notification-events`, `outbox-events` 단위로 명시적 재처리를 수행할 수 있습니다.
- DB 스키마의 소스 오브 트루스는 이제 Flyway migration입니다.

## 2. 아키텍처 요약

### Business Flow

1. `POST /api/orders`로 주문을 생성합니다.
2. `POST /api/orders/{orderId}/orchestrate`가 호출되면 orchestration이 시작됩니다.
3. payment 승인 후 주문을 `PAID`로 전이합니다.
4. settlement 요청 후 주문을 `SETTLEMENT_REQUESTED`로 전이하고 outbox event를 남깁니다.
5. notification 요청 후 주문을 `NOTIFICATION_REQUESTED`로 전이하고 outbox event를 남깁니다.
6. 최종적으로 `COMPLETED`, 또는 실패 분기에 따라 `FAILED` / `CANCELLED`로 종료합니다.

### Module Intent

- `order`
  주문 생성, 상태 전이, 외부 비즈니스 진입 facade
- `orchestration`
  흐름 제어, orchestration step 기록
- `payment`
  결제 승인/취소와 provider 연동 추상화
- `settlement`
  정산 요청 기록
- `notification`
  알림 요청 기록
- `outbox`
  이벤트 저장, 재시도, publish, dead-letter 전환
- `audit`
  분기/재실행/실패 기록
- `common`
  공통 응답, 예외, 공통 기반 타입

### Dependency Direction

- `controller -> facade/service -> repository`
- `orchestration -> domain application service`
- 다른 domain의 repository를 직접 주입해서 흐름을 제어하지 않습니다.
- repository 패키지를 외부에 공개하는 방식보다 service 경계를 통해 협력하는 방식을 우선합니다.

## 3. Payment Provider 구조

`PaymentProviderClient`는 두 구현 중 하나가 설정으로 선택됩니다.

- `mock`
  `MockPaymentProviderClient`
  기본 모드입니다. `PAYMENT_PROVIDER_MOCK_FAILURE_TOKEN`이 description에 포함되면 실패를 시뮬레이션합니다.
- `external`
  `ExternalPaymentProviderClient`
  `baseUrl`, `apiKey`, `approvePath`, `cancelPath`, `connectTimeout`, `readTimeout`를 사용합니다.

현재 external 구현은 실제 연동을 붙일 수 있는 골격과 오류 매핑까지 포함하지만, provider별 상세 error mapping과 retry policy는 후속 과제입니다.

## 4. Outbox / Compensation 기준

### Outbox

- outbox event는 settlement / notification 후속 publish용으로 저장됩니다.
- publisher는 `nextAttemptAt`이 지난 `READY`, `RETRY_WAIT` 이벤트만 발행 대상으로 가져옵니다.
- publish 실패 시 `retryCount`, `failureCode`, `failureReason`, `nextAttemptAt`이 갱신됩니다.
- `maxRetryCount`를 초과하면 `DEAD_LETTER`로 전환됩니다.

### Compensation

- settlement 실패:
  payment 취소 보상을 수행하고 주문을 `CANCELLED`로 마무리합니다.
- notification 실패:
  payment/settlement를 되돌리지 않고 `FAILED` 상태와 `manual intervention / retry required` 성격의 compensation step을 남깁니다.

notification 운영 정책은 현재 1차 분리까지만 완료된 상태이며, 채널별 재전송/무시/운영자 개입 정책은 TODO입니다.

### Admin Reprocessing

- `POST /api/admin/notification-events/{id}/retry`
  notification 실패 건을 재전송 성공으로 처리하고 주문을 `COMPLETED`로 복구합니다.
- `POST /api/admin/notification-events/{id}/ignore`
  무시 가능한 notification 실패 건을 `IGNORED`로 정리하고 주문을 `COMPLETED`로 복구합니다.
- `POST /api/admin/outbox-events/{id}/retry`
  `DEAD_LETTER` outbox event만 대상으로 즉시 재발행을 시도합니다.

현재 admin 재처리는 전체 orchestration 재실행이 아니라, 실패한 하위 처리 단위를 명시적으로 복구하는 방식입니다.

## 5. 로컬 실행

### Prerequisites

- Java 21
- Docker

### Quick Start

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 로컬 값은 아래를 사용합니다.

- PostgreSQL: `jdbc:postgresql://localhost:5432/commerce_orchestration`
- Kafka: `localhost:9092`
- Kafka UI: `http://localhost:8085`
- payment provider mode: `mock`

`.env.example`을 참고해 환경변수를 맞출 수 있습니다.

### External Payment Provider 골격 확인

```bash
SPRING_PROFILES_ACTIVE=local \
PAYMENT_PROVIDER_MODE=external \
PAYMENT_PROVIDER_BASE_URL=http://localhost:8089 \
./gradlew bootRun
```

## 6. DB Schema / Migration

현재 DB 스키마는 Flyway migration으로 관리합니다.

- 초기 스키마: [V1__init.sql](/Users/gseobi/workspace/commerce-orchestration-backend/src/main/resources/db/migration/V1__init.sql)
- outbox retry/dead-letter 변경: [V2__outbox_retry_dead_letter.sql](/Users/gseobi/workspace/commerce-orchestration-backend/src/main/resources/db/migration/V2__outbox_retry_dead_letter.sql)

애플리케이션 기본/로컬/통합 테스트 프로필은 Flyway를 적용한 뒤 JPA `ddl-auto=validate`로 매핑 정합성을 확인합니다.  
단위 테스트용 `test` 프로필만 H2 `create-drop`과 `flyway disabled`를 유지합니다.

운영 점검용 SQL은 `docs/sql`에 정리되어 있습니다.

- [SQL Guide](docs/sql/README.md)
- [Outbox Operations](docs/sql/outbox-operations.sql)

## 7. 테스트 / CI

### Local Commands

- 컴파일 확인: `./gradlew compileJava`
- unit 성격 테스트: `./gradlew test`
- PostgreSQL/Kafka Testcontainers 통합 테스트: `./gradlew integrationTest`

### Current Coverage

- order create / detail / flow API
- JWT 발급 및 `/api/**` 보호
- orchestration happy path
- settlement failure compensation
- notification failure 분기
- outbox publish unit test
- PostgreSQL / Kafka 기반 outbox happy path integration test
- PostgreSQL / Kafka 기반 outbox retry -> dead-letter integration test

### GitHub Actions

현재 workflow는 아래 두 job을 사용합니다.

- `build-and-test`
  `compileJava`, `test`, unit report upload
- `integration-test`
  `integrationTest`, integration report upload

## 8. Notification 운영 정책

- `AUTO_RETRY`
  일시적 실패로 간주하며 notification event를 `RETRY_SCHEDULED`로 남깁니다.
- `MANUAL_INTERVENTION`
  운영자 확인이 필요하며 notification event를 `MANUAL_INTERVENTION_REQUIRED`로 남깁니다.
- `IGNORE`
  현재 범위에서 주문 완료를 막지 않아도 되는 실패로 보고 `IGNORED` 처리합니다.

현재 mock 분기 기준은 아래 description 토큰으로 시뮬레이션합니다.

- `FAIL_NOTIFICATION_RETRY`
- `FAIL_NOTIFICATION_MANUAL`
- `FAIL_NOTIFICATION_IGNORE`
- `FAIL_NOTIFICATION`

## 9. 남아 있는 TODO

- 실제 payment provider별 timeout / retry / error mapping 구체화
- notification 운영 정책 세분화
- admin 재처리 / 재검증 API
- refresh token / key rotation / user store 연동
- dead-letter 이벤트의 운영 자동화

## 10. Docs

- [Architecture Notes](docs/architecture/README.md)
- [Flow Notes](docs/flows/README.md)
- [Design Notes](docs/design-notes.md)
- [Test Report](docs/test-report.md)
- [Troubleshooting](docs/troubleshooting.md)
- [SQL Guide](docs/sql/README.md)
