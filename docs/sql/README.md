# SQL Guide

## 소스 오브 트루스

실제 DB 스키마의 소스 오브 트루스는 `src/main/resources/db/migration` 아래 Flyway migration입니다.

- `V1__init.sql`
  현재 엔티티 기준 초기 스키마
- `V2__outbox_retry_dead_letter.sql`
  outbox retry/backoff/dead-letter 컬럼 및 인덱스 추가

`docs/sql`은 운영 확인과 수동 점검을 돕기 위한 참고 문서입니다.

## 현재 문서 구성

- [outbox-retry-dead-letter-ddl.sql](outbox-retry-dead-letter-ddl.sql)
  outbox 변경분만 따로 확인하기 위한 참고용 DDL
- [outbox-operations.sql](outbox-operations.sql)
  상태 확인, retry 대상 확인, dead-letter 확인, 수동 재처리 예시

## 적용 방식

- 애플리케이션 기본/로컬/통합 테스트 프로필은 Flyway를 자동 실행합니다.
- 단위 테스트용 `test` 프로필은 H2 `create-drop`을 유지하고 Flyway를 비활성화합니다.
