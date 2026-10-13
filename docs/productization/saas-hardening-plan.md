# SaaS Hardening / 미완성 경계 보강 계획

- Base: `a51487f`, 2026-10-12 (Asia/Seoul)
- 사용자 결정: IdP 미선정, 후보 비교·연결 설계만 진행. 계정 생성/설치/계약 별도 승인.
- 사용자 결정: 기존 데이터 보존·legacy 격리, 신규 SaaS 거래부터 tenant 필수.
- 상태: HB1 완료, HB2 V9/legacy runtime guard 구현. HB3 tenant writer/IdP/복구 기능은 Planned이며 출시 완료 아님.

## Audit / 현재 결합과 공백

| 영역 | 현재 근거·공백 | 먼저 보강할 내용 |
|---|---|---|
| 인증 | JWT/JWKS fixture 통과, 실제 제공자 미연결 | token-kind/issuer/audience 계약, dev realm 검증, provisioning |
| 거래 소유권 | V1의 orders/payments/settlements/notification_events/outbox_events/orchestration_steps/audit_logs에 tenant 없음 | legacy 격리 + owner 전파와 DB/API/worker negative tests |
| 이벤트 결합 | orchestration이 topic과 JSON 생성, publisher port가 JPA entity 노출 | typed intent + outbox 직렬화 + immutable publish snapshot (이번 구현) |
| 결제 | approve replay 조회/unique, mock timeout unknown 기록 | 동시 최초 요청의 provider 중복 호출 방지, durable intent/PSP idempotency, confirmation |
| 보상 | settlement 실패 후 cancel; cancel 실패 자체의 복구 모델 부족 | compensation attempt 상태/재확인/운영 hold, 확정 전 중복 환불 금지 |
| 알림 | demo 채널과 공통 retry/manual/ignore | 실제 채널 adapter, merchant별 설정/receipt/quiet-hours |
| worker | PROCESSING claim, 자동 lease recovery 없음 | tenant별 정책 + 만료 lease/heartbeat/중복 외부 효과 방어 |
| 운영 | counter/log/audit 존재, dashboard/alert/backup rehearsal 없음 | SLO/비용 baseline, bounded 알림, 복원 연습 |

각 모듈은 자기 entity/repository/state를 소유합니다. 신규 merchant 확인은 ingress/정책 경계에서 하며
payment/notification/outbox가 모두 security나 merchant repository를 참조하도록 만들지 않습니다.
공개 계약에는 검증된 owner와 versioned intent를 명시적으로 전달하고 자체 ThreadLocal tenant context는 만들지 않습니다.
tenant ID/actorId를 metric tag로 사용하지 않습니다.

## Delivery Order / 우선순위와 완료 기준

1. **HB1 Outbox boundary** (이번 unit): orchestration→infrastructure 의존 제거, entity named interface 폐쇄,
   typed event 생성/JSON 직렬화와 immutable adapter 계약. 기존 topic/key/payload 의미와 retry/dead-letter 유지.
2. **HB2 Ownership design + migration slice**: 데이터 inventory/정합성 검사, legacy/tenant 상태 모델과 forward migration.
   기존 row를 보존하고 임의 merchant backfill/삭제를 하지 않음. 미할당 데이터는 tenant query에서 제외.
3. **HB3 Tenant order vertical slice**: owner-aware order/payment/settlement/notification/outbox/audit/step 계약과
   worker/admin recovery 경계까지 연결. 이것이 완료되기 전 SaaS 주문 생성 API를 공개하지 않음.
4. **HB4 Identity onboarding**: IdP 선택·dev 연결 승인 후 실제 access token negative tests,
   membership 생성/권한 변경/회수 감사 및 마지막 운영자 보호. caller role/email로 자동 권한 부여 금지.
5. **HB5 Financial reliability**: durable payment intent와 provider idempotency, timeout 확인,
   compensation 실패/중복 요청/부분 환불 정책. 실제 PG 계약은 별도 승인.
6. **HB6 Channel/recovery operability**: 첫 실제 알림 채널, tenant 설정, lease recovery,
   dashboard/alert와 backup/restore rehearsal. inventory/shipping 신규 기능은 이후 roadmap 단계.

각 단위는 별도 `codex/dev-*` branch/PR, 전체 회귀/정량 보고, CI와 main 확인을 유지합니다.
필요한 module 계약을 확장하되 별도 서비스·이벤트 프레임워크·SDK를 근거 없이 추가하지 않습니다.

## Tenant Migration / 단계별 구현 경계

[HB2 확정 설계](tenant-ownership-migration.md)의 V9 ownership 제약과 legacy ORM/worker guard를 구현합니다.
아래 내용 중 API owner 전파·tenant event·전체 worker/admin 경계는 HB3에서 구현해야 합니다.

- migration 후보: 명시적인 ownership mode(LEGACY/TENANT)와 merchant owner. legacy는 owner 미할당,
  TENANT 신규 거래는 non-null owner/merchant FK 필수라는 CHECK 제약. nullable owner만 추가해 신규 누락을 허용하지 않음.
