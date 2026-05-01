# AI-assisted Development & Verification

## Purpose / 목적

이 문서는 이 프로젝트에서 AI Agent를 단순 코드 생성기가 아니라, 반복 구현 초안, 테스트 보강 아이디어, 문서 초안, 정합성 점검을 돕는 productivity tool로 활용했다는 점을 설명합니다.

백엔드 문제 정의, architecture boundary, 구현 범위, 검증 기준은 개발자가 직접 통제했습니다. 이 프로젝트는 AI가 무비판적으로 생성한 결과물이 아니라, 명시적인 작업 규칙 아래에서 code, tests, docs, OpenAPI, claim consistency를 함께 검토하며 정리한 포트폴리오 프로젝트입니다.

## Human-owned Decisions / 개발자가 직접 통제한 결정

- Domain problem definition: 일반 CRUD 기능 수가 아니라 주문 이후 flow reliability를 중심 문제로 정의했습니다.
- Order-after-flow focus: payment, settlement, notification, outbox, audit, admin recovery를 주요 흐름으로 선택했습니다.
- State transition policy: order, payment, settlement, notification, outbox 상태 전이가 명시적으로 드러나야 한다는 기준을 유지했습니다.
- Failure branching criteria: payment, settlement, notification, outbox 실패를 같은 오류로 뭉개지 않고 서로 다른 recovery policy로 분리했습니다.
- Retry and dead-letter criteria: retryable failure, manual intervention, ignored failure, dead-letter 상태를 구분했습니다.
- Admin recovery responsibility boundary: Admin API는 전체 orchestration을 재실행하지 않고 실패한 하위 처리 단위를 복구하도록 제한했습니다.
- Spring Modulith boundary decisions: 모듈 간 협력은 직접 repository 접근이 아니라 public `*.api` contract를 통해 수행하도록 유지했습니다.
- Implemented vs Future Scope boundary: 구현되지 않은 흐름은 Future Scope 또는 Not Implemented로 문서화했습니다.
- Documentation claim discipline: README, docs, OpenAPI의 claim이 code와 tests를 앞서가지 않도록 점검했습니다.

## AI-assisted Scope / AI Agent를 보조적으로 활용한 범위

- 이미 정의된 동작에 대한 반복 implementation draft 작성 보조
- edge case와 regression coverage를 위한 test case draft 작성 보조
- architecture, flows, runbooks, OpenAPI 관련 documentation draft 작성 보조
- implemented APIs only 원칙에 맞춘 OpenAPI draft 작성 보조
- README, docs, tests, OpenAPI 변경 영향 checklist 작성 보조
- Controller mapping, tests, docs를 비교한 documentation-code mismatch 탐지 보조
- Verification Matrix와 Claim Audit 초안 작성 보조

## Verification Rules / 검증 규칙

- 변경을 수용하기 전에 production code diff를 검토합니다.
- 작업 후 unit tests를 실행합니다.
- Docker/Testcontainers가 사용 가능하거나 integration behavior가 영향을 받는 경우 integration tests를 실행합니다.
- PostgreSQL/Kafka 동작이 claim의 일부라면 Testcontainers 기반 검증을 우선합니다.
- Controller endpoint mapping과 OpenAPI paths를 비교합니다.
- README/docs claim과 code/tests 근거를 비교합니다.
- 구현되지 않은 기능은 implemented claim에서 제외합니다.
- 실제로 실행하지 않은 tests를 passed로 표시하지 않습니다.
- Admin recovery API는 명시적 breaking change가 문서화되지 않는 한 no-body backward compatibility를 유지합니다.
- Spring Modulith boundary와 CI test 의미를 약화하지 않습니다.

## Examples from This Project / 이 프로젝트의 적용 예시

- Admin recovery request body와 OpenAPI consistency: `operatorId`와 `reason`은 optional이며, 기존 no-body 호출은 계속 유효합니다. OpenAPI도 optional request body로 이를 반영합니다.
- Retry and ignore audit traceability: admin notification retry, notification ignore, outbox dead-letter retry는 operator context를 bounded audit detail로 남깁니다.
- Notification retry batch response claim cleanup: `POST /api/admin/notification-events/retry-due`는 구현과 테스트에 맞게 batch result를 직접 반환하는 것으로 문서화했습니다.
- ApiDog import verification: `docs/openapi/openapi.yaml`은 spec 작성 후 ApiDog에 manual import로 확인했습니다. 별도 언급이 없는 한 automated validation은 YAML syntax와 repository verification commands 범위로 제한됩니다.
- OpenAPI and Controller consistency: OpenAPI paths는 구현된 Controller endpoint와 맞는 범위만 포함하도록 관리합니다.
- Kafka consumer-based state transition: 현재 구현은 Outbox publisher adapter를 통해 publish result 기반 상태 전이를 수행하며, Kafka consumer 기반 상태 전이는 Future Scope / Not Implemented로 남깁니다.
- Prometheus/Grafana dashboard: Micrometer counters와 structured logs는 존재하지만, dashboard와 alert rules는 Not Implemented로 남깁니다.
- Provider callback과 WebClient timeout confirmation: 현재 구현 범위에서 provider callback flow와 post-timeout confirmation flow는 Future Scope / Not Implemented로 유지합니다.

## What This Document Does Not Claim / 이 문서가 주장하지 않는 것

- AI Agent가 최종 설계 판단이나 검증 책임을 대신했다는 뜻이 아닙니다.
- 모든 코드가 AI로 자동 생성되었다는 뜻이 아닙니다.
- 실행하지 않은 tests를 통과했다고 주장하지 않습니다.
- Prometheus/Grafana dashboard, alert rules, Kafka consumer-based state transition, stale `PROCESSING` automatic recovery job을 implemented로 주장하지 않습니다.
- Refresh token, key rotation, real user store, provider callback flow, WebClient timeout confirmation flow가 구현되었다고 주장하지 않습니다.
