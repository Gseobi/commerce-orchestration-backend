# Local Development / 로컬 개발 안내

초기 Spring Initializr 추천 목록 대신 실제 저장소 설정을 기준으로 안내합니다.
사용하지 않는 Batch/Thymeleaf/Authorization Server/Kafka Streams가 구현되어 있다는 의미는 아닙니다.

## 시작

- Java 21, Docker daemon 필요
- Compose: PostgreSQL `16-alpine`, Kafka `3.8.0`, Kafka UI `v0.7.2`
- 애플리케이션은 Compose service가 아니며 별도 실행

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local APP_SECURITY_MODE=demo ./gradlew bootRun
```

기본 `disabled`는 인증 우회가 아니라 업무 API 차단입니다.
OIDC 설정을 넣어도 아직 merchant HTTP API는 제공되지 않습니다.
`.env` 값은 `bootRun`에 자동 전달되지 않습니다.

## 검증

```bash
./gradlew compileJava
./gradlew test
./gradlew integrationTest --rerun-tasks
python3 scripts/test_report.py
docker compose ps
```

통합 테스트는 Compose와 별도의 Testcontainers 인프라를 사용합니다.
DB 초기화나 volume 삭제는 데이터 손실을 일으킬 수 있으므로 자동 해결책으로 실행하지 않습니다.

## 상세 안내

- [현재 상태](docs/project-status.md): 완료·미완료 경계와 인증 mode
- [README](README.md): 실행 설정과 demo 요청 예시
- [Troubleshooting](docs/troubleshooting.md): 인증·profile·Flyway·Docker 장애 점검
- [Test Report](docs/test-report.md): suite 종류와 검증 근거
- [Agent Guides](docs/agent-guides/README.md): 작업과 검증 규칙
