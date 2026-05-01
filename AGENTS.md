# AGENTS.md

> Repository-level working rules for Codex / AI Agent.  
> 이 문서는 `commerce-orchestration-backend` 프로젝트에서 Codex 또는 AI Agent가 작업할 때 반드시 따라야 하는 루트 가이드입니다.

---

## 0. Purpose / 목적

이 레포지토리는 단순 CRUD 기능 수를 보여주기 위한 프로젝트가 아니다.

`commerce-orchestration-backend`는 주문 생성 이후의 커머스 후속 흐름을 중심으로 다음 역량을 보여주기 위한 백엔드 포트폴리오 프로젝트다.

- Explicit State Transition / 명시적인 상태 전이
- Orchestration Flow / 주문 이후 결제·정산·알림·Outbox 흐름 조율
- Failure Branching / 실패 원인별 분기
- Compensation / 보상 처리
- Retry & Dead-letter / 재시도 및 실패 격리
- Admin Recovery / 운영자 기반 수동 복구
- Idempotency / 중복 요청 방어
- Observability / 운영 관측 가능성
- Test Verification / 테스트 기반 검증
- Spring Modulith Boundary / 모듈 경계 유지

작업자는 기능을 많이 추가하는 것보다, **구현된 범위를 정확하게 유지하고 문서·테스트·코드의 정합성을 지키는 것**을 우선한다.

---

## 1. Core Non-Negotiable Rules / 절대 훼손 금지 규칙

아래 규칙은 다른 모든 작업보다 우선한다.

### 1.1 Spring Modulith Boundary must be preserved

Spring Modulith 경계를 지키는 것이 이 프로젝트의 핵심 원칙이다.

- 다른 도메인 모듈의 Repository를 직접 주입하지 않는다.
- 모듈 간 협력은 가능한 한 `*.api` public interface를 통해 수행한다.
- `@NamedInterface("api")`, `allowedDependencies` 규칙을 임의로 깨지 않는다.
- Modulith verification test가 실패하는 방향의 변경은 허용하지 않는다.
- 경계 변경이 필요하다면 코드, 테스트, 문서를 함께 수정하고 이유를 명확히 남긴다.

### 1.2 Docker Compose must remain runnable

Docker Compose는 어떤 개발 환경에서도 실행 가능한 상태를 유지해야 한다.

- `compose.yaml`, `.env.example`, application profile 설정을 임의로 깨지 않는다.
- PostgreSQL, Kafka, Kafka UI, application service 간 연결성을 훼손하지 않는다.
- 로컬 실행 편의성을 낮추는 변경은 피한다.
- 환경 변수 이름을 변경하면 README, docs, `.env.example`, CI 설정을 함께 수정한다.
- Docker 기반 integration test가 필요한 경우 실행 가능성을 반드시 확인한다.

### 1.3 Gradle dependencies must stay minimal

Gradle 설정과 의존성은 추가할 수 있지만, 불필요한 dependency를 포함해서는 안 된다.

- 사용하지 않는 라이브러리를 추가하지 않는다.
- 대규모 프레임워크를 작은 문제 해결을 위해 추가하지 않는다.
- Spring Boot, Java, Testcontainers, Spring Modulith 등 주요 버전 업그레이드는 명시 요청이 없으면 수행하지 않는다.
- dependency를 추가했다면 왜 필요한지 작업 요약에 남긴다.
- build.gradle 변경 후 반드시 build/test를 수행한다.

### 1.4 Tests are mandatory after work

작업 이후 테스트는 필수다.

기본 검증 명령:

```bash
./gradlew compileJava
./gradlew test
```

Docker 환경이 필요한 변경이라면 다음도 수행한다.

```bash
./gradlew integrationTest
```

전체 회귀 검증이 필요한 경우:

```bash
./gradlew clean test --rerun-tasks
./gradlew clean integrationTest --rerun-tasks --stacktrace
```

테스트를 실행하지 못했다면, 실행하지 못한 이유를 작업 결과에 명확히 적는다. 실행하지 않은 테스트를 통과했다고 말하지 않는다.

### 1.5 CI Test rules must not be broken

CI Test는 반드시 유지되어야 한다.

- `.github/workflows/*` 변경 시 CI 실행 조건과 test command를 훼손하지 않는다.
- CI에서 compile/test/integration verification이 깨지지 않게 한다.
- CI 실패를 숨기거나 문서에서 통과한 것처럼 작성하지 않는다.
- CI에서 사용하는 환경 변수, service container, Gradle command 변경 시 README 또는 docs에 반영한다.
- 테스트를 약화시켜 통과시키는 방식은 금지한다. 단, 기존 테스트가 명백히 잘못된 경우에는 수정 이유를 남긴다.

---

## 2. Implementation Truthfulness / 구현 범위 정직성

