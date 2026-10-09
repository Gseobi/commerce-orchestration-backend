# Merchant Membership Access / 구현 설계

## 범위와 계약

P0의 두 번째 단위는 내부 `MerchantAccessApplication.authorize(merchantId, actorId, permission)` 계약입니다.
actorId는 검증된 issuer/subject에서 만든 canonical identity를 전달해야 합니다.
OIDC resolver는 구현했지만 HTTP 연결은 아직 하지 않았습니다.
현재 demo JWT나 request body의 actorId를 연결하면 사용자가 identity를 위조할 수 있으므로 이 계약을 HTTP에 연결하지 않습니다.

- `VIEWER`: READ
- `OPERATOR`: READ, WRITE
- 활성 membership이 없거나 사업자가 SUSPENDED이면 FORBIDDEN
- 존재하지 않는 사업자와 권한이 없는 사업자는 같은 FORBIDDEN 응답
- 같은 actor는 서로 다른 사업자에 각각 다른 role을 가질 수 있음
- membership revoke 후 다음 authorize 호출은 DB 상태를 다시 확인

`MerchantAccessContext`는 불변 조회 결과입니다. 자체로 인증 토큰이나 지속적인 권한 증명은 아닙니다.
직접 생성하거나 오래 보관한 context를 신뢰하지 않고, protected operation마다 권한을 다시 확인해야 합니다.
이미 진행 중인 transaction과 revoke 사이의 원자적 직렬화는 이번 구현 범위에 포함하지 않습니다.

## 데이터와 모듈 경계

Flyway V8은 `merchant_memberships`만 추가합니다. 기존 데이터 backfill이나 public API 변경은 없습니다.
`(merchant_id, actor_id)` unique constraint와 merchant FK로 잘못된 membership 연결을 막습니다.
role은 DB check constraint로 VIEWER/OPERATOR만 허용합니다.

membership repository와 entity는 merchant 내부에 두며, 다른 모듈은 `merchant::api` 계약을 사용합니다.
membership 생성과 role 변경 HTTP API, 초대 흐름, production user store는 후속 범위입니다.
기존 `MerchantApplication` 등록/조회는 trusted bootstrap 및 내부 조회 계약으로 유지됩니다.
이번 authorize 계약이 기존 주문·결제·알림·outbox 전체를 보호한다는 의미는 아닙니다.

## 검증

- OPERATOR 읽기·쓰기 허용, VIEWER 쓰기 거부
- 잘못된 입력은 repository 조회 전에 거부
- 다른 사업자 membership을 권한으로 재사용할 수 없음
- revoke/SUSPENDED 후 다음 authorize 호출 거부
- 중복 membership/FK 위반은 PostgreSQL에서 거부
- 전체 단위·통합 회귀 및 Spring Modulith 검증

## 다음 단위

OIDC Resource Server는 [구현 범위](oidc-resource-server.md)까지 완료했습니다.
다음은 검증된 주체와 membership을 연결하는 HTTP adapter 및 읽기 전용 store slice입니다.
기존 business aggregate/event의 ownership 전파는 이후 별도 unit입니다.

사용자 선택으로 외부 OIDC/JWT 검증 방향을 확정했습니다.
구현 순서와 보안 경계는 [OIDC Membership Security Adapter 설계](oidc-membership-security-design.md)를 따릅니다.
이 문서 추가만으로 demo JWT가 신뢰된 production identity가 되지는 않습니다.
