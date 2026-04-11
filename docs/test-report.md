# Test Report

## 현재 검증 기준

이 문서는 현재 레포지토리 상태에서 확인한 compile / test 범위와, 아직 실인프라 검증으로 넘어가지 않은 범위를 정리합니다.

## Verification Summary

| Area | Status | Notes |
|---|---|---|
| `./gradlew compileJava` | Pass | 2026-04-11 기준 구조 정리 후 컴파일 성공 |
| `./gradlew test` | Pass | 2026-04-11 기준 전체 테스트 통과 |
| Spring context loading | Implemented | `CommerceOrchestrationBackendApplicationTests` |
| JWT token issuance | Implemented | `OrderFlowIntegrationTest`에서 `/api/auth/token` 사용 |
| `/api/**` authentication | Implemented | 인증 없는 주문 생성 요청 `401` 검증 |
| Order create / detail / flow API | Implemented | `OrderFlowIntegrationTest` |
| Orchestration happy path | Implemented | 상태 전이, step 수, outbox 생성 검증 |
| Settlement failure compensation | Implemented | 실패 step과 compensation step 검증 |
| Notification failure TODO branch | Implemented | compensation `READY` 상태 검증 |
| Payment service unit test | Implemented | 승인 성공/실패 분기 검증 |
| Outbox publisher unit test | Implemented | publish 후 `PUBLISHED` 상태 전이 검증 |
| PostgreSQL integration test | Not Yet | 현재는 H2 메모리 DB 사용 |
| Kafka broker integration test | Not Yet | 현재는 mock `KafkaTemplate` 사용 |

## 현재 테스트 구조의 의미

- 애플리케이션 구조와 상태 전이, 보안 설정이 깨지지 않았는지 빠르게 확인하는 데 초점을 둡니다.
- 외부 인프라가 없는 환경에서도 CI와 로컬 회귀가 가능하도록 H2와 mock Kafka를 사용합니다.
- 대신 실제 PostgreSQL DDL 차이, Kafka publish round-trip, Docker Compose 환경 상호작용은 아직 별도 검증 범위입니다.

## 다음 확장 권장

1. Testcontainers로 PostgreSQL / Kafka 통합 테스트 추가
2. outbox scheduler와 publisher의 실패 재시도 경로 검증
3. JWT refresh / key rotation이 추가되면 auth 관련 테스트 분리
4. GitHub Actions에 integration-test job 추가 및 artifact/report 업로드 확장