문서에는 실제 구현된 것만 구현된 것처럼 작성한다.

다음 항목은 코드와 테스트로 확인되지 않았다면 implemented로 표현하지 않는다.

- Prometheus / Grafana dashboard
- Alert rules
- Kafka consumer-based state transition
- Stale `PROCESSING` automatic recovery job
- Refresh token / key rotation / real user store
- Real external payment provider production integration
- Payment provider callback flow
- WebClient timeout confirmation flow
- Notification channel-specific retry policy
- Dead-letter automation beyond implemented admin/manual APIs

구현되지 않은 기능은 다음 중 하나로 명확히 표시한다.

- Future Scope
- Planned
- Not Implemented
- Extension Point
- TODO

문서가 코드보다 앞서가면 안 된다.

---

## 3. Architecture Principles / 아키텍처 원칙

### 3.1 Explicit State Transition

상태 전이는 명시적으로 드러나야 한다.

- Order, Payment, Settlement, Notification, Outbox 상태를 숨은 side effect로 변경하지 않는다.
- 상태 변경은 서비스 책임과 repository update 흐름이 읽히도록 유지한다.
- 실패 상태와 복구 가능 상태를 구분한다.
- 재시도 가능한 실패와 수동 개입이 필요한 실패를 구분한다.

### 3.2 Orchestration Responsibility

`CommerceOrchestrationService`는 주문 이후 흐름의 coordinator다.

- 결제, 정산, 알림, Outbox, Audit 흐름을 조율할 수 있다.
- 단, 다른 도메인의 내부 repository를 직접 소유하거나 침범하지 않는다.
- 각 도메인의 세부 규칙은 해당 도메인 service가 담당한다.

### 3.3 Compensation First Thinking

정산 실패 또는 후속 처리 실패가 발생했을 때 전체 흐름을 무조건 재실행하지 않는다.

- Payment 승인 이후 Settlement 실패 시 Payment Cancel compensation을 고려한다.
- Notification 실패는 주문 자체 rollback과 분리될 수 있다.
- Outbox publish 실패는 business transaction 실패와 분리해 다룬다.

---

## 4. Outbox Rules / Outbox 작업 규칙

Outbox publishing은 Kafka 구현체와 직접 결합하지 않는다.

권장 의존 방향:

```text
OutboxPublisherService
  -> OutboxEventPublisher
    -> KafkaOutboxEventPublisher
      -> KafkaTemplate
```

`OutboxPublisherService` 책임:

- publish 대상 조회 또는 claim
- 상태 전이
- retry count 증가
- backoff 계산
- dead-letter 전환
- publish 결과 기록

`KafkaOutboxEventPublisher` 책임:

- KafkaTemplate 사용
- topic publish
- Kafka 예외 처리
- infra adapter 역할

Kafka consumer 기반 상태 전이를 추가하려면 별도 설계 문서, 테스트, README 반영이 필요하다. 암묵적으로 추가하지 않는다.

---

## 5. Concurrency and Claim Rules / 동시성 및 Claim 규칙

Scheduler, Admin API, 다중 application instance가 같은 event를 동시에 처리할 수 있다고 가정한다.

따라서 핵심 동시성 보호는 Java `synchronized`가 아니라 DB conditional update 기반 claim으로 처리한다.

예시:

```text
RETRY_SCHEDULED -> PROCESSING
READY / RETRY_WAIT -> PROCESSING
```

- update count가 `1`이면 claim 성공
- update count가 `0`이면 이미 다른 worker가 처리 중이므로 skip
- skip은 실패가 아니라 경쟁 상황에서의 정상 방어 결과로 본다.

이 규칙은 notification retry, outbox publish, admin recovery에서 유지되어야 한다.

---

## 6. Admin Recovery Rules / 운영자 복구 규칙

Admin Recovery는 전체 orchestration을 다시 실행하는 기능이 아니다.

목표는 실패한 하위 처리 단위를 안전하게 복구하는 것이다.

현재 또는 확장 가능한 대상:

- failed notification event retry
- notification event ignore
- outbox dead-letter retry
- due notification retry batch

Admin API를 변경하거나 추가할 때는 다음을 고려한다.

- `operatorId`, `reason`, `requestedBy` 같은 optional request context를 우선 고려한다.
- 기존 no-body API 호출은 가능하면 backward compatible하게 유지한다.
- admin action은 audit log에 남긴다.
- audit detail에는 action, target id, previous status, current status, result, operator, reason을 가능한 범위에서 남긴다.
- reason이 너무 길면 DB column을 고려해 안전하게 truncate하거나 migration을 추가한다.
- authorization header, token, secret, raw payload는 log/audit에 남기지 않는다.

---

## 7. Observability Rules / 관측 가능성 규칙

Observability는 운영 분석을 돕기 위한 것이며, 상태 테이블과 audit log를 대체하지 않는다.

