# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this app does

Spring Boot 4 / Java 21 service that generates randomized weapon builds for Escape from Tarkov. A Spring Batch job repeatedly composes a weapon + a tree of slot-filling attachments (subject to allowed-item filters and conflict tables), hashes the result, and persists unique builds into Postgres. Source-of-truth data is fetched from `https://api.tarkov.dev/graphql` via the Apollo Java client.

## Build, run, codegen

- Toolchain: JDK 21 (Gradle toolchains will provision it).
- Build (also runs Flyway + jOOQ codegen first): `./gradlew build`
- Just the bootable jar (CI uses this): `./gradlew clean bootJar`
- Run locally (Spring Boot devtools brings up the dev Postgres in `compose.yaml` automatically): `./gradlew bootRun`
- Run tests: `./gradlew test` — single test: `./gradlew test --tests "<FQCN>"` (note: `src/test` is currently empty).
- Force-refresh GraphQL schema (introspect `api.tarkov.dev`): `./gradlew downloadServiceApolloSchemaFromIntrospection generateServiceApolloSources`
- Force-regenerate jOOQ classes against current migrations: `./gradlew jooqCodegen`

### Two separate Postgres instances — don't confuse them

- **Codegen DB** (ephemeral, used at *build time* by Flyway → jOOQ codegen): brought up/down by tasks `startGunsmithCodegenPostgres` / `stopGunsmithCodegenPostgres` defined via the custom `io.github.pgatzka.docker` Gradle plugin. Port `15432`, db/user/password all `gunsmith_codegen`. `compileJava` depends on `jooqCodegen` → `flywayMigrate`, which auto-starts/stops this container; `clean` removes its volume. Don't add app data here.
- **Dev DB** (runtime, used by `bootRun` / IDE): defined in `compose.yaml`, port `5432`, db/user/password all `gunsmith_dev`. Spring Boot's `docker-compose` integration starts it on app startup in dev.

The custom docker plugin (`io.github.pgatzka.docker`, version `1.0.0`) is hosted on GitHub Packages and requires `gpr.user`/`gpr.token` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars to resolve (see `settings.gradle.kts`).

## Architecture (the parts that span files)

### Data flow

