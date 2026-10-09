# OIDC Security Design / 검증 보고서

> Historical snapshot: Resource Server 구현 전 설계 unit의 회귀 결과입니다.
> 후속 구현 완료·미완료 상태는 [Project Status](../project-status.md)를 참고합니다.

- Date: 2026-10-09 (Asia/Seoul)
- Base: `f1695e6`
- Branch: `codex/dev-security-oidc-design`
- Scope: OIDC identity/membership adapter 설계와 roadmap 연결. 실행 코드 변경 없음.

## Changes / 변경 파일

- `docs/productization/oidc-membership-security-design.md`: 인증/권한 분리, JWT 검증, identity mapping, demo 격리, dependency 승인 gate, 구현 순서와 테스트 계획.
- `docs/productization/README.md`: 완료된 membership 이후의 실제 next unit 정리.
- `docs/productization/merchant-membership-access.md`: 후속 설계 링크.
- 이 보고서: 현재 구현의 회귀 결과. 계획된 OIDC 기능의 검증 보고서가 아님.

## Local Verification / 정량 결과

```text
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks
BUILD SUCCESSFUL in 41s; compile 포함, 6 tasks executed
python3 scripts/test_report.py
PASS
git diff --check
PASS
```

| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | Suite time |
|---|---:|---:|---:|---:|---:|---:|---:|
| test | 15 | 68 | 68 | 0 | 0 | 0 | 10.958s |
| integrationTest | 7 | 17 | 17 | 0 | 0 | 0 | 14.545s |
| Total | 22 | 85 | 85 | 0 | 0 | 0 | 25.503s |

직전 unit과 test 수 차이는 0입니다. 문서 단위이므로 테스트가 추가되지 않았습니다.
suite time은 XML 합계이며 Gradle wall time과 다릅니다. 실행시간을 성능 개선으로 해석하지 않습니다.
Modulith 및 기존 PostgreSQL/Kafka Testcontainers integration tests를 포함한 현재 코드 회귀가 통과했습니다.
기존 unchecked/JVM class sharing 경고는 유지되며 이번 설계 단위에서 수정하지 않았습니다.

## Runtime / CI

시작 시 Compose 서비스는 내려가 있었습니다. 기존 `docker compose up -d`로 PostgreSQL/Kafka/Kafka UI를 시작했습니다.
`docker compose up -d`는 Kafka healthy 확인 후 Kafka UI를 시작하고 정상 종료했습니다.
PR/병합 후 CI 상태는 delivery 완료 보고와 해당 GitHub PR checks에서 확인합니다.
Docker Compose, CI workflow, Gradle dependency, migration, OpenAPI 파일은 변경하지 않았습니다.

## Claims and Remaining TODO

외부 OIDC/JWT 방향은 사용자 선택으로 확정했지만 Resource Server와 HTTP adapter는 아직 구현하지 않았습니다.
기존 demo authentication을 production에 안전하다고 주장하지 않습니다.
dependency의 정확한 transitive version/license/advisory 검토와 다운로드 승인,
issuer/audience/JWKS/token-use 계약 및 실제 제공자 선정은 다음 구현 전 gate입니다.
mobile token-expiry notification 설정은 사용자 요청대로 이번 작업에서 보류했습니다.

Suggested commit: `docs: design OIDC membership security adapter`
