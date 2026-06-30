# Web Layer - Controllers & REST APIs

## REST Controller Pattern

```java
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    private final UserService userService;

    public UserController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<UserResponse> getUsers(final @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return userService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(final @PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(final @Valid @RequestBody UserCreateRequest request) {
        final UserResponse user = userService.create(request);
        final URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.id())
                .toUri();
        return ResponseEntity.created(location).body(user);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(final @PathVariable Long id,
                                   final @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(final @PathVariable Long id) {
        userService.delete(id);
    }
}
```

## Request DTOs with Validation

```java
public record UserCreateRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).*$",
             message = "Password must contain uppercase, lowercase, and digit")
    String password,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must be alphanumeric")
    String username,

    @Min(value = 18, message = "Must be at least 18")
    @Max(value = 120, message = "Must be at most 120")
    Integer age
) {}

public record UserUpdateRequest(
    @Email(message = "Email must be valid")
    String email,

    @Size(min = 3, max = 50)
    String username
) {}
```

## Response DTOs

```java
public record UserResponse(
    Long id,
    String email,
    String username,
    Integer age,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getAge(),
            user.getActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
```

## Global Exception Handling

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ErrorResponse handleNotFound(final ResourceNotFoundException ex, final WebRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getDescription(false),
                LocalDateTime.now()
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ValidationErrorResponse handleValidation(final MethodArgumentNotValidException ex) {
        final Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Invalid value"
                ));
        return new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors,
                LocalDateTime.now()
        );
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ErrorResponse handleDataIntegrity(final DataIntegrityViolationException ex, final WebRequest request) {
        log.error("Data integrity violation", ex);
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Data integrity violation - resource may already exist",
                request.getDescription(false),
                LocalDateTime.now()
        );
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleGlobalException(final Exception ex, final WebRequest request) {
        log.error("Unexpected error", ex);
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred",
                request.getDescription(false),
                LocalDateTime.now()
        );
    }
}

record ErrorResponse(
        int status,
        String message,
        String path,
        LocalDateTime timestamp
) {
}

record ValidationErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        LocalDateTime timestamp
) {
}
```

## Custom Validation

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    private final UserRepository userRepository;

    public UniqueEmailValidator(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public boolean isValid(final String email, final ConstraintValidatorContext context) {
        if (email == null) {
            return true;
        }
        return !userRepository.existsByEmail(email);
    }
}
```

## WebClient for External APIs

```java
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient webClient(final WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(logRequest())
            .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }
}

@Service
public class ExternalApiService {
    private final WebClient webClient;

    public ExternalApiService(final WebClient webClient) {
        this.webClient = webClient;
    }
    
    public Mono<ExternalDataResponse> fetchData(String id) {
        return webClient
            .get()
            .uri("/data/{id}", id)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new ResourceNotFoundException("External resource not found")))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new ServiceUnavailableException("External service unavailable")))
            .bodyToMono(ExternalDataResponse.class)
            .timeout(Duration.ofSeconds(5))
            .retry(3);
    }
}
```

## CORS Configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000", "https://example.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

## Quick Reference

| Annotation | Purpose |
|------------|---------|
| `@RestController` | Marks class as REST controller (combines @Controller + @ResponseBody) |
| `@RequestMapping` | Maps HTTP requests to handler methods |
| `@GetMapping/@PostMapping` | HTTP method-specific mappings |
| `@PathVariable` | Extracts values from URI path |
| `@RequestParam` | Extracts query parameters |
| `@RequestBody` | Binds request body to method parameter |
| `@Valid` | Triggers validation on request body |
| `@RestControllerAdvice` | Global exception handling for REST controllers |
| `@ResponseStatus` | Sets HTTP status code for method |