# Docs Index

README가 첫 화면 요약을 담당한다면, `docs`는 "설계 의도", "검증 범위", "운영 참고"를 분리해서 보여주기 위한 보조 문서 모음입니다.

## 1. Recommended Order

처음 보는 사람 기준 권장 읽기 순서는 아래입니다.

- [Architecture Notes](architecture/README.md)
  현재 모듈 경계, 의존 방향, architecture/table diagram이 붙을 자리
- [Flow Notes](flows/README.md)
  happy path, failure path, admin reprocessing 흐름 요약
- [Design Notes](design-notes.md)
  왜 이런 구조를 택했는지와 아직 남은 TODO
- [Test Report](test-report.md)
  현재 실제로 통과시키는 로컬 검증 기준과 workflow 범위
- [Troubleshooting](troubleshooting.md)
  로컬 실행, 인프라, Testcontainers, profile mismatch 점검 포인트

## 2. Supporting Notes

- [SQL Guide](sql/README.md)
  운영 점검과 수동 복구용 SQL
- [Diagram Guide](diagrams/README.md)
  추후 draw.io 자산을 어떤 이름과 설명 기준으로 둘지 정리한 안내

## 3. Diagram Placeholders

이 단계에서는 draw.io 기반 자산을 새로 만들지 않았습니다.  
대신 아래 문서가 추후 시각 자료가 들어올 위치를 미리 분리합니다.

- `architecture/`
  overall architecture, module relation, table relation overview
- `flows/`
  order lifecycle, outbox retry/dead-letter, notification retry/manual intervention flow
- `diagrams/`
  파일 네이밍, 설명 문장, 원본/PNG/PDF 관리 기준
