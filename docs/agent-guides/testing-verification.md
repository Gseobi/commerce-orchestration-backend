# Testing & Verification

## 기본 원칙

작업 후 변경 범위에 맞는 테스트는 필수입니다.
테스트를 실행하지 못했다면 실행하지 못한 이유를 작업 결과에 명확히 적습니다.
실행하지 않은 테스트를 PASS로 보고하지 않습니다.

## Required Commands by Change Type / 변경 유형별 명령

documentation-only 변경이며 executable behavior, configuration, Docker Compose, CI path, test path를 바꾸지 않은 경우 최소 검증은 다음과 같습니다.

```bash
git diff --check
./gradlew test
```

code/config/infrastructure 변경의 baseline은 다음과 같습니다.

```bash
git diff --check
./gradlew compileJava
./gradlew test
docker info
docker compose ps
./gradlew integrationTest --rerun-tasks
```

전체 회귀 검증이 필요한 경우:

```bash
./gradlew clean test --rerun-tasks
./gradlew clean integrationTest --rerun-tasks --stacktrace
```

CI와 동일한 조건을 확인해야 하는 경우 CI에서 사용하는 Gradle command와 service container 조건을 로컬에서도 최대한 맞춰 검증합니다.

## Docker / Testcontainers Rules

Docker 환경이 필요한 변경이라면 `integrationTest`를 수행하고 Docker 실행 가능성을 확인합니다.
Testcontainers 기반 테스트가 실패하면 Docker daemon, image pull, container network, PostgreSQL/Kafka startup log를 확인합니다.

Docker Compose services가 local verification에 필요하거나 code/config/infrastructure 변경으로 영향을 받을 수 있으면 다음을 확인합니다.

```bash
docker compose ps
docker compose logs postgres --tail=100
docker compose logs kafka --tail=100
```

PostgreSQL과 Kafka container는 unhealthy 상태를 무시하지 않습니다.
Docker 미가동, 권한 문제, image pull 실패 등으로 검증을 생략했다면 skip 이유를 명확히 보고합니다.

documentation-only 변경이며 executable behavior, configuration, Docker Compose, CI, test path를 바꾸지 않았다면 Docker/Kafka/PostgreSQL checks는 요구하지 않습니다.
단, repository rule 또는 사용자 요청이 full baseline을 요구하면 full baseline을 실행합니다.

## CI Test Preservation / CI 보존

CI Test는 포트폴리오 신뢰도의 일부입니다.

- `.github/workflows/*` 변경 시 CI 실행 조건과 test command를 훼손하지 않습니다.
- compile/test/integration verification이 기존에 존재한다면 제거하지 않습니다.
- service container, env, cache 설정 변경 시 이유를 남깁니다.
- CI 실패를 숨기거나 README/docs에서 성공한 것처럼 작성하지 않습니다.
- 테스트를 약화시켜 통과시키지 않습니다. 기존 테스트가 명백히 잘못된 경우에는 수정 이유를 남깁니다.

## Failure Reporting / 실패 보고 형식

테스트 결과 보고에는 다음을 포함합니다.

- 실행한 command
- pass/fail 여부
- 실패한 test class/method
- 실패 원인 추정
- Docker 미가동, PostgreSQL/Kafka unhealthy, network 제한 등으로 skip한 항목

권장 보고 예시:

```text
Verification
- command: git diff --check
  result: PASS
- command: ./gradlew test
  result: FAIL
  failed test: com.example.SomeTest.someCase
  suspected cause: ...

Skipped
- docker compose logs kafka --tail=100: documentation-only change라서 실행하지 않음
```
