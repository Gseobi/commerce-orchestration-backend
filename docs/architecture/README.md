# Architecture Notes

이 디렉터리는 현재 코드가 어떤 모듈 경계와 협력 방향을 의도하는지 설명합니다.
이 문서는 텍스트 기준선이며, 추후 draw.io 기반 architecture/table 자료가 붙을 위치를 먼저 정리합니다.

처음 보는 경우에는 [Docs Index](docs/README.md)에서 전체 순서를 보고, 이 문서 다음에 [Flow Notes](docs/flows/README.md)로 넘어가는 편이 자연스럽습니다.

## 아키텍처 자산

controller entry, central orchestration, domain services, PostgreSQL, Kafka, external payment provider 의존성을 현재 구현 기준으로 요약한 그림입니다.

![Overall architecture](docs/diagrams/png/commerce_orchestration_overall_architecture.png)

- [draw.io 원본](docs/diagrams/source/commerce_orchestration_overall_architecture.drawio)
- [PNG 이미지](docs/diagrams/png/commerce_orchestration_overall_architecture.png)
- [PDF 문서](docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf)

현재 자산은 참조용이며, pinned 품질 기준의 정식 diagram/table 정리는 후속 작업으로 남겨 둡니다.

## Additional Architecture Material

복원된 기존 architecture 자산도 현재 프로젝트 네이밍 규칙으로 다시 포함합니다.

- [reference draw.io](docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio)
- [reference PNG](docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png)
- [reference PDF](docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf)

## 추후 보강 예정 다이어그램

- overall architecture
  API, orchestration, domain services, Kafka/PostgreSQL, provider 연동을 한 장으로 설명하는 그림
- module dependency overview
  `controller -> facade/service -> repository`, `orchestration -> domain application service` 방향을 보여주는 그림
- table relation overview
  `orders`, `payments`, `settlements`, `notification_events`, `outbox_events`, `audit_logs`, `orchestration_steps` 관계를 요약하는 표/다이어그램

파일 네이밍과 설명 기준은 [Diagram Guide](docs/diagrams/README.md)를 따릅니다.

## 현재 구조 원칙

- 비즈니스 외부 진입점은 `OrderController`입니다.
- `AuthController`는 데모 JWT 발급용 보조 진입점입니다.
- `AdminController`는 운영 재처리용 admin 진입점입니다.
- `CommerceOrchestrationService`는 전체 흐름 제어를 담당합니다.
- `OrderService`, `PaymentService`, `SettlementService`, `NotificationService`는 각자 자기 repository를 내부적으로 소유합니다.
- `OutboxService`는 저장, `OutboxPublisherService`는 발행을 담당합니다.
- `AuditService`는 운영 추적 로그를 남깁니다.

## 경계 규칙

- `controller -> facade/service -> repository`
- `orchestration -> domain application service`
- 다른 domain의 repository를 직접 주입하지 않습니다.
- entity나 repository를 외부에 공개하는 방식보다, 필요한 조회/명령 메서드를 service 경계에 둡니다.

## 현재 코드와 다이어그램의 관계

다이어그램은 중앙 orchestration, domain 분리, outbox 확장성이라는 큰 방향을 설명합니다.  
실제 코드는 그 위에 아래 요소가 추가된 상태입니다.

- JWT 보호
- Docker Compose 기반 로컬 인프라
- Flyway migration 기반 스키마 관리
- outbox retry/backoff/dead-letter
- notification 정책 분류와 admin 재처리
- Testcontainers 통합 테스트
- unit/integration CI 분리
