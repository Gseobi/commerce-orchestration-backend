# commerce-orchestration-backend

주문 생성 이후 결제, 정산, 알림 같은 후속 작업이 이어지는 환경에서,  
단순 CRUD보다 **흐름 제어와 상태 가시성**이 더 중요하다는 점을 보여주기 위한  
Java / Spring Boot 기반 Commerce Orchestration Backend입니다.

이 프로젝트는 기능 수를 늘리는 것보다,  
**order lifecycle을 명시적 상태 전이와 orchestration step 기록으로 관리하고  
실패 분기와 후속 확장 포인트를 설명 가능한 구조로 만드는 것**에 초점을 맞췄습니다.

<br/>

## 1. Quick Proof

- `CommerceOrchestrationService`가 주문 이후의 흐름을 중앙에서 제어합니다.
- `payment`, `settlement`, `notification`, `outbox`, `audit`는 각자 자기 저장소를 내부 service에서 소유합니다.
- `OrderController`가 주문 관련 public API 진입점을 유지하고, 인증은 JWT 발급용 `AuthController`로 분리되어 있습니다.
- 상태 전이와 orchestration step, outbox event를 함께 남겨 흐름 진행 상태를 추적할 수 있습니다.
- 실제 Kafka consumer나 외부 PG 연동을 전부 붙이기 전에도, **확장 방향과 운영 관찰 지점**을 코드와 문서로 설명할 수 있습니다.

<br/>

## 2. Execution Evidence

### Architecture Overview

<div align="center">
  <img src="docs/images/kafka_orchestration_backend_architecture.png" alt="Commerce Orchestration Backend Architecture" width="960" />
</div>

<div align="center">
  <sub>
    Source:
    <a href="docs/diagram/kafka_orchestration_backend_architecture.drawio">draw.io</a> ·
    <a href="docs/pdf/kafka_orchestration_backend_architecture.pdf">PDF</a>
  </sub>
</div>

<br/>

### Verification Summary

| Scenario | Expected Behavior | Status | Evidence |
|---|---|---|---|
| `POST /api/auth/token` | 데모용 JWT access token 발급 | Implemented | 코드, `docs/test-report.md` |
| `POST /api/orders` | 주문 생성 시 `CREATED` 상태 저장 | Implemented | 코드, `docs/test-report.md` |
| `POST /api/orders/{orderId}/orchestrate` | happy path와 실패 분기 기준 상태 전이 및 step 기록 생성 | Implemented | 코드, `docs/test-report.md` |
| `GET /api/orders/{orderId}` | 주문과 후속 단계 상태 요약 조회 | Implemented | 코드, `docs/test-report.md` |
| `GET /api/orders/{orderId}/flow` | orchestration step / outbox event 조회 | Implemented | 코드, `docs/test-report.md` |
| JWT 보호 | `/api/**` 인증 필요, `/api/auth/token`만 공개 | Implemented | 코드, `docs/test-report.md` |
| Docker Compose 로컬 인프라 | PostgreSQL, Kafka, Kafka UI 실행 가능 | Implemented | `compose.yaml`, `docs/troubleshooting.md` |
| CI 기본 검증 | compile, test, test report artifact 업로드 | Implemented | `.github/workflows/ci.yml` |
| Payment provider 실제 외부 연동 | mock 기반, 실제 timeout/retry 정책 미구현 | Planned | `docs/design-notes.md` |
| Outbox retry/backoff / dead-letter | publish 실패 표시까지만 구현 | Planned | `docs/design-notes.md` |
| JWT refresh/key rotation/user store | access token 발급 중심 최소 구조 | Planned | `docs/design-notes.md` |

### What This Proves

- 주문 이후의 후속 처리 흐름을 **중앙 orchestration 계층**으로 제어할 수 있습니다.
- 각 domain service가 자기 repository를 내부적으로 소유하도록 정리해, repository 패키지 직접 참조 없이도 흐름 제어가 가능합니다.
- JWT, Docker Compose, CI가 붙은 상태에서도 구조의 핵심은 여전히 **flow control + explicit state + outbox 확장성**입니다.

