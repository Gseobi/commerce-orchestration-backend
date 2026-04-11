# Test Report

## 현재 상태

이 문서는 초기 스캐폴드 기준 검증 결과를 기록하기 위한 자리입니다.

## Verification Summary

| Area | Status | Notes |
|---|---|---|
| `./gradlew compileJava` | Pass | Java / Spring Boot 초기 스캐폴드 컴파일 성공 |
| `./gradlew test` | Pass | `contextLoads` 기준 Spring context 로딩 성공 |
| Order create API scaffold | Initial Scaffold | `POST /api/orders` 구현 |
| Orchestration API scaffold | Initial Scaffold | `POST /api/orders/{orderId}/orchestrate` 구현 |
| Order flow query scaffold | Initial Scaffold | `GET /api/orders/{orderId}/flow` 구현 |
| Retry / idempotency | Planned | TODO only |
| Kafka publish | Planned | Outbox placeholder only |

## 메모

- 초기 단계에서는 compile-safe와 문서 구조 정리에 우선순위를 둡니다.
- 테스트는 happy path 중심 최소 검증부터 점진적으로 추가할 예정입니다.
- 테스트는 현재 `H2` 기반 임베디드 DB로 context를 검증합니다.
