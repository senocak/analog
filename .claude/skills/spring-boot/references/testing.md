---
name: testing
description: >
  Use when writing unit tests, integration tests or test slice. Covers test structure, naming conventions, Mockito patterns, @SpringBootTest, @WebMvcTest, @DataJpaTest, and Testcontainers setup.
---

# Integration Testing - Spring Boot Test

## Required Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.instancio</groupId>
    <artifactId>instancio-junit</artifactId>
    <scope>test</scope>
</dependency>
```

If database is using oracle then `testcontainers-oracle-free` for integration tests with Testcontainers.
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-oracle-free</artifactId>
    <scope>test</scope>
</dependency>
```

If external API calls are made, then `wiremock` dependency can be used to mock external API calls.
```xml
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <scope>test</scope>
</dependency>
```

## Unit Testing with JUnit 5

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @InjectMocks private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    // ❌ BAD: Mutable test data, can be modified in tests
    private final UserCreateRequest request = new UserCreateRequest("test@example.com", "Password123", "testuser", 25);

    // ✅ GOOD: Immutable test data, cannot be modified in tests
    private final UserCreateRequest userCreateRequest = UserCreateRequestFactory.create();

    @Test
    @DisplayName("Should create user successfully")
    void shouldCreateUser() {
        // Given
        final User user = new User();
        user.setId(1L);
        user.setEmail(request.email());
        user.setUsername(request.username());

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        final UserResponse response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo(request.email());

        verify(userRepository).existsByEmail(request.email());
        verify(passwordEncoder).encode(request.password());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        // Given
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.create(request))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any(User.class));
    }
}
```

## Integration Testing with @SpringBootTest

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserIntegrationTest {
    @Inject private TestRestTemplate restTemplate;
    @Inject private UserRepository userRepository;

    private final UserCreateRequest request = new UserCreateRequest("test@example.com", "Password123", "testuser", 25);

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Should create user via API")
    void shouldCreateUserViaApi() {
        // When
        final ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/users", request,
            UserResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo(request.email());
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Should return validation error for invalid request")
    void shouldReturnValidationError() {
        // When
        final ResponseEntity<ValidationErrorResponse> response = restTemplate.postForEntity("/api/v1/users", request,
            ValidationErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).isNotEmpty();
    }
}
```

