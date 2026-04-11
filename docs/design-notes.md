# Design Notes

## 현재 설계 의도

- 주문 이후의 후속 처리 흐름은 `CommerceOrchestrationService`가 제어합니다.
- 각 domain service는 자기 repository를 내부적으로 소유합니다.
- `controller -> service -> repository`, `orchestration -> domain service` 방향을 유지해 cross-domain repository 직접 참조를 줄였습니다.
- 실제 외부 인프라 연동 전에 상태 전이, 실패 분기, step 기록, outbox 이벤트를 먼저 고정했습니다.

## 현재 구조 요약

### orchestration

- `CommerceOrchestrationService`가 happy path와 failure branch를 제어합니다.
- orchestration은 `OrderService`, `PaymentService`, `SettlementService`, `NotificationService`, `AuditService`, `OutboxService`와 협력합니다.
- orchestration 자체 repository는 `OrchestrationStepRepository`만 직접 사용합니다.

### order

- 주문 생성과 상태 전이의 소유자는 `OrderService`입니다.
- 주문 상세 조회 시에도 payment / settlement / notification repository를 직접 읽지 않고 각 domain service의 조회 메서드를 사용합니다.

### payment

- `PaymentService`가 `PaymentRepository`를 내부적으로 사용합니다.
- 현재 `PaymentProviderClient`는 mock 성격 구현(`MockPaymentProviderClient`) 중심입니다.
- 외부 PG 연동 시 timeout, retry, circuit-breaker, idempotency key 정책이 함께 필요합니다.

### settlement / notification

- settlement와 notification은 요청 결과를 자기 repository에 기록합니다.
- settlement 실패 시에는 payment 취소 보상까지 연결되어 있습니다.
- notification 실패 시 compensation 정책은 아직 명시적으로 고정하지 않았습니다.

### outbox / audit

- `OutboxService`는 outbox event 생성과 조회를 담당합니다.
- `OutboxPublisherService`는 scheduler에 의해 `READY` 이벤트를 Kafka로 publish합니다.
- `AuditService`는 orchestration 분기 기록을 남깁니다.

## 현재 남겨 둔 TODO와 이유

### 1. 실제 payment provider client 구현과 timeout/retry 정책

- 현재는 흐름 제어를 먼저 안정화하기 위해 mock provider를 사용합니다.
- 실제 외부 연동을 붙이면 네트워크 timeout, 재시도, idempotency, 실패 코드 매핑이 함께 들어오기 때문에 별도 단계로 분리했습니다.

### 2. outbox FAILED 재발행 정책과 backoff

- 지금 구조는 publish 성공과 실패 기록까지는 갖췄습니다.
- 하지만 언제 다시 시도할지, 실패 횟수를 어떻게 누적할지, dead-letter로 언제 넘길지는 운영 정책과 직접 연결되므로 후속 설계가 필요합니다.

### 3. notification 실패 compensation 구체화

- settlement 실패는 payment 취소로 비교적 명확한 반면, notification 실패는 재시도만으로 충분한지, 주문 상태 롤백이 필요한지 정책이 다를 수 있습니다.
- 그래서 현재는 TODO를 코드와 문서에 명시만 하고 섣불리 일반화하지 않았습니다.

### 4. JWT refresh token / key rotation / user store

- 현재 JWT는 데모용 access token 발급과 API 보호에 집중합니다.
- 운영 수준 인증으로 가려면 refresh token 저장, 키 교체, 사용자/권한 저장소와의 연동이 필요합니다.

### 5. Flyway / Liquibase 기반 schema 관리

- 현재는 개발 속도를 위해 `ddl-auto`를 사용합니다.
- 스키마 변경 이력 관리와 배포 재현성을 확보하려면 migration tool 도입이 필요합니다.

### 6. Kafka 실제 integration test / Testcontainers

- 현재 테스트는 H2 + mock Kafka 위주입니다.
- outbox publish와 보안 설정의 실제성 검증은 Testcontainers로 확장하는 편이 적절합니다.

## 다음 구현 권장 순서

1. `PaymentProviderClient`를 실제 외부 연동 client와 mock client로 분리 고도화
2. outbox retry/backoff 및 dead-letter 전략 추가
3. compensation 정책을 settlement / notification 별로 분리
4. Testcontainers 기반 PostgreSQL / Kafka 통합 테스트 추가
5. GitHub Actions에 integration-test job과 artifact/report 업로드 확장

이 순서를 권장하는 이유는 외부 실패 원인과 재처리 정책을 먼저 고정해야, 이후 통합 테스트와 CI 검증 범위도 자연스럽게 따라오기 때문입니다.
