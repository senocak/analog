---
name: unit-testing
description: "Writing unit tests in this project — JUnit 5 + Mockito, Instancio test-data factories under test/factory, the @Tag('unit') vs @Tag('integration') split, and WireMock for stubbing HTTP. Use when adding or fixing unit tests, generating test data, or deciding unit vs integration. For DB-backed tests see testcontainers-oracle."
---

# Unit Testing Skill

How fast, isolated tests are written here. For container-backed integration tests, use the **testcontainers-oracle** skill instead — this skill is the unit / mocked side.

## When to Use
- "write a unit test" / "add a test for this service or config class"
- Generating test data without hand-building large DTOs
- Deciding whether something is a unit (`@Tag("unit")`) or integration (`@Tag("integration")`) test
- Stubbing an outbound HTTP call instead of hitting a real endpoint

## Stack
- **JUnit 5** (Jupiter) — `@Test`, `@Nested`, `@DisplayName`, `@ParameterizedTest` + `@ValueSource`.
- **Mockito** — `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`.
- **Instancio `5.5.1`** — random/auto-populated test objects.
- **WireMock `3.13.2`** — available for stubbing outbound HTTP (RestClient/apigw) at the wire level.
- Surefire JUnit5 tree reporter for readable output.

## Test-data convention: Instancio factories (important)
This project does **not** call `Instancio.create(...)` inline all over tests. Instead there is a `src/test/java/.../factory/` package of small factory classes, one per type:
```java
public final class DeviceQueryResultFactory {
    public static DeviceQueryResult create() {
        return Instancio.create(DeviceQueryResult.class);
    }
}
```
Config-object factories live under `factory/config/` (e.g. `UdmPropertiesFactory.create()`), response DTOs under `factory/response/`. **When you need a populated object in a test, reuse the existing factory; if none exists, add one to the matching `factory/...` package** rather than building the object inline or scattering `Instancio.create` calls.

## Patterns
Pure unit test of a config/POJO (parameterized):
```java
@Tag("unit")
@DisplayName("UdmProperties")
class UdmPropertiesTest {
    private final UdmProperties properties = UdmPropertiesFactory.create();

    @ParameterizedTest
    @ValueSource(ints = {0, 5000, 10000})
    @DisplayName("set and get restClientConnectTimeout")
    void setAndGetRestClientConnectTimeout(final int timeout) {
        properties.setRestClientConnectTimeout(timeout);
        assertEquals(timeout, properties.getRestClientConnectTimeout());
    }
}
```
Service test with mocked collaborators:
```java
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UdmAdapterRestServiceTest {
    @Mock private DeviceAttributeVwRepository repository;
    @InjectMocks private UdmAdapterRestService service;
    // arrange with factories, stub with when(...), assert
}
```

## The unit vs integration split
- `@Tag("unit")` — no Spring context, no Docker. Mockito + factories. Fast; always runs.
- `@Tag("integration")` — via the `@SpringBootTestConfig` meta-annotation (real Oracle container,
  `integration-test` profile). Gated by `@EnabledIfDockerAvailable` and the `integration-test` Maven phase. See **testcontainers-oracle**.
Pick unit by default; reach for integration only when you genuinely need the DB / full context.

## Rules
1. Tag every test class (`@Tag("unit")` or `@Tag("integration")`).
2. Use/extend the `factory/...` Instancio factories for test data — don't hand-build big DTOs inline.
3. Unit tests mock collaborators (`@Mock`/`@InjectMocks`); never start a Spring context or DB for a unit test.
4. Stub outbound HTTP with WireMock rather than calling real endpoints.
5. Use `@DisplayName` + `@Nested` for readable, grouped output (matches existing style).
6. A `SimpleMeterRegistry` substitutes for Micrometer in tests where a `MeterRegistry` is needed.
