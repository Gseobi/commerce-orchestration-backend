# HB2 Tenant Ownership Migration / 정량 보고서

- Date: 2026-10-13 (Asia/Seoul), base `e3e4425`
- Branch: `codex/dev-tenancy-ownership-migration`
- Scope: V9 expand schema + 기존 legacy ORM/worker 차단, 잔여 설계/제품 readiness 점검.
- tenant 거래 writer/HTTP API/전체 owner 전파/실제 IdP 연결은 미구현.

## Changes / 변경

7개 거래 table ownership mode/owner FK·CHECK, parent scope/owner FK, immutable owner trigger.
7개 legacy entity의 공통 read-only mapping 및 정적 restriction, 2개 bulk claim 명시 LEGACY 조건.
V8 이력 보존·orphan/null audit 유지 및 신규 잘못된 참조 거부를 검증합니다.
[확정 설계](../productization/tenant-ownership-migration.md)와
[잔여 설계/트레이드오프 점검](../productization/product-readiness-review.md)에 rollout·출시 blocker를 정리했습니다.
V1~V8, OpenAPI paths, provider/Gradle 의존성, CI/Compose 설정은 변경하지 않습니다.

## Verification / 실행 결과

```text
git diff --check: PASS
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks: PASS (53s, 6 tasks executed)
python3 scripts/test_report.py: PASS
./gradlew --no-daemon compileJava build: PASS (6s, bootJar 생성; test UP-TO-DATE)
```

| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | XML suite time |
|---|---:|---:|---:|---:|---:|---:|---:|
| test | 23 | 147 | 147 | 0 | 0 | 0 | 16.837s |
| integrationTest | 9 | 59 | 59 | 0 | 0 | 0 | 24.606s |
| Total | 32 | 206 | 206 | 0 | 0 | 0 | 41.443s |

이전 183개 회귀에 PostgreSQL integration 23개 추가. 기존 test 삭제/CI 완화 없음.
첫 실행은 신규 TransactionTemplate/AssertJ generic 타입 추론으로 compileTestJava 실패(12s)했고,
Integer 결과 변수로 수정 후 위 전체 실행이 통과했습니다. 실패 실행을 PASS로 보고하지 않습니다.
최초 전체 통과는 75s였으며 Legacy→TENANT immutable trigger assertion 추가 후 최종 53s 실행 수치를 사용합니다.
Markdown 52개/local inline link 428건: missing file 0건. heading/외부 링크/일반 OpenAPI validator는 이번 점검 제외.
XML 합계는 wall time/부하/SLO 수치가 아니며 H2 unit은 PostgreSQL constraint/trigger를 대신하지 않습니다.

## Cases / 신규 검증 23개

- V8 무작위 테스트 schema의 7개 table 전체 JSON 내용/row 수를 V9 전후 비교, 보존 확인 1개.
  legacy orphan notification/non-order audit도 유지. scope FK 6개 NOT VALID 상태와 신규 orphan 거부 확인.
- 7개 table의 TENANT null/없는 owner, LEGACY owner 지정, unknown mode 거부 7개.
- 6개 child의 cross-merchant/cross-scope 참조 거부, 정상 TENANT child insert 허용 6개.
- 7개 repository findById/findAll/count의 TENANT 제외, owner/scope 변경 거부, LEGACY 조회 허용 7개.
- TENANT audit order 필수 및 legacy null audit 허용 1개.
- outbox/notification candidate 및 bulk claim의 TENANT 제외·status 보존 1개.

테스트에서만 trusted JDBC로 TENANT fixture를 생성합니다. production tenant writer 지원을 의미하지 않습니다.
임시 upgrade schema만 DROP하며 Compose/공유/운영 DB의 데이터·volume을 초기화하지 않습니다.

## Runtime / CI / Remaining

Docker engine 가동과 별도 Testcontainers PostgreSQL/Kafka/Flyway V1~V9 + JPA validate 전체 통과.
Compose PostgreSQL/Kafka healthy, Kafka UI Up 확인. 기존 volume만 재사용해 기동했습니다.
PR/main CI 최종 결과는 완료 보고 및 PR 기록에서 확인합니다.
현재 Compose DB에 V9를 운영 배포하거나 기존 이력을 cleanup/강제 VALIDATE한 것은 아닙니다.

NOT VALID historical scope FK 정합성, 실제 데이터 규모의 lock/IO/upgrade 리허설은 남습니다.
정적 legacy guard는 RLS/tenant 동적 권한이 아니며 HB3에서 owner-aware 전체 경로로 대체해야 합니다.
default LEGACY를 신규 SaaS 누락 방지로 주장하지 않습니다. tenant write 공개 전 old binary 격리와 HB3 검증 필수입니다.
실제 IdP/PG/알림/배송 계약, 결제 unknown/실패 보상/lease recovery, 가격·재고·제품 운영 조건은
Product Readiness Review의 후속 exit evidence입니다.

Suggested commit: `feat: add staged commerce ownership migration and legacy guards`