## Web Layer Testing with MockMvc

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {
    @Inject private MockMvc mockMvc;
    @Inject private ObjectMapper objectMapper;
    @MockBean private UserService userService;

    private final UserCreateRequest request = new UserCreateRequest("test@example.com", "Password123", "testuser", 25);

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should get all users")
    void shouldGetAllUsers() throws Exception {
        // Given
        final Page<UserResponse> users = new PageImpl<>(List.of(
            new UserResponse(1L, "user1@example.com", "user1", 25, true, null, null),
            new UserResponse(2L, "user2@example.com", "user2", 30, true, null, null)
        ));

        when(userService.findAll(any(Pageable.class))).thenReturn(users);

        // When & Then
        mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].email").value("user1@example.com"))
            .andDo(print());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should create user")
    void shouldCreateUser() throws Exception {
        // Given
        final UserResponse response = new UserResponse(
            1L,
            request.email(),
            request.username(),
            request.age(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(userService.create(any(UserCreateRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.email").value(request.email()))
            .andExpect(jsonPath("$.username").value(request.username()))
            .andDo(print());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should return 403 for non-admin user")
    void shouldReturn403ForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
```

## Data JPA Testing

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {
    @Inject private UserRepository userRepository;
    @Inject private TestEntityManager entityManager;

    private final User user = User.builder()
            .email("test@example.com")
            .password("password")
            .username("testuser")
            .active(true)
            .build();

    @Test
    @DisplayName("Should find user by email")
    void shouldFindUserByEmail() {
        // Given
        entityManager.persistAndFlush(user);
        // When
        final Optional<User> found = userRepository.findByEmail("test@example.com");
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should check if email exists")
    void shouldCheckIfEmailExists() {
        // Given
        entityManager.persistAndFlush(user);
        // When
        final boolean exists = userRepository.existsByEmail("test@example.com");
        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should fetch user with roles")
    void shouldFetchUserWithRoles() {
        // Given
        final Role adminRole = Role.builder().name("ADMIN").build();
        entityManager.persist(adminRole);
        user.setRoles(Set.of(adminRole));
        entityManager.persistAndFlush(user);
        entityManager.clear();
        // When
        final Optional<User> found = userRepository.findByEmailWithRoles("admin@example.com");
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).hasSize(1);
        assertThat(found.get().getRoles()).extracting(Role::getName).contains("ADMIN");
    }
}
```

## Testcontainers for Database

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UserServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Inject private UserService userService;
    @Inject private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create and find user in real database")
    void shouldCreateAndFindUser() {
        // Given
        final UserCreateRequest request = new UserCreateRequest("test@example.com", "Password123", "testuser", 25);

        // When
        final UserResponse created = userService.create(request);
        final UserResponse found = userService.findById(created.id());

        // Then
        assertThat(found).isNotNull();
        assertThat(found.email()).isEqualTo(request.email());
    }
}
```

## Testing Reactive Endpoints with WebTestClient

```java
@WebFluxTest(UserReactiveController.class)
class UserReactiveControllerTest {
    @Inject private WebTestClient webTestClient;
    @MockBean private UserReactiveService userService;

    private final UserCreateRequest request = new UserCreateRequest("test@example.com", "Password123", "testuser", 25);

    @Test
    @DisplayName("Should get user reactively")
    void shouldGetUserReactively() {
        // Given
        final UserResponse user = new UserResponse(
            1L,
            "test@example.com",
            "testuser",
            25,
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(userService.findById(1L)).thenReturn(Mono.just(user));

        // When & Then
        webTestClient.get()
            .uri("/api/v1/users/{id}", 1L)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserResponse.class)
            .value(response -> {
                assertThat(response.id()).isEqualTo(1L);
                assertThat(response.email()).isEqualTo("test@example.com");
            });
    }

    @Test
    @DisplayName("Should create user reactively")
    void shouldCreateUserReactively() {
        // Given
        final UserResponse response = new UserResponse(
            1L,
            request.email(),
            request.username(),
            request.age(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(userService.create(any(UserCreateRequest.class))).thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
            .uri("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(request), UserCreateRequest.class)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectBody(UserResponse.class)
            .value(user -> {
                assertThat(user.email()).isEqualTo(request.email());
            });
    }
}
```

## Wiremock for External API Mocking

```java
@SpringBootTest
class PaymentServiceTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();
    
    // make the related configure for wiremock server port in the application properties for test profile using wireMock.getPort()

    @Test
    @DisplayName("Should retrieve payment successfully")
    void shouldRetrievePayment() {

        stubFor(get(urlEqualTo("/payments/1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": 1,
                        "status": "SUCCESS",
                        "amount": 250
                    }
                """)));

        final PaymentResponse response = paymentService.getPayment(1L);

        assertThat(response.status()).isEqualTo("SUCCESS");

        verify(getRequestedFor(urlEqualTo("/payments/1")));
    }
}
```

### Wiremock Best Practices

- Mock only external services, not your own application.
- Verify outgoing requests whenever possible.
- Prefer request matching over exact JSON string comparison.
- Simulate error responses (4xx, 5xx), timeouts, and network failures in addition to successful responses.
- Keep stubbed responses small and focused on the fields required by the test.
- Reset WireMock between tests to prevent test interference.
- Use random ports (`WireMockServer(options().dynamicPort())`) to avoid port conflicts in parallel test execution.
- Store large response bodies under `src/test/resources` and load them instead of embedding large JSON strings in tests.

## Testing Configuration

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  security:
    user:
      name: test
      password: test

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```
```java
// Test Configuration Class
@TestConfiguration
public class TestConfig {
    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4); // Faster for tests
    }

    @Bean
    public Clock fixedClock() {
        return Clock.fixed(
            Instant.parse("2024-01-01T00:00:00Z"),
            ZoneId.of("UTC")
        );
    }
}
```

## Test Fixtures

```java
@Component
public class TestDataFactory {
    public static User createUser(String email, String username) {
        return User.builder()
            .email(email)
            .password("encodedPassword")
            .username(username)
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public static UserCreateRequest createUserRequest() {
        return new UserCreateRequest(
            "test@example.com",
            "Password123",
            "testuser",
            25
        );
    }
}
```

> Use @Inject to autowire the dependencies for injection in tests. Use @MockBean to mock dependencies in Spring context. Use @WithMockUser to simulate authenticated users in security tests.
> Always use factory methods to create test data to avoid duplication and maintain consistency across tests. Factory methods should be under `factory` package or `objectfactory` package.
> Final keyword should be used to create immutable test data objects, ensuring that test data remains unchanged during test execution unless explicitly modified.

## Quick Reference

| Annotation | Purpose |
|------------|---------|
| `@SpringBootTest` | Full application context integration test |
| `@WebMvcTest` | Test MVC controllers with mocked services |
| `@WebFluxTest` | Test reactive controllers |
| `@DataJpaTest` | Test JPA repositories with in-memory database |
| `@MockBean` | Add mock bean to Spring context |
| `@WithMockUser` | Mock authenticated user for security tests |
| `@Testcontainers` | Enable Testcontainers support |
| `@ActiveProfiles` | Activate specific Spring profiles for test |

## Testing Best Practices

- Write tests following Given-When-Then pattern
- Use descriptive test names with @DisplayName
- Mock external dependencies, use real DB with Testcontainers
- Achieve 85%+ code coverage
- Test happy path and edge cases
- Use @Transactional for test data cleanup
- Separate unit tests from integration tests
- Use parameterized tests for multiple scenarios
- Test security rules and validation
- Keep tests fast and independent