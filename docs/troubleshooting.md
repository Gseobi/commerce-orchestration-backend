# Troubleshooting

## 1. `401 Unauthorized`가 발생하는 경우

- `/api/**`는 JWT 인증이 필요합니다.
- 먼저 `POST /api/auth/token`으로 access token을 발급받아야 합니다.
- `Authorization: Bearer <token>` 헤더가 빠졌는지 확인합니다.

## 2. 로컬에서 PostgreSQL / Kafka 연결이 실패하는 경우

- 먼저 `docker compose up -d`로 `compose.yaml`의 인프라를 올립니다.
- 기본 기대 값은 PostgreSQL `localhost:5432`, Kafka `localhost:9092`, Kafka UI `localhost:8085`입니다.
- 포트를 바꿨다면 `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `POSTGRES_PORT`, `KAFKA_PORT`를 같이 맞춰야 합니다.

## 3. profile mismatch로 실행이 꼬이는 경우

- 로컬 실행은 `SPRING_PROFILES_ACTIVE=local`을 기준으로 맞추는 편이 안전합니다.
- 단위 테스트는 `test` 프로필, Testcontainers 통합 테스트는 `integration-test` 프로필을 사용합니다.
- IDE 기본 실행 설정에 남아 있는 profile/env var가 현재 문서와 다를 수 있으니 먼저 확인합니다.

## 4. Flyway migration 오류가 나는 경우

- 기본/로컬/통합 테스트 프로필은 Flyway 적용 후 JPA `ddl-auto=validate`를 사용합니다.
- 이미 존재하는 로컬 DB가 예전 `ddl-auto=update` 기반 스키마를 가지고 있다면 migration 적용 시점에 충돌할 수 있습니다.
- 이 경우 로컬 개발 DB를 비우고 migration부터 다시 적용하는 편이 안전합니다.
- 실제 스키마 기준은 `src/main/resources/db/migration`입니다.

## 5. 테스트는 통과하는데 로컬 실행에서만 인프라 오류가 나는 경우

- `./gradlew test`는 H2 + mock Kafka 중심이므로 외부 인프라 의존성이 낮습니다.
- 반면 애플리케이션 기본 실행과 `integrationTest`는 PostgreSQL / Kafka를 사용합니다.
- 따라서 `test` 성공이 곧 실인프라 연결 성공을 의미하지는 않습니다.

## 6. Payment provider가 예상과 다르게 동작하는 경우

- 기본값은 `app.payment.provider.mode=mock`입니다.
- 실제 외부 provider 골격을 사용하려면 `PAYMENT_PROVIDER_MODE=external`로 바꿔야 합니다.
- 외부 provider 모드에서는 `PAYMENT_PROVIDER_BASE_URL`, 필요 시 `PAYMENT_PROVIDER_API_KEY`도 지정해야 합니다.
- 외부 호출 실패는 내부적으로 `PAYMENT_FAILED` 계열 예외로 매핑됩니다.

## 7. outbox 이벤트가 바로 publish되지 않는 경우

- 현재 outbox는 `READY -> RETRY_WAIT -> PUBLISHED / DEAD_LETTER` 흐름을 사용합니다.
- publish 실패 시 `retryCount`, `nextAttemptAt`, `failureCode`, `failureReason`이 기록됩니다.
- 최대 재시도 초과 시 `DEAD_LETTER`로 이동합니다.
- 운영 점검/수동 재처리 SQL은 `docs/sql/outbox-operations.sql`을 참고합니다.

## 8. settlement 실패와 notification 실패가 다르게 처리되는 이유

- settlement 실패는 현재 payment 취소 보상까지 연결되어 있습니다.
- notification 실패는 payment/settlement를 되돌리지 않고 재시도 또는 운영자 개입 대상으로 남깁니다.

## 9. notification 실패가 모두 같은 상태로 보이지 않는 경우

- 현재 notification 실패는 `RETRY_SCHEDULED`, `MANUAL_INTERVENTION_REQUIRED`, `IGNORED`로 나뉠 수 있습니다.
- `RETRY_SCHEDULED`는 일시적 실패, `MANUAL_INTERVENTION_REQUIRED`는 운영자 확인 필요, `IGNORED`는 주문 완료를 막지 않는 실패를 뜻합니다.
- 운영 점검/수동 처리 SQL은 `docs/sql/notification-admin-operations.sql`을 참고합니다.

## 10. admin 재처리가 기대한 대상을 다시 태우지 않는 경우

- notification admin 재처리는 전체 order orchestration 재실행이 아닙니다.
- `notification-events/{id}/retry` 또는 `ignore`는 실패한 notification 처리 단위만 복구하고, 성공 시 주문 상태를 `COMPLETED`로 복구합니다.
- `outbox-events/{id}/retry`는 `DEAD_LETTER` outbox event만 즉시 재발행 대상으로 허용합니다.

## 11. `integrationTest`가 로컬에서 실패하는 경우

- Docker daemon이 켜져 있어야 합니다.
- `integrationTest`는 PostgreSQL / Kafka Testcontainers를 사용합니다.
- 로컬에서는 `./gradlew test`와 `./gradlew integrationTest`를 구분해서 보는 편이 맞습니다.

## 12. GitHub Actions `integration-test`가 실패하는 경우

- 현재 failure surface는 business logic보다 Docker/Testcontainers runner 상태일 가능성이 큽니다.
- 이번 정리에서 workflow에 `docker info`와 `--stacktrace`를 추가해 원인을 더 직접적으로 남기도록 조정했습니다.
- Actions에서 실패하면 먼저 Docker availability 단계와 Testcontainers 초기화 예외를 확인합니다.

## 13. module boundary warning과 구조 문제를 구분하고 싶은 경우

- 현재 modulith warning은 일부 보류 상태입니다.
- 하지만 cross-domain repository 직접 참조를 늘리지 않는 방향은 유지하고 있습니다.
- 현재 권장 방향은 `controller -> service -> repository`, `orchestration -> domain application service`입니다.
