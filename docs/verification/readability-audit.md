# Repository Readability Audit

## Purpose / 목적

이 문서는 repository의 Java / Gradle / YAML / SQL / Markdown 파일이 리뷰 가능한 형태인지 점검하고,
후속 formatting recovery 작업의 범위를 분리하기 위해 작성합니다.

이번 Partition 1은 audit and recovery plan 작성이 목적입니다.
실제 formatting recovery는 수행하지 않습니다.

## Audit Context / 점검 맥락

- Branch: `development`
- Commit: `7baaefb`
- Scope: `git ls-files`로 조회한 tracked text files 186개
- Script: 요청된 helper script를 사용했습니다. 로컬에 `python` 명령이 없어 `python` 실행은 실패했고, 동일 script를 `python3`로 재실행했습니다.
- Documentation-only 여부: 이 partition은 문서 추가/갱신만 수행합니다.

대상 file type:

```text
*.java
*.gradle
*.yml
*.yaml
*.sql
*.md
.editorconfig
.env.example
Dockerfile
compose.yaml
docker-compose.yml
```

## Summary / 요약

helper script 기준으로 복구 후보는 4개입니다.

- `src/main/resources/application.yaml`: final newline 누락
- `docs/diagrams/README.md`: 긴 Markdown table line 다수
- `docs/verification-matrix.md`: 긴 Markdown table line 다수
- `docs/verification/claim-audit.md`: 긴 Markdown table line 다수

수동 spot check 대상 중 `.editorconfig`, `README.md`, `PaymentService.java`, `ExternalPaymentProviderClient.java`는 현재 partition에서 즉시 복구가 필요한 single-line/minified 문제는 확인되지 않았습니다.
다만 `README.md`는 외부 URL과 다이어그램 표로 인해 일부 긴 line이 있으므로 Markdown recovery partition에서 필요 시 함께 재검토할 수 있습니다.

## Script Evidence / 스크립트 근거

`python` 명령은 현재 환경에 없어 실패했습니다.

```text
zsh:1: command not found: python
```

동일 helper script를 `python3`로 실행한 결과:

```text
('src/main/resources/application.yaml', 59, 1732, 78, 'missing-final-newline', 'long_lines=0')
('docs/diagrams/README.md', 64, 5290, 558, 'many-long-lines', 'long_lines=11')
('docs/verification-matrix.md', 43, 10062, 502, 'many-long-lines', 'long_lines=25')
('docs/verification/claim-audit.md', 37, 7311, 403, 'many-long-lines', 'long_lines=20')
```

## Files Requiring Recovery / 복구 대상 파일

| File | Type | Issue | Recommended Partition | Notes |
|---|---|---|---|---|
| `src/main/resources/application.yaml` | YAML config | `missing-final-newline` | Partition 3 | Config file이므로 final newline만 복구하더라도 code/config baseline 검증 필요성을 판단해야 합니다. |
| `docs/diagrams/README.md` | Markdown docs | `many-long-lines` | Partition 4 | Markdown table line이 길어 raw review가 어렵습니다. Binary diagram asset은 수정하지 않습니다. |
| `docs/verification-matrix.md` | Markdown docs | `many-long-lines` | Partition 4 | 구현-검증 매핑 표가 길어 line break recovery 대상입니다. Claim 의미는 바꾸지 않습니다. |
| `docs/verification/claim-audit.md` | Markdown docs | `many-long-lines` | Partition 4 | Claim audit 표가 길어 line break recovery 대상입니다. Status와 evidence 의미는 바꾸지 않습니다. |

## Files Checked but Acceptable / 점검했으나 정상인 파일

