# Docs Index

README가 첫 화면 요약을 담당한다면, `docs`는 "설계 의도", "검증 범위", "운영 참고"를 분리해서 보여주기 위한 보조 문서 모음입니다.

## 1. Recommended Order

처음 보는 사람 기준 권장 읽기 순서는 아래입니다.

- [Architecture Notes](docs/architecture/README.md)
  현재 모듈 경계, 의존 방향, architecture/table diagram이 붙을 자리
- [Flow Notes](docs/flows/README.md)
  happy path, failure path, admin reprocessing 흐름 요약
- [Design Notes](docs/design-notes.md)
  왜 이런 구조를 택했는지와 아직 남은 TODO
- [Test Report](docs/test-report.md)
  현재 실제로 통과시키는 로컬 검증 기준과 workflow 범위
- [Troubleshooting](docs/troubleshooting.md)
  로컬 실행, 인프라, Testcontainers, profile mismatch 점검 포인트

## 2. Supporting Notes

- [SQL Guide](docs/sql/README.md)
  운영 점검과 수동 복구용 SQL
- [Diagram Guide](docs/diagrams/README.md)
  `source / png / pdf` 기준의 다이어그램 자산 구조와 네이밍 규칙 안내

## 3. Diagram Placeholders

현재 다이어그램 자산은 `docs/diagrams/` 아래로 정리합니다.
문서에서는 아래 순서로 이어집니다.

- `architecture/`
  overall architecture와 table relation overview를 설명
- `flows/`
  order lifecycle, outbox retry/dead-letter, notification retry/manual intervention flow를 설명
- `diagrams/`
  실제 파일 경로는 `source/`, `png/`, `pdf/`로 분리해 관리
