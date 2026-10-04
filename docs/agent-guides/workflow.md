# Workflow

## Branch Workflow / 브랜치 운영

- 기준 브랜치는 `main`입니다.
- 기능 개발 브랜치는 최신 `main`에서 `codex/dev-<module>-<scope>` 형식으로 생성합니다.
- 기능 구현은 branch에서 검증하고 PR로 `main`에 반영합니다.
- `main` 직접 작업은 사용자가 명시적으로 승인한 repository governance/bootstrap 작업으로 제한합니다.
- 작업 시작 전 다음 명령으로 현재 상태를 확인합니다.

```bash
git status --short
git branch --show-current
git log --oneline -5
```

- 작업 시작 시 변경 범위에 맞는 feature branch인지 확인합니다.
- 관련 없는 사용자 변경이 있으면 보존하며, 같은 파일과 충돌할 때만 사용자에게 보고합니다.
- rebase와 force push는 사용자의 명시적 지시 없이 수행하지 않습니다.
- PR은 required verification이 통과하고 문서 claim이 구현 상태와 일치할 때만 merge 대상으로 봅니다.
- 상세 절차와 완료 조건은 [Module Delivery Playbook](../productization/module-delivery-playbook.md)을 따릅니다.

## Partition Working Model / 작업 분할

이 프로젝트의 후속 작업은 하나의 작업당 하나의 delivery unit으로 진행합니다.
서로 다른 성격의 변경을 하나의 branch, PR 또는 commit에 과도하게 섞지 않습니다.

- Partition 0 - Agent Rules: `AGENTS.md`와 agent guide 문서 정리
- Partition 1 - Repository Formatting: behavior change 없는 formatting 정리와 `.editorconfig`
- Partition 2 - Documentation Claim Audit: README/docs/test-report/runbook과 코드·테스트 정합성 점검
- Partition 3 - Admin Recovery Traceability: operator/reason context, audit log, recovery test 보강
- Partition 4 - OpenAPI / ApiDog Readiness: 구현된 API 기준 OpenAPI 정리

제품화 단계의 delivery unit과 우선순위는 [Productization Roadmap](../productization/README.md)에서 관리합니다.

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