<br/>

## 3. Problem & Design Goal

커머스 시스템에서 주문 생성만 성공했다고 해서 비즈니스 플로우가 끝난 것은 아닙니다.

- payment 단계가 실패할 수 있습니다.
- payment 성공 후 settlement나 notification이 누락될 수 있습니다.
- retry 과정에서 같은 요청이 중복 처리될 수 있습니다.
- DB 상태 변경과 메시지 발행 시점이 어긋날 수 있습니다.
- 운영 관점에서는 지금 어떤 주문이 어느 단계에서 멈췄는지 **상태 가시성**이 중요합니다.

그래서 이 프로젝트는 CRUD 수를 늘리는 것보다,  
**주문 이후 이어지는 flow를 어떻게 제어하고 관찰할 것인가**를 먼저 보여주도록 설계했습니다.

<br/>

## 4. Key Design

### 1) orchestration과 domain 책임 분리

- `CommerceOrchestrationService`는 흐름 제어만 담당합니다.
- `OrderService`, `PaymentService`, `SettlementService`, `NotificationService`는 각자 자기 domain 상태와 저장소를 관리합니다.
- orchestration은 repository를 직접 주입받기보다 domain service와 협력하도록 정리했습니다.

### 2) 명시적 상태 전이

- 주문은 `CREATED -> PAYMENT_PENDING -> PAID -> SETTLEMENT_REQUESTED -> NOTIFICATION_REQUESTED -> COMPLETED` 흐름을 가집니다.
- 실패 시 `FAILED`, `CANCELLED`로 분기하며 보상 여부를 step과 audit에 남깁니다.

### 3) 단계 기록과 운영 가시성

- `OrchestrationStep`으로 단계별 성공/실패/보상 준비 상태를 남깁니다.
- `AuditLog`로 idempotent replay, rejected state, failure branch를 기록합니다.

### 4) Outbox 기반 확장성

- settlement / notification 후속 publish는 `OutboxEvent`로 남깁니다.
- 현재는 scheduler + publisher가 `READY -> PUBLISHED / FAILED` 상태 전이만 담당합니다.
- 재발행 정책과 backoff, dead-letter는 의도적으로 후속 과제로 남겨 두었습니다.

### 5) JWT 기반 API 보호

- `/api/auth/token`에서 데모용 access token을 발급합니다.
- `/api/**`는 기본적으로 JWT 인증이 필요합니다.
- `/actuator/health`는 공개하고, 나머지 actuator는 `ROLE_ADMIN`으로 보호합니다.
- 현재 구조는 **API 보호와 테스트 가능성 확보**가 목적이며, refresh token / key rotation / user store는 이후 단계입니다.

<br/>

## 5. Runtime / API / Infra

### Flow Summary

1. 클라이언트가 JWT를 발급받습니다.
2. `POST /api/orders`로 주문을 생성합니다.
3. `POST /api/orders/{orderId}/orchestrate` 호출 시 orchestration이 시작됩니다.
4. payment 승인 후 주문 상태를 `PAID`로 전이합니다.
5. settlement 요청과 outbox event를 기록합니다.
6. notification 요청과 outbox event를 기록합니다.
7. 최종적으로 `COMPLETED`, 또는 실패 분기에 따라 `FAILED` / `CANCELLED` 상태와 compensation step을 남깁니다.

### Current API List

- `POST /api/auth/token`
- `POST /api/orders`
- `POST /api/orders/{orderId}/orchestrate`
- `GET /api/orders/{orderId}`
- `GET /api/orders/{orderId}/flow`

### Local Infrastructure

- PostgreSQL: `localhost:5432`
- Kafka: `localhost:9092`
- Kafka UI: `localhost:8085`

