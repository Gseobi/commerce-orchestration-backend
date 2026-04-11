# Troubleshooting

## 예상 이슈

### 1. 외부 인프라 미구성 상태

- PostgreSQL, Kafka가 올라와 있지 않으면 실제 실행 시 datasource 또는 broker 연결 이슈가 발생할 수 있습니다.
- 현재 스캐폴드는 compile-safe와 구조 설명이 우선이며, 실제 인프라 연동은 후속 작업입니다.

### 2. payment provider 미연동

- 현재 `PaymentService`는 실제 외부 provider 호출을 수행하지 않습니다.
- 향후 timeout, retry, idempotency를 고려한 client 분리가 필요합니다.

### 3. outbox는 placeholder 상태

- `OutboxEvent`는 발행 대상 이벤트 기록만 담당합니다.
- 실제 Kafka publish scheduler 또는 publisher worker는 아직 구현하지 않았습니다.

### 4. compensation 미구현

- settlement 또는 notification 실패 시 보상 흐름은 TODO 상태입니다.
- 현재는 실패 지점을 표현할 엔티티와 status 중심으로만 열어 두었습니다.
