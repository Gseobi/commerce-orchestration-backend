# Merchant Foundation Core Test Report

> Historical snapshot: 이 보고서의 count/time/미구현 항목은 해당 delivery 당시 기준입니다.
> 후속 membership/OIDC 반영 상태는 [Project Status](../project-status.md)를 참고합니다.

## Scope

- Date: 2026-10-05 Asia/Seoul
- Branch: `codex/dev-merchant-foundation-core`
- Change: merchant/store core model, Flyway V7, internal application contract, merchant-scoped store query
- Excluded: HTTP tenant context, production identity/RBAC, existing order/payment/notification/outbox tenant migration

## Quantitative Results

| Profile | Baseline | After change | Delta | Result |
|---|---:|---:|---:|---|
| Unit test suites | 12 | 14 | +2 | PASS |
| Unit tests | 44 | 55 | +11 | PASS |
| Unit failures/errors/skipped | 0/0/0 | 0/0/0 | 0/0/0 | PASS |
| Integration test suites | 5 | 6 | +1 | PASS |
| Integration tests | 11 | 13 | +2 | PASS |
| Integration failures/errors/skipped | 0/0/0 | 0/0/0 | 0/0/0 | PASS |

Test XML의 suite `time` 합계와 Gradle wall time은 다음과 같습니다.

| Run | Test time | Gradle wall time |
|---|---:|---:|
| Baseline full unit | 16.037s | 28s |
| Independent after full unit | 10.494s | 13s |
| Baseline full integration | 13.251s | 22s |
| Independent after full integration | 12.763s | 17s |
| Targeted merchant unit, 11 tests | 0.851s | 4s |
| Targeted merchant integration, 2 tests | 7.808s | 13s |
| Final CI-parity clean unit + integration | 10.695s + 13.100s | 34s |

실행시간은 Gradle daemon, JVM warm-up, Docker image/container cache 영향을 받으므로 성능 개선 지표로 해석하지 않습니다. 테스트 수와 pass/fail 결과를 회귀 기준으로 사용합니다.

## Commands

```bash
./gradlew test --rerun-tasks
./gradlew test --tests 'io.github.gseobi.commerce.orchestration.merchant.*' --rerun-tasks
./gradlew integrationTest --tests 'io.github.gseobi.commerce.orchestration.integration.MerchantFoundationIntegrationTest' --rerun-tasks
./gradlew integrationTest --rerun-tasks
./gradlew --no-daemon --stacktrace --no-parallel clean test integrationTest --rerun-tasks
docker compose ps
git diff --check
```

## Verified Behavior

- merchant code normalization and validation
- Java `ZoneId` timezone validation
- explicit merchant activation/suspension state
- safe default `MANUAL_REVIEW` recovery policy
- store operational setting snapshot from merchant
- merchant/store duplicate code pre-check
- DB unique constraint race mapping to business errors
- same store code allowed for different merchants
- cross-merchant store ID lookup returns `STORE_NOT_FOUND`
- merchant-scoped store list query
- Flyway V7 migration with PostgreSQL and JPA validation
- Spring Modulith boundary verification

## Runtime Status

- Docker Engine: available
- PostgreSQL Compose container: healthy
- Kafka Compose container: healthy
- Kafka UI Compose container: running
- Testcontainers PostgreSQL/Kafka: integration suite PASS

## Remaining Risk

- `MerchantApplication`은 아직 HTTP 인증 principal과 연결되지 않았습니다.
- 기존 business aggregate와 outbox event에는 아직 merchant ownership이 없습니다.
- production user store, membership, RBAC가 없으므로 merchant API를 외부에 공개하지 않습니다.
- shared-schema 격리는 application query와 DB constraint 기반이며 PostgreSQL RLS는 적용하지 않았습니다.
