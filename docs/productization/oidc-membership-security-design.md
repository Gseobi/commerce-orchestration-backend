# OIDC Membership Security Adapter / 설계 결정

- Date: 2026-10-09 (Asia/Seoul)
- Phase: P0 - Product foundation
- Status: `Resource Server implemented; membership HTTP adapter pending`
- Base: `f1695e6` (merchant membership access 병합)
- 사용자 선택: 외부 OIDC / JWT 검증. 자체 비밀번호 저장소는 만들지 않음.

## 1. 현재 구현과 이번 범위

현재 `MerchantAccessApplication.authorize`는 신뢰된 actorId를 요구하는 내부 계약입니다.
`AuthController`는 username/role을 호출자가 지정하는 demo 토큰을 발급합니다.
기존 `JwtAuthenticationFilter`와 `JwtTokenProvider`는 이 demo용이며 production identity가 아닙니다.
2026-10-09 후속 구현에서 mode 분리와 JWKS/issuer 검증을 추가했습니다.
실제 구현과 테스트 범위는 [OIDC Resource Server](oidc-resource-server.md)를 기준으로 확인합니다.

원래 설계 delivery unit은 설계와 실행 기준선 보고만 추가했습니다.
아래 내용은 target 설계이며 Resource Server 외 HTTP API와 membership 연결은 여전히 `Planned`입니다.

## 2. 인증과 권한 분리

```text
Bearer access token
  -> Spring Security Resource Server / JwtDecoder
  -> 검증된 issuer + subject / TrustedActorResolver
  -> MerchantRequestAccessAdapter
  -> merchant::api / MerchantAccessApplication.authorize
  -> merchant-scoped application operation
```

- Backend는 Resource Server이며 login redirect, authorization code 교환, refresh token을 소유하지 않습니다.
- 로그인 UI/client의 OIDC 흐름, 계정 복구, MFA는 제공자와 client의 별도 범위입니다.
- API는 해당 API audience에 발급된 JWT access token만 받습니다. ID token은 받지 않습니다.
- token claim의 email, username, role, merchantId는 membership의 source of truth가 아닙니다.
- merchant READ/WRITE는 활성 membership과 ACTIVE merchant를 DB에서 확인합니다.
- JWT scope가 있더라도 membership 검증을 대신하지 않습니다.
- 기존 `ROLE_ADMIN`으로 보호된 전역 복구 API/Actuator 권한은 별도 승인 전 OIDC 주체에게 부여하지 않습니다.
  IdP role을 자동으로 `ROLE_ADMIN`에 매핑하지 않습니다.

## 3. Token validation / Fail Closed

첫 구현은 운영자가 설정한 단일 issuer만 신뢰합니다. token의 `iss`, `jku`, `x5u`로 discovery URL을 선택하지 않습니다.
production issuer와 JWKS endpoint는 HTTPS이며 request 입력으로 바꿀 수 없습니다.
HTTP 테스트 JWKS는 test profile의 loopback fixture에만 허용합니다.

검증 조건:

- 서명: 지정된 JWKS의 공개키와 제공자 계약에 맞춘 알고리즘 allowlist. `none`과 demo HMAC은 거부.
- `iss`: 설정값과 exact match. 대소문자/경로/trailing slash를 임의 정규화하지 않음.
- `aud`: 설정된 API audience 포함. client ID만을 API audience로 임의 대체하지 않음.
- `exp`: 필수이며 만료 거부. `nbf`가 있으면 미래 시점 거부. clock skew는 bounded 설정으로 테스트.
- `sub`: non-empty, non-blank String. malformed claim은 401이며 DB 접근 전에 거부.
- access-token 구분: 제공자의 문서화된 `typ` 또는 token-use claim 계약을 고정해 ID token 거부를 검증.
  구분 계약이 불명확한 제공자는 production 연결을 승인하지 않음.

검증 실패는 401, 인증 성공 후 membership 부족/정지 merchant는 기존 계약과 동일한 403입니다.
없는 merchant와 타 사업자 접근은 동일한 403이며 존재 여부를 노출하지 않습니다.
JWKS timeout/unknown kid/잘못된 응답은 허용으로 fallback하지 않습니다.
이미 cache된 유효 공개키로 검증 가능한 요청의 처리는 cache 정책에 따르며 외부 장애와 token 오류를 구분해 관측합니다.
새 키 조회는 bounded timeout/cache로 제한하고 키 교체·cache 만료·장애 시나리오를 검증합니다.
JWT는 bearer credential이므로 탈취 후 만료까지 replay가 가능할 수 있습니다. logout 즉시 무효화를 보장하지 않습니다.
짧은 access-token TTL 및 revocation/introspection 필요 여부는 제공자 선정 시 확정합니다.