`compose.yaml`은 로컬 개발용 인프라 부트스트랩을 담당합니다.  
애플리케이션의 기본 datasource와 Kafka bootstrap 서버도 여기에 맞춰 설정되어 있습니다.

### CI

GitHub Actions는 현재 다음을 수행합니다.

- JDK 21 설정
- `./gradlew compileJava`
- `./gradlew test`
- 테스트 리포트와 결과 artifact 업로드

이 단계는 **compile-safe 유지와 기본 회귀 확인**이 목적입니다.  
PostgreSQL/Kafka 실환경 검증은 아직 CI 기본 job에 포함되어 있지 않습니다.

<br/>

## 6. Why Some TODOs Still Remain

남아 있는 TODO는 단순 미완성이 아니라, 현재 구조에서 **다음 복잡도를 어디에 추가할지 명확히 하기 위해** 일부러 뒤로 둔 항목입니다.

- 실제 `PaymentProviderClient` 연동:
  지금은 mock provider로 흐름 제어와 실패 분기부터 고정해 둔 상태입니다. 외부 PG 연동을 먼저 붙이면 timeout, retry, idempotency, error mapping 복잡도가 한 번에 들어오므로 다음 단계로 분리했습니다.
- outbox retry/backoff / dead-letter:
  현재는 publish 성공/실패 기록까지만 구현되어 있습니다. 재시도 정책은 운영 정책과 결합되므로, 실제 발행 책임을 먼저 고정한 뒤 넣는 편이 자연스럽습니다.
- notification compensation:
  settlement 실패와 notification 실패는 동일한 보상 전략을 쓰지 않을 가능성이 큽니다. 그래서 지금은 notification compensation을 TODO 상태로 명시만 해 두었습니다.
- refresh token / key rotation / user store:
  현재 JWT는 서비스 보호와 테스트 자동화를 위한 access token 중심 구조입니다. 인증 체계를 운영 수준으로 올리려면 저장소 연동과 키 관리가 필요합니다.
- schema migration:
  현재는 `ddl-auto` 기반으로 개발 속도를 우선하고 있습니다. 협업/배포 단계로 가려면 Flyway 또는 Liquibase로 전환해야 합니다.
- Kafka/PostgreSQL integration test:
  현재 테스트는 H2와 mock Kafka 중심입니다. 인프라 실제성 검증은 Testcontainers 단계에서 추가하는 것이 맞습니다.

<br/>

## 7. Recommended Next Order

현재 구조 기준으로 다음 구현 순서는 아래가 가장 자연스럽습니다.

1. `PaymentProviderClient`를 실제 외부 연동 client와 mock client로 분리 고도화
2. outbox retry/backoff 및 dead-letter 전략 추가
3. compensation 정책을 settlement / notification 별로 분리
4. Testcontainers 기반 PostgreSQL / Kafka 통합 테스트 추가
5. GitHub Actions에 integration-test job과 artifact/report 업로드 확장

이 순서를 택한 이유는 **흐름 제어의 외부 실패 요인 -> 재처리 정책 -> 보상 정책 -> 실인프라 검증 -> CI 확장** 순서가 가장 변경 비용이 낮기 때문입니다.

<br/>

## 8. Notes / Docs

- [Architecture Notes](docs/architecture/README.md)
- [Flow Notes](docs/flows/README.md)
- [Design Notes](docs/design-notes.md)
- [Test Report](docs/test-report.md)
- [Troubleshooting](docs/troubleshooting.md)

### Development Notes

초기 프로젝트 부트스트랩 단계에서는 AI 도구를 활용해 반복성 높은 클래스 뼈대와 문서 초안을 빠르게 만들었습니다.

반면, 이 프로젝트의 핵심인 도메인 책임 분리, 상태 전이 흐름, orchestration 경계, repository 의존 방향, outbox 확장 포인트, 공개 API 설계는 직접 판단하여 반영했습니다. 생성 결과는 그대로 두지 않고 compile 확인과 구조 수정, 문서 갱신을 거쳐 현재 방향에 맞게 정제했습니다.