- HB2는 V9 forward migration이며 V1~V8은 변경하지 않습니다. 기존 row는 LEGACY/NULL로 보존합니다.
- 주문 owner는 인증 후 membership 권한과 store 소속을 확인한 ingress에서 확정. body/header owner를 신뢰하지 않음.
- 모든 child row는 부모 order와 같은 owner. 후보 composite FK `(merchant_id, order_id)`와 parent unique로
  cross-tenant 참조를 DB에서 방어. 현재 legacy orphan/nullable audit order부터 조사하고 무조건 FK를 추가하지 않음.
- 공개 조회/변경 계약은 owner + aggregate ID 필수. legacy 전역 조회는 별도 demo/운영 경계에 남기며 OIDC 접근 금지 유지.
- legacy 이벤트 payload는 그대로 보존. 새 tenant envelope는 schemaVersion/merchantId/eventId를 포함하고
  row/envelope owner 일치 확인. 기존 v1을 자동 tenant 변환하지 않음.
- scheduler/retry/dead-letter/admin 경로도 owner-aware. production에서 legacy worker의 무제한 전역 처리를 허용하지 않음.
- migration 검증: 기존 row count/내용 보존, legacy 조회 제외, 신규 tenant 누락 거부, A→B API/query/worker 차단,
  부모-child mismatch/동시 요청/replay 거부, 새 DB·기존 V8 upgrade 모두 확인.
- 기존 binary가 새 tenant row를 전역 조회하는 rolling upgrade/rollback은 안전하지 않음.
  새 tenant 쓰기 전 old writers/readers를 격리하고, 쓰기 이후에는 검증된 forward-fix를 기본으로 함.

## IdP Candidates / 제공자 계약 비교

비교는 공개 문서 기준이며 실제 token/계정 검증이나 비용 견적이 아닙니다. 제공자는 아직 확정하지 않습니다.

| 후보 | 현재 decoder 계약과의 관계 | 운영 검토 |
|---|---|---|
| Auth0 | RFC 9068 profile의 at+jwt를 문서화. 기본 Auth0 profile JWT와 구분 필요 | API profile/RS256/audience/user subject와 dev 계정 검증을 먼저. managed 운영·요금/제약 별도 확인 |
| Amazon Cognito | token_use=access 및 resource audience 계약을 확인해야 함. 현재 at+jwt-only decoder와 그대로 호환된다고 주장하지 않음 | AWS 운영 환경/region, user-pool client와 resource binding, 제공자별 validator 변경 승인 필요 |
| Keycloak | OIDC discovery/JWKS endpoint 제공. 선택 버전/realm의 typ와 API audience 검증 필요 | 별도 배포·업데이트·backup·장애 대응·운영 비용 검토. 이번 Compose에 설치하지 않음 |

Auth0 RFC 9068 profile은 **현재 token-kind 계약 검증을 시작하기 좋은 후보라는 추론**이지 선정 결정이 아닙니다.
MAU/관리 시간/region/계정 복구/MFA/키 rotation SLA/예산을 확인하기 전 저렴함·무운영·무료 제품으로 단정하지 않습니다.
월 비용은 인증 사용자 수, 계정/organization 기능, 이메일/SMS/MFA, 자체 운영 시간까지 포함해 비교해야 합니다.

근거:

- [Auth0 access token profiles](https://auth0.com/docs/secure/tokens/access-tokens/access-token-profiles): RFC 9068와 기본 profile의 typ/claim 차이.
- [Cognito access token](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-access-token.html): token_use/access token 계약.
- [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers): discovery/JWKS 연결 경계.

연결 승인 이후 공개 issuer/audience/JWKS를 환경 설정으로 넣고, 실제 dev access token은 보호된 client에서만 사용합니다.
secret/private key/token을 Git/보고서에 첨부하지 않습니다. 유효 token + membership 성공, ID token/wrong issuer/audience,
revoke/정지/다른 tenant 거부와 JWKS 장애/rotation을 확인해야 실제 연결 검증 완료라고 기록합니다.

## HB1 Contract Decision / 이번 구현

- `OutboxApplication.appendOrderEvent(orderId, OrderEventType)`는 business intent만 받음.
- topic mapping과 JSON 직렬화는 outbox 소유. caller가 임의 topic/type/payload 조합을 만들지 않음.
- `OutboxEventPublisher.publish(OutboxPublishMessage)`는 immutable 값만 받으며 entity/state 변경 권한 없음.
- `orchestration`의 infrastructure allowed dependency, `infrastructure`의 outbox::entity 의존과 entity named interface 제거.
- 내부 Java source contract 변경: repository의 모든 호출자를 같은 PR에서 갱신. 외부 배포된 SDK 호환성 주장은 없음.
- HTTP/OpenAPI path, 기존 저장 row/topic/key/payload 의미는 미변경. 실제 tenant envelope/새 provider/자동 recovery는 추가하지 않음.
- 예상 검증: enum 두 event의 기존 JSON 의미, invalid 입력/serialization failure 시 저장 없음,
  adapter key/payload 유지 및 failure mapping, 기존 Kafka happy/retry/dead-letter/Modulith 전체 회귀.

전체 남은 작업·트레이드오프·출시 기준은 [Product Readiness Review](product-readiness-review.md)를 참고합니다.