## 4. Stable Actor Identity

서로 다른 issuer에서 같은 `sub`를 사용하는 사용자는 다른 actor입니다.
기존 membership의 `actor_id VARCHAR(120)`를 유지하기 위한 내부 actorId 형식은 다음으로 고정합니다.

```text
oidc:<lowercase SHA-256 hex of versioned length-prefixed issuer/sub UTF-8 bytes>
bytes = ASCII("oidc-v1") || uint32be(issuer byte length) || issuer bytes
        || uint32be(subject byte length) || subject bytes
```

총 길이는 69 ASCII 문자입니다. delimiter 단순 연결 대신 length prefix로 입력 경계를 구분합니다.
issuer/sub를 trim하거나 case-fold하지 않습니다. SHA-256 collision 가능성은 이론적 잔여 위험으로 남습니다.
이 값은 비밀이 아니지만 사용자 연결 식별자이므로 metric tag나 일반 로그에 넣지 않습니다.
security adapter만 canonical mapping을 수행하며 다른 모듈은 issuer/JWT 타입을 참조하지 않습니다.

demo username으로 만든 membership을 자동 변환하거나 email로 계정을 자동 연결하지 않습니다.
운영자가 검증한 `(issuer, sub)`로 membership을 명시적으로 provision해야 합니다.
issuer/client의 subject 정책이 바뀌면 새 identity로 취급합니다. 이전 권한 이전은 감사 가능한 별도 절차입니다.
membership 관리 API와 셀프 가입/초대/계정 연결은 이번 구현 단위에서 제외합니다.

## 5. HTTP Tenant Boundary

첫 HTTP slice 후보는 읽기 전용 `GET /api/merchants/{merchantId}/stores/{storeId}`입니다.
아직 구현/OpenAPI에 추가하지 않습니다.

- actorId는 검증된 SecurityContext에서만 얻음. header/body/query의 actorId를 받지 않음.
- merchantId는 path 한 곳에서만 지정. 현재 slice에는 다른 tenant selector가 없음.
- operation 직전에 READ 권한 authorize 후 `MerchantApplication.getStore(merchantId, storeId)` 호출.
- 권한이 있는 merchant의 store가 없으면 기존 application 계약의 NOT_FOUND 응답 사용.
- request-scoped 불변 context 사용. ThreadLocal 상속이나 오래 저장한 context로 권한을 재사용하지 않음.
- 권한 check 후 concurrent revoke를 직렬화한다고 주장하지 않음. 다음 요청에서 revoke 반영.
- 기존 order/payment/notification/outbox/admin API는 아직 tenant-owned가 아님.
  OIDC mode에서는 이 legacy business/admin 경로를 fail closed로 막고, 검증된 새 slice만 노출.
  실서비스 공개 전 aggregate ownership과 비동기 event tenant 전파를 별도 delivery로 완료해야 함.

security module의 `allowedDependencies`에 `merchant::api`를 추가하는 이유를 구현 PR에서 설명합니다.
merchant는 security에 의존하지 않고, security는 merchant repository/entity에 접근하지 않습니다.
Modulith verification은 그대로 유지합니다.

## 6. Demo Isolation / 설정 전환

다음 구현에서는 인증 mode를 명시적인 `disabled`, `demo`, `oidc`로 구분합니다.
default는 `disabled`이며 protected API에 접근을 허용하지 않습니다. local/test demo는 명시적으로 opt-in합니다.
production + demo 조합, 불명확한 복수 mode, issuer/audience 누락은 startup 실패입니다.
mode별 filter chain은 상호 배타적이며 같은 요청에서 demo와 OIDC decoder를 동시에 적용하지 않습니다.

OIDC mode에서 `/api/auth/token` controller와 demo JWT bean은 생성하지 않습니다.
production 경로에 demo signing secret/default key가 필요하지 않아야 합니다.
OIDC 설정 실패를 demo mode로 fallback하지 않습니다.
Compose 파일과 기존 test fixture의 demo 사용은 명시 설정으로 보존하며 새 mode의 회귀 테스트를 추가합니다.
배포 전 demo API를 사용하는 기존 client의 호환성 영향을 공지합니다.

## 7. Dependencies and Provider Approval Gate

