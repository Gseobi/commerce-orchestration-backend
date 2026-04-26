# Diagrams

이 디렉터리는 commerce-orchestration-backend 프로젝트의 주요 설계 흐름을 정리한 다이어그램을 보관합니다.

draw.io 원본은 `source`, 문서 본문에서 보여주는 이미지는 `png`, 공유용 문서는 `pdf` 아래에 둡니다.

## Directory

- `/docs/diagrams/source/`
- `/docs/diagrams/png/`
- `/docs/diagrams/pdf/`

## Naming Rule

다이어그램 파일명은 아래 규칙을 따릅니다.

```text
commerce_orchestration_{scope}_{purpose}
```

예시:

```text
commerce_orchestration_overall_architecture
commerce_orchestration_outbox_retry_dead_letter
commerce_orchestration_payment_idempotency_flow
```

source / png / pdf는 동일 basename을 사용합니다.

## Core Architecture Diagrams

| Diagram | Description | Source |
|---|---|---|
| `commerce_orchestration_overall_architecture` | 전체 주문 이후 orchestration 구조 | [draw.io](/docs/diagrams/source/commerce_orchestration_overall_architecture.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_overall_architecture.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf) |
| `commerce_orchestration_order_flow` | 주문 생성 이후 payment, settlement, notification 흐름 | [draw.io](/docs/diagrams/source/commerce_orchestration_order_flow.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_order_flow.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_order_flow.pdf) |
| `commerce_orchestration_notification_recovery_flow` | notification 실패 복구 흐름 | [draw.io](/docs/diagrams/source/commerce_orchestration_notification_recovery_flow.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_notification_recovery_flow.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_notification_recovery_flow.pdf) |
| `commerce_orchestration_outbox_retry_dead_letter` | outbox retry/dead-letter 흐름 | [draw.io](/docs/diagrams/source/commerce_orchestration_outbox_retry_dead_letter.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_outbox_retry_dead_letter.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_outbox_retry_dead_letter.pdf) |
| `commerce_orchestration_table_relation_overview` | Flyway migration 기준 logical table relation overview | [draw.io](/docs/diagrams/source/commerce_orchestration_table_relation_overview.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_table_relation_overview.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_table_relation_overview.pdf) |

## Reliability Hardening Diagrams

| Diagram | Description | Source |
|---|---|---|
| `commerce_orchestration_reliability_hardening_overview` | idempotency, retry claim, publisher adapter 보강 개요 | [draw.io](/docs/diagrams/source/commerce_orchestration_reliability_hardening_overview.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_reliability_hardening_overview.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_reliability_hardening_overview.pdf) |
| `commerce_orchestration_payment_idempotency_flow` | `paymentRequestId` 기반 결제 멱등성 흐름 | [draw.io](/docs/diagrams/source/commerce_orchestration_payment_idempotency_flow.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_payment_idempotency_flow.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_payment_idempotency_flow.pdf) |
| `commerce_orchestration_notification_outbox_processing_claim_flow` | notification/outbox `PROCESSING` claim 흐름 | [draw.io](/docs/diagrams/source/commerce_orchestration_notification_outbox_processing_claim_flow.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_notification_outbox_processing_claim_flow.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_notification_outbox_processing_claim_flow.pdf) |
| `commerce_orchestration_outbox_publisher_adapter` | `OutboxPublisherService`와 `KafkaOutboxEventPublisher` 분리 구조 | [draw.io](/docs/diagrams/source/commerce_orchestration_outbox_publisher_adapter.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_outbox_publisher_adapter.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_outbox_publisher_adapter.pdf) |

## Reference Assets

| Diagram | Description | Source |
|---|---|---|
| `commerce_orchestration_overall_architecture_reference` | overall architecture 작성 참고용 reference | [draw.io](/docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio) / [PNG](/docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png) / [PDF](/docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf) |
