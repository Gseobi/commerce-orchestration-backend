# Merchant Membership Access / 로컬 검증 보고서

> Historical snapshot: base/count/time과 미구현 범위는 해당 delivery 당시 기록입니다.
> 최신 OIDC 및 HTTP adapter 진행 상태는 [Project Status](../project-status.md)를 참고합니다.

- Date: 2026-10-05 (Asia/Seoul)
- Base: `43472f4` (merchant foundation PR 병합 확인)
- Branch: `codex/dev-merchant-membership-access`
- Scope: 내부 membership 및 접근 권한 계약, Flyway V8, 테스트 결과 집계 도구

## 정량 결과

기준선은 이전 delivery unit의 최종 실행 기록입니다. 이번에는 clean 전체 실행 후 XML을 집계했습니다.

| Profile | Baseline tests | Current tests | Delta | Suites | Failures / Errors / Skipped | Suite time |
|---|---:|---:|---:|---:|---|---:|
| test | 55 | 68 | +13 | 15 | 0 / 0 / 0 | 11.259s |
| integrationTest | 13 | 17 | +4 | 7 | 0 / 0 / 0 | 13.744s |
| Total | 68 | 85 | +17 | 22 | 0 / 0 / 0 | 25.003s |

Gradle clean unit + integration wall time: 36s.
suite time은 XML 합계이며 wall time과 다릅니다. daemon/이미지 cache 등의 영향을 받으므로 성능 개선으로 해석하지 않습니다.

## 실행 명령

```bash
./gradlew --no-daemon --stacktrace --no-parallel clean test integrationTest --rerun-tasks
python3 scripts/test_report.py
./gradlew build
git diff --check
docker compose ps
```

- compile, unit, integration, build, diff check: PASS
- PostgreSQL/Kafka Compose: healthy
- Testcontainers PostgreSQL/Kafka: PASS
- 기존 Spring Modulith architecture test: PASS
- 원격 CI: [PR #3](https://github.com/Gseobi/commerce-orchestration-backend/pull/3)의 최신 head 기준 checks에서 확인

## 검증된 동작

- OPERATOR는 READ/WRITE 허용, VIEWER는 READ만 허용
- 권한 context의 permission set은 불변
- 잘못된 merchantId/actorId/permission은 query 전에 FORBIDDEN
- membership이 없는 사업자 및 존재하지 않는 사업자는 FORBIDDEN
- 같은 actor의 사업자별 권한 독립성
- revoke 후 다음 authorize 거부
- SUSPENDED 사업자 접근 거부
- PostgreSQL의 membership unique/FK constraint 검증
- V8 migration 후 JPA validate 및 기존 흐름 회귀

## 잔여 범위

identity provider, HTTP adapter, membership 관리 API, 기존 business aggregate tenant 전파는 아직 구현하지 않았습니다.
현재 demo JWT의 username/role을 믿고 이 계약을 호출하면 identity 위조가 가능하므로 외부에 연결하지 않습니다.
context는 조회 snapshot이며 진행 중인 작업과 revoke 간 직렬화는 후속 설계가 필요합니다.

## 자동 Delivery 상태

로컬 구현·테스트·수치 집계·build는 완료했습니다.
GitHub HTTPS 인증을 macOS Keychain에 저장하고 branch push 및 PR #3 생성을 완료했습니다.
토큰 값은 repository와 보고서에 기록하지 않습니다.
원격 CI와 merge의 최종 상태는 PR의 최신 head 및 merge 기록을 기준으로 확인합니다.
