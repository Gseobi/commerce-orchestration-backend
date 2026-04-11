# Architecture Notes

이 디렉터리는 commerce orchestration backend의 상위 구조를 설명하기 위한 문서입니다.

현재 architecture 자산은 아래 파일을 기준으로 관리합니다.

- [draw.io 원본](../diagram/kafka_orchestration_backend_architecture.drawio)
- [PNG 이미지](../images/kafka_orchestration_backend_architecture.png)
- [PDF 문서](../pdf/kafka_orchestration_backend_architecture.pdf)

`README.md`의 Architecture Overview 섹션은 위 자산을 그대로 참조합니다.

현재 코드 스캐폴드는 아래 구조를 설명하는 데 초점을 둡니다.

- `OrderController`는 public API 진입점 역할만 담당
- `CommerceOrchestrationService`가 전체 order lifecycle flow 제어
- `payment`, `settlement`, `notification`은 내부 단계 도메인으로 분리
- `outbox`는 Kafka publish 확장 지점 역할
- `audit`는 운영 추적 이력을 남길 수 있는 저장소 역할

실제 다이어그램과 코드 사이 차이는 이후 구현 진행에 따라 점진적으로 맞춰 갈 예정입니다.