후보: `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`.
버전은 현재 Boot `4.0.5` dependency management를 따르고 주요 버전 업그레이드는 하지 않습니다.
Spring Security OAuth2 Resource Server + JOSE/Nimbus를 사용하며 수동 JJWT 외부 키 검증을 만들지 않습니다.
기존 JJWT는 demo mode에만 유지합니다. provider SDK와 OAuth2 client starter는 이 API 서버에 불필요합니다.

2026-10-09 사용자 승인으로 starter를 추가하고 Security 계열만 `7.0.7`로 patch했습니다.
승인/보안 검토 기록은 [OIDC Resource Server](oidc-resource-server.md)에 남깁니다.
실제 제공자 연결 전 다음 항목을 계속 확인합니다.

- 정확한 resolved runtime/test transitive versions, license와 공개 advisory 검토
- 출처: Spring 공식 프로젝트/Maven Central. runtime 권한: 설정된 IdP/JWKS로 outbound HTTPS
- 대안: 자체 JWT 검증은 키 교체·claim 검증 책임이 커 채택하지 않음
- 제거: starter/decoder 설정을 제거하고 disabled mode 유지. demo fallback은 금지
- 제공자: JWT access token, issuer, API audience, JWKS/rotation, token-use 구분, TTL 및 비용 확인
- 실제 IdP 계정/realm 생성, 유료 계약, credential 제출은 별도 승인. private key/client secret은 받지 않음

제공자 선택 전에도 승인된 dependency와 로컬 RSA/JWKS fixture로 adapter를 검증할 수 있습니다.
fixture 테스트 성공은 실제 제공자 production 연동 성공을 의미하지 않습니다.

## 8. Implementation Units and Verification Plan

1. `codex/dev-security-oidc-resource-server`: mode 분리, decoder/validator, canonical actor resolver.
2. `codex/dev-merchant-tenant-http-access`: security membership adapter와 읽기 전용 store HTTP slice/OpenAPI.
3. 이후 별도 unit: membership provisioning 운영 절차, legacy aggregate ownership/event 전파.

각 unit은 main 기준 독립 branch/PR, 전체 회귀와 numeric report, CI 통과 후 expected head SHA로 merge합니다.

| Planned scenario | Expected | Verification layer |
|---|---|---|
| 올바른 서명/issuer/audience/subject | 인증 성공 | signed JWT + local JWKS |
| 위조, none/demo HMAC, 만료, missing exp, future nbf | 401 | decoder/HTTP |
| 잘못된 issuer/audience, ID token, 잘못된 subject | 401, DB 조회 없음 | decoder/HTTP |
| 같은 sub + 다른 issuer / 경계가 모호한 문자열 | 다른 actorId | resolver unit |
| 같은 identity 반복 | 동일 69자 actorId | resolver unit |
| JWKS rotation, unknown kid, timeout, cache 만료 | 계약에 따른 검증/거부, fallback 없음 | fixture integration |
| VIEWER READ / WRITE, OPERATOR | 기존 membership 권한 유지 | PostgreSQL + HTTP |
| 없는/revoke membership, SUSPENDED, 다른 tenant | 403 | PostgreSQL + HTTP |
| request actorId spoof / token role spoof | identity/권한 변경 불가 | HTTP |
| OIDC mode demo token 발급/legacy API 호출 | 발급 불가/접근 거부 | mode integration |
| production demo 또는 누락 설정 | startup 실패 | context test |
| 두 요청 사이 tenant context 잔존 | 없음 | sequential HTTP |
| 모든 module boundary, 기존 demo 흐름 | 회귀 유지 | Modulith + 전체 tests |

tokens/Authorization/email/raw claims를 log/audit/error response에 출력하지 않습니다.
metric tag는 result/failure category 수준으로 제한하며 issuer/sub/actorId/merchantId/kid를 넣지 않습니다.
보호된 데이터 반환 전에 authentication/authorization을 완료하며 OpenAPI에는 구현된 slice만 반영합니다.

## 9. Sources / 설계 근거

- [Spring Security 7.0 JWT Resource Server](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html): 표준 decoder, issuer/time/audience 검증 및 JWKS 처리 근거.
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability): issuer/subject identity의 안정성 근거. API용 access-token 검증 조건은 제공자 계약과 별도로 고정.
- [Spring Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide): Resource Server starter 이름 확인.

위 표준은 framework 지원 근거이며 mode, actor mapping, legacy endpoint 차단은 이 프로젝트의 설계 결정입니다.
