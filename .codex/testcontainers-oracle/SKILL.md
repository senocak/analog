---
name: testcontainers-oracle
description: "Integration testing against a real Oracle database with Testcontainers (gvenzl/oracle-free) in this project — the OracleInitializer, the @SpringBootTestConfig meta-annotation, init scripts, and the PL/SQL loading workaround. Use when writing or fixing integration tests, adding DB seed/schema scripts, or debugging container startup."
---

# Testcontainers Oracle Skill

How integration tests get a real Oracle database in this project. Tests run against an `gvenzl/oracle-free` container booted before the Spring context, with schema and seed data loaded from `src/test/resources/db/udm/`.

## When to Use
- "write an integration test" / "test against the DB" / "test a repository or query"
- Adding or changing SQL schema / seed scripts under `src/test/resources/db/udm/`
- Debugging container startup, missing tables, or PL/SQL package errors in tests
- Questions about `OracleInitializer`, `@SpringBootTestConfig`, or test profiles

## How it's wired
- **`OracleInitializer`** (`ApplicationContextInitializer`): owns a static `OracleContainer("gvenzl/oracle-free:slim-faststart")` with `withReuse(true)`, a 2-min startup timeout, fixed user `ADCR`, and `withInitScripts(...)` for plain DDL/DML. A static block starts the container and loads PL/SQL separately. `initialize()` injects the JDBC URL and credentials into the Spring `Environment`.
- **`@SpringBootTestConfig`** (meta-annotation): the one annotation integration tests use. It bundles: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("integration-test")`, `@ContextConfiguration(initializers = OracleInitializer.class)`, `@Import(TestConfig.class)`, `@Transactional(propagation = NOT_SUPPORTED)`, `@Tag("integration")`, and `@EnabledIfDockerAvailable`.
- **Profile `integration-test`** activates `DatabaseConfiguration#simpleDataSource()` (UCP pool), not the PROD JNDI datasource.

## Pattern: an integration test
```java
@SpringBootTestConfig
class UdmAdapterRestServiceTest {
    @Autowired
    private UdmAdapterRestService service;

    @Test
    void queriesDeviceByMsisdn() {
        // container is already up, schema + seed loaded, profile = integration-test
        ...
    }
}
```
Just annotate with `@SpringBootTestConfig` — do not re-declare `@SpringBootTest`, profiles, or initializers on the test class.

## The PL/SQL gotcha (important)
Testcontainers' `ScriptUtils` splits scripts on `;`, which **breaks PL/SQL `PACKAGE`/`PACKAGE BODY`and functions**. So:
- Plain DDL/DML (tables, views, types, seed rows) → add to `withInitScripts(...)` in `OracleInitializer`.
- PL/SQL blocks (`package_spec.sql`, `functions.sql`) → loaded by `executePlSqlScript(...)` as a single JDBC `Statement`, with the trailing `/` terminator stripped. Add new PL/SQL scripts there, **not** to `withInitScripts`.

## Adding a new schema/seed script
1. Put the `.sql` file in `src/test/resources/db/udm/`.
2. Plain SQL → add its classpath path to the `withInitScripts(...)` list (mind ordering: types/parent tables before dependents).
3. PL/SQL → add an `executePlSqlScript("db/udm/yourfile.sql")` call in the static block.
4. Run the test class; `OracleInitializer` logs `user_objects` and `user_errors` to help debug
   invalid types/packages.

## Rules
1. New integration tests use `@SpringBootTestConfig` only — keep the wiring in one place.
2. Never point integration tests at a shared/real Oracle; always the container.
3. Tests run with `@Transactional(NOT_SUPPORTED)` — they do **not** auto-rollback. Seed and clean up deliberately, or rely on idempotent seed data.
4. Container reuse is on; don't assume a pristine DB between runs — write tests that tolerate pre-existing seed rows.
5. Keep `dependency` versions (`testcontainers-oracle-free`, `testcontainers-junit-jupiter`) managed by the Spring Boot / Testcontainers BOM.
