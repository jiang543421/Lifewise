# plan-shared-infra — TDD Evidence Report

> Generated: 2026-07-31
> Branch: `worktree-shared-infra`
> Commits (chronological): `6bbe74e` (RED) → `466b1b7` (GREEN) → `475ce81` (REFACTOR)

## 1. Source plan

- `docs/lifewise/planning/plan-shared-infra.md` — primary implementation plan
- `docs/lifewise/architecture/business-architecture.md` — module boundaries
- `docs/lifewise/architecture/data-model-v1.2-amendment.md` — V1–V35 schema
- `docs/lifewise/architecture/technical-architecture.md` — Spring Boot / Redis / Tomcat sizing
- `CLAUDE.md` §7 — security baseline (BCrypt strength, rate limit scopes, JWT rotation)

## 2. Scope implemented

Four横切 packages under `app/src/main/java/com/lifewise/shared/infra/`:

| Package | Purpose | Files |
|---|---|---|
| `security/` | JWT, password, annotations, exceptions | 9 files |
| `ratelimit/` | Redis token bucket, `@RateLimit` annotation, decision record | 5 files |
| `audit/` | `@Auditable` annotation, payload hasher, audit record | 3 files |
| `async/` | Bounded `ThreadPoolTaskExecutor` (`@EnableAsync`) | 1 file |

Out of scope (deferred to plan-auth / plan-shared-*-followup):
- `JwtAuthenticationFilter` (Spring Security FilterChain wiring)
- `RateLimitAspect` / `AuditAspect` / `AuthAspect` (AOP enforcement)
- `AuditWriter` (V26 `operation_logs` insert via JPA)
- Redis Lua token-bucket script (Redis client not yet on classpath)

## 3. RED → GREEN → REFACTOR cycle

### 3.1 RED — `6bbe74e` `test(infra): shared-infra RED baseline — 9 contract/unit tests`

| Test file | Tests | RED signal |
|---|---|---|
| `security/JwtTokenProviderTest.java` | 6 | compile error: `程序包 com.lifewise.shared.infra.security.exception 不存在` |
| `security/JwtRefreshTokenServiceContractTest.java` | 2 | compile error: `找不到符号 JwtRefreshTokenService` |
| `security/PasswordEncoderConfigTest.java` | 1 | compile error: `找不到符号 PasswordEncoderConfig` |
| `security/SecurityAnnotationContractTest.java` | 2 | compile error: `程序包 ...annotation 不存在` |
| `ratelimit/TokenBucketServiceTest.java` | 4 | compile error: `找不到符号 TokenBucketService` |
| `ratelimit/RateLimitContractTest.java` | 2 | compile error: `找不到符号 RateLimit` |
| `audit/AuditEntryTest.java` | 3 | compile error: `找不到符号 AuditEntry` |
| `audit/AuditableContractTest.java` | 1 | compile error: `找不到符号 Auditable` |
| `async/AsyncConfigTest.java` | 1 | compile error: `找不到符号 AsyncConfig` |

**RED validation command** (executed at commit time):
```
mvn -f app/pom.xml -q -DfailIfNoTests=false test
→ BUILD FAILURE (compile error: 21 unresolved symbols)
```

RED is compile-time. All 9 test files were successfully compiled-and-failed by the absence of production code; this is the intended RED signal per tdd-workflow §"Compile-time RED".

### 3.2 GREEN — `466b1b7` `feat(infra): shared-infra GREEN — security/ratelimit/audit/async production`

| Package | Production files | Method/field coverage |
|---|---|---|
| `security/JwtTokenProvider.java` | 1 | `createAccessToken`, `parseAccessToken`, `createRefreshToken`, `parseRefreshToken`, `AccessClaims`, `Decoded` (private) |
| `security/JwtRefreshTokenService.java` | 1 | `rotate`, `detectReuse`, `revokeFamily`, `parseClaims`, `RefreshResult`, `RefreshClaims` |
| `security/PasswordEncoderConfig.java` | 1 | `passwordEncoder()` (BCrypt strength 12) |
| `security/annotation/{RequireAuth, RequireRole}.java` | 2 | runtime annotations |
| `security/exception/{JwtExpired,JwtInvalid,ReuseDetected}Exception.java` | 3 | domain exceptions |
| `ratelimit/{RateLimit, RateLimits, TokenBucketScript, TokenBucketService, RateLimitDecision}.java` | 5 | repeat annotation, script SPI, fail-open service, decision record |
| `audit/{Auditable, AuditEntry, AuditPayloadHasher}.java` | 3 | `@Auditable`, V26-aligned record, SHA-256 hasher |
| `async/AsyncConfig.java` | 1 | `@EnableAsync` + bounded `ThreadPoolTaskExecutor` |

