# Project Status / 현재 구현 기준선

- 점검일: 2026-10-12 (Asia/Seoul)
- 구현 기준: `a51487f`에서 시작한 outbox SaaS hardening delivery (이 문서와 같은 변경 단위)
- 제품화 단계: `P0 In Progress`. 실서비스 출시 완료가 아닙니다.

## Implemented / 구현된 범위

| 영역 | 현재 구현 | 아직 없는 범위 |
|---|---|---|
| 주문 이후 흐름 | payment/settlement/notification/outbox 상태 전이, 보상, retry/dead-letter, admin recovery | 상품/SKU/주문 라인, 실제 PG 환불·callback·confirmation |
| Merchant foundation | merchant/store, timezone, 기본 수동 복구 정책 snapshot, V7 | 영업 시간·휴일·사업자별 자동 복구 정책 |
| Membership | V8, VIEWER READ / OPERATOR READ·WRITE, revoke/정지 차단, OIDC 스토어 GET의 READ adapter | 가입·초대·membership 관리 API |
| OIDC security | RS256, 고정 JWKS, issuer/audience/claim/typ 검증, canonical actorId | 실제 IdP 연결, 로그인 UI/client, 즉시 token revoke |
| Tenant boundary | merchant-scoped store GET/repository 조회, 매 요청 membership 확인 | 기존 주문/결제/알림/outbox 및 event의 tenant ownership |
| 운영 | Micrometer counter, structured log, audit, SQL/runbook, Testcontainers | dashboard/alert rule, stale PROCESSING 자동 회수 |
| 제품 모듈 | 위 기반만 구현 | catalog/inventory, 실제 알림 채널, shipping/tracking, 비용/SLO 검증 |

`Verified`는 해당 테스트 시나리오가 통과했다는 의미입니다. 실제 provider 운영,
전체 multi-tenant API 격리, 장애 없는 운영이나 무취약성을 보장하지 않습니다.

## Authentication Modes / 인증과 접근 범위

| Mode | Token 발급·검증 | HTTP 접근 |
|---|---|---|
| `disabled` (기본) | demo 발급 bean 없음 | GET `/actuator/health`, `/error` 외 차단 |
| `demo` | 호출자가 username/role을 지정하는 데모 JWT | local/test/integration-test opt-in. 기존 주문 JWT, 전역 admin ADMIN 보호 |
| `oidc` | 외부 access token 검증만 수행. authority 자동 매핑 없음 | 단일 store GET은 active READ membership 필요. 유효 token도 legacy business/admin `403` |

`demo`는 production identity가 아닙니다. `prod`/`production` + demo는 startup 실패입니다.
OIDC에서 invalid/missing token은 protected 요청에 `401`이며, token의 role/scope/merchantId로
권한을 만들지 않습니다. OIDC 업무 경로는 [스토어 GET](productization/merchant-tenant-http-access.md) 하나뿐입니다.
외부 JWKS rotation은 fixture로 검증했지만 demo signing-key rotation은 미구현입니다.

## Local Verification / 재현 명령

Java 21과 Docker daemon이 필요합니다. Compose는 PostgreSQL `16-alpine`, Kafka `3.8.0`,
Kafka UI `v0.7.2`만 실행하며 애플리케이션은 별도 실행합니다.

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local APP_SECURITY_MODE=demo ./gradlew bootRun
```

`.env`는 Docker Compose 치환용이며 `bootRun`이 자동으로 로딩한다고 가정하지 않습니다.
실제 secret/token을 CLI 이력·보고서·Git에 복사하지 않습니다.

```bash
git diff --check
./gradlew --no-daemon --no-parallel test integrationTest --rerun-tasks
python3 scripts/test_report.py
docker compose ps
```

`test`는 non-integration tag suite이고 H2/mock 및 로컬 JWKS 검증 등을 포함합니다.
`integrationTest`는 별도 PostgreSQL/Kafka Testcontainers를 사용하므로 Compose DB의 데이터로
테스트하는 것이 아닙니다. XML suite time 합계는 wall time이나 부하 성능 수치가 아닙니다.
최신 실행 수치는 [Outbox SaaS 보강 보고서](verification/outbox-saas-hardening-test-report.md),
capability 근거는 [Verification Matrix](verification-matrix.md)에서 확인합니다.

## Next Unit / 다음 개발 단위

읽기 전용 merchant-scoped store HTTP slice와 membership adapter를 구현했습니다.
신규 기능 이전에 [SaaS Hardening](productization/saas-hardening-plan.md)을 우선합니다.
outbox 계약 결합을 완화했고 다음은 legacy 보존/격리와 신규 거래 owner 필수 적용 migration 설계·구현입니다.
실제 IdP는 후보 비교만 진행하며 선택·계정 생성·설치·운영 연결은 별도 승인입니다.
실제 계정/가입/초대와 거래 owner migration은 아직 구현하지 않았습니다.
P0 완료 전 catalog/inventory/payment/shipping 제품 모듈을 완료로 표현하지 않습니다.

## Document Lifecycle / 문서 수명

- 현재 상태: 이 문서, [Roadmap](productization/README.md), [OIDC 구현 범위](productization/oidc-resource-server.md).
- 검증 근거: Verification Matrix/Claim Audit와 delivery별 검증 보고서.
- 과거 기록: 보고서의 base/branch/count/time은 당시 snapshot이며 덮어쓰지 않습니다.
- 계획: 설계·review의 target flow와 테스트 계획은 구현 근거가 아닙니다.
- 기존 PNG/PDF/drawio는 orchestration 기준선이며 merchant/OIDC 전체 구조를 보여주지 않습니다.
- 외부 블로그·공식 문서 링크는 참고 자료입니다. 이번 감사는 외부 사이트의 최신성/응답을 검증하지 않습니다.