1. `BuildJobScheduler` (`@EnableScheduling` trigger task) — on `ApplicationReadyEvent` it queries weapon IDs from the Tarkov API, subtracts `gunsmith.job.ignored-weapons`, computes `target / weaponCount` builds-per-weapon, then schedules `generateJob` repeatedly. Delay between runs adapts: while builds-per-tick are below `slowdownThreshold * target` it interpolates between `min-delay` and `max-delay`; above it, it stays at `min-delay`. Hard stop at `limit = 500_000` rows in `BUILD` (sleeps a day).
2. `JobConfiguration` / `StepConfiguration` — single-step Spring Batch job, chunk size 50.
3. `GenerateReader` — for each non-ignored weapon, emits `target / weaponCount` `BuildSettings` items (just the weapon's tarkov id), then shuffles. Re-runs per step (it's `@StepScope`).
4. `GenerateProcessor` — for each `BuildSettings`:
   - resolves `WeaponRecord` via `WeaponService` (which lazily fetches missing/stale rows from Apollo and writes them through to Postgres),
   - recursively walks the weapon's slots, picking a random allowed `AttachmentRecord` per slot (or skipping if `required=false`), populating sub-slots,
   - filters candidates against `ATTACHMENT_CONFLICTING_ATTACHMENT` and `ATTACHMENT_CONFLICTING_WEAPON` (pre-filter is best-effort because conflicts are sometimes recorded only on one side; there's a backstop check on the final build),
   - serializes the build to JSON, MD5-hashes it, deduplicates against a per-step `seenHashes` set seeded from `BUILD.JSON_HASH`,
   - computes ergonomics + recoil totals.
5. `GenerateWriter` — single batched `INSERT` of unique-by-hash `BuildResult`s. The `build.json_hash` unique constraint is the ultimate dedupe.

### Apollo / Tarkov API client

- `ApolloConfiguration` builds an `ApolloClient` with two interceptors:
  - `RateLimitingHttpInterceptor` — Guava `RateLimiter` at `gunsmith.api.permits / 60` permits/sec.
  - `FileCacheHttpInterceptor` — disk cache under `./apollo-cache/` keyed by SHA-256 of the request body, 12h TTL. Folder is gitignored.
- `ApolloService.query(...)` wraps the async client in a synchronous retry loop. It classifies failures: HTTP 429 + 5xx + network errors → retryable with exponential backoff (`gunsmith.api.backoff` doubled per attempt + jitter, or honors `Retry-After`); 4xx + GraphQL errors + empty data → non-retryable.
- GraphQL queries live in `src/main/graphql/queries.graphql`; Apollo Gradle plugin generates Java models into `io.github.pgatzka.gunsmith.apollo`.

### Persistence

- Postgres + Flyway migrations under `src/main/resources/db/migration/V0xx__*.sql`. Schema is `weapon`, `attachment`, `slot`, plus join tables (`weapon_slot`, `attachment_slot`, `slot_allowed_attachment`) and conflict tables (`attachment_conflicting_attachment`, `attachment_conflicting_weapon`), and the result table `build` (with a `jsonb` column and a unique `json_hash`).
- jOOQ generated sources go to `io.github.pgatzka.gunsmith.jooq` (table classes use the `*Table` suffix per the codegen strategy in `build.gradle.kts`). Always reference tables via the `Tables.*` static imports rather than the raw record classes.
- `WeaponService` and `AttachmentService` mutually depend on each other (`@Lazy` setter injection) because attachments can recursively introduce sub-slots that allow further attachments which themselves carry slots etc.; both have a 2-day "stale row" check that triggers a re-fetch from Apollo.

### Observability

- Actuator endpoints exposed: `health`, `info`, `prometheus`, plus a custom `jooqstats`.
- `JooqQueryStatsListener` (registered automatically via Spring jOOQ autoconfig; configured in `JooqStatsConfiguration`) records per-rendered-SQL count/min/avg/max/total nanos. The `insert into build` SQL is normalized to a single key so every batch insert aggregates together. The `JooqStatsEndpoint` (`GET /actuator/jooqstats`, `DELETE` to reset) returns the snapshot sorted by call count.

## Configuration knobs (`application.yml` / `gunsmith.*`)

- `gunsmith.api.{url, permits, retries, backoff}` — Tarkov API client behavior. `permits` is per *minute* (converted to per-second internally).
- `gunsmith.job.{target, min-delay, max-delay, startup-delay, slowdown-threshold, ignored-weapons}` — scheduler/batch loop. `target` is the desired total builds across all non-ignored weapons per job run. `ignored-weapons` is a long, commented list of Tarkov item IDs to exclude.
- `spring.batch.job.enabled: false` is intentional — jobs are launched via `JobOperator` from the scheduler, not auto-run on startup.

## Conventions worth preserving

- Lombok is on the build (`@Slf4j`, `@RequiredArgsConstructor`, `@Data`); prefer constructor injection via `@RequiredArgsConstructor` over `@Autowired` fields. The two service-circular-dependency cases (`WeaponService` ↔ `AttachmentService`) use `@Lazy` setter injection on purpose.
- Use jspecify (`@NonNull` / `@Nullable`) for batch component method signatures, matching what's already there.
- New migrations: next free `V0xx__<snake_case_description>.sql`. Migrations run against the codegen DB at build time, so any new column/table is required to compile (jOOQ codegen will regenerate `Tables`).
- New GraphQL queries: add to `queries.graphql`; rerun `generateServiceApolloSources` if the schema needs refreshing.

## Workflow rules from the user (global `~/.claude/CLAUDE.md` — summarized here so they're not forgotten in this repo)

- Plan-first for non-trivial work; track in `tasks/todo.md`; capture corrections in `tasks/lessons.md`.
- Notes / journal / research go to the user's Obsidian vault via the `obsidian` MCP server, *not* into this repo.
- Verify before claiming done. Prefer simple, root-cause fixes over patches.
