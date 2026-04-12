# Test Report

이 문서는 현재 레포지토리 기준선에서 실제로 수행하는 검증 범위를 정리합니다.  
존재하지 않는 테스트 범위는 구현된 것처럼 적지 않고, 설계 TODO는 `docs/design-notes.md`에서 다룹니다.

## 1. 로컬 검증 명령

- `./gradlew compileJava`  
  메인 소스 컴파일 확인
- `./gradlew test`  
  H2 + mock Kafka 기반 검증
- `./gradlew integrationTest`  
  Testcontainers 기반 PostgreSQL / Kafka 통합 테스트

## 2. 현재 통과하는 검증 범위

| Area | Status | Notes |
|---|---|---|
| `compileJava` | Pass | 메인 소스 컴파일 성공 |
| `test` | Pass | 단위 테스트와 MockMvc 기반 흐름 검증 |
| `integrationTest` | Pass | PostgreSQL / Kafka Testcontainers 검증 |
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
| PostgreSQL / Kafka outbox happy path | Implemented | publish 후 Kafka 소비 검증 |
| PostgreSQL / Kafka outbox dead-letter path | Implemented | retry 후 dead-letter 전환 검증 |

## 3. 테스트 종류 차이

### `test`

- H2 메모리 DB 사용
- Flyway 비활성화
- mock `KafkaTemplate` 기반 검증 포함
- 빠른 회귀 확인 목적

### `integrationTest`

- PostgreSQL Testcontainer 사용
- Kafka Testcontainer 사용
- Flyway migration 적용 후 JPA `validate`
- outbox publish와 DB 스키마를 실인프라에 가깝게 검증

## 4. GitHub Actions 검증 범위

현재 workflow는 아래 두 job을 수행합니다.

- `build-and-test`  
  `./gradlew compileJava`, `./gradlew test`, unit 리포트 업로드
- `integration-test`  
  `./gradlew integrationTest`, integration 리포트 업로드

현재 artifact 이름은 아래와 같습니다.

- `gradle-unit-test-reports`
- `gradle-integration-test-reports`

## 5. CI 안정화 메모

이번 정리에서 `integrationTest` 실패 원인은 단순 Docker 부재가 아니라 Kafka Testcontainers 조합 문제로 확인했습니다.

기존 테스트 지원 코드는 `org.testcontainers.containers.KafkaContainer`와 `apache/kafka-native:3.8.0` 이미지를 함께 사용하고 있었고,  
GitHub Actions에서는 이 조합이 초기화 시점 `ExceptionInInitializerError`, `IllegalStateException`으로 드러났습니다.

현재는 `org.testcontainers.kafka.KafkaContainer`로 정합성을 맞췄고, 아래 기준으로 재검증했습니다.

- `./gradlew clean test --rerun-tasks`
- `./gradlew clean integrationTest --rerun-tasks --stacktrace`
- `./gradlew integrationTest --rerun-tasks --stacktrace`

## 6. 아직 검증하지 않은 범위

- 실제 외부 payment provider와의 네트워크 round-trip
- notification 운영 정책의 세분화된 채널별 재처리 / 운영 규칙
- dead-letter 운영 자동화
- admin 레벨 재처리 / 재검증 API 고도화
- refresh token / key rotation / user store 연동