# Diagram Guide

이 디렉터리는 pinned 대표 포트폴리오 기준으로 draw.io 자산을 한 곳에서 관리하기 위한 기준 경로입니다.
현재는 원본, PNG 미리보기, PDF 배포본을 아래 구조로 정리합니다.

## 현재 디렉터리 구조

- `docs/diagrams/source/`
  draw.io 원본
- `docs/diagrams/png/`
  README / docs 미리보기용 PNG
- `docs/diagrams/pdf/`
  공유/배포용 PDF

## 대상 다이어그램

- overall architecture
  API, orchestration, domain service, infra 의존성을 한 장으로 요약
- order orchestration flow
  주문 생성 이후 happy path와 주요 상태 전이 요약
- outbox retry / dead-letter flow
  publish 실패, retry, dead-letter 전이와 운영 포인트 요약
- notification retry / manual intervention flow
  notification 실패 정책 분기와 admin recovery 요약
- table relation overview
  주요 테이블 관계와 역할을 한 장으로 요약

현재 실제 자산은 아래 4종입니다.

- `commerce_orchestration_overall_architecture`
- `commerce_orchestration_overall_architecture_reference`
- `commerce_orchestration_order_flow`
- `commerce_orchestration_outbox_retry_dead_letter`

아래 2종은 TODO로 남깁니다.

- `commerce_orchestration_notification_recovery_flow`
- `commerce_orchestration_table_overview`

## 파일 네이밍 기준

- 원본:
  `docs/diagrams/source/<topic>.drawio`
- PNG:
  `docs/diagrams/png/<topic>.png`
- PDF:
  `docs/diagrams/pdf/<topic>.pdf`

예시 topic 이름:

- `commerce_orchestration_overall_architecture`
- `commerce_orchestration_overall_architecture_reference`
- `commerce_orchestration_order_flow`
- `commerce_orchestration_outbox_retry_dead_letter`
- `commerce_orchestration_notification_recovery_flow`
- `commerce_orchestration_table_overview`

## 현재 파일 목록

- overall architecture
  - [docs/diagrams/source/commerce_orchestration_overall_architecture.drawio](docs/diagrams/source/commerce_orchestration_overall_architecture.drawio)
  - [docs/diagrams/png/commerce_orchestration_overall_architecture.png](docs/diagrams/png/commerce_orchestration_overall_architecture.png)
  - [docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf](docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf)
- overall architecture reference
  - [docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio](docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio)
  - [docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png](docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png)
  - [docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf](docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf)
- order orchestration flow
  - [docs/diagrams/source/commerce_orchestration_order_flow.drawio](docs/diagrams/source/commerce_orchestration_order_flow.drawio)
  - [docs/diagrams/png/commerce_orchestration_order_flow.png](docs/diagrams/png/commerce_orchestration_order_flow.png)
  - [docs/diagrams/pdf/commerce_orchestration_order_flow.pdf](docs/diagrams/pdf/commerce_orchestration_order_flow.pdf)
- outbox retry / dead-letter flow
  - [docs/diagrams/source/commerce_orchestration_outbox_retry_dead_letter.drawio](docs/diagrams/source/commerce_orchestration_outbox_retry_dead_letter.drawio)
  - [docs/diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png](docs/diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png)
  - [docs/diagrams/pdf/commerce_orchestration_outbox_retry_dead_letter.pdf](docs/diagrams/pdf/commerce_orchestration_outbox_retry_dead_letter.pdf)

## 문서 연결 기준

- 구조 중심 다이어그램은 `docs/architecture/README.md`에서 링크
- 흐름 중심 다이어그램은 `docs/flows/README.md`에서 링크
- README 본문에는 대표 이미지로 overall architecture PNG 1장만 노출

## 설명 문장 기준

각 다이어그램은 아래 2줄을 같이 둡니다.

1. 이 그림이 무엇을 설명하는지
2. 현재 코드 기준선과 어떤 관계인지

예시:

- "This diagram summarizes the order-after-create orchestration path and its main state transitions."
- "It is a visual companion to the current implementation, not a broader future-state architecture."
