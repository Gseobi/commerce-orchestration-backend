# commerce-orchestration-backend

Spring Boot 기반 commerce orchestration backend입니다.

이 프로젝트는 커머스 주문 API 자체보다, 주문 이후 이어지는 payment · settlement · notification · outbox publish 흐름에서 발생하는 상태 불일치, 실패 분기, 복구 경로를 어떻게 제어할 것인가에 초점을 둡니다.

주문 이후 후속 처리를 단순 순차 호출로 흩뿌리지 않고, `CommerceOrchestrationService`를 중심으로 명시적 상태 전이, failure branching, compensation, retry/dead-letter, admin reprocessing 흐름으로 추적 가능하게 구성했습니다.

이 레포는 pinned 대표 포트폴리오 레포로,
"커머스 거래 흐름에서 상태·정합성·실패 복구를 어떻게 설계했고, 어디까지 검증했는가"를
짧은 시간 안에 이해시키는 것을 목표로 합니다.

핵심 포인트는 세 가지입니다.

- **무엇을 제어하나:**  
  order 이후 payment, settlement, notification, outbox publish를 하나의 orchestration 흐름으로 제어하고, 각 단계의 상태 전이와 실패 지점을 데이터로 남깁니다.
- **무엇을 증명하나:**  
  커머스 거래 흐름에서 중요한 explicit state transition, failure branching, compensation, retry/dead-letter, admin reprocessing, Modulith boundary, Flyway + integration-test 기준선을 함께 보여줍니다.
- **어디를 보면 되나:**  
  먼저 이 README에서 전체 문제 정의와 검증 범위를 보고, 이어서 [Docs Index](/docs/README.md), [Architecture Notes](/docs/architecture/README.md), [Flow Notes](/docs/flows/README.md)를 보면 됩니다.

이 레포는 CRUD 화면 수나 엔드포인트 수보다 아래를 먼저 증명하는 데 초점을 둡니다.

- `CommerceOrchestrationService`를 중심으로 order lifecycle과 후속 거래 흐름을 명시적으로 제어하는 것
- 상태 전이, 실패 분기, 운영 복구 지점을 `order status`, `orchestration step`, `outbox event`, `notification event`, `audit log`로 남기는 것
- settlement 실패와 notification 실패를 같은 오류로 뭉개지 않고, 서로 다른 보상 / 재처리 / 무시 가능 실패 정책으로 분리하는 것
- outbox retry/dead-letter와 notification retry/manual intervention 흐름을 통해 실패 후 복구 가능한 운영 지점을 드러내는 것
- Spring Modulith 기준으로 module boundary를 검증하고, Testcontainers 기반 integration test로 실제 흐름을 확인하는 것

## 1. Start Here

처음 보는 사람 기준 권장 읽기 순서는 아래입니다.

1. **문서 구조:** [Docs Index](/docs/README.md)
2. **Modulith / 패키지 구조:** [Architecture Notes](/docs/architecture/README.md)
3. **Order / Payment / Settlement 흐름:** [Flow Notes](/docs/flows/README.md)
4. **설계 결정과 Trade-off:** [Design Notes](/docs/design-notes.md)
5. **구현 검토 / Boundary 판단:** [Implementation Review Notes](/docs/implementation-review-notes.md)
6. **테스트 결과 / 이슈 대응:** [Test Report](/docs/test-report.md) / [Troubleshooting](/docs/troubleshooting.md)

프로젝트 설계 의도와 구현 범위는 Velog 글에서도 정리했습니다.

