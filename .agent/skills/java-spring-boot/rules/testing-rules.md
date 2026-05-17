# Testing Rules

## Standards

### Test Architecture
- Aim for a healthy **Testing Pyramid**: a high volume of Unit Tests, a focused set of Integration Tests, and minimal End-to-End tests
- Organize tests in the same package structure under `src/test/java`
- Ensure tests are completely isolated — running tests must never affect external environments or depend on the execution order of other tests

### Unit Testing (Service Layer)
- Use **JUnit 5** and **Mockito** for unit testing business logic
- Use `@ExtendWith(MockitoExtension.class)` to enable Mockito annotations
- Mock all external dependencies (Repositories, Clients, Services)
- Focus on testing business scenarios, edge cases, and exception handling

### Controller Slice Testing (Web Layer)
- Use `@WebMvcTest` to test controllers in isolation (loads only the web slice, keeping tests fast)
- Inject dependencies using `@MockBean`
- Use `MockMvc` to perform requests and assert responses (status, body contents, headers)
- Verify request validation (JSR-380 annotations) triggers 400 Bad Request

### Repository Slice Testing (Data Layer)
- Use `@DataJpaTest` to test JPA repository queries and custom Specification logic in isolation
- Use **Testcontainers** with a real PostgreSQL container instead of H2 database. H2 does not support PG-specific functions (like `ILIKE`, JSONB, window functions) and behaves differently under transactions
- Disable default test database auto-replacement: `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`

### Integration Testing
- Use `@SpringBootTest` to test the full application context context-loaded
- Configure the context with `@ActiveProfiles("test")`
- Use Testcontainers to spin up dependent systems (PostgreSQL, Redis)
- Keep database state clean between tests by using `@Transactional` (rollback by default) or clean-up scripts

---

## Unit Test Example (Service Layer)

```java
@ExtendWith(MockitoExtension.class)
class SampleEntityServiceTest {

    @Mock
    private SampleEntityRepository repository;

    @Mock
    private SampleEntityMapper mapper;

    @InjectMocks
    private SampleEntityService service;

    private UUID entityId;
    private SampleEntity entity;
    private EntityResponse response;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        entity = new SampleEntity("Test Entity", "Description", EntityStatus.ACTIVE);
        entity.setId(entityId);
        response = new EntityResponse(entityId, "Test Entity", "Description", EntityStatus.ACTIVE);
    }

    @Test
    void getEntityById_WhenEntityExists_ShouldReturnResponse() {
        // Arrange
        when(repository.findByIdAndDeletedAtIsNull(entityId)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(response);

        // Act
        EntityResponse result = service.getEntityById(entityId);

        // Assert
        assertNotNull(result);
        assertEquals(entityId, result.getId());
        assertEquals("Test Entity", result.getName());
        verify(repository, times(1)).findByIdAndDeletedAtIsNull(entityId);
    }

    @Test
    void getEntityById_WhenEntityDoesNotExist_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(repository.findByIdAndDeletedAtIsNull(entityId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> service.getEntityById(entityId));
        verify(repository, times(1)).findByIdAndDeletedAtIsNull(entityId);
        verifyNoInteractions(mapper);
    }
}
```

---

## Controller Test Example (Web Layer)

```java
@WebMvcTest(SampleEntityController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable spring security filters for pure web slice tests
class SampleEntityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SampleEntityService service;

    private UUID entityId;
    private EntityResponse response;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        response = new EntityResponse(entityId, "Valid Name", "Description", EntityStatus.ACTIVE);
    }

    @Test
    void getEntity_WhenFound_ShouldReturn200AndData() throws Exception {
        // Arrange
        when(service.getEntityById(entityId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/entities/{id}", entityId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(entityId.toString()))
            .andExpect(jsonPath("$.data.name").value("Valid Name"));
    }

    @Test
    void createEntity_WhenNameIsBlank_ShouldReturn400BadRequest() throws Exception {
        // Arrange
        CreateEntityRequest invalidRequest = new CreateEntityRequest("", "Description");

        // Act & Assert
        mockMvc.perform(post("/api/entities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Validation failed"));
            
        verifyNoInteractions(service);
    }
}
```

---

## Repository & Testcontainers Integration Test Example

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
class SampleEntityRepositoryTest {

    // ✅ Testcontainers runs a real PostgreSQL instance to guarantee PostgreSQL-specific compatibility
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SampleEntityRepository repository;

    @Test
    void findActiveById_WhenEntityIsSoftDeleted_ShouldReturnEmpty() {
        // Arrange
        SampleEntity active = new SampleEntity("Active Entity", "Active Desc", EntityStatus.ACTIVE);
        SampleEntity deleted = new SampleEntity("Deleted Entity", "Deleted Desc", EntityStatus.ACTIVE);
        deleted.softDelete(); // Set deletedAt timestamp

        repository.save(active);
        repository.save(deleted);

        // Act
        Optional<SampleEntity> activeResult = repository.findActiveById(active.getId());
        Optional<SampleEntity> deletedResult = repository.findActiveById(deleted.getId());

        // Assert
        assertTrue(activeResult.isPresent());
        assertFalse(deletedResult.isPresent());
    }
}
```

---

## Anti-Patterns to Avoid

### ❌ Using H2 for Repository/Integration Tests
H2 has a different query planner, behaves differently with transactions, and lacks PG features (e.g., jsonb columns, custom extensions, native PostgreSQL syntax). Tests that pass on H2 can easily fail in production. Use **Testcontainers PostgreSQL** instead.

### ❌ Polluting Contexts across `@SpringBootTest` runs
Writing dirty data or changing system properties inside an integration test can cause downstream tests to fail unpredictably. Make sure tests are either wrapped in transactional rollbacks or use `@DirtiesContext` when application state is modified globally.

### ❌ Sleeping in Threads (`Thread.sleep()`)
Never use `Thread.sleep()` to wait for asynchronous execution in tests. This is brittle and slows down pipelines. Use **Awaitility** instead:
```java
// ✅ Use Awaitility for resilient async assertions
Awaitility.await()
    .atMost(Duration.ofSeconds(5))
    .untilAsserted(() -> verify(eventListener).handleUpdate(any()));
```
