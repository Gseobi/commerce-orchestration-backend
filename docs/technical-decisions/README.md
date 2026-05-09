# Technical Decisions

이 디렉터리는 commerce orchestration 구조에서 중요한 기술 선택을 기능 목록이 아니라 선택 이유와 trade-off 중심으로 정리합니다.

현재 프로젝트는 기능 수보다 상태 전이, 실패 분기, 보상 처리, 재처리, 검증 근거를 설명하는 데 초점을 둔다. Technical Decision 문서는 그 설명을 리뷰어가 빠르게 따라갈 수 있도록 코드 위치, 테스트 근거, 구현 경계와 함께 묶습니다.

## Decision Records

- [Orchestration Reliability Decision Record](orchestration-reliability-decision-record.md)
  - orchestration service, explicit state transition, settlement compensation, notification recovery, payment idempotency, retry/publish claim, outbox publisher adapter, observability 경계를 정리합니다.

## Relationship With Other Documents

- [README](/README.md)
  - 프로젝트의 문제 정의, 현재 구현 범위, 대표 검증 포인트를 요약합니다.
- [Design Notes](/docs/design-notes.md)
  - compensation, notification policy, outbox reliability, DB claim 같은 설계 선택을 더 세부적으로 설명합니다.
- [Verification Matrix](/docs/verification-matrix.md)
  - claim별 구현 위치, 테스트 위치, 문서 위치, 상태를 대조합니다.
- [Claim Audit](/docs/verification/claim-audit.md)
  - README/docs에서 말할 수 있는 claim과 말하면 안 되는 claim을 evidence 기준으로 점검합니다.
- [Technical Discussion Points](/docs/technical-discussion/commerce-orchestration-technical-discussion-points.md)
  - 같은 근거를 기술 검토 Q&A 형태로 재구성합니다.

## Scope Rule

이 문서들은 "무엇을 구현했는가"보다 "왜 그렇게 선택했는가"를 설명합니다.

문서에 적는 모든 선택 이유는 현재 코드, 테스트, 기존 문서 중 하나 이상의 근거를 가져야 합니다. 구현되지 않은 provider callback API, external provider confirmation request, admin confirmation API, Kafka consumer 기반 상태 전이, Prometheus/Grafana dashboard, stale `PROCESSING` automatic recovery job은 구현된 기능처럼 표현하지 않습니다.
