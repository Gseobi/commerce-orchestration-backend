## Delivery unit

- Module:
- Scope:
- Base branch: `main`
- Related roadmap phase:

## What changed

-

## Boundaries and claims

- [ ] Spring Modulith boundary is preserved.
- [ ] Documentation and OpenAPI describe only implemented behavior.
- [ ] Tenant/security/PII impact was reviewed.
- [ ] No secret or token is logged or committed.
- [ ] Metrics do not use high-cardinality business identifiers as tags.

## Verification

- [ ] `git diff --check`
- [ ] `./gradlew compileJava`
- [ ] `./gradlew test`
- [ ] `docker compose ps`
- [ ] `./gradlew integrationTest --rerun-tasks`
- [ ] PostgreSQL/Kafka health checked when relevant
- [ ] CI checks passed

Commands and results:

```text

```

## Failure and recovery scenarios

-

## Remaining risks / follow-up

-
