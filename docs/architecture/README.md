# Architecture Notes

현재 모듈 경계, 의존 방향, 테이블 관계를 정리한 문서입니다.

이 프로젝트의 architecture 핵심은 주문 이후 흐름을 여러 service가 서로 직접 얽히는 구조로 만들지 않고, `CommerceOrchestrationService`가 후속 처리 흐름을 제어하되 각 domain은 자기 상태와 저장소를 소유하도록 나누는 것입니다.

## 1. Architecture Intent

- order는 주문 생성과 상태 전이를 소유합니다.
- orchestration은 payment / settlement / notification / outbox / audit 흐름을 조합하되, 각 domain repository를 직접 주입하지 않습니다.
- payment는 provider 연동 추상화와 승인/취소 기록을 담당합니다.
- settlement는 정산 요청 기록과 실패 지점을 담당합니다.
- notification은 알림 요청, 실패 정책, retry 상태를 담당합니다.
- outbox는 후속 이벤트 저장, publish, retry, dead-letter 전환을 담당합니다.
- audit은 분기, 실패, 재처리 지점을 추적합니다.
- admin은 전체 orchestration 재실행이 아니라 실패한 하위 처리 단위의 명시적 복구 API를 제공합니다.

## 2. Dependency Direction

권장 의존 방향은 아래와 같습니다.

- `controller -> facade/service -> repository`
- `orchestration -> domain application contract`
- 다른 domain의 repository를 직접 주입해서 흐름을 제어하지 않습니다.
- repository 패키지를 외부에 공개하는 방식보다 `*.api` 공개 계약을 통해 협력하는 방식을 우선합니다.
- Spring Modulith의 `@NamedInterface("api")`와 `allowedDependencies`를 통해 module boundary를 명시합니다.

## 3. Current Assets

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_overall_architecture.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_overall_architecture.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_overall_architecture.pdf)

## 4. Reference Assets

- [reference draw.io](/docs/diagrams/source/commerce_orchestration_overall_architecture_reference.drawio)
- [reference PNG](/docs/diagrams/png/commerce_orchestration_overall_architecture_reference.png)
- [reference PDF](/docs/diagrams/pdf/commerce_orchestration_overall_architecture_reference.pdf)

## 5. Current Scope

- controller
- orchestration
- payment / settlement / notification
- outbox
- audit
- admin
- common

## 6. Table Relation Overview

- [draw.io 원본](/docs/diagrams/source/commerce_orchestration_table_relation_overview.drawio)
- [PNG 이미지](/docs/diagrams/png/commerce_orchestration_table_relation_overview.png)
- [PDF 문서](/docs/diagrams/pdf/commerce_orchestration_table_relation_overview.pdf)

이 다이어그램은 `src/main/resources/db/migration` 아래 Flyway migration을 source of truth로 삼아 현재 테이블 구조를 요약한 logical relation overview입니다.

실제 FK constraint를 새로 추가한 것이 아니라, 대부분 `order_id`를 기준으로 `orders`와 연결되는 운영/상태 추적 구조를 보여줍니다.

## 7. Why This Matters

커머스 거래 흐름에서는 결제, 정산, 알림, 이벤트 발행이 모두 같은 실패 의미를 가지지 않습니다.

따라서 architecture의 목표는 모든 후속 처리를 하나의 거대한 service에 밀어 넣는 것이 아니라, orchestration은 흐름을 제어하고 각 domain은 자기 상태와 복구 기준을 소유하도록 분리하는 것입니다.

이 구조를 통해 실패 지점을 추적하고, 재시도/보상/수동 개입 대상을 하위 처리 단위로 좁힐 수 있습니다.