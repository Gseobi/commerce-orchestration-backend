# Docs Index

`docs`는 commerce-orchestration-backend의 설계 의도, 처리 흐름, 다이어그램, 테스트, 운영 참고 자료를 정리한 문서 모음입니다.

이 프로젝트는 주문 이후 payment · settlement · notification · outbox publish 흐름을 단순 기능 호출이 아니라, 상태 전이·실패 분기·보상 처리·재처리 경로가 보이는 orchestration 구조로 설명하는 것을 목표로 합니다.

최근 reliability hardening 범위에서는 `paymentRequestId` 기반 결제 멱등성, notification/outbox `PROCESSING` claim, Outbox publisher adapter 분리가 추가되었습니다. 자세한 설계와 검증 결과는 Architecture Notes, Flow Notes, Diagram Guide, Test Report에 나누어 정리합니다.

## 1. Recommended Order

처음 보는 사람 기준 권장 읽기 순서는 아래입니다.

1. [Architecture Notes](/docs/architecture/README.md)  
   모듈 경계, 의존 방향, 테이블 관계와 publisher adapter / DB claim 설계를 먼저 확인합니다.
2. [Flow Notes](/docs/flows/README.md)  
   주문 이후 payment / settlement / notification / outbox 흐름, 멱등성 replay, retry/publish claim 상태 전이를 확인합니다.
3. [Design Notes](/docs/design-notes.md)
   compensation, notification policy, outbox reliability, DB 상태 기반 claim을 선택한 이유를 확인합니다.
4. [Reliability Hardening Diagrams](/docs/diagrams/README.md#reliability-hardening-diagrams)
   paymentRequestId 기반 결제 멱등성, notification/outbox `PROCESSING` claim, Outbox publisher adapter 분리 흐름과 draw.io 원본, PNG, PDF 자산을 확인합니다.
5. [Test Report](/docs/test-report.md)
   실제로 검증한 범위, reliability hardening 테스트 결과, 아직 검증하지 않은 범위를 구분합니다.
6. [Verification Matrix](/docs/verification-matrix.md)
   구현 위치, 테스트 커버리지, 문서 위치, 현재 상태를 한 표로 대조합니다.
7. [AI-assisted Development & Verification](/docs/ai-assisted-development.md)
   AI Agent 활용 범위와 개발자 주도 검증 기준을 확인합니다.
8. [Claim Audit](/docs/verification/claim-audit.md)
   주요 포트폴리오 claim이 코드, 테스트, 문서 어디에 근거하는지 확인합니다.
9. [OpenAPI / ApiDog](/docs/openapi/README.md)
   구현된 HTTP API만 포함한 ApiDog import-ready OpenAPI 파일을 확인합니다.
10. [Admin Recovery Runbook](/docs/runbooks/admin-recovery-runbook.md)
   notification retry, outbox dead-letter, `PROCESSING` 장기 체류를 metric/log/SQL/admin API 기준으로 확인하는 운영 복구 절차입니다. 관련 observability/recovery diagram은 [Diagram Guide](/docs/diagrams/README.md)에서 확인할 수 있습니다.
11. [Troubleshooting](/docs/troubleshooting.md)
   로컬 실행, 인증, Flyway, Testcontainers, retry/dead-letter 문제를 확인합니다.

## 2. Supporting Notes

- [Design Notes](/docs/design-notes.md)  
  현재 구조를 왜 이렇게 나눴는지, compensation / notification policy / outbox reliability / DB claim 기준을 정리합니다.
- [SQL Guide](/docs/sql/README.md)  
  Flyway migration과 운영 점검용 SQL 문서의 역할을 구분합니다.
- [Verification Matrix](/docs/verification-matrix.md)
  README/docs의 구현 주장과 실제 코드/테스트 위치를 빠르게 대조합니다.
- [AI-assisted Development & Verification](/docs/ai-assisted-development.md)
  AI Agent를 생산성 도구로 사용한 범위와 개발자 주도 검증 기준을 정리합니다.
- [Claim Audit](/docs/verification/claim-audit.md)
  README/docs의 주요 claim이 실제 코드, 테스트, 문서 어디에 근거하는지 정리합니다.
- [OpenAPI / ApiDog](/docs/openapi/README.md)
  `docs/openapi/openapi.yaml` import와 local server 사용 기준을 정리합니다.
- [Admin Recovery Runbook](/docs/runbooks/admin-recovery-runbook.md)  
  notification retry, outbox dead-letter, admin recovery 실패를 metric/log/SQL/admin API 기준으로 점검하는 절차입니다.

## 3. Diagram Status

현재 포함된 자산:

- overall architecture
- overall architecture reference
- order orchestration flow
- outbox retry / dead-letter flow
- notification retry / manual intervention flow
- table relation overview
- reliability hardening overview
- payment idempotency flow
- notification / outbox processing claim flow
- outbox publisher adapter

권장 읽기 흐름은 architecture에서 모듈/테이블 관계를 먼저 보고, 
flows에서 order / outbox / notification recovery와 reliability hardening flow를 확인한 뒤, Diagram Guide에서 source / PNG / PDF 자산을 직접 여는 순서입니다.
