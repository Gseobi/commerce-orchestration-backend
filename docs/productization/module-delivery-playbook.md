# Module Delivery Playbook

## 1. Delivery Unit

하나의 delivery unit은 독립적으로 검토하고 되돌릴 수 있는 기능 또는 기반 변경입니다. 모듈 전체를 한 PR에 넣지 않고, schema/domain/application/API/adapter 중 의미 있는 vertical slice로 나눕니다.

각 unit은 다음 순서로 진행합니다.

1. 최신 `main`과 clean worktree 확인
2. `codex/dev-<module>-<scope>` branch 생성
3. 현재 claim과 module boundary 확인
4. 필요한 설계/테스트 시나리오 작성
5. 구현과 migration/OpenAPI/문서 동기화
6. 변경 범위에 맞는 verification 실행
7. commit, push, PR 생성
8. PR checks와 review 결과 확인
9. merge 후 `main` 기준 smoke verification
10. 단위 완료 보고

## 2. Branch and PR Rules

- base branch: `main`
- branch pattern: `codex/dev-<module>-<scope>`
- 한 branch에는 한 delivery unit만 포함합니다.
- formatting-only 변경과 behavior 변경을 섞지 않습니다.
- rebase와 force push는 명시적 사용자 지시 없이는 수행하지 않습니다.
- PR 제목은 conventional commit 형식을 권장합니다.
- 구현하지 않은 API를 OpenAPI나 README에 선반영하지 않습니다.

## 3. Definition of Ready

구현 시작 전에 다음이 명확해야 합니다.

- 사용자 가치와 제외 범위
- aggregate/data ownership과 module dependency 방향
- 성공, 실패, retry, duplicate 요청 시나리오
- tenant/security/PII 영향
- migration과 backward compatibility 영향
- 필요한 외부 dependency/provider와 승인 여부
- 테스트 수준과 완료 기준

불명확한 선택이 금전 처리, 보안, 데이터 삭제, public API 호환성을 바꾸면 구현 전에 사용자 결정을 받습니다.

## 4. Definition of Done

- Spring Modulith boundary가 유지됩니다.
- unit/integration test가 변경된 상태 전이와 실패 분기를 검증합니다.
- `git diff --check`, compile, unit test, integration test가 통과합니다.
- Docker Compose의 PostgreSQL과 Kafka 상태를 확인합니다.
- Flyway migration은 새 DB와 기존 schema upgrade 관점에서 검토합니다.
- 문서, OpenAPI, runbook은 실제 구현된 범위만 반영합니다.
- secrets/tokens/PII를 로그 또는 fixture에 노출하지 않습니다.
- metric tag에 merchant/order/payment 같은 고카디널리티 값을 넣지 않습니다.
- PR 설명과 완료 보고에 실행한 검증 결과와 잔여 위험을 기록합니다.

## 5. Verification Profiles

### Documentation/governance only

```bash
git diff --check
./gradlew test
```

사용자 요청 또는 baseline 재확인이 필요하면 integration profile을 실행합니다.

### Code/config/schema/infrastructure

```bash
git diff --check
./gradlew compileJava
./gradlew test
docker info
docker compose up -d
docker compose ps
./gradlew integrationTest --rerun-tasks
```

CI parity가 필요한 unit은 `.github/workflows/ci.yml`의 command를 동일하게 실행합니다.

## 6. Dependency and Plugin Approval

- JDK/Gradle 표준 기능과 현재 dependency로 해결 가능한지 먼저 확인합니다.
- 새 dependency, plugin, provider SDK는 유지보수 상태, license, 알려진 취약점, transitive dependency, runtime 권한을 검토합니다.
- 다운로드 또는 설치가 필요하면 목적, 후보, 버전, 출처, 권한, 대안, 제거 방법을 사용자에게 제시하고 승인 후 진행합니다.
- secret 접근, 외부 계정 연결, repository write 권한을 요구하는 plugin은 최소 권한으로 제한합니다.
- 검토 없이 편의를 위해 provider SDK를 core domain에 직접 추가하지 않습니다.

## 7. Unit Completion Report

각 delivery unit 완료 시 다음 형식으로 간략히 보고합니다.

```text
Work unit
- name / branch / PR

What changed
- 구현 또는 문서 변경 요약

Files changed
- 주요 파일

Verification
- command: result

Runtime status
- Docker / integrationTest
- PostgreSQL / Kafka health
- CI or PR checks

Remaining TODO / risks
- 다음 unit으로 넘기는 범위

Git
- commit / push / merge 상태
- suggested commit message
```

## 8. Merge Gate

다음 중 하나라도 해당하면 merge하지 않습니다.

- required test 실패 또는 미실행 사유가 해결되지 않음
- migration, OpenAPI, module boundary 불일치
- 실제 구현보다 강한 문서 claim
- secret/PII 노출 가능성
- merchant 간 데이터 격리 위반 가능성
- 외부 provider 오류의 불명확한 금전 상태
- unrelated change가 같은 PR에 포함됨
