# Architecture Notes

이 디렉터리는 commerce orchestration backend의 상위 구조와 현재 코드가 어떤 경계를 의도하는지 설명합니다.

현재 architecture 자산은 아래 파일을 기준으로 관리합니다.

- [draw.io 원본](../diagram/kafka_orchestration_backend_architecture.drawio)
- [PNG 이미지](../images/kafka_orchestration_backend_architecture.png)
- [PDF 문서](../pdf/kafka_orchestration_backend_architecture.pdf)

`README.md`의 Architecture Overview 섹션은 위 자산을 그대로 참조합니다.

## 현재 구조 원칙

- `OrderController`는 주문 관련 public API 진입점입니다.
- `AuthController`는 데모용 JWT 발급을 위한 보조 진입점입니다.
- `CommerceOrchestrationService`는 전체 flow control을 담당합니다.
- `OrderService`, `PaymentService`, `SettlementService`, `NotificationService`는 각자 자기 repository를 내부적으로 소유합니다.
- `OutboxService`, `OutboxPublisherService`는 이벤트 저장과 발행을 나눠 담당합니다.
- `AuditService`는 운영 추적 로그를 남깁니다.

## 경계 관점에서 중요한 점

- orchestration은 다른 domain의 repository를 직접 주입받지 않습니다.
- order 상세 조회도 payment / settlement / notification repository를 직접 읽지 않고 각 domain service를 통해 조회합니다.
- repository 패키지를 외부로 export하는 방식보다, 필요한 협력 메서드를 service 경계에 두는 방향을 우선합니다.

## 현재 다이어그램과 코드의 관계

- 다이어그램은 중앙 orchestration, domain 분리, outbox 확장성을 설명하는 데 초점을 둡니다.
- 실제 코드는 JWT 보호, Compose 기반 로컬 인프라, CI, outbox publisher scheduler까지 일부 더 진행된 상태입니다.
- 반면 외부 payment provider, retry/backoff, integration test는 아직 후속 단계입니다.