**GREEN validation command**:
```
mvn -f app/pom.xml -DfailIfNoTests=false test
→ Tests run: 64, Failures: 0, Errors: 0, Skipped: 0
→ BUILD SUCCESS
```

Note on GREEN defects fixed in same commit (pre-review):
- `RateLimitDecision.degraded()` static factory collided with record accessor `degraded()` → renamed factory to `failOpen()`
- `JwtTokenProvider.Decoded` field was `fid`, but `parseRefreshToken` called `familyId()` → renamed record field to `familyId`

### 3.3 REFACTOR — `475ce81` `refactor(infra): shared-infra review fixes (4 HIGH, 1 MEDIUM, 3 LOW)`

Code-reviewer + security-reviewer parallel review surfaced 23 findings (0 CRITICAL, 4 HIGH, 6 MEDIUM/INFO, 13 LOW). HIGH issues fixed in this commit:

| Finding | Severity | File | Fix |
|---|---|---|---|
| `AsyncConfig(Object redisOrNull)` constructor would crash Spring startup with `NoSuchBeanDefinitionException` | HIGH | `AsyncConfig.java` | Removed constructor parameter (no-arg now) |
| `@RateLimit` only `@Target(METHOD)` blocks class-level layered limits | HIGH | `RateLimit.java`, `RateLimits.java` | Added `ElementType.TYPE` to both |
| Hand-rolled `constantTimeEquals` early-returns on length mismatch | MEDIUM | `JwtTokenProvider.java` | Replaced with `MessageDigest.isEqual` (JEP 244) |
| `V27` doc drift (real schema is V26) | LOW | `Auditable.java`, `AuditEntry.java` | Replaced `V27` → `V26` |
| `createAccessToken` doesn't snapshot `roles` | LOW | `JwtTokenProvider.java` | `List.copyOf(roles)` defensive copy |

**REFACTOR validation command**:
```
mvn -f app/pom.xml -DfailIfNoTests=false test
→ Tests run: 64, Failures: 0, Errors: 0, Skipped: 0
→ BUILD SUCCESS
```

Findings deferred (track in PR body / next plan revision):
- `AuditEntry` schema drift — `module / statusCode / latencyMs / traceId / requestHash` are V36+ migration candidates; record carries them forward but writer not implemented
- `JwtTokenProvider.parseClaims` hand-rolled JSON parser fragility → switch to JJWT in follow-up PR
- `iss` claim validation missing in `decode(...)` → add in follow-up PR + regression test
- `JwtRefreshTokenService` interface should document `parentJti` cryptographic binding for reuse detection
- `RateLimitAspect` / `AuditAspect` / `AuthAspect` AOP implementations (plan-shared-infra §1)

## 4. Test specification — what is guaranteed

| # | Guarantee | Test | Type | Result |
|---|---|---|---|---|
| 1 | Access token round-trip preserves userId/roles/jti/expiresAt; roles immutable | `JwtTokenProviderTest#security_should_round_trip_access_token_claims` | unit | PASS |
| 2 | Expired JWT maps to `JwtExpiredException` (HTTP 401 TOKEN_EXPIRED) | `JwtTokenProviderTest#security_should_reject_expired_jwt` | unit | PASS |
| 3 | Tampered signature maps to `JwtInvalidException` | `JwtTokenProviderTest#security_should_reject_tampered_jwt` | unit | PASS |
| 4 | Refresh token carries jti + familyId | `JwtTokenProviderTest#security_should_round_trip_refresh_claims` | unit | PASS |
| 5 | Refresh token cannot authenticate an API request | `JwtTokenProviderTest#security_should_reject_refresh_token_as_access_token` | unit | PASS |
| 6 | HS256 secret < 256 bits rejected at construction | `JwtTokenProviderTest#security_should_reject_short_secret` | unit | PASS |
| 7 | `JwtRefreshTokenService.rotate / detectReuse / revokeFamily / parseClaims` shape | `JwtRefreshTokenServiceContractTest#security_should_expose_refresh_rotation_contract` | contract | PASS |
| 8 | `RefreshResult / RefreshClaims` records carry plan-defined fields | `JwtRefreshTokenServiceContractTest#security_should_define_refresh_contract_records` | contract | PASS |
| 9 | BCrypt cost 12 hashes + verifies | `PasswordEncoderConfigTest#security_should_bcrypt_password` | unit | PASS |
| 10 | `@RequireAuth` available at RUNTIME on METHOD + TYPE | `SecurityAnnotationContractTest#security_should_define_require_auth_annotation` | contract | PASS |
| 11 | `@RequireRole` carries the required role string | `SecurityAnnotationContractTest#security_should_define_require_role_annotation` | contract | PASS |
| 12 | Token bucket allows under limit | `TokenBucketServiceTest#ratelimit_should_allow_under_limit` | unit | PASS |
| 13 | Token bucket rejects over limit (with retryAfterSeconds) | `TokenBucketServiceTest#ratelimit_should_reject_over_limit` | unit | PASS |
| 14 | Redis failure → fail-open (`degraded=true`) | `TokenBucketServiceTest#ratelimit_should_fail_open_when_redis_is_unavailable` | unit | PASS |
| 15 | Token bucket validates inputs (blank key / 0 limit / zero window) | `TokenBucketServiceTest#ratelimit_should_validate_bucket_inputs` | unit | PASS |
| 16 | `@RateLimit` defaults: key=userId, limit=60, window=60, scope=api | `RateLimitContractTest#ratelimit_should_define_plan_defaults` | contract | PASS |
| 17 | `@RateLimit` is repeatable via `@RateLimits` | `RateLimitContractTest#ratelimit_should_support_layered_limits` | contract | PASS |
| 18 | `AuditEntry` carries V26 fields + immutable payload | `AuditEntryTest#audit_should_create_immutable_entry` | unit | PASS |
| 19 | `AuditPayloadHasher` SHA-256 hashes selected args, deterministic | `AuditEntryTest#audit_should_hash_selected_args` | unit | PASS |
| 20 | Masked args stored as literal `***` (no plaintext) | `AuditEntryTest#audit_should_mask_sensitive_args` | unit | PASS |
| 21 | `@Auditable` exposes action / resourceType / captureArgs / mask | `AuditableContractTest#audit_should_define_annotation_contract` | contract | PASS |
| 22 | `AsyncConfig` executor: core 8 / max 16 / queue 200 / CallerRuns | `AsyncConfigTest#async_should_configure_bounded_executor` | unit | PASS |