| File | Notes |
|---|---|
| `.editorconfig` | UTF-8/LF/final newline, Java 4 spaces, YAML 2 spaces, SQL 4 spaces 기준이 명확합니다. |
| `README.md` | 수동 확인 기준 single-line/minified 문제는 없습니다. 긴 Velog URL과 일부 table line은 Partition 4에서 필요 시 재검토합니다. |
| `src/main/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentService.java` | Java formatting과 indentation이 리뷰 가능한 상태입니다. |
| `src/main/java/io/github/gseobi/commerce/orchestration/payment/client/ExternalPaymentProviderClient.java` | Java formatting과 indentation이 리뷰 가능한 상태입니다. |
| `AGENTS.md` | Partition 0 이후 concise entrypoint로 유지됩니다. |
| `docs/agent-guides/*` | Partition 0에서 생성된 guide 문서는 현재 script 기준 복구 후보에 포함되지 않았습니다. |
| `build.gradle`, `settings.gradle` | helper script 기준 복구 후보에 포함되지 않았습니다. |
| `compose.yaml`, `.env.example`, `.github/workflows/ci.yml` | helper script 기준 복구 후보에 포함되지 않았습니다. |
| `docs/sql/*.sql`, `src/main/resources/db/migration/*.sql` | helper script 기준 복구 후보에 포함되지 않았습니다. |

## Files Intentionally Excluded / 제외 대상

| Path / Pattern | Reason |
|---|---|
| Untracked files | `git ls-files` 기반 audit이므로 build artifact와 local-only file은 제외합니다. |
| `build/` | generated output이며 tracked audit 대상이 아닙니다. |
| `.gradle/` | local Gradle cache이며 tracked audit 대상이 아닙니다. |
| `docs/diagrams/png/*` | binary image asset입니다. 이번 partition에서 수정하지 않습니다. |
| `docs/diagrams/pdf/*` | binary document asset입니다. 이번 partition에서 수정하지 않습니다. |
| `docs/diagrams/source/*.drawio` | diagram source asset입니다. 명시 요청 없이는 수정하지 않습니다. |

## Recovery Order / 복구 순서

1. Partition 2 - `.editorconfig` normalization
   현재 `.editorconfig`는 기본 기준을 갖추고 있으나, 필요한 경우 Markdown wrapping 정책과 config file final newline 정책을 명확히 합니다.
2. Partition 3 - Java / Gradle / YAML / SQL / config formatting recovery
   `src/main/resources/application.yaml` final newline을 복구합니다. Config path 변경이므로 `git diff --check`, `./gradlew compileJava`, `./gradlew test`, Docker/PostgreSQL/Kafka/integration baseline 필요성을 판단합니다.
3. Partition 4 - Markdown documentation line break recovery
   `docs/diagrams/README.md`, `docs/verification-matrix.md`, `docs/verification/claim-audit.md`의 긴 table line을 의미 변경 없이 분할하거나 표 구조를 리뷰 가능한 형태로 정리합니다.
4. Partition 5 - Final readability baseline verification
   helper script를 재실행해 `single-line-candidate`, `many-long-lines`, `missing-final-newline` 후보가 남았는지 확인합니다.

## Verification Commands / 검증 명령

Partition 1에서 실행한 audit command:

```bash
git branch --show-current
git status --short
git log --oneline -5
git ls-files '*.java' '*.gradle' '*.yml' '*.yaml' '*.sql' '*.md' '.editorconfig' '.env.example' 'Dockerfile' 'compose.yaml' 'docker-compose.yml'
python - <<'PY'
# requested helper script
PY
python3 - <<'PY'
# same helper script, used because python was unavailable
PY
sed -n '1,220p' .editorconfig
sed -n '1,220p' README.md
sed -n '1,260p' src/main/java/io/github/gseobi/commerce/orchestration/payment/service/PaymentService.java
sed -n '1,260p' src/main/java/io/github/gseobi/commerce/orchestration/payment/client/ExternalPaymentProviderClient.java
sed -n '1,220p' docs/verification-matrix.md
sed -n '1,220p' docs/verification/claim-audit.md
```

Partition 1 완료 verification:

- `git diff --check`: PASS
- `./gradlew test`: PASS (`BUILD SUCCESSFUL`, tasks up-to-date)

Docker/PostgreSQL/Kafka/integrationTest는 이번 변경이 documentation-only이고 executable behavior, configuration, Docker Compose, CI path, test path를 바꾸지 않으므로 실행 대상에서 제외합니다.

## Remaining TODO / 남은 TODO

- Partition 2에서 `.editorconfig` normalization 필요 여부를 결정합니다.
- Partition 3에서 `src/main/resources/application.yaml` final newline을 복구합니다.
- Partition 4에서 긴 Markdown table line을 의미 변경 없이 정리합니다.
- Partition 5에서 helper script와 `git diff --check`로 final readability baseline을 확인합니다.
