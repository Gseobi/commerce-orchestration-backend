# Workflow

## Branch Workflow / 브랜치 운영

- 기본 작업 브랜치는 `development`입니다.
- `main`에는 직접 작업하지 않습니다.
- 작업 시작 전 다음 명령으로 현재 상태를 확인합니다.

```bash
git status --short
git branch --show-current
git log --oneline -5
```

- 현재 브랜치가 `development`가 아니면 작업을 중단하고 사용자에게 보고합니다.
- 사용자의 명시적 지시 없이 branch switch, merge, rebase, force push를 수행하지 않습니다.
- 하루 작업을 `main`으로 merge하는 것은 사용자가 모든 작업과 verification 결과를 확인한 뒤 명시적으로 요청한 경우에만 수행합니다.

## Partition Working Model / 작업 분할

이 프로젝트의 후속 작업은 하나의 작업당 하나의 partition으로 진행합니다.
서로 다른 성격의 변경을 하나의 partition 또는 commit에 과도하게 섞지 않습니다.

- Partition 0 - Agent Rules: `AGENTS.md`와 agent guide 문서 정리
- Partition 1 - Repository Formatting: behavior change 없는 formatting 정리와 `.editorconfig`
- Partition 2 - Documentation Claim Audit: README/docs/test-report/runbook과 코드·테스트 정합성 점검
- Partition 3 - Admin Recovery Traceability: operator/reason context, audit log, recovery test 보강
- Partition 4 - OpenAPI / ApiDog Readiness: 구현된 API 기준 OpenAPI 정리

## Commit Scope / 커밋 범위

- 각 partition은 가능한 한 별도 commit으로 분리합니다.
- formatting-only 변경과 behavior 변경은 섞지 않습니다.
- dependency 추가, endpoint 변경, CI 변경처럼 영향 범위가 큰 변경은 이유와 검증 결과를 작업 요약에 남깁니다.
- 권장 commit message 예시는 다음과 같습니다.

```text
docs: add agent working rules
style: normalize repository formatting
docs: align implementation claims with code
feat: enhance admin recovery traceability
docs: add openapi baseline
```

## Verification Policy / 검증 정책

code/config/infrastructure 변경 후에는 Gradle build, Docker containers, PostgreSQL, Kafka, CI-related test baseline을 확인해야 합니다.
대표 baseline은 [Testing & Verification](testing-verification.md)을 따릅니다.

documentation-only 변경은 executable behavior, configuration, Docker Compose, CI path, test path를 바꾸지 않았을 때 Docker/Kafka/PostgreSQL runtime verification을 생략할 수 있습니다.
이 경우에도 `git diff --check`와 요청된 최소 test command는 실행하고 결과를 정직하게 보고합니다.

## 작업 후 확인

작업 후에는 다음을 확인합니다.

```bash
git status --short
git diff --stat
git diff --check
```

테스트를 실행하지 못했거나 실패했다면, 실패 command, 실패 test class/method, 원인 추정, Docker 미가동 같은 skip 이유를 작업 결과에 명확히 적습니다.
