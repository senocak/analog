---
name: api-error-handling
description: "Standardized REST error handling in this project — the UdmExceptionHandler @RestControllerAdvice, ServerException, the UdmResponseCode enum, and the ExceptionDto response shape. Use when adding an endpoint, throwing/handling errors, defining a new response code, or shaping error responses."
---

# API Error Handling Skill

How errors are turned into consistent HTTP responses in this project. All REST errors flow through one advice and are rendered as a single DTO with a UDM-specific response code.

## When to Use
- "add error handling" / "handle this exception" / "return a proper error"
- Adding a new endpoint that can fail, or a new business error condition
- Defining a new `UdmResponseCode`, throwing `ServerException`, or shaping the error body
- Debugging why an error returns the wrong status or an inconsistent body

## Components
- **`UdmExceptionHandler`** (`service/`, `@RestControllerAdvice`): the single global handler. Maps each exception type to `(HttpStatus, UdmResponseCode, variables)` and builds the response.
- **`ServerException`** (`domain/model/`, checked `extends Exception`): the project's business exception. Carries `UdmResponseCode udmResponseCode`, `String[] variables`, and `HttpStatus statusCode`.
- **`UdmResponseCode`** (`util/enums/`): enum of `code` + `message` pairs (e.g. `SUCCESS`, `BASIC_INVALID_INPUT`, `MANDATORY_INPUT_MISSING`, `UNAUTHORIZED`, `GENERIC_SERVICE_ERROR`, `SYSTEM_ERROR`, `NOT_FOUND`).
- **`ExceptionDto`** (`ws/rest/response/`): the serialized body — `statusCode`, nested `UdmResponseCodeDto {code, message}`, and `variables[]`.

## Throwing a business error
```java
throw new ServerException(
        UdmResponseCode.BASIC_INVALID_INPUT,            // which code
        new String[]{ "imei", imei },                   // variables (context for the message)
        HttpStatus.BAD_REQUEST);                         // HTTP status to return
```
`UdmExceptionHandler#handleServerException` reads those three fields directly, so the status and code you choose at throw-time are exactly what the client receives.

## How framework exceptions are mapped (already handled — don't re-catch)
| Exception | HttpStatus | UdmResponseCode |
|---|---|---|
| `ConstraintViolationException`, `InvalidParameterException` | 400 | `BASIC_INVALID_INPUT` |
| `MethodArgumentNotValidException` | from body | `MANDATORY_INPUT_MISSING` (lists field errors) |
| `HttpMessageNotReadableException` | 400 | `GENERIC_SERVICE_ERROR` |
| `HttpMediaTypeNotSupportedException` | 415 | `BASIC_INVALID_INPUT` |
| `HttpRequestMethodNotSupportedException` | 405 | `EXTRA_INPUT_NOT_ALLOWED` |
| `MissingPathVariableException` / `TypeMismatchException` | 400 | `MANDATORY_INPUT_MISSING` / `BASIC_INVALID_INPUT` |
| `NoHandlerFoundException` | 404 | `NOT_FOUND` |
| `AccessDeniedException` | 401 | `UNAUTHORIZED` |
| `Exception` (fallback) | 500 | `GENERIC_SERVICE_ERROR` |

## Rules
1. **One handler.** Add new mappings to `UdmExceptionHandler` via `@ExceptionHandler`; do not put try/catch-to-ResponseEntity logic in controllers or scatter `@RestControllerAdvice`.
2. **Business failures throw `ServerException`** with an explicit `HttpStatus` + `UdmResponseCode`. Don't return raw error strings or naked `ResponseEntity` from services.
3. **New error condition → add a `UdmResponseCode`** (code + message) rather than reusing an unrelated one. Use `variables[]` to carry the dynamic context (ids, field names).
4. **Every error response is an `ExceptionDto`** — keep the shape consistent so clients can parse `statusCode` + `udmResponseCodeDto.code` uniformly.
5. The fallback `handleGeneralException` exists so nothing leaks a raw stack trace; don't remove it, but prefer a specific handler/`ServerException` over relying on the 500 fallback.
6. `generateResponseEntity` logs every handled exception — don't double-log in the throwing code.

## Checklist for a new endpoint
- [ ] Validation via `@Valid` / constraints (auto-mapped to 400) instead of manual checks where possible.
- [ ] Business failures throw `ServerException` with the right status + code.
- [ ] Any genuinely new failure mode has a dedicated `UdmResponseCode`.
- [ ] No controller-level error-to-ResponseEntity plumbing.
