# Test Report

## 1. 현재 검증 기준

이 문서는 현재 레포지토리 기준선에서 어떤 검증을 실제로 수행하는지 정리합니다.  
존재하지 않는 테스트 범위는 구현된 것처럼 적지 않습니다.

## 2. 로컬 검증 명령

- `./gradlew compileJava`
  메인 소스 compile-safe 확인
- `./gradlew test`
  H2 + mock Kafka 중심 unit/integration-lite 검증
- `./gradlew integrationTest`
  Testcontainers 기반 PostgreSQL / Kafka 통합 테스트

## 3. 현재 통과하는 검증 범위

| Area | Current Status | Notes |
|---|---|---|
| `compileJava` | Pass | 메인 소스 컴파일 성공 |
| `test` | Pass | 단위 테스트와 MockMvc 기반 흐름 검증 |
| `integrationTest` | Pass | PostgreSQL/Kafka Testcontainers 검증 |
| JWT token issuance | Implemented | `/api/auth/token` |
| `/api/**` authentication | Implemented | 인증 없는 주문 생성 `401` 확인 |
| Order create / detail / flow API | Implemented | `OrderFlowIntegrationTest` |
| Orchestration happy path | Implemented | 상태 전이, step, outbox 생성 검증 |
| Settlement failure compensation | Implemented | payment cancel compensation 검증 |
| Notification failure branch | Implemented | compensation step `READY` 검증 |
| Notification ignore policy | Implemented | ignore 가능한 실패는 주문 완료 유지 |
| Admin notification reprocessing | Implemented | retry 후 주문 `COMPLETED` 복구 |
| Admin outbox reprocessing | Implemented | dead-letter 즉시 재발행 검증 |
| Outbox publish unit test | Implemented | `PUBLISHED`, `RETRY_WAIT`, `DEAD_LETTER` 전이 검증 |
| PostgreSQL/Kafka outbox happy path | Implemented | publish 후 Kafka 소비 검증 |
| PostgreSQL/Kafka outbox dead-letter path | Implemented | retry 후 dead-letter 전환 검증 |

## 4. 테스트 종류 차이

### `test`

- H2 메모리 DB 사용
- Flyway 비활성화
- mock `KafkaTemplate` 기반 검증 포함
- 빠른 회귀 확인이 목적

### `integrationTest`

- PostgreSQL Testcontainer 사용
- Kafka Testcontainer 사용
- Flyway migration 적용 후 JPA `validate`
- outbox publish와 DB 스키마를 실인프라에 가깝게 검증

## 5. GitHub Actions 검증 범위

현재 workflow는 아래 두 job을 수행합니다.

- `build-and-test`
  `./gradlew compileJava`, `./gradlew test`, unit 리포트 업로드
- `integration-test`
  `./gradlew integrationTest`, integration 리포트 업로드

artifact 이름도 실제 workflow와 맞춰 아래를 사용합니다.

- `gradle-unit-test-reports`
- `gradle-integration-test-reports`

## 6. 아직 검증하지 않는 범위

- 실제 외부 payment provider와의 네트워크 round-trip
- notification 운영 정책의 세분화된 채널별 재처리
- admin 수동 재처리 API
- refresh token / key rotation / user store 연동
