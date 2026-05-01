# OpenAPI / ApiDog Guide

## Purpose / 목적

이 문서는 `docs/openapi/openapi.yaml`을 ApiDog에 가져와 현재 구현된 HTTP API를 확인하는 방법과, OpenAPI 문서를 유지할 때 지켜야 할 범위를 정리합니다.

OpenAPI는 포트폴리오 검토자가 Controller, DTO, admin recovery 흐름을 빠르게 확인할 수 있도록 돕는 보조 문서입니다. 구현되지 않은 Future Scope API를 미리 paths에 넣는 용도로 사용하지 않습니다.

## Spec File / 명세 파일

`docs/openapi/openapi.yaml`은 현재 구현된 HTTP API만 포함하는 static OpenAPI 3.0.3 specification입니다.

- spec file: `docs/openapi/openapi.yaml`
- format: OpenAPI 3.0.3
- scope: implemented APIs only
- validation status: 별도 validator가 명시되지 않은 경우 YAML syntax parse와 repository verification commands 기준입니다.

## Import to ApiDog / ApiDog 가져오기

1. ApiDog을 엽니다.
2. OpenAPI / Swagger import를 선택합니다.
3. `docs/openapi/openapi.yaml`을 선택합니다.
4. environment base URL을 `http://localhost:8080`으로 설정합니다.
5. `POST /api/auth/token`으로 demo token을 발급한 뒤, protected APIs 호출 시 Bearer token으로 사용합니다.

Manual import status:

- ApiDog manual import: OpenAPI spec 작성 후 로컬에서 확인했습니다.
- Automated validation: 별도 validator가 기록되지 않은 한 YAML syntax parse와 repository verification commands 범위로 제한됩니다.

## Local Server / 로컬 서버

로컬 실행 기준 base URL은 아래와 같습니다.

```text
http://localhost:8080
```

Docker Compose 또는 local profile 실행 방법은 repository root의 `README.md`와 `docs/troubleshooting.md`를 기준으로 확인합니다.

## Demo Authentication / 데모 인증

`POST /api/auth/token`은 demo token 발급용 endpoint입니다.

- production auth, refresh token, key rotation, real user store를 의미하지 않습니다.
- Admin APIs는 Bearer JWT와 ADMIN role이 필요합니다.
- token, authorization header, secret 값은 docs, logs, audit detail에 남기지 않습니다.

## Documented APIs / 문서화된 API

- OpenAPI spec은 구현된 Controller endpoint만 포함합니다.
- Admin recovery endpoints는 audit traceability를 위해 추가된 optional `operatorId` / `reason` request body를 반영합니다.
- 기존 no-body admin recovery calls는 계속 유효합니다.
- `POST /api/admin/notification-events/retry-due`는 `ApiResponse<T>` envelope이 아니라 batch result를 직접 반환합니다.

## Rules / 작성 규칙

- Future Scope API는 구현되기 전까지 OpenAPI paths에 추가하지 않습니다.
- Controller path, request DTO, response shape가 바뀌면 OpenAPI도 같은 작업에서 갱신합니다.
- 인증이 필요한 API는 security requirement를 명시합니다.
- demo token 발급 API는 production auth로 오해되지 않게 설명합니다.
- request/response example에는 실제 secret, token, private value를 넣지 않습니다.
- `ApiResponse<T>` envelope 사용 여부는 실제 Controller 응답 구조에 맞춥니다.

## Future Scope / 후속 범위

아래 항목은 현재 OpenAPI paths에 포함하지 않습니다.

- provider callback flow
- WebClient timeout confirmation flow
- Kafka consumer-based state transition
- Prometheus/Grafana dashboards or alert rules
- stale `PROCESSING` automatic recovery job
- refresh token, key rotation, or real user-store APIs
