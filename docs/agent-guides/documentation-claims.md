# Documentation Claims

## Implementation Truthfulness / 구현 범위 정직성

문서에는 실제 구현된 것만 Implemented처럼 작성합니다.
문서가 코드보다 앞서가면 안 됩니다.

다음 표현은 명확히 구분합니다.

- Implemented / 구현됨
- Verified / 테스트로 검증됨
- Future Scope / 후속 범위
- Planned / 계획
- Not Implemented / 미구현
- Extension Point / 확장 지점
- Known Limitation / 알려진 한계
- Design Decision / 설계 판단
- TODO / 후속 작업

테스트로 검증되지 않은 항목은 Verified로 쓰지 않습니다.
코드와 테스트로 확인되지 않은 항목은 Implemented로 표현하지 않습니다.

## Overclaim 금지

다음 항목은 코드와 테스트로 확인되지 않았다면 Implemented로 표현하지 않습니다.

- Prometheus / Grafana dashboard
- Alert rules
- Kafka consumer-based state transition
- Stale `PROCESSING` automatic recovery job
- Refresh token / key rotation / real user store
- Real external payment provider production integration
- Payment provider callback flow
- WebClient timeout confirmation flow
- Notification channel-specific retry policy
- Dead-letter automation beyond implemented admin/manual APIs

Prometheus/Grafana dashboard, Kafka consumer 기반 상태 전이, provider callback, timeout confirmation은 현재 구현 여부와 검증 여부를 문서마다 동일하게 유지해야 합니다.
구현되지 않은 내용은 Future Scope, Design Note, Not Implemented, Extension Point 중 하나로 명확히 표시합니다.

## Consistency Rule / 문서 정합성

코드 변경이 동작이나 설계 의미를 바꾸면 관련 문서도 같은 작업에서 갱신합니다.
특히 아래 문서는 claim consistency 기준입니다.

- `README.md`
- `docs/README.md`
- `docs/architecture/README.md`
- `docs/flows/README.md`
- `docs/design-notes.md`
- `docs/implementation-review-notes.md`
- `docs/test-report.md`
- `docs/troubleshooting.md`
- `docs/runbooks/admin-recovery-runbook.md`
- `docs/verification-matrix.md`
- `docs/verification/claim-audit.md`
- `docs/openapi/openapi.yaml`
- `docs/sql/*`
- `docs/diagrams/*`

README, docs, test-report, verification-matrix, claim-audit는 서로 다른 claim을 만들지 않아야 합니다.
한 문서에서 Future Scope로 표시한 내용을 다른 문서에서 구현 완료처럼 쓰지 않습니다.

## Security Claims / 보안 표현

- demo token 발급 기능은 production auth로 오해되지 않게 문서화합니다.
- real user store, refresh token, key rotation이 구현되지 않았다면 Future Scope 또는 Not Implemented로 표시합니다.
- request/response example에는 실제 secret, token, private value를 넣지 않습니다.
- authorization header, token, secret, raw payload는 log/audit에 남긴다고 표현하지 않습니다.
