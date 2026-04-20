# Troubleshooting

이 문서는 로컬 실행, 테스트, Flyway, Kafka/PostgreSQL, payment provider, outbox retry/dead-letter, notification recovery 흐름에서 발생할 수 있는 문제를 빠르게 확인하기 위한 운영 참고 문서입니다.

단순 실행 오류뿐 아니라, settlement 실패와 notification 실패가 왜 서로 다르게 처리되는지, admin 재처리가 왜 전체 orchestration 재실행이 아닌 하위 처리 단위 복구인지도 함께 설명합니다.

## 1. Quick Checks

### 1.1 `401 Unauthorized`가 발생하는 경우

- `/api/**`는 JWT 인증이 필요합니다.
- 먼저 `POST /api/auth/token`으로 access token을 발급받아야 합니다.
- `Authorization: Bearer <token>` 헤더가 빠졌는지 확인합니다.

### 1.2 로컬에서 PostgreSQL / Kafka 연결이 실패하는 경우

- 먼저 `docker compose up -d`로 `compose.yaml`의 인프라를 올립니다.
- 기본 기대 값은 PostgreSQL `localhost:5432`, Kafka `localhost:9092`, Kafka UI `localhost:8085`입니다.
- 포트를 바꿨다면 `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `POSTGRES_PORT`, `KAFKA_PORT`를 같이 맞춰야 합니다.

### 1.3 profile mismatch로 실행이 꼬이는 경우

- 로컬 실행은 `SPRING_PROFILES_ACTIVE=local`을 기준으로 맞추는 편이 안전합니다.
- 단위 테스트는 `test` 프로필, Testcontainers 통합 테스트는 `integration-test` 프로필을 사용합니다.
- IDE 기본 실행 설정에 남아 있는 profile/env var가 현재 문서와 다를 수 있으니 먼저 확인합니다.

### 1.4 Flyway migration 오류가 나는 경우

- 기본/로컬/통합 테스트 프로필은 Flyway 적용 후 JPA `ddl-auto=validate`를 사용합니다.
- 이미 존재하는 로컬 DB가 예전 `ddl-auto=update` 기반 스키마를 가지고 있다면 migration 적용 시점에 충돌할 수 있습니다.
- 이 경우 로컬 개발 DB를 비우고 migration부터 다시 적용하는 편이 안전합니다.
- 실제 스키마 기준은 `src/main/resources/db/migration`입니다.

### 1.5 테스트는 통과하는데 로컬 실행에서만 인프라 오류가 나는 경우

- `./gradlew test`는 H2 + mock Kafka 중심이므로 외부 인프라 의존성이 낮습니다.
- 반면 애플리케이션 기본 실행과 `integrationTest`는 PostgreSQL / Kafka를 사용합니다.
- 따라서 `test` 성공이 곧 실인프라 연결 성공을 의미하지는 않습니다.

### 1.6 `integrationTest`가 로컬에서 실패하는 경우

- Docker daemon이 켜져 있어야 합니다.
- `integrationTest`는 PostgreSQL / Kafka Testcontainers를 사용합니다.
- 로컬에서는 `./gradlew test`와 `./gradlew integrationTest`를 구분해서 보는 편이 맞습니다.

### 1.7 GitHub Actions `integration-test`가 실패하는 경우

- Docker availability만 확인하지 말고, integration-test 시작 로그에서 Flyway가 실제로 어떤 JDBC URL / classpath migration을 보는지 같이 확인하는 편이 안전합니다.
- 현재 integration-test 공통 지원 코드는 `spring.datasource.*`뿐 아니라 `spring.flyway.*`도 같은 Testcontainers PostgreSQL로 명시적으로 고정합니다.
- 또한 Flyway migrate 직전/직후에 `flyway_schema_history`와 `information_schema.tables` 기준 테이블 목록을 로그로 남깁니다.
- 그래서 CI에서 다시 `SchemaManagementException`이 나면 `audit_logs`가 migration 미적용인지, 다른 datasource/schema를 본 것인지 바로 구분할 수 있습니다.

## 2. Runtime / Operation Notes

### 2.1 Payment provider가 예상과 다르게 동작하는 경우

- 기본값은 `app.payment.provider.mode=mock`입니다.
- 실제 외부 provider 골격을 사용하려면 `PAYMENT_PROVIDER_MODE=external`로 바꿔야 합니다.
- 외부 provider 모드에서는 `PAYMENT_PROVIDER_BASE_URL`, 필요 시 `PAYMENT_PROVIDER_API_KEY`도 지정해야 합니다.
- 외부 호출 실패는 내부적으로 `PAYMENT_FAILED` 계열 예외로 매핑됩니다.

### 2.2 outbox 이벤트가 바로 publish되지 않는 경우

- 현재 outbox는 `READY -> RETRY_WAIT -> PUBLISHED / DEAD_LETTER` 흐름을 사용합니다.
- publish 실패 시 `retryCount`, `nextAttemptAt`, `failureCode`, `failureReason`이 기록됩니다.
- 최대 재시도 초과 시 `DEAD_LETTER`로 이동합니다.
- 운영 점검/수동 재처리 SQL은 `docs/sql/outbox-operations.sql`을 참고합니다.

### 2.3 settlement 실패와 notification 실패가 다르게 처리되는 이유

- settlement 실패는 현재 payment 취소 보상까지 연결되어 있습니다.
- notification 실패는 payment/settlement를 되돌리지 않고 재시도 또는 운영자 개입 대상으로 남깁니다.

### 2.4 notification 실패가 모두 같은 상태로 보이지 않는 경우

- 현재 notification 실패는 `RETRY_SCHEDULED`, `MANUAL_INTERVENTION_REQUIRED`, `IGNORED`로 나뉠 수 있습니다.
- `RETRY_SCHEDULED`는 일시적 실패, `MANUAL_INTERVENTION_REQUIRED`는 운영자 확인 필요, `IGNORED`는 주문 완료를 막지 않는 실패를 뜻합니다.
- 운영 점검/수동 처리 SQL은 `docs/sql/notification-admin-operations.sql`을 참고합니다.

### 2.5 admin 재처리가 기대한 대상을 다시 태우지 않는 경우

- notification admin 재처리는 전체 order orchestration 재실행이 아닙니다.
- `notification-events/{id}/retry` 또는 `ignore`는 실패한 notification 처리 단위만 복구하고, 성공 시 주문 상태를 `COMPLETED`로 복구합니다.
- `outbox-events/{id}/retry`는 `DEAD_LETTER` outbox event만 즉시 재발행 대상으로 허용합니다.

### 2.6 notification retry processor가 대상을 처리하지 않는 경우

`RETRY_SCHEDULED` 이벤트가 processor 대상이 되려면 아래 조건을 만족해야 합니다.

- `status = RETRY_SCHEDULED`
- `nextAttemptAt <= now`
- `retryCount < maxRetryCount`

`nextAttemptAt`이 미래이면 processor는 해당 이벤트를 건드리지 않습니다.  
반복 실패로 최대 재시도 기준에 도달하면 이벤트는 `MANUAL_INTERVENTION_REQUIRED`로 전환되고, 주문은 자동 완료 복구하지 않습니다.

현재 구현은 processor/application method를 유지하면서, property-gated `NotificationRetryScheduler`와 `POST /api/admin/notification-events/retry-due` endpoint가 동일 trigger port를 호출하는 구조입니다.  
현재 admin batch 응답은 `{ "status": "triggered" }`만 반환하며, processedCount 같은 운영 지표는 후속 확장 범위입니다.

## 3. Structure Notes

### 3.1 module boundary warning과 구조 문제를 구분하고 싶은 경우

현재 모듈 경계는 Spring Modulith 기준으로 검증합니다.

- 각 domain의 외부 공개 계약은 주로 `*.api` 패키지에 둡니다.
- 외부에서 접근 가능한 API 패키지는 `@NamedInterface("api")`로 명시합니다.
- orchestration/admin처럼 여러 domain을 조합하는 모듈은 `allowedDependencies`에서 `module::api` 형태로 의존 범위를 제한합니다.
- 구조 변경 후에는 `ApplicationModules.verify()` 기반 Modulith 테스트를 실행해 module cycle과 내부 패키지 침범 여부를 확인합니다.

IDE에서 다시 warning이 보이면 먼저 아래를 확인합니다.

- 다른 모듈의 `service`, `repository`, `entity`를 직접 참조하고 있지 않은가
- 필요한 public contract가 `*.api` 패키지에 있는가
- 해당 패키지가 `@NamedInterface("api")`로 공개되어 있는가
- `allowedDependencies`가 모듈 전체가 아니라 필요한 named interface만 허용하고 있는가
