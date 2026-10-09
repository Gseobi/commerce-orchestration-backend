# Documentation Current-State Audit / 문서 정합성 보고서

- Date: 2026-10-09 (Asia/Seoul)
- Base: `09427b5` / `main` (OIDC Resource Server 병합 후)
- Branch: `codex/dev-docs-current-state-audit`
- Scope: documentation-only. 코드/config/schema/Compose/CI/test/OpenAPI paths 변경 없음.

## What Changed / 정리한 내용

- `docs/project-status.md`: 제품화 P0의 완료·미완료 범위, mode별 HTTP 접근, 다음 unit, 문서 snapshot 규칙.
- README/Docs index/architecture/flows: merchant/membership/OIDC foundation 반영, 기존 다이어그램 범위 제한.
- HELP/troubleshooting/OpenAPI guide: 실제 Compose 이미지·실행 형태, explicit demo와 `.env` 전달 안내.
- SQL/design notes: V7/V8 목록, legacy 데이터 tenant ownership 미구현, 직접 UPDATE와 API 복구 차이.
- Roadmap/OIDC/membership 설계: Resource Server 완료, HTTP adapter 및 실제 IdP 연결 Planned 구분.
- Agent guides/검증/technical review/AI 활용 문서: 외부 JWKS rotation과 demo key rotation 구분.
- 과거 검증 보고서/PR 본문: 당시 base/count/time 보존, 최신 상태 링크 추가.
- `HELP.md`는 기존 ignore 대상인 로컬 생성 문서였습니다. 실제 프로젝트 안내로 재작성해 이번 unit에서 추적합니다.

## Audit Coverage / 점검 범위와 한계

repository Markdown 및 hidden PR template, OpenAPI, SQL 참고 자료, diagram 자산을 inventory로 확인했습니다.
인증·tenant·migration·실행·테스트·제품화 상태를 검색하고 영향 문서를 코드/테스트/config와 대조했습니다.
변경이 필요 없는 문서는 유지합니다. 전체 production 코드 보안 감사나 모든 문장의 형식적 증명은 아닙니다.

| 점검 대상 | 수량 | 검증 수준 |
|---|---:|---|
| Markdown (HELP와 신규 상태/보고서 포함) | 45 | inventory, claim 검색, 로컬 inline link 점검 |
| 로컬 inline links | 398 | missing target 0건, heading fragment 3건 포함 |
| 외부 links | 14 | inventory만 확인; HTTP/최신성 미검증 |
| SQL 참고 문서 | 3 | migration/수동 복구 경계 검토; SQL 미실행·미변경 |
| OpenAPI | 1 | 10 paths/10 operations와 9 Controller mappings + health 대조; 경로 미변경 |
| drawio 원본 | 11 | XML parse 성공 |
| PNG/PDF 자산 | 22 | 동일 basename의 11쌍 존재·non-empty; 시각적 재렌더링 QA 아님 |

로컬 inline link는 repository-root 경로와 상대 경로의 대상 존재를 확인합니다.
heading fragment는 GitHub식 slug 근사 검증이며 모든 Markdown parser/HTML anchor의 호환성 보장이 아닙니다.
reference-style link/외부 사이트/API 실제 호출/ApiDog 재import는 이번 자동 점검 대상이 아닙니다.
임시 표준 라이브러리 checker `/private/tmp/commerce-doc-audit.py`를 사용했으며 repository dependency/CI를 추가하지 않았습니다.
OpenAPI는 변경하지 않았고 general YAML/OpenAPI validator를 새로 설치·실행하지 않았습니다.

## Verification / 실제 실행 결과

```text
git diff --check: PASS
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks: PASS (57s)
  compileJava 포함, 6 tasks executed
python3 scripts/test_report.py: PASS
docker compose ps: postgres healthy / kafka healthy / kafka-ui Up
docker compose logs postgres --tail=8: recovery 완료, ready to accept connections
docker compose logs kafka --tail=5: Kafka Server started; 과거 controller disconnect 로그 확인
```

| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | XML suite time |
|---|---:|---:|---:|---:|---:|---:|---:|
| test | 19 | 122 | 122 | 0 | 0 | 0 | 22.750s |
| integrationTest | 8 | 21 | 21 | 0 | 0 | 0 | 16.997s |
| Total | 27 | 143 | 143 | 0 | 0 | 0 | 39.747s |

시간은 이 실행의 XML suite 합계이며 Gradle wall time(57s)과 다릅니다.
성능 개선·처리량·SLO/부하 테스트 수치로 해석하지 않습니다.
unchecked/JVM sharing 경고는 기존 baseline이며 이번 unit에서 변경하지 않았습니다.
Compose volume은 삭제/초기화하지 않았습니다. 통합 suite는 별도 Testcontainers DB/Kafka를 사용합니다.

## CI / Delivery Gate

PR head의 `build-and-test`, `integration-test` 성공과 변경되지 않은 head SHA를 확인한 뒤 merge합니다.
이 보고서의 로컬 PASS는 아직 실행하지 않은 CI를 PASS로 표현하는 근거가 아닙니다.
최종 CI 및 merge 결과는 GitHub PR/check 기록과 delivery 완료 보고에서 확인합니다.

## Remaining TODO / 다음 범위

- merchant membership HTTP adapter와 읽기 전용 store slice: 다음 별도 dev branch/PR.
- membership provisioning, legacy aggregate/event tenant ownership: 후속 P0 unit.
- 실제 IdP/PG/알림/택배사 계약, 재고와 배송 모듈: Roadmap의 Planned 범위.
- merchant/OIDC를 포함한 새로운 시각 다이어그램: 별도 자산 갱신·렌더링 검증 필요.
- 이 감사는 신규 dependency/plugin 다운로드나 secret 접근을 요구하지 않습니다.

Suggested commit: `docs: align repository documentation with current foundation`
