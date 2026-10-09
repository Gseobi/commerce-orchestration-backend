# OIDC Resource Server / 구현 범위

- Date: 2026-10-09 (Asia/Seoul)
- Phase: P0
- Branch: `codex/dev-security-oidc-resource-server`
- Status: Implemented and fixture-tested; 실제 IdP production 연결은 미완료

## Implemented / 현재 동작

- default `disabled`: health/error 이외 요청 차단, demo 발급 bean 없음
- `demo`: local/test/integration-test profile 명시 opt-in. prod/production과 함께 활성화하면 startup 실패
- `oidc`: 단일 고정 issuer/JWKS, RS256만 허용, HTTPS 및 test loopback HTTP만 허용
- issuer exact match, API audience, 필수 exp/non-blank subject, nbf/clock skew 검증
- 원본 issuer/subject/audience/time 타입 검사 후 Nimbus decoder의 서명·claim 검증
- `typ=at+jwt` 또는 `application/at+jwt`만 허용. 이 계약에 맞지 않는 제공자는 별도 검토 필요
- issuer/subject의 versioned length-prefixed SHA-256 identity, 69자 `oidc:` actorId
- token role/scope/merchant claim을 권한으로 매핑하지 않음
- 401/403 응답은 기존 envelope의 고정 오류 메시지. token/claim/검증 세부 정보를 반환하지 않음
- OIDC에서도 아직 business/admin API는 전부 deny. membership 연결은 다음 별도 unit

## Configuration / 설정

```text
APP_SECURITY_MODE=oidc
OIDC_ISSUER=https://idp.example/issuer
OIDC_AUDIENCE=commerce-api
OIDC_JWK_SET_URI=https://idp.example/jwks
```

위 URI는 placeholder이며 실제 provider 연결 예제가 아닙니다.
issuer/audience/JWKS 누락, 비허용 URL, 잘못된 mode 또는 범위를 벗어난 duration은 startup 실패입니다.
mode는 하나의 canonical 문자열만 허용하며 demo와 OIDC filter chain이 함께 생성되지 않습니다.
OIDC mode는 demo signing secret을 요구하지 않습니다.

Optional properties (`app.security.oidc.*`):

| Property | Default | Allowed |
|---|---|---|
| clock-skew | 30s | 0..60s |
| connect-timeout | 2s | 1ms..10s |
| read-timeout | 3s | 1ms..10s |
| cache-ttl | 5m | 1ms..15m |

JWKS는 설정 URI에서만 조회하며 redirect를 따르지 않습니다. token jku/x5u 기반 URL 선택과 discovery는 하지 않습니다.
단일 JWKS cache에 monotonic TTL을 적용합니다. 새 kid는 refresh를 시도하며,
기존 cache의 유효 키로는 provider 장애 중에도 검증할 수 있습니다. cache 만료 후 조회 실패는 거부됩니다.
cache가 유지되는 동안 제거된 키로 검증될 수 있으므로 즉시 key revocation을 보장하지 않습니다.
unknown kid 반복에 대한 rate limiting, JWT replay 차단, JWKS 장애 전용 metric은 후속 범위입니다.
실제 provider TTL/rotation SLA를 확인하기 전 production 운영 완료로 주장하지 않습니다.

## Dependency Approval / 보안 검토

2026-10-09 사용자가 starter 다운로드 및 Security `7.0.4 -> 7.0.7` patch를 승인했습니다.
Spring Boot `4.0.5`, Framework `7.0.6` 및 다른 framework 버전은 그대로 유지합니다.
Security BOM property override로 core/config/web/test/OAuth2 계열을 같은 `7.0.7`로 맞춥니다.
resolved Nimbus JOSE JWT는 `10.4`입니다. 기존 JJWT는 demo bean에서만 사용합니다.

- 출처: Spring 공식/Maven Central. Spring Security 및 Nimbus POM license: Apache-2.0
- 신규 주요 runtime: OAuth2 Core/JOSE/Resource Server 7.0.7, Nimbus 10.4
- Nimbus POM의 Tink/BouncyCastle은 optional이며 이 변경에서 직접 추가하지 않음
- 권한: 설정 JWKS로 outbound HTTP(S), public key cache. OAuth2 client/Authorization Server/provider SDK 미추가
- 제거: starter/decoder 제거 후 disabled mode 유지. demo fallback 금지
- 공개 advisory 확인은 전수 SCA 스캔이 아니며 전체 기존 의존성의 무취약성을 보장하지 않음

검토한 공식 공지:

- [CVE-2026-41707](https://spring.io/security/cve-2026-41707/): DPoP replay 수정 7.0.7. 이 서버는 DPoP를 사용하지 않음.
- [CVE-2026-47877](https://spring.io/security/cve-2026-47877/), [CVE-2026-59354](https://spring.io/security/cve-2026-59354/): Authorization Server 조건. 이 starter는 Authorization Server를 추가하지 않음.
- [CVE-2026-47842](https://spring.io/security/cve-2026-47842/): AES encryptor 수정 7.0.7. 해당 encryptor를 사용하지 않음.
- [CVE-2025-53864](https://github.com/advisories/GHSA-xwmg-2g98-w7v9): Nimbus nested JSON DoS. 확인된 10.4는 공지의 취약 버전 범위 밖.

## Verification / 주장 경계

`OidcJwtDecoderTest`는 임시 RSA 키와 JDK HttpServer를 사용하여 실제 compact JWT/JWKS 검증을 실행합니다.
위조/none/HMAC, malformed claims/ID token, 시간/audience/issuer, rotation/cache/timeout/redirect를 확인합니다.
`OidcPropertiesTest`, `SecurityModeTest`는 설정 거부와 HTTP 401/403·demo bean 분리를 확인합니다.
`OidcApplicationIntegrationTest`는 실제 Boot + PostgreSQL/Kafka에서 OIDC mode와 legacy 차단을 확인합니다.
`TrustedActorResolverTest`는 identity 안정성/경계/다른 issuer/입력 거부를 확인합니다.
Modulith 경계는 변경하지 않습니다. security가 아직 merchant module을 호출하지 않습니다.

Future Scope: HTTP membership adapter, tenant API/OpenAPI, membership provisioning,
기존 aggregate/event tenant ownership, 실제 provider 연결, MFA/계정 복구/refresh lifecycle.
모바일 토큰 만료 알림은 이번 개발 범위와 무관하며 사용자 요청대로 보류 상태입니다.