### Metrics

Metric tag에는 high-cardinality 값을 넣지 않는다.

금지 예시:

- orderId
- eventId
- paymentRequestId
- providerTransactionId
- raw exception message
- token
- authorization header

허용 예시:

- result
- status
- action
- failureType
- policy
- channel

### Structured Logs

Structured log는 장애 분석에 필요한 안전한 값을 남긴다.

권장 event name 예시:

- `notification_retry_started`
- `notification_retry_succeeded`
- `notification_retry_failed`
- `outbox_publish_started`
- `outbox_publish_dead_lettered`
- `admin_recovery_requested`
- `admin_recovery_succeeded`
- `admin_recovery_failed`

secret, token, API key, authorization header는 절대 로그에 남기지 않는다.

---

## 8. API and OpenAPI Rules / API 및 OpenAPI 규칙

이 프로젝트는 후속으로 ApiDog 기반 API 문서화 및 테스트를 고려한다.

API를 추가하거나 변경할 때:

- `docs/openapi/openapi.yaml`을 함께 추가 또는 갱신한다.
- OpenAPI는 실제 구현된 endpoint만 포함한다.
- 아직 구현되지 않은 future API는 OpenAPI paths에 넣지 않는다.
- 인증이 필요한 API는 security requirement를 명시한다.
- demo token 발급 API는 production auth가 아니라 demo-only임을 명시한다.
- `ApiResponse<T>` response envelope을 실제 응답 구조에 맞게 반영한다.
- request/response example에는 실제 secret, token, private value를 넣지 않는다.

---

## 9. Documentation Rules / 문서 작성 규칙

코드 변경이 동작이나 설계 의미를 바꾸면 관련 문서도 같은 작업에서 갱신한다.

중요 문서:

- `README.md`
- `docs/README.md`
- `docs/architecture/README.md`
- `docs/flows/README.md`
- `docs/design-notes.md`
- `docs/implementation-review-notes.md`
- `docs/test-report.md`
- `docs/troubleshooting.md`
- `docs/runbooks/admin-recovery-runbook.md`
- `docs/verification-matrix.md`
- `docs/openapi/openapi.yaml`
- `docs/sql/*`
- `docs/diagrams/*`

문서는 다음을 구분해야 한다.

- Implemented / 구현됨
- Verified / 테스트로 검증됨
- Future Scope / 후속 범위
- Known Limitation / 알려진 한계
- Design Decision / 설계 판단

문서가 길어지더라도 구현되지 않은 내용을 구현된 것처럼 쓰지 않는다.

---

## 10. Formatting and Repository Readability / 포맷 및 가독성 규칙

이 레포지토리는 포트폴리오 프로젝트이므로 코드와 문서가 리뷰 가능한 상태여야 한다.

- Java, Gradle, YAML, SQL, Markdown 파일을 한 줄로 압축된 상태로 두지 않는다.
- UTF-8을 사용한다.
- LF line ending을 사용한다.
- 파일 끝에는 newline을 둔다.
- Java indentation은 4 spaces를 기본으로 한다.
- YAML indentation은 2 spaces를 기본으로 한다.
- SQL migration은 statement와 logical block이 읽히도록 줄바꿈한다.
- README와 docs의 heading/table/list 구조를 훼손하지 않는다.
- PNG, PDF, drawio 같은 binary/diagram asset은 명시 요청 없이는 수정하지 않는다.

포맷 변경과 기능 변경은 가능하면 별도 commit으로 분리한다.

---

## 11. Security Rules / 보안 규칙

- secret, token, API key, password를 코드나 문서에 하드코딩하지 않는다.
- `.env.example`에는 placeholder만 둔다.
- demo token 발급 기능은 production auth로 오해되지 않게 문서화한다.
- admin endpoint는 admin role 또는 security rule을 우회하지 않는다.
- test 편의를 위해 production security를 약화하지 않는다.

---

## 12. Testing Rules / 테스트 규칙

작업이 끝나면 변경 범위에 맞는 테스트를 반드시 실행한다.

최소 기준:

```bash
./gradlew compileJava
./gradlew test
```

통합 테스트 대상 변경 시:

```bash
./gradlew integrationTest
```

전체 검증 필요 시:

```bash
./gradlew clean test --rerun-tasks
./gradlew clean integrationTest --rerun-tasks --stacktrace
```

추가 권장 검증:

```bash
git diff --check
```

테스트 결과 보고 시 반드시 포함한다.

- 실행한 command
- pass/fail 여부
- 실패한 test class/method
- 실패 원인 추정
- Docker 미가동 등으로 skip한 항목

---

## 13. CI Rules / CI 규칙

CI는 포트폴리오 신뢰도의 일부다.

