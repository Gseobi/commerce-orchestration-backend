## Delivery Unit / 작업 단위

- Module: `merchant`
- Scope: 내부 membership 권한 확인, Flyway V8, 테스트 정량 보고
- Base branch: `main`
- Related roadmap phase: `P0 - Product foundation`
- Branch: `codex/dev-merchant-membership-access`

## What Changed / 변경 사항

같은 actor라도 사업자별 membership에 따라 권한을 다르게 적용합니다.
`VIEWER`는 READ, `OPERATOR`는 READ/WRITE를 허용하며 revoke된 membership과 SUSPENDED 사업자는 접근을 거부합니다.

- 내부 `MerchantAccessApplication` 계약과 불변 `MerchantAccessContext` 추가
- 활성 membership을 `(merchantId, actorId)`로 조회하고 요청 권한과 사업자 상태 확인
- Flyway V8의 membership unique/FK/role constraint 추가
- PostgreSQL의 tenant별 권한, revoke, 정지 사업자, 잘못된 membership 연결 테스트
- XML 기반 `scripts/test_report.py` 추가: 결과 누락/실패/오류/skip이면 non-zero 반환
- 자동 delivery 및 한국어 중심 PR 설명 규칙 문서화

## Boundaries and Claims / 경계 및 구현 주장

- [x] Spring Modulith boundary is preserved.
- [x] 문서와 OpenAPI는 실제 구현 동작만 설명합니다.
- [x] Tenant/security/PII 영향을 검토했습니다.
- [x] Secret/token을 로그 또는 저장소에 추가하지 않았습니다.
- [x] Metric에 고카디널리티 business identifier를 추가하지 않았습니다.

actorId는 신뢰할 수 있는 identity provider의 subject를 전달해야 합니다.
현재 demo JWT는 username/role을 호출자가 지정하므로 이 계약과 연결하지 않습니다.
HTTP endpoint를 추가하지 않아 OpenAPI paths는 변경되지 않았습니다.

## Verification / 검증

- [x] `git diff --check`
- [x] `./gradlew compileJava` (clean 전체 실행에 포함)
- [x] `./gradlew test`
- [x] `docker compose ps`
- [x] `./gradlew integrationTest --rerun-tasks`
- [x] PostgreSQL/Kafka healthy
- [ ] GitHub Actions CI checks passed (push 후 확인)

```text
./gradlew --no-daemon --stacktrace --no-parallel clean test integrationTest --rerun-tasks
PASS: 36s
Unit: 68/68, 15 suites, 11.259s suite time
Integration: 17/17, 7 suites, 13.744s suite time
Failures / Errors / Skipped: 0 / 0 / 0

Baseline: unit 55, integration 13
Delta: unit +13, integration +4

python3 scripts/test_report.py
PASS

python3 scripts/test_report.py --results /private/tmp/commerce-missing-test-results
Expected exit 1: XML 누락 감지

./gradlew build
PASS

git diff --check
PASS

Docker Engine 29.4.1
PostgreSQL/Kafka: healthy
```

## Failure and Recovery Scenarios / 실패 및 복구

- Membership 없음, 권한 부족, 없는 사업자, 정지 사업자는 동일한 FORBIDDEN
- 잘못된 입력은 repository 조회 전에 거부
- 다른 사업자의 membership 권한 재사용 차단
- Membership revoke 후 다음 authorize 요청 거부
- 중복 membership 및 없는 merchant 연결은 DB constraint로 거부

## Remaining Risks / 후속 작업

- Production identity provider, HTTP 인증 adapter, membership 관리 API는 후속 범위
- 기존 주문/결제/알림/outbox에 대한 tenant ownership 전파는 후속 범위
- Context는 조회 snapshot이며 보호된 작업마다 다시 authorize 필요
- 진행 중인 transaction과 revoke 사이의 직렬화는 후속 설계 필요

검증 보고서: `docs/verification/merchant-membership-access-test-report.md`
