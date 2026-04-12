# Diagram Guide

이 디렉터리는 pinned 대표 포트폴리오 기준으로 draw.io 자산을 어떤 규칙으로 추가할지 정리하는 자리입니다.
이번 턴에서는 새 diagram을 추가하지 않고, 추후 자료가 들어올 위치와 설명 기준만 고정합니다.

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

## 파일 네이밍 기준

- 원본:
  `docs/diagram/<topic>.drawio`
- PNG:
  `docs/images/<topic>.png`
- PDF:
  `docs/pdf/<topic>.pdf`

예시 topic 이름:

- `commerce_orchestration_overall_architecture`
- `commerce_orchestration_order_flow`
- `commerce_orchestration_outbox_retry_dead_letter`
- `commerce_orchestration_notification_recovery_flow`
- `commerce_orchestration_table_overview`

## 문서 연결 기준

- 구조 중심 다이어그램은 `docs/architecture/README.md`에서 링크
- 흐름 중심 다이어그램은 `docs/flows/README.md`에서 링크
- README 본문에는 "어디에 정리되어 있는가"만 짧게 노출

## 설명 문장 기준

각 다이어그램은 아래 2줄을 같이 둡니다.

1. 이 그림이 무엇을 설명하는지
2. 현재 코드 기준선과 어떤 관계인지

예시:

- "This diagram summarizes the order-after-create orchestration path and its main state transitions."
- "It is a visual companion to the current implementation, not a broader future-state architecture."
