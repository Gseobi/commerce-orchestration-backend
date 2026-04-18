# Architecture Notes

현재 모듈 경계와 의존 방향을 정리한 문서입니다.

## Current Assets

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_overall_architecture.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_overall_architecture.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf)

## Reference Assets

- [reference draw.io](/docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio)
- [reference PNG](/docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png)
- [reference PDF](/docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf)

## Current Scope

- controller
- orchestration
- payment / settlement / notification
- outbox
- audit

## Table Relation Overview

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_table_relation_overview.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_table_relation_overview.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_table_relation_overview.pdf)

이 다이어그램은 `src/main/resources/db/migration` 아래 Flyway migration을 source of truth로 삼아 현재 테이블 구조를 요약한 logical relation overview입니다.  
실제 FK constraint를 새로 추가한 것이 아니라, 대부분 `order_id`를 기준으로 `orders`와 연결되는 운영/상태 추적 구조를 보여줍니다.
