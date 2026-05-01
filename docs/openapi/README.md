# OpenAPI / ApiDog

`docs/openapi/openapi.yaml` is a static OpenAPI 3.0.3 specification for the implemented HTTP APIs in this repository.

## ApiDog Import

1. Open ApiDog.
2. Choose import from OpenAPI / Swagger.
3. Select `docs/openapi/openapi.yaml`.
4. Set the environment base URL to `http://localhost:8080`.
5. Issue a demo token through `POST /api/auth/token`, then use the returned value as a Bearer token for protected APIs.

## Scope Rules

- The spec contains implemented APIs only.
- Admin recovery endpoints include the optional `operatorId` / `reason` request body added for audit traceability.
- Existing no-body admin recovery calls remain valid.
- `POST /api/admin/notification-events/retry-due` returns its batch result directly, not inside `ApiResponse<T>`.
- Future scope APIs stay in README/docs planning notes, not in OpenAPI paths.

Not included as paths:

- provider callback flow
- WebClient timeout confirmation flow
- Kafka consumer-based state transition
- Prometheus/Grafana dashboards or alert rules
- stale `PROCESSING` automatic recovery job
- refresh token, key rotation, or real user-store APIs

