# Troubleshooting

## 1. API 호출이 `401 Unauthorized`로 실패하는 경우

- 현재 `/api/**`는 JWT 인증이 필요합니다.
- 먼저 `POST /api/auth/token`으로 access token을 발급받고, `Authorization: Bearer <token>` 헤더를 포함해야 합니다.
- `/actuator/health`만 공개이며, 다른 actuator endpoint는 `ROLE_ADMIN`이 필요합니다.

## 2. JWT는 동작하지만 운영 수준 인증처럼 보이지 않는 경우

- 현재 구조는 access token 단일 발급에 집중합니다.
- refresh token 저장, key rotation, 사용자 저장소 연동은 아직 구현되지 않았습니다.
- 이 동작은 의도된 현재 범위이며, 보안 설계가 완결되었다는 의미는 아닙니다.

## 3. 로컬에서 PostgreSQL / Kafka 연결이 실패하는 경우

- 기본 설정은 PostgreSQL `localhost:5432`, Kafka `localhost:9092`를 기대합니다.
- 로컬 인프라가 없다면 `docker compose up -d`로 `compose.yaml`의 서비스를 먼저 올려야 합니다.
- Kafka UI는 `localhost:8085`에서 확인할 수 있습니다.

## 4. 테스트는 통과하는데 로컬 실행에서만 인프라 오류가 나는 경우

- 테스트 프로필은 H2와 mock Kafka를 사용하므로 외부 인프라 의존성이 낮습니다.
- 반면 기본 실행은 PostgreSQL / Kafka 설정을 사용하므로, 테스트 성공이 곧 실인프라 연결 성공을 의미하지는 않습니다.

## 5. module boundary warning 비슷한 경고가 보이는 경우

- 주된 원인은 한 계층이나 한 domain service가 다른 domain의 `repository` 하위 패키지를 직접 참조할 때입니다.
- 현재 권장 방향은 `controller -> service -> repository`, `orchestration -> domain service`입니다.
- repository를 외부 공개하기보다, 필요한 조회/명령 메서드를 해당 domain service에 추가하는 편이 구조적으로 안전합니다.

## 6. outbox 이벤트가 `FAILED`에 머무는 경우

- 현재는 publish 실패 시 `FAILED` 상태와 failure reason 기록까지만 구현되어 있습니다.
- 자동 재발행 backoff, 최대 재시도 횟수, dead-letter 이동은 아직 후속 작업입니다.
- 즉, `FAILED`는 지금 당장 운영 자동복구가 된다는 의미가 아니라, 후속 정책을 붙일 수 있는 상태 표시입니다.

## 7. settlement 실패와 notification 실패가 다르게 처리되는 이유

- settlement 실패는 현재 payment 취소 보상까지 연결되어 있습니다.
- notification 실패는 실제 서비스 정책에 따라 재시도, 대체 채널 발송, 운영자 개입 등 선택지가 달라질 수 있어 아직 TODO로 남아 있습니다.

## 8. 스키마가 환경마다 달라질 수 있는 경우

- 현재는 `spring.jpa.hibernate.ddl-auto=update`로 개발 편의성을 우선합니다.
- 협업/배포 단계에서는 Flyway 또는 Liquibase로 전환해 스키마 변경 이력을 관리해야 합니다.