- CI workflow를 단순화하더라도 검증 의미를 약화하지 않는다.
- compile/test/integration test가 기존에 존재한다면 제거하지 않는다.
- CI에서 사용하는 Gradle command를 변경하면 로컬에서도 동일하게 검증한다.
- service container, env, cache 설정 변경 시 이유를 남긴다.
- CI 실패를 README나 docs에서 성공한 것처럼 작성하지 않는다.

---

## 14. Git Workflow Rules / Git 작업 규칙

작업 전:

```bash
git status --short
```

작업 후:

```bash
git status --short
git diff --stat
git diff --check
```

권장 commit 분리:

- `docs: add agent working rules`
- `style: normalize repository formatting`
- `docs: align implementation claims with code`
- `feat: enhance admin recovery traceability`
- `docs: add openapi baseline`

서로 다른 성격의 변경을 하나의 commit에 과도하게 섞지 않는다.

---

## 15. Dependency Rules / 의존성 규칙

현재 프로젝트의 기술 기반은 유지한다.

주요 기준:

- Java 21
- Spring Boot
- Spring MVC
- WebClient
- Spring Security
- JPA
- Flyway
- PostgreSQL
- Kafka
- Spring Modulith
- Testcontainers
- Micrometer / Actuator

의존성 추가 전 확인:

- 현재 코드로 해결 가능한가?
- test scope만 필요한가, runtime도 필요한가?
- 기존 Spring Boot dependency management로 관리 가능한가?
- CI와 Docker 환경에 영향이 있는가?
- 문서화가 필요한가?

불필요한 dependency는 추가하지 않는다.

---

## 16. Partition Working Model / 작업 분할 모델

이 프로젝트의 후속 작업은 하나의 작업당 하나의 Partition으로 진행한다.

### Partition 0 - Agent Rules

목표:

- `AGENTS.md` 생성
- Codex / AI Agent 작업 규칙 고정
- Spring Modulith, Docker Compose, Gradle dependency, test, CI 규칙 명문화

검증:

```bash
git diff --check
```

권장 commit:

```bash
git add AGENTS.md
git commit -m "docs: add agent working rules"
```

### Partition 1 - Repository Formatting

목표:

- Java, Gradle, YAML, SQL, Markdown 포맷 정상화
- `.editorconfig` 추가
- 기능 변경 없이 리뷰 가능한 형태로 정리

필수:

- behavior change 금지
- binary asset 수정 금지
- formatting-only commit 권장

### Partition 2 - Documentation Claim Audit

목표:

- README/docs/test-report/runbook 내용과 실제 코드/테스트 정합성 점검
- 구현되지 않은 기능을 implemented로 표현하지 않도록 수정
- `docs/verification-matrix.md` 추가 또는 갱신

필수:

- 문서가 코드보다 앞서가지 않게 한다.
- 테스트로 검증되지 않은 항목은 Verified로 쓰지 않는다.

### Partition 3 - Admin Recovery Traceability

목표:

- Admin recovery에 operator/reason context 추가
- audit log 강화
- retry/ignore/dead-letter 복구 흐름 테스트 보강
- OpenAPI baseline 또는 admin API 문서 갱신

필수:

- 기존 API backward compatibility 고려
- admin security rule 유지
- high-cardinality metric tag 금지

### Partition 4 - OpenAPI / ApiDog Readiness

목표:

- `docs/openapi/openapi.yaml` 정리
- ApiDog import 가능한 수준의 API 명세 구성
- 구현된 API만 문서화

필수:

- future API를 OpenAPI paths에 넣지 않는다.
- demo auth와 production auth를 혼동시키지 않는다.

---

## 17. Required Final Report Format / 작업 완료 보고 형식

Codex 또는 AI Agent는 작업 완료 시 아래 형식으로 보고한다.

```text
## Summary
- What changed

## Files Changed
- file path and purpose

## Verification
- command: result
- command: result

## Tests
- Passed:
- Failed:
- Skipped and why:

## Documentation
- Updated:
- Not updated and why:

## Remaining TODO
- item

## Suggested Commit Message
- commit message
```

테스트를 실행하지 않았거나 실패했는데 성공한 것처럼 보고하지 않는다.

---

## 18. Final Reminder / 최종 원칙

이 프로젝트에서 가장 중요한 것은 기능 수가 아니라 설계 의도와 검증 가능성이다.

작업자는 항상 다음 질문을 기준으로 판단한다.

1. 이 변경이 Spring Modulith 경계를 지키는가?
2. Docker Compose 실행 가능성을 훼손하지 않는가?
3. Gradle dependency가 꼭 필요한가?
4. 테스트를 실행했고 결과를 정직하게 남겼는가?
5. CI Test 규칙을 깨지 않는가?
6. 문서가 실제 구현보다 앞서가지 않는가?
7. 면접에서 설명 가능한 설계 판단인가?
