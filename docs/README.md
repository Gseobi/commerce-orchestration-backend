# Docs Index

README가 첫 화면 요약을 담당한다면, `docs`는 "설계 의도", "검증 범위", "운영 참고"를 분리해서 보여주기 위한 보조 문서 모음입니다.

## 1. Start Here

- [Architecture Notes](architecture/README.md)
  현재 모듈 경계와 추후 architecture/table diagram이 연결될 위치
- [Flow Notes](flows/README.md)
  happy path, failure path, admin reprocessing 흐름 요약
- [Test Report](test-report.md)
  현재 실제로 통과시키는 로컬 검증 기준과 workflow 범위
- [Troubleshooting](troubleshooting.md)
  로컬 실행, 인프라, Testcontainers, profile mismatch 점검 포인트

## 2. Supporting Notes

- [Design Notes](design-notes.md)
  구조 선택 이유와 아직 남겨 둔 TODO
- [SQL Guide](sql/README.md)
  운영 점검과 수동 복구용 SQL

## 3. Diagram Placeholders

이 단계에서는 draw.io 기반 자산을 새로 만들지 않았습니다.  
대신 아래 문서가 추후 시각 자료가 들어올 위치를 미리 분리합니다.

- `architecture/`
  시스템 구성도, 모듈 관계도, dependency direction table
- `flows/`
  order lifecycle, compensation, admin reprocessing flow
