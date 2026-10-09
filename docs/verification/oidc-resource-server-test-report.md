# OIDC Resource Server / 검증 보고서

- Date: 2026-10-09 (Asia/Seoul)
- Base: `95d370b`
- Branch: `codex/dev-security-oidc-resource-server`
- Scope: Resource Server, mode 분리, canonical identity. HTTP membership 연결은 미포함.

## What Changed / 변경 파일

- `config/SecurityModeProperties`, `config/OidcProperties`: mode와 bounded OIDC 설정
- `security/SecurityConfig`, `OidcSecurityConfig`, `StrictClaimShapeJwtDecoder`, `OidcTokenValidator`: filter chain/decoder/claim 검증
- `security/ExpiringJwksCache`, `TrustedActorResolver`, `RestAccessDeniedHandler`: TTL, identity, 고정 오류 응답
- `auth/AuthController`, demo JWT provider/filter: demo mode에서만 bean 생성
- `build.gradle`: 승인된 starter 추가 및 Security 7.0.7 patch
- `application.yaml`, test profiles, `.env.example`: default disabled와 명시 demo opt-in
- security 테스트 5개와 JWKS fixture, 기존 demo MVC test profile 명시
- README/OpenAPI/roadmap/runbook/claim 문서: mode별 구현 범위와 미구현 API 구분

정확한 전체 파일 목록은 이 branch의 Git diff에서 확인합니다. schema/migration/Compose/CI 변경은 없습니다.

## Final Local Results / 정량 결과

```text
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks
BUILD SUCCESSFUL in 52s; compile 포함, 6 tasks executed
앞선 clean 전체 회귀도 45s에 통과했으며, 최종 오류 메시지 보강 후 전체를 다시 실행함
python3 scripts/test_report.py
PASS
git diff --check
PASS
./gradlew build
PASS (bootJar 포함)
```

| Profile | Baseline | Current | Delta | Suites | Passed | Failures / Errors / Skipped | Suite time |
|---|---:|---:|---:|---:|---:|---|---:|
| test | 68 | 122 | +54 | 19 | 122 | 0 / 0 / 0 | 19.764s |
| integrationTest | 17 | 21 | +4 | 8 | 21 | 0 / 0 / 0 | 17.976s |
| Total | 85 | 143 | +58 | 27 | 143 | 0 / 0 / 0 | 37.740s |

suite time은 XML 합계이며 Gradle wall time과 다릅니다. 성능 개선으로 주장하지 않습니다.
전체 기존 demo 주문·복구·outbox 흐름과 Spring Modulith 검증이 유지됐습니다.
개발 중 targeted test 실패를 해결한 뒤 최종 full run을 집계했습니다. 실패 실행을 PASS로 집계하지 않습니다.

## Failure Findings / 발견과 해결

- numeric subject의 framework 보정 가능성: raw claim type 검사 후 표준 서명 검증으로 해결
- unpaired surrogate의 UTF-8 변환 손실: identity 입력 거부 및 실제 JSON escape fixture로 검증
- mode 문자열 보정과 conditional bean 조건 차이: 단일 canonical mode 검증으로 fallback 차단
- 잘못된 endpoint URL의 오류 메시지: 설정 원문 대신 고정 문구만 사용
- JWKS redirect/unknown kid/timeout/invalid response/cache 만료: 허용 fallback 없이 거부 검증
- token ADMIN/merchant claim: 인증 성공이어도 legacy API에 403, 다음 무토큰 요청은 401

## Runtime and Dependencies

- Docker Engine: 29.4.1
- `docker compose ps`: PostgreSQL/Kafka healthy, Kafka UI up
- Compose logs: PostgreSQL은 이전 비정상 종료 뒤 자동 복구 완료 후 ready, Kafka started
- PostgreSQL/Kafka Testcontainers + 실제 OIDC Boot context: PASS
- `dependencyInsight` 확인: Spring Security core/config/web/OAuth2 7.0.7, Nimbus 10.4
- Security starter 승인: 사용자 2026-10-09 명시 승인. 세부 검토는 `docs/productization/oidc-resource-server.md`
- CI baseline workflow는 변경하지 않음. PR 및 merge commit의 최종 checks는 GitHub에서 확인

## Remaining TODO / 한계

실제 IdP 연결, membership HTTP adapter, tenant API, provisioning, aggregate/event ownership은 미완료입니다.
ID token은 일반 JWT가 아니라 이 서버의 access-token typ 계약 기준으로 거부합니다.
provider가 다른 token-use 계약을 사용하면 별도 검토와 테스트가 필요합니다.
revocation/replay/rate limiting/JWKS 장애 전용 metric 및 전체 dependency SCA는 후속입니다.
기존 unchecked compilation/JVM sharing 경고는 남아 있습니다.

Suggested commit: `feat: add OIDC resource server foundation`
