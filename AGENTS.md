# AGENTS.md

## Purpose / 목적

이 파일은 Codex / AI Agent가 작업 전에 반드시 읽는 repository-level instruction entrypoint입니다.
상세 규칙은 `docs/agent-guides/` 문서를 참고합니다.

`commerce-orchestration-backend`는 주문 생성 이후의 payment, settlement, notification, outbox 흐름에서 Explicit State Transition, Orchestration Flow, Failure Branching, Compensation, Retry & Dead-letter, Admin Recovery, Idempotency, Observability, Test Verification, Spring Modulith Boundary를 보여주는 백엔드 포트폴리오 프로젝트입니다.

작업자는 기능 수를 늘리는 것보다, **구현된 범위를 정확하게 유지하고 문서·테스트·코드의 정합성을 지키는 것**을 우선합니다.

## Current Branch Workflow / 브랜치 운영

- Work branch: `development`
- `main`에는 직접 작업하지 않습니다.
- 하루 작업이 검증 완료된 뒤 사용자가 명시적으로 요청할 때만 `main`으로 merge합니다.
- Codex는 사용자의 명시적 지시 없이 merge, rebase, force push를 수행하지 않습니다.
- 각 partition은 가능한 한 독립된 commit으로 분리합니다.

## Non-negotiable Rules / 절대 규칙

1. Preserve Spring Modulith boundaries.
2. Do not break Docker Compose.
3. Do not add unnecessary Gradle dependencies.
4. Run required tests after work.
5. Do not weaken CI test rules.
6. Do not document unimplemented features as implemented.
7. Keep OpenAPI paths limited to implemented APIs.
8. Do not use high-cardinality metric tags.
9. Do not log secrets or tokens.
10. Do not mix formatting-only changes with behavior changes.
11. After code/config/infrastructure changes, verify Gradle build, Docker containers, PostgreSQL, Kafka, and CI-related test baseline.
12. Documentation-only changes may skip Docker/Kafka/PostgreSQL runtime verification if no executable behavior, configuration, Docker Compose, or CI path changed.

## Guide Index / 세부 규칙 문서

- [Workflow](docs/agent-guides/workflow.md)
- [Architecture Boundaries](docs/agent-guides/architecture-boundaries.md)
- [Documentation Claims](docs/agent-guides/documentation-claims.md)
- [Testing & Verification](docs/agent-guides/testing-verification.md)
- [Formatting & Readability](docs/agent-guides/formatting-readability.md)
- [OpenAPI & ApiDog](docs/agent-guides/openapi-apidog.md)

## Required Final Report / 작업 완료 보고 형식

- What changed
- Files changed
- Verification commands run
- Test result
- Docker / integrationTest status
- PostgreSQL / Kafka container health status when relevant
- CI-related test baseline status when relevant
- Remaining TODO
- Suggested commit message
