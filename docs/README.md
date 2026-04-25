# Docs Index

`docs`는 commerce-orchestration-backend의 설계 의도, 처리 흐름, 다이어그램, 테스트, 운영 참고 자료를 정리한 문서 모음입니다.

이 프로젝트는 주문 이후 payment · settlement · notification · outbox publish 흐름을 단순 기능 호출이 아니라, 상태 전이·실패 분기·보상 처리·재처리 경로가 보이는 orchestration 구조로 설명하는 것을 목표로 합니다.

최근 reliability hardening 범위에서는 `paymentRequestId` 기반 결제 멱등성, notification/outbox `PROCESSING` claim, Outbox publisher adapter 분리가 추가되었습니다. 자세한 설계와 검증 결과는 Architecture Notes, Flow Notes, Test Report에 나누어 정리합니다.

## 1. Recommended Order

처음 보는 사람 기준 권장 읽기 순서는 아래입니다.

1. [Architecture Notes](/docs/architecture/README.md)  
   모듈 경계, 의존 방향, 테이블 관계와 publisher adapter / DB claim 설계를 먼저 확인합니다.
2. [Flow Notes](/docs/flows/README.md)  
   주문 이후 payment / settlement / notification / outbox 흐름, 멱등성 replay, retry/publish claim 상태 전이를 확인합니다.
3. [Diagram Guide](/docs/diagrams/README.md)  
   draw.io 원본, PNG, PDF 자산을 확인합니다.
4. [Test Report](/docs/test-report.md)  
   실제로 검증한 범위, reliability hardening 테스트 결과, 아직 검증하지 않은 범위를 구분합니다.
5. [Troubleshooting](/docs/troubleshooting.md)  
   로컬 실행, 인증, Flyway, Testcontainers, retry/dead-letter 문제를 확인합니다.

## 2. Supporting Notes

- [Design Notes](/docs/design-notes.md)  
  현재 구조를 왜 이렇게 나눴는지, compensation / notification policy / outbox reliability / DB claim 기준을 정리합니다.
- [SQL Guide](/docs/sql/README.md)  
  Flyway migration과 운영 점검용 SQL 문서의 역할을 구분합니다.

## 3. Diagram Status

현재 포함된 자산:

- overall architecture
- overall architecture reference
- order orchestration flow
- outbox retry / dead-letter flow
- notification retry / manual intervention flow
- table relation overview

권장 읽기 흐름은 architecture에서 모듈/테이블 관계를 먼저 보고, 
flows에서 order / outbox / notification recovery를 확인한 뒤, Diagram Guide에서 source / PNG / PDF 자산을 직접 여는 순서입니다.
