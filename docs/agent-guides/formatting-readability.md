# Formatting & Readability

## Repository Readability / 가독성

이 레포지토리는 포트폴리오 프로젝트이므로 code와 docs가 리뷰 가능한 상태여야 합니다.

- Java, Gradle, YAML, SQL, Markdown 파일을 minified 또는 single-line 상태로 두지 않습니다.
- UTF-8을 사용합니다.
- LF line ending을 사용합니다.
- 파일 끝에는 final newline을 둡니다.
- Java indentation은 4 spaces를 기본으로 합니다.
- YAML indentation은 2 spaces를 기본으로 합니다.
- SQL migration은 statement와 logical block이 읽히도록 줄바꿈합니다.
- README와 docs의 heading/table/list 구조를 훼손하지 않습니다.

## Formatting-only Partition / 포맷 전용 작업

formatting-only partition은 behavior change를 포함하지 않습니다.

- 포맷 변경과 feature 변경은 가능하면 별도 commit으로 분리합니다.
- formatting recovery와 기능 변경을 섞지 않습니다.
- 포맷 전용 작업에서 endpoint path, SQL semantics, Gradle dependency, CI command, Docker Compose service 관계를 바꾸지 않습니다.
- binary diagram asset은 명시 요청 없이는 수정하지 않습니다.

보호 대상 예시:

- PNG
- PDF
- drawio
- 기타 binary/diagram asset

## `.editorconfig` Policy

`.editorconfig`는 repository formatting을 일관되게 유지하기 위한 기준입니다.
추가하거나 수정할 때는 기존 Java/Gradle/YAML/SQL/Markdown 스타일과 충돌하지 않게 합니다.

권장 기준:

- charset: `utf-8`
- end_of_line: `lf`
- insert_final_newline: `true`
- trim_trailing_whitespace: text/code 파일에서 `true`
- Java indent: 4 spaces
- YAML indent: 2 spaces

`.editorconfig` 추가는 formatting policy 변경이므로 문서화하고 검증 결과를 남깁니다.

## Verification / 검증

formatting-only 문서 변경은 documentation-only 기준을 따를 수 있습니다.
다만 formatting-only라도 code/config path를 touch하면 Gradle test와 Docker/integration verification 필요성을 판단해야 합니다.

code/config path를 touch한 경우 baseline은 다음을 우선 고려합니다.

```bash
git diff --check
./gradlew compileJava
./gradlew test
docker info
docker compose ps
./gradlew integrationTest --rerun-tasks
```

문서만 touch했고 executable behavior, configuration, Docker Compose, CI path, test path를 바꾸지 않았다면 Docker/Kafka/PostgreSQL runtime verification은 생략할 수 있습니다.
