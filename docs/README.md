# Docs Index

`docs`는 설계, 흐름, 테스트, 운영 참고 자료를 정리한 문서 모음입니다.

## 1. Recommended Order

- [Architecture Notes](/docs/architecture/README.md)
- [Flow Notes](/docs/flows/README.md)
- [Diagram Guide](/docs/diagrams/README.md)
- [Test Report](/docs/test-report.md)
- [Troubleshooting](/docs/troubleshooting.md)

## 2. Supporting Notes

- [Design Notes](/docs/design-notes.md)
- [SQL Guide](/docs/sql/README.md)

## 3. Diagram Status

현재 포함된 자산:
- architecture
- overall architecture reference
- order orchestration flow
- outbox retry / dead-letter flow
- notification retry / manual intervention flow
- table relation overview

권장 읽기 흐름은 architecture에서 모듈/테이블 관계를 먼저 보고, flows에서 order / outbox / notification recovery를 확인한 뒤, Diagram Guide에서 source / PNG / PDF 자산을 직접 여는 순서입니다.
