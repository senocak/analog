---
name: rest-client-integration
description: Calling external HTTP services with Spring RestClient in this project — bean setup, the logging/masking interceptor, timeouts, and the SSL-disabled variant. Use when adding or debugging an outbound REST call, the @RestClientLogMask annotation, or RestClientInterceptor.
---

# REST Client Integration Skill

How outbound HTTP calls are made and logged in this project. The app talks to the Turkcell API Gateway and other UDM backends through Spring `RestClient`.

## When to Use
- "call this external API" / "add an outbound REST call" / "integrate service X"
- Working with `RestClientInterceptor`, request/response logging, or field masking
- Configuring timeouts, or needing the SSL-validation-disabled client
- Debugging why an outbound call isn't logged or a secret leaked into logs

## Beans (config/AppConfig.java)
- `RestTemplate getRestTemplate()` — sets connect/read timeouts from `UdmProperties` (`getRestClientConnectTimeout()`, `getRestClientReadTimeout()`).
- `RestClient getRestClient(RestTemplate)` — the **default** client: `RestClient.builder(restTemplate).requestInterceptor(restClientInterceptor).build()`.
- `RestClient getRestClientSslDisable(RestTemplate)` — same, but with TLS validation disabled. **Use only for known internal endpoints with self-signed certs.** Qualify the injection so you don't pick it up by accident.

Inject the client; never `new RestClient`/`new RestTemplate` inside a service.

## Calling pattern (service layer)
```java
private final RestClient restClient; // injected via constructor

@RestClientLogMask(uriFieldsToMask = {"password", "client_secret"},
                   responseFieldsToMask = {"access_token"})
public void fetchToken() {
    final ResponseEntity<TurkcellApigwTokenResponse> response = restClient.post()
            .uri(apiGwTokenUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .retrieve()
            .toEntity(TurkcellApigwTokenResponse.class);

    if (!response.getStatusCode().is2xxSuccessful()) {
        return; // handle non-2xx explicitly
    }
    final TurkcellApigwTokenResponse body = response.getBody();
    if (body == null || StringUtils.isEmpty(body.getAccessToken())) {
        return;
    }
    // ...
}
```

## Logging & masking — the key project rule
`RestClientInterceptor` (a `ClientHttpRequestInterceptor`) automatically logs every outbound request/response: it resolves the **calling method** via `StackWalker`, measures elapsed time, maps HTTP status to a `UdmResponseCode`, and buffers the response body so it can be read twice.

Control it per-method with `@RestClientLogMask` (in `aop/`):
- `logRestClient = false` → skip logging entirely for that call.
- `uriFieldsToMask` → mask query params (e.g. credentials in a token URL).
- `requestFieldsToMask` / `responseFieldsToMask` → mask JSON fields in bodies.

**Any call that carries secrets (passwords, client_secret, tokens) MUST annotate the calling method with `@RestClientLogMask` and list those fields**, or they will be logged in clear text. The interceptor reads the annotation off the method that initiated the call, so annotate the service method, not a helper.

## Rules
1. Inject a `RestClient` bean; pick the SSL-disabled one only when explicitly required and qualify it.
2. Always handle non-2xx and null bodies explicitly (see pattern). Don't assume success.
3. Set timeouts via `UdmProperties` — never leave them at infinite defaults.
4. Mask secrets with `@RestClientLogMask`; verify masking in `RestClientInterceptorTest`.
5. Don't add your own logging around the call — the interceptor already covers it; duplicating it risks logging unmasked bodies.