- [커머스 주문 이후 흐름을 상태 전이와 Orchestration으로 설계하기](https://velog.io/@wsx2386/%EC%BB%A4%EB%A8%B8%EC%8A%A4-%EC%A3%BC%EB%AC%B8-%EC%9D%B4%ED%9B%84-%ED%9D%90%EB%A6%84%EC%9D%84-%EC%83%81%ED%83%9C-%EC%A0%84%EC%9D%B4%EC%99%80-Orchestration%EC%9C%BC%EB%A1%9C-%EC%84%A4%EA%B3%84%ED%95%98%EA%B8%B0)

draw.io 자산은 [Diagram Guide](/docs/diagrams/README.md) 기준으로 관리합니다.  
현재 overall architecture는 README 대표 이미지로만 유지하고, 세부 흐름과 테이블 관계는 Architecture Notes / Flow Notes / Diagram Guide에서 이어서 확인할 수 있습니다.

## Diagram Snapshot

이 그림은 현재 구현 기준의 overall architecture를 한 장으로 요약한 미리보기입니다.

![Overall architecture](/docs/diagrams/png/commerce_orchestration_overall_architecture.png)

- overall architecture PNG:  
  [commerce_orchestration_overall_architecture.png](/docs/diagrams/png/commerce_orchestration_overall_architecture.png)
- draw.io 원본:  
  [commerce_orchestration_overall_architecture.drawio](/docs/diagrams/source/commerce_orchestration_overall_architecture.drawio)
- PDF:  
  [commerce_orchestration_overall_architecture.pdf](/docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf)

## 2. Snapshot

- **역할:**  
  주문 생성 이후 payment, settlement, notification, outbox publish를 orchestration 관점에서 제어하는 backend
- **핵심 문제:**  
  결제 성공 이후 정산 실패, 알림 실패, 이벤트 발행 실패처럼 일부 후속 처리만 실패했을 때 내부 상태를 어떻게 남기고 복구할 것인가
- **강점:**  
  explicit state transition, compensation 분리, notification retry/manual/ignore 정책, outbox retry/dead-letter, admin reprocessing
- **거래/연동 관점:**  
  외부 payment provider 연동 골격, settlement failure compensation, outbox 기반 후속 publish 분리를 통해 거래 흐름의 정합성과 복구 가능성을 보여줍니다.
- **모듈 구조:**  
  Spring Modulith 기반으로 `*.api` 공개 계약과 `ApplicationModules.verify()` 검증을 사용합니다.
- **의존성 상태:**  
  현재 build.gradle은 검증된 구현 범위 기준으로 정리되어 있습니다.  
  Spring MVC + WebClient + Security + JPA/Flyway + Kafka + Modulith + Testcontainers 조합을 유지합니다.
- **현재 로컬 검증:**  
  `./gradlew clean test --rerun-tasks`,  
  `./gradlew clean integrationTest --rerun-tasks --stacktrace` 기준 통과
- **현재 CI 상태:**  
  GitHub Actions는 `build-and-test`, `integration-test` 두 job으로 유지 중이며, 현재 기준 green baseline을 확보했습니다.

## 3. 이 레포가 현재 증명하는 것

- 주문 이후 후속 작업을 controller 단위로 흩뿌리지 않고 orchestration service에서 흐름 중심으로 제어합니다.
- 주문 상태 전이는 묵시적 처리 대신 명시적 상태 변경으로 기록됩니다.
- payment / settlement / notification / outbox 실패를 서로 다른 정책으로 분기합니다.
- settlement 실패는 payment cancel compensation 흐름으로 처리합니다.
- notification 실패는 `AUTO_RETRY`, `MANUAL_INTERVENTION`, `IGNORE` 정책으로 분기합니다.
- `RETRY_SCHEDULED` notification event는 due processor를 통해 재처리할 수 있습니다.
- outbox를 통해 후속 이벤트 발행을 분리하고, retry/dead-letter 전이를 코드와 데이터로 추적합니다.
- 운영자는 전체 orchestration 재실행이 아니라 실패한 하위 처리 단위를 admin 재처리 API로 복구할 수 있습니다.
- Spring Modulith 기준으로 domain module의 공개 API와 내부 구현 경계를 검증합니다.
- Testcontainers 기반 PostgreSQL / Kafka integration test로 주요 상태 전이와 복구 흐름을 검증합니다.

## 4. 문제 정의

커머스 주문 이후에는 결제 승인, 정산 요청, 알림 발송, 이벤트 발행, 실패 복구가 이어집니다.

실제 운영 환경에서는 모든 후속 처리가 한 번에 성공하지 않습니다. 예를 들어 결제는 성공했지만 정산 요청이 실패하거나, 거래 자체는 완료됐지만 알림 발송 또는 후속 이벤트 발행만 실패할 수 있습니다. 이때 중요한 문제는 단순 rollback이 아니라, 어느 단계까지 성공했는지, 어디서 실패했는지, 어떤 단위로 재처리할 수 있는지를 명확히 남기는 것입니다.

이 레포는 주문 이후 후속 흐름을 여러 controller와 ad-hoc service 호출에 분산시키지 않고, 명시적 상태 전이와 운영 복구 경로가 보이는 orchestration backend로 정리하는 것을 목표로 합니다.

현재 기준선은 아래와 같습니다.

- 비즈니스 외부 진입점은 `OrderController`입니다.
- 인증용 `AuthController`는 데모 JWT 발급 보조 엔드포인트만 제공합니다.
- `CommerceOrchestrationService`는 주문 이후 흐름 제어를 담당합니다.
- `NotificationRetryProcessor`는 `RETRY_SCHEDULED` notification event의 due retry 처리를 담당합니다.
- `payment`, `settlement`, `notification`, `outbox`, `audit`는 각자 자기 repository를 내부 service가 소유합니다.
- 결제 provider는 `PaymentProviderClient` 인터페이스 아래 `mock` / `external` 구현으로 분리되어 있습니다.
- outbox는 `READY -> RETRY_WAIT -> PUBLISHED / DEAD_LETTER` 상태를 사용합니다.
- settlement 실패와 notification 실패는 동일 보상 정책으로 처리하지 않습니다.
- admin은 `notification-events`, `outbox-events` 단위로 명시적 재처리를 수행할 수 있습니다.
- DB 스키마의 source of truth는 Flyway migration입니다.

## 5. 핵심 설계

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
  흐름 제어, orchestration step 기록, notification retry processor 조합
- `payment`  
  결제 승인/취소와 provider 연동 추상화
- `settlement`  
  정산 요청 기록
- `notification`  
  알림 요청 기록, 실패 정책, retry 상태 관리
- `outbox`  
  이벤트 저장, 재시도, publish, dead-letter 전환
- `audit`  
  분기/재실행/실패 기록
- `admin`  
  notification / outbox 운영 재처리 API
- `common`  
  공통 응답, 예외, 공통 기반 타입

### Dependency Direction

- `controller -> facade/service -> repository`
- `orchestration -> domain application contract`
- 다른 domain의 repository를 직접 주입해서 흐름을 제어하지 않습니다.
- repository 패키지를 외부에 공개하는 방식보다 `*.api` 공개 계약을 통해 협력하는 방식을 우선합니다.
- Spring Modulith의 `@NamedInterface("api")`와 `allowedDependencies`를 통해 module boundary를 명시합니다.

### 왜 이런 구조를 택했는가

- 주문 이후 거래 흐름은 payment, settlement, notification, outbox publish처럼 서로 다른 실패 가능성과 복구 기준을 가진 단계로 이어지기 때문에, 흐름 제어를 한곳에 모아 분기와 종료 조건을 읽기 쉽게 유지합니다.
- 상태 전이와 보상 경로를 명시적으로 남겨 "어디까지 성공했고, 어디서 실패했으며, 어떤 단위로 복구할 수 있는가"를 추적 가능하게 만듭니다.
- settlement 실패는 거래 정합성에 영향을 주는 실패로 보고 payment cancel compensation까지 연결합니다.
- notification 실패는 거래 자체를 되돌릴 실패가 아니므로 단순 rollback 대신 retry / manual intervention / ignore 정책으로 분리합니다.
- outbox를 별도 관리해 후속 publish의 재시도와 dead-letter 전환을 비즈니스 처리와 분리합니다.
- 각 domain의 내부 구현을 숨기고, orchestration은 공개된 application contract만 조합하도록 구성합니다.

## 6. Payment Provider 구조

`PaymentProviderClient`는 두 구현 중 하나가 설정으로 선택됩니다.

- `mock`  
  `MockPaymentProviderClient`  
  기본 모드입니다. `PAYMENT_PROVIDER_MOCK_FAILURE_TOKEN`이 description에 포함되면 실패를 시뮬레이션합니다.
- `external`  
  `ExternalPaymentProviderClient`  
  `baseUrl`, `apiKey`, `approvePath`, `cancelPath`, `connectTimeout`, `readTimeout`를 사용합니다.

현재 external 구현은 실제 연동을 붙일 수 있는 골격과 오류 매핑까지 포함하지만, provider별 상세 error mapping과 retry policy는 후속 과제입니다.

## 7. Outbox / Compensation / Recovery 기준

### Outbox

- outbox event는 settlement / notification 후속 publish용으로 저장됩니다.
- publisher는 `nextAttemptAt`이 지난 `READY`, `RETRY_WAIT` 이벤트만 발행 대상으로 가져옵니다.
- publish 실패 시 `retryCount`, `failureCode`, `failureReason`, `nextAttemptAt`이 갱신됩니다.
- `maxRetryCount`를 초과하면 `DEAD_LETTER`로 전환됩니다.

### Compensation

- settlement 실패:  
  payment 취소 보상을 수행하고 주문을 `CANCELLED`로 마무리합니다.
- notification 실패:  
  payment/settlement를 되돌리지 않고 notification policy에 따라 `AUTO_RETRY`, `MANUAL_INTERVENTION`, `IGNORE`로 분기합니다.

### Notification Recovery

- `AUTO_RETRY`  
  notification event를 `RETRY_SCHEDULED`로 남기고, `nextAttemptAt` 이후 due processor가 재처리할 수 있습니다.
- `MANUAL_INTERVENTION`  
  운영자 확인이 필요하며 notification event를 `MANUAL_INTERVENTION_REQUIRED`로 남깁니다.
- `IGNORE`  
  현재 범위에서 주문 완료를 막지 않아도 되는 실패로 보고 `IGNORED` 처리합니다.

`NotificationRetryProcessor`는 due retry 대상만 조회해 처리합니다.

- 성공 시 notification event를 `SENT`로 전환하고 order를 `COMPLETED`로 복구합니다.
- future retry event는 처리하지 않습니다.
- 반복 실패 시 backoff 재스케줄 또는 `MANUAL_INTERVENTION_REQUIRED` 전환을 수행합니다.

기본 설정에서는 `notification.retry.scheduler.enabled=false`로 비활성화되어 있습니다.  
속성을 켜면 property-gated `NotificationRetryScheduler`가 `NotificationRetryProcessor`를 주기적으로 호출합니다.  
운영자는 `POST /api/admin/notification-events/retry-due`로 due notification retry batch를 수동 실행할 수 있으며, 현재 응답은 `{ "status": "triggered" }`입니다.

### Admin Reprocessing

- `POST /api/admin/notification-events/{id}/retry`  
  notification 실패 건을 재전송 성공으로 처리하고 주문을 `COMPLETED`로 복구합니다.
- `POST /api/admin/notification-events/{id}/ignore`  
  무시 가능한 notification 실패 건을 `IGNORED`로 정리하고 주문을 `COMPLETED`로 복구합니다.
- `POST /api/admin/outbox-events/{id}/retry`  
  `DEAD_LETTER` outbox event만 대상으로 즉시 재발행을 시도합니다.

현재 admin 재처리는 전체 orchestration 재실행이 아니라, 실패한 하위 처리 단위를 명시적으로 복구하는 방식입니다.

## 8. 현재 검증 범위

이 레포는 "흐름 제어와 실패 처리 구조가 실제로 동작하는가"를 현재 기준으로 아래까지 검증합니다.

- `./gradlew compileJava`  
  메인 소스 컴파일 확인
- `./gradlew test`  
  H2 + MockMvc 중심 단위/흐름 검증
- `./gradlew integrationTest`  
  PostgreSQL/Kafka Testcontainers 기반 통합 검증

현재 실제 구현 및 검증 범위는 아래를 포함합니다.

- order create / detail / flow API
- JWT 발급 및 `/api/**` 보호
- orchestration happy path
- settlement failure compensation
- notification failure 분기와 ignore policy
- admin notification / outbox reprocessing
- outbox publish 상태 전이
- PostgreSQL / Kafka 기반 outbox happy path integration test
- PostgreSQL / Kafka 기반 outbox retry -> dead-letter integration test
- notification AUTO_RETRY due processor
- RETRY_SCHEDULED notification event 재처리
- retry 성공 시 notification `SENT` 및 order `COMPLETED` 복구
- future retry event skip
- 반복 실패 시 retry backoff 또는 `MANUAL_INTERVENTION_REQUIRED` 전환
- Spring Modulith `ApplicationModules.verify()` 기반 module boundary 검증

GitHub Actions workflow도 아래 두 job을 수행합니다.

- `build-and-test`  
  `compileJava`, `test`, unit report upload
- `integration-test`  
  `integrationTest`, integration report upload

이번 정리 시점의 로컬 재검증에서는 `test`, `integrationTest` 기준선을 다시 확인했습니다.  
README에서는 구현 범위를 과장하지 않고, "무엇을 검증하는 레포인지"와 "어디까지 확인했는지"를 분리해 적습니다.

## 9. 문서와 다이어그램 위치

- 문서 진입점: [Docs Index](/docs/README.md)
- 구조 설명: [Architecture Notes](/docs/architecture/README.md)
- 흐름 설명: [Flow Notes](/docs/flows/README.md)
- 다이어그램 가이드: [Diagram Guide](/docs/diagrams/README.md)

현재 아래 draw.io 자산을 관리합니다.

- overall architecture
- overall architecture reference
- order orchestration flow
- outbox retry / dead-letter flow
- notification retry / manual intervention flow
- table relation overview

## 10. 아직 남은 범위

아래는 현재도 후속 과제로 유지하는 항목입니다.

- 실제 payment provider별 timeout / retry / error mapping 고도화
- notification 채널별 retry policy 세분화
- dead-letter 이벤트의 운영 자동화
- refresh token / key rotation / user store 연동
- admin 레벨 재처리 / 재검증 API 고도화
- notification retry batch 응답에 processedCount 등 운영 지표 추가

짧게 말해 이 프로젝트는 CRUD showcase보다는 커머스 거래 흐름의 orchestration, explicit state transition, failure handling, compensation, retry/dead-letter, 운영 복구 지점을 보여주는 포트폴리오 성격이 강합니다.

## 11. 로컬 실행

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

## 12. DB Schema / Migration

DB 스키마는 Flyway migration을 기준으로 관리합니다.

- 초기 스키마: [V1__init.sql](src/main/resources/db/migration/V1__init.sql)
- outbox retry / dead-letter 변경: [V2__outbox_retry_dead_letter.sql](src/main/resources/db/migration/V2__outbox_retry_dead_letter.sql)
- notification admin policy 변경: [V3__notification_admin_policy.sql](src/main/resources/db/migration/V3__notification_admin_policy.sql)

애플리케이션 기본/로컬/통합 테스트 프로필은 Flyway를 적용한 뒤 JPA `ddl-auto=validate`로 매핑 정합성을 확인합니다.  
단위 테스트용 `test` 프로필만 H2 `create-drop`과 `flyway disabled`를 유지합니다.

운영 점검용 SQL은 `docs/sql`에 정리되어 있습니다.

- [SQL Guide](/docs/sql/README.md)
- [Outbox Operations](/docs/sql/outbox-operations.sql)

## 13. Notification 운영 정책

- `AUTO_RETRY`  
  일시적 실패로 간주하며 notification event를 `RETRY_SCHEDULED`로 남깁니다.  
  due 시점이 된 이벤트는 `NotificationRetryProcessor`를 통해 재처리되며, scheduler는 기본 비활성 상태에서 property로 활성화할 수 있습니다.
  운영자는 `POST /api/admin/notification-events/retry-due`로 동일 trigger를 수동 실행할 수 있고, 현재 응답은 `{ "status": "triggered" }`만 반환합니다.
- `MANUAL_INTERVENTION`  
  운영자 확인이 필요하며 notification event를 `MANUAL_INTERVENTION_REQUIRED`로 남깁니다.
- `IGNORE`  
  현재 범위에서 주문 완료를 막지 않아도 되는 실패로 보고 `IGNORED` 처리합니다.

현재 mock 분기 기준은 아래 description 토큰으로 시뮬레이션합니다.

- `FAIL_NOTIFICATION_RETRY`
- `FAIL_NOTIFICATION_MANUAL`
- `FAIL_NOTIFICATION_IGNORE`
- `FAIL_NOTIFICATION`

## 14. Blog / Notes

- Velog: [커머스 주문 이후 흐름을 상태 전이와 Orchestration으로 설계하기](https://velog.io/@wsx2386/%EC%BB%A4%EB%A8%B8%EC%8A%A4-%EC%A3%BC%EB%AC%B8-%EC%9D%B4%ED%9B%84-%ED%9D%90%EB%A6%84%EC%9D%84-%EC%83%81%ED%83%9C-%EC%A0%84%EC%9D%B4%EC%99%80-Orchestration%EC%9C%BC%EB%A1%9C-%EC%84%A4%EA%B3%84%ED%95%98%EA%B8%B0)
- Portfolio Index: [Backend Portfolio / Notes Index](https://velog.io/@wsx2386/%EB%B0%B1%EC%97%94%EB%93%9C-%ED%8F%AC%ED%8A%B8%ED%8F%B4%EB%A6%AC%EC%98%A4-%EA%B8%80-%EB%AA%A8%EC%9D%8C-%EC%9A%B4%EC%98%81%ED%98%95-Backend-%EB%AC%B8%EC%A0%9C%EB%A5%BC-%EA%B5%AC%EC%A1%B0%EB%A1%9C-%ED%92%80%EC%96%B4%EB%82%B8-%EA%B8%B0%EB%A1%9D)

## 15. Docs

- [Docs Index](/docs/README.md)
- [Architecture Notes](/docs/architecture/README.md)
- [Flow Notes](/docs/flows/README.md)
- [Diagram Guide](/docs/diagrams/README.md)
- [Design Notes](/docs/design-notes.md)
- [Test Report](/docs/test-report.md)
- [Troubleshooting](/docs/troubleshooting.md)
- [SQL Guide](/docs/sql/README.md)
