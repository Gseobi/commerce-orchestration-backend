# OpenAPI & ApiDog

## OpenAPI Scope / 명세 범위

`docs/openapi/openapi.yaml`은 실제 구현된 HTTP API만 문서화합니다.

- 구현되지 않은 Future Scope API는 `paths`에 추가하지 않습니다.
- endpoint path를 코드와 다르게 쓰지 않습니다.
- 인증이 필요한 API는 security requirement를 명시합니다.
- `ApiResponse<T>` response envelope을 실제 응답 구조에 맞게 반영합니다.
- request/response example에는 실제 secret, token, private value를 넣지 않습니다.

API를 추가하거나 변경할 때는 `docs/openapi/openapi.yaml`과 관련 README/docs를 함께 갱신합니다.
OpenAPI YAML은 tooling compatibility를 위해 English/tool-friendly style을 유지할 수 있습니다.
README와 guide docs는 Korean-first + English technical terms style을 유지합니다.

## ApiDog Import Readiness / ApiDog 준비

이 프로젝트는 후속으로 ApiDog 기반 API 문서화와 테스트를 고려합니다.

- import 가능한 YAML 구조를 유지합니다.
- future API를 import 편의 목적으로 미리 넣지 않습니다.
- server URL, auth scheme, examples는 local/demo 환경을 기준으로 명확히 구분합니다.
- schema name과 response example은 실제 controller response와 맞춥니다.

## Demo Auth / 인증 설명

demo token 발급 API는 production auth가 아니라 demo-only임을 명시합니다.
real user store, refresh token, key rotation이 구현되지 않았다면 OpenAPI 또는 README에서 production auth처럼 표현하지 않습니다.

## Admin Endpoint Auth / Admin API

admin endpoint는 admin role 또는 security rule을 우회하지 않습니다.
문서에는 admin API가 운영자용 recovery 기능임을 명확히 하고, authorization header나 token 값을 example에 노출하지 않습니다.

Admin Recovery는 전체 orchestration을 다시 실행하는 기능이 아닙니다.
목표는 실패한 하위 처리 단위를 안전하게 복구하는 것입니다.

현재 또는 확장 가능한 대상:

- failed notification event retry
- notification event ignore
- outbox dead-letter retry
- due notification retry batch

Admin API를 변경하거나 추가할 때는 `operatorId`, `reason`, `requestedBy` 같은 optional request context를 우선 고려합니다.
기존 no-body API 호출은 가능하면 backward compatible하게 유지합니다.
admin action은 audit log에 남기되 authorization header, token, secret, raw payload는 log/audit에 남기지 않습니다.
audit detail에는 action, target id, previous status, current status, result, operator, reason을 가능한 범위에서 남깁니다.
reason이 너무 길면 DB column을 고려해 안전하게 truncate하거나 migration을 추가합니다.
