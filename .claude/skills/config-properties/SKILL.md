---
name: config-properties
description: Typed configuration in this project via @ConfigurationProperties — UdmProperties (udm.core), UcpProperties, TurkcellApiGwProperties, EnvironmentConfig — how they're scanned, bound, defaulted, and tested. Use when adding a config value, a new properties class, env/profile-specific settings, or wiring application.yml to code.
---

# Config Properties Skill

How externalized configuration is modeled here: typed `@ConfigurationProperties` beans bound from `application.yml` / environment variables, scanned automatically.

## When to Use
- "add a config value" / "make this configurable" / "read this from application.yml"
- Creating a new `@ConfigurationProperties` class
- Profile- or environment-specific values (DEV / local / integration-test / PROD)
- Debugging why a property isn't binding

## How it's wired
- The main class `UdmAdapterApplication` carries `@ConfigurationPropertiesScan` **and** `@EnableConfigurationProperties`. So a properties class only needs the `@ConfigurationProperties(prefix = "...")` annotation — it's picked up automatically, no manual `@EnableConfigurationProperties(X.class)` per class and no `@Component`.
- Existing properties beans (in `config/`):
  - `UdmProperties` — prefix **`udm.core`** (timeouts, max-input limits, `ipCheckEnabled`, `vendorMap`, …)
  - `UcpProperties` — Oracle UCP pool settings
  - `TurkcellApiGwProperties` — API gateway credentials / URLs
  - `EnvironmentConfig` — environment-derived settings

## Pattern: a properties class
```java
@ConfigurationProperties(prefix = "udm.core")
public class UdmProperties {
    private int restClientConnectTimeout = 10_000; // sensible default in the field
    private boolean ipCheckEnabled = false;
    private Map<Integer, String> vendorMap = new HashMap<>();
    // standard getters/setters (no defensive copies — see static-analysis-gates / EI_EXPOSE_REP)
    public int getRestClientConnectTimeout() {
        return restClientConnectTimeout;
    }
    public void setRestClientConnectTimeout(final int v) {
        this.restClientConnectTimeout = v;
    }
}
```
Bound YAML:
```yaml
udm:
  core:
    rest-client-connect-timeout: 10000
    ip-check-enabled: false
```

## Consuming
Inject the properties bean via constructor (as `AppConfig`, `IpCheckInterceptor`, etc. do) and read typed getters — never `@Value` scattered across the codebase for values that belong to a group, and never re-read `Environment` directly.

## Profiles & environment
- Profiles in use: `DEV`, `local`, `integration-test`, `PROD` (see `DatabaseConfiguration` `@Profile` beans). Keep profile-specific values in the right `application-<profile>.yml` / property source; default in the field.
- Secrets/URLs come from env vars with `${VAR:default}` syntax (e.g. `ORACLE_JDBC_URL`, `ORACLE_USERNAME`). Don't hardcode secrets; add an env-backed property.

## Testing
There's a factory per properties class (e.g. `factory/config/UdmPropertiesFactory.create()`) and a matching `*PropertiesTest` (`@Tag("unit")`, often `@ParameterizedTest` over getters/setters). When you add a field, extend the factory and the test. See the **unit-testing** skill.

## Rules
1. Group related settings in a `@ConfigurationProperties(prefix=...)` bean; don't sprinkle `@Value`.
2. No extra registration needed — `@ConfigurationPropertiesScan` handles discovery.
3. Give every field a sensible default in the field declaration.
4. Inject the bean; never read `Environment`/system props directly in business code.
5. Secrets and host-specific values via `${ENV_VAR:default}`, not literals.
6. New field → update the Instancio factory + the `*PropertiesTest`.