Pre-existing tests preserved (no regression):
- `shared/integration/dto/{ApiResponse, ErrorCode, ErrorEnvelope, PageMeta}Test` — 15 tests
- `shared/integration/event/{EventEnvelope, EventType, TaskCompletedPayload}Test` — 13 tests
- `shared/integration/port/{PortContract, ResourceNotFoundException}Test` — 8 tests
- `shared/integration/port/snapshot/TaskSnapshotTest` — 4 tests
- `FlywayMigrationIT` — 1 test (skipped on `mvn test`; runs on `mvn verify`)

## 5. Coverage and known gaps

**Coverage command not run** — JaCoCo is not yet configured in `pom.xml`. Per project rule (CLAUDE.md §6.1 ≥ 80% line coverage), JaCoCo must be added before the `plan-shared-infra-followup` PR merges to `main`. Recommended config:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

**Known coverage gaps** (no test exercises the path):
- `JwtTokenProvider.decode` malformed-body paths (`extractLong` `NumberFormatException`, `extractStringArray` unterminated, blank token, split-into-≠3 parts)
- `JwtRefreshTokenService` runtime implementation — interface only, no impl in this commit
- `RateLimitAspect` / `AuditAspect` / `AuthAspect` — not implemented (RED→GREEN boundary)

## 6. Merge evidence

Squash-merging into `main` will collapse three commits into one. The PR body must include the
RED/GREEN/REFACTOR summary above (per tdd-workflow §"Merge evidence"). The summary table in §4
is the canonical artifact for review.

## 7. Plan compliance check

| Plan item (plan-shared-infra §1, §2.2) | Status | Evidence |
|---|---|---|
| BCrypt strength 12 | met | `PasswordEncoderConfigTest#security_should_bcrypt_password` (asserts `$12$`) |
| JWT HS256 with ≥ 256-bit secret | met | `JwtTokenProviderTest#security_should_reject_short_secret` + ctor check |
| Refresh-token rotation + reuse detection contract | met (interface) | `JwtRefreshTokenServiceContractTest` |
| `@RequireAuth` runtime, METHOD + TYPE | met | `SecurityAnnotationContractTest` |
| `@RequireRole("ADMIN")` value passthrough | met | `SecurityAnnotationContractTest` |
| `@RateLimit(key=userId, limit=60, window=60s, scope=api)` defaults | met | `RateLimitContractTest` |
| `@RateLimit` repeatable for layered AI limits | met | `RateLimitContractTest#ratelimit_should_support_layered_limits` |
| `@Auditable` action/resourceType/captureArgs/mask | met | `AuditableContractTest` |
| Audit payload SHA-256 + `***` masking | met | `AuditEntryTest` |
| `operation_logs` schema (V26) alignment | met (record shape) | `AuditEntry` |
| Redis-down fail-open (`degraded=true`) | met | `TokenBucketServiceTest` |
| `ThreadPoolTaskExecutor` core 8 / max 16 / queue 200 / CallerRuns | met | `AsyncConfigTest` |