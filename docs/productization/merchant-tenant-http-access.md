# Merchant Tenant HTTP Access / 구현 범위

- Date: 2026-10-12 (Asia/Seoul)
- Phase: P0 - Product foundation (계속 진행 중)
- Base: `5a51f31`
- Branch: `codex/dev-merchant-tenant-http-access`

## Implemented / 제공 API

`GET /api/merchants/{merchantId}/stores/{storeId}` 하나만 OIDC mode에서 제공합니다.
응답은 `ApiResponse<StoreView>`이며 id/merchantId/code/name/status/timezone/recoveryPolicy만 포함합니다.
실제 IdP 연결이나 membership 등록 API가 추가된 것은 아닙니다.

```text
configured JWT decoder validates signature/claims
  -> canonical JwtAuthenticationToken principal
  -> MerchantRequestAccessAdapter (current SecurityContext only)
  -> merchant::api / authorize(merchantId, actorId, READ)
  -> MerchantApplication.getStore(authorized merchantId, storeId)
  -> merchant-scoped repository lookup
```

adapter/controller는 security module 내부입니다. security의 allowedDependencies에 `merchant::api`만
추가하며 repository/entity를 참조하지 않습니다. merchant가 security/JWT에 의존하지 않습니다.
domain model, DB migration, dependency, Compose, CI 설정은 변경하지 않습니다.
request/operation 사이에 context를 저장하거나 자체 ThreadLocal tenant context를 만들지 않습니다.

## Authorization / 접근 기준

- 검증된 JwtAuthenticationToken의 issuer/subject와 canonical principal name이 일치해야 합니다.
- request body/query/header의 actorId/merchantId, token roles/scope/merchantId는 identity/권한 근거가 아닙니다.
- ACTIVE merchant + active VIEWER/OPERATOR membership의 READ 권한을 매 요청 DB에서 확인합니다.
- demo/disabled에서는 controller/adapter bean을 생성하지 않습니다. demo ADMIN도 merchant 경로 접근 불가입니다.
- OIDC에서도 legacy 주문/전역 admin/token 발급/ADMIN Actuator 경로는 계속 차단합니다.
- GET 외 method와 등록/목록/수정/삭제/초대 경로는 노출하지 않습니다. 자동 CORS/preflight 지원도 없습니다.
- store의 ACTIVE/INACTIVE는 metadata입니다. 별도 영업/판매 가능 여부 판정은 이번 기능이 아닙니다.

## Responses / 오류 경계

| 상황 | 응답 |
|---|---|
| valid token + active membership + 자기 merchant store | 200 / SUCCESS |
| missing/invalid/expired/ID token/wrong issuer | 401 / UNAUTHORIZED |
| 없는 merchant, membership 없음/revoke, SUSPENDED, 타 merchant path | 403 / FORBIDDEN |
| 권한 있는 merchant 안에서 store 없음/다른 merchant의 storeId | 404 / STORE_NOT_FOUND |
| non-numeric/overflow path identifier | 400 / BAD_REQUEST (고정 메시지, 입력 echo 없음) |
| READ 확인 후 storeId <= 0 | 400 / BAD_REQUEST |
| merchantId <= 0 | 403 / FORBIDDEN (기존 authorize 입력 계약) |

membership 거부 시 store 조회를 실행하지 않습니다. 없는 merchant와 권한 없는 merchant는 같은 응답입니다.
revoke는 같은 token의 다음 요청에 반영되지만 이미 시작한 요청과 revoke를 원자적으로 직렬화하지 않습니다.
성공 응답에 actorId, JWT claim, version, credential을 포함하지 않습니다.
새 metric/log/audit field를 추가하지 않아 business identifier tag/secret 기록 정책을 그대로 유지합니다.

## Verification / 검증 근거

- `MerchantRequestAccessAdapterTest`: missing/demo/unauthenticated/noncanonical/malformed identity 거부, canonical identity 위임.
- `MerchantStoreControllerTest`: READ authorize 후 scoped lookup, 거부 시 조회 없음, 400/404 유지.
- `OidcApplicationIntegrationTest`: 실제 RSA/JWKS + PostgreSQL/Kafka에서 role/read, revoke/정지/cross-tenant/spoof/연속 요청/잘못된 ID/method 차단.
- `SecurityModeTest`: 실제 demo ADMIN token도 merchant 접근 거부, mode별 bean 분리.
- 전체 suite 및 Modulith boundary 결과: [정량 보고서](../verification/merchant-tenant-http-access-test-report.md).
- [Spring Security request authorization](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html)는 method/path별 허용 후 나머지 deny 구조의 framework 근거입니다. membership 규칙은 프로젝트 계약입니다.

## Remaining / 다음 범위

membership provisioning/초대/계정 연결, 실제 IdP 계정 설정, legacy aggregate/event tenant ownership은 미완료입니다.
현재 코드는 일반 user-password 저장소를 추가하지 않습니다. 운영 membership을 demo username/email로 자동 생성하지 않습니다.
전체 주문/결제 multi-tenant 보안이나 실서비스 공개 완료로 해석하지 않습니다.
