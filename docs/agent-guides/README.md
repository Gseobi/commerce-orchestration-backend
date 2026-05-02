# Agent Guide Index

이 디렉터리는 Codex / AI Agent가 `commerce-orchestration-backend`에서 작업할 때 따라야 하는 세부 규칙을 보관합니다.
루트 `AGENTS.md`는 빠르게 읽는 entrypoint이고, 상세 판단 기준은 아래 문서를 기준으로 합니다.

## Guides

1. [Workflow](workflow.md)
   브랜치 운영, partition 기반 작업, commit scope, merge/rebase/force push 금지, verification 범위를 정의합니다.
2. [Architecture Boundaries](architecture-boundaries.md)
   Spring Modulith boundary, Outbox decoupling, DB conditional claim, Docker Compose, Gradle dependency 원칙을 정의합니다.
3. [Documentation Claims](documentation-claims.md)
   Implemented / Verified / Future Scope / Not Implemented 표현 기준과 claim audit 원칙을 정의합니다.
4. [Testing & Verification](testing-verification.md)
   변경 유형별 test command, Docker/Testcontainers, PostgreSQL/Kafka health check, CI baseline 보존 기준을 정의합니다.
5. [Formatting & Readability](formatting-readability.md)
   UTF-8/LF/final newline, formatting-only partition, `.editorconfig`, binary asset 보호 기준을 정의합니다.
6. [OpenAPI & ApiDog](openapi-apidog.md)
   OpenAPI path 범위, ApiDog import readiness, demo auth/admin auth 문서화 기준을 정의합니다.

## 원칙

- 문서는 Korean-first로 작성하되, Spring Modulith, Docker Compose, OpenAPI, ApiDog 같은 technical term은 English를 유지합니다.
- 구현되지 않은 기능은 Implemented처럼 쓰지 않습니다.
- 테스트를 실행하지 않았다면 PASS로 보고하지 않습니다.
- code/config/infrastructure 변경은 Gradle, Docker, PostgreSQL, Kafka, CI-related baseline 검증 필요성을 먼저 판단합니다.
