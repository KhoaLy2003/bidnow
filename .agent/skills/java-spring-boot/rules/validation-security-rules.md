# Validation & Security Rules

## Standards

### Input Validation
- Validate at controller layer using Jakarta Bean Validation
- Apply business rule validation at service layer
- Use custom validators for complex validation logic
- Provide meaningful error messages
- Handle validation failures gracefully

### Security Configuration
- Separate security concerns by domain
- Use method-level security where appropriate
- Configure CORS properly for web applications
- Handle authentication and authorization consistently
- Secure sensitive endpoints and data

### Authentication & Authorization
- Use JWT tokens for stateless authentication
- Implement role-based and permission-based access control
- Use custom annotations for user context injection
- Validate user ownership for resource access
- Handle security exceptions appropriately

## Input Validation Patterns

### Request DTO Validation
```java
public class CreateUserRequest {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$", 
             message = "Password must contain at least one lowercase letter, one uppercase letter, and one digit")
    private String password;
    
    @Valid
    @NotNull(message = "Profile information is required")
    private CreateProfileRequest profile;
    
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;
    
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    @DecimalMax(value = "999999.99", message = "Price cannot exceed 999,999.99")
    private BigDecimal price;
}
```

### Custom Validation Annotations
```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueUsernameValidator.class)
public @interface UniqueUsername {
    String message() default "Username already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class UniqueUsernameValidator implements ConstraintValidator<UniqueUsername, String> {
    
    @Autowired
    private UserService userService;
    
    @Override
    public boolean isValid(String username, ConstraintValidatorContext context) {
        if (username == null || username.trim().isEmpty()) {
            return true; // Let @NotBlank handle this
        }
        return !userService.existsByUsername(username);
    }
}
```

### Conditional Validation
```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ConditionalValidator.class)
public @interface ConditionalValidation {
    String message() default "Conditional validation failed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class ConditionalValidator implements ConstraintValidator<ConditionalValidation, Object> {
    
    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        if (obj instanceof ConditionalRequest) {
            ConditionalRequest request = (ConditionalRequest) obj;
            
            if ("PREMIUM".equals(request.getType()) && request.getLicenseKey() == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("License key is required for premium type")
                       .addPropertyNode("licenseKey")
                       .addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
```

## Security Configuration

### Main Security Configuration
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    
    @Value("${security.jwt.issuer}")
    private String jwtIssuer;
    
    @Value("${security.cors.allowed-origins}")
    private List<String> allowedOrigins;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/health/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                
                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // User endpoints
                .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                .requestMatchers("/api/users/**").hasAnyRole("USER", "ADMIN")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    .decoder(jwtDecoder())
                )
            )
            .build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(jwtIssuer + "/.well-known/jwks.json")
            .build();
        decoder.setJwtValidator(jwtValidator());
        return decoder;
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("permissions");
        
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // ❌ Do NOT use allowedHeaders("*") with allowCredentials(true) — violates CORS spec
        // Modern browsers block this combination. Explicitly list required headers.
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "Accept",
            "X-Correlation-ID", "X-Request-ID", "X-Requested-With"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
    
    private Oauth2TokenValidator<Jwt> jwtValidator() {
        List<Oauth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(jwtIssuer));
        return new DelegatingOauth2TokenValidator<>(validators);
    }
}
```

### Method-Level Security
```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    @PreAuthorize("hasRole('ADMIN') or @userSecurityService.isCurrentUser(#userId)")
    public UserResponse getUserById(UUID userId) {
        // Implementation
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getAllUsers() {
        // Implementation
    }
    
    @PreAuthorize("@userSecurityService.canEditUser(#userId, authentication.name)")
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        // Implementation
    }
    
    @PostAuthorize("hasRole('ADMIN') or returnObject.id == authentication.principal.userId")
    public UserResponse createUser(CreateUserRequest request) {
        // Implementation
    }
}

@Component
public class UserSecurityService {
    
    private final UserRepository userRepository;
    
    public boolean isCurrentUser(UUID userId) {
        return SecurityUtils.getCurrentUserId()
            .map(currentUserId -> currentUserId.equals(userId))
            .orElse(false);
    }
    
    public boolean canEditUser(UUID userId, String username) {
        if (SecurityUtils.hasRole("ADMIN")) {
            return true;
        }
        
        return userRepository.findByUsername(username)
            .map(user -> user.getId().equals(userId))
            .orElse(false);
    }
    
    public boolean canViewProfile(UUID userId, String username) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        
        // Public profiles can be viewed by anyone
        if (user.isPublicProfile()) {
            return true;
        }
        
        // Private profiles only by owner or admin
        return isCurrentUser(userId) || SecurityUtils.hasRole("ADMIN");
    }
}
```

### Custom Security Annotations
```java
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {
    
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class) 
            && parameter.getParameterType().equals(UUID.class);
    }
    
    @Override
    public Object resolveArgument(MethodParameter parameter, 
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, 
                                WebDataBinderFactory binderFactory) {
        
        return SecurityUtils.getCurrentUserId()
            .orElseThrow(() -> new AccessDeniedException("User not authenticated"));
    }
}
```

## Security Exception Handling

### JWT Authentication Entry Point
```java
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    // ✅ Inject singleton ObjectMapper — never instantiate new ObjectMapper() per request
    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException {
        
        log.warn("Unauthorized access attempt: {}", authException.getMessage());
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .code("UNAUTHORIZED")
            .message("Authentication required")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
            
        ApiResponse<ErrorDetails> apiResponse = ApiResponse.error("Authentication required", errorDetails);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
```

### JWT Access Denied Handler
```java
@Component
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    
    // ✅ Inject singleton ObjectMapper — never instantiate new ObjectMapper() per request
    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void handle(HttpServletRequest request, 
                      HttpServletResponse response,
                      AccessDeniedException accessDeniedException) throws IOException {
        
        log.warn("Access denied for user: {}", SecurityUtils.getCurrentUsername().orElse("anonymous"));
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .code("ACCESS_DENIED")
            .message("Insufficient permissions")
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
            
        ApiResponse<ErrorDetails> apiResponse = ApiResponse.error("Access denied", errorDetails);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
```

## Data Security Patterns

### Field-Level Security
```java
@Entity
public class User extends BaseEntity {
    
    private String username;
    
    @JsonIgnore // Never serialize in JSON
    private String password;
    
    @Column(name = "email")
    private String email;
    
    @JsonView(Views.Admin.class) // Only visible to admin
    private String internalNotes;
    
    @JsonView({Views.Owner.class, Views.Admin.class}) // Only visible to owner or admin
    private String privateData;
}

public class Views {
    public static class Public {}
    public static class Owner extends Public {}
    public static class Admin extends Owner {}
}

@RestController
public class UserController {
    
    @GetMapping("/users/{id}")
    @JsonView(Views.Public.class)
    public UserResponse getPublicProfile(@PathVariable UUID id) {
        return userService.getUserById(id);
    }
    
    @GetMapping("/users/{id}/private")
    @JsonView(Views.Owner.class)
    @PreAuthorize("@userSecurityService.isCurrentUser(#id)")
    public UserResponse getPrivateProfile(@PathVariable UUID id) {
        return userService.getUserById(id);
    }
}
```

### Data Masking
```java
@Component
public class DataMaskingService {
    
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 2) {
            return "*".repeat(username.length()) + "@" + domain;
        }
        
        return username.charAt(0) + "*".repeat(username.length() - 2) + 
               username.charAt(username.length() - 1) + "@" + domain;
    }
    
    public String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
    
    public String maskCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return cardNumber;
        }
        
        return "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4);
    }
}
```

## Rate Limiting

### Rate Limiting Configuration
```java
@Configuration
@ConditionalOnProperty(name = "security.rate-limiting.enabled", havingValue = "true")
public class RateLimitingConfig {
    
    @Bean
    public RedisTemplate<String, String> rateLimitRedisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
    
    @Bean
    public RateLimitingService rateLimitingService(RedisTemplate<String, String> rateLimitRedisTemplate) {
        return new RateLimitingService(rateLimitRedisTemplate);
    }
}

@Component
@RequiredArgsConstructor
public class RateLimitingService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Lua script for atomic INCR + EXPIRE — prevents race condition where two
    // concurrent requests both see count=0 and both create keys with TTL,
    // effectively allowing 2x the intended limit on the first window.
    private static final String RATE_LIMIT_LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])
        local current = redis.call('INCR', key)
        if current == 1 then
            redis.call('EXPIRE', key, window)
        end
        if current > limit then
            return 0
        end
        return 1
        """;
    
    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_LUA_SCRIPT, Long.class);

    public boolean isAllowed(String key, int maxRequests, Duration window) {
        String redisKey = "rate_limit:" + key;
        
        try {
            // Atomic Lua script: INCR and set TTL in one operation — no race condition
            Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(redisKey),
                String.valueOf(maxRequests),
                String.valueOf(window.getSeconds())
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            // If Redis is down, fail open (allow request) — log the degradation
            log.warn("Rate limiter Redis error, failing open: {}", e.getMessage());
            return true;
        }
    }
}
```

### Rate Limiting Interceptor
```java
@Component
@RequiredArgsConstructor
public class RateLimitingInterceptor implements HandlerInterceptor {
    
    private final RateLimitingService rateLimitingService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        
        if (rateLimit != null) {
            String key = generateKey(request, rateLimit);
            Duration window = Duration.ofSeconds(rateLimit.windowSeconds());
            
            if (!rateLimitingService.isAllowed(key, rateLimit.maxRequests(), window)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                
                ErrorDetails errorDetails = ErrorDetails.builder()
                    .code("RATE_LIMIT_EXCEEDED")
                    .message("Rate limit exceeded. Please try again later.")
                    .timestamp(Instant.now())
                    .build();
                    
                ApiResponse<ErrorDetails> apiResponse = ApiResponse.error("Rate limit exceeded", errorDetails);
                
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(response.getOutputStream(), apiResponse);
                
                return false;
            }
        }
        
        return true;
    }
    
    private String generateKey(HttpServletRequest request, RateLimit rateLimit) {
        String identifier;
        
        switch (rateLimit.keyType()) {
            case IP:
                identifier = getClientIpAddress(request);
                break;
            case USER:
                identifier = SecurityUtils.getCurrentUserId()
                    .map(UUID::toString)
                    .orElse(getClientIpAddress(request));
                break;
            default:
                identifier = getClientIpAddress(request);
        }
        
        return rateLimit.name() + ":" + identifier;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String name() default "";
    int maxRequests() default 100;
    int windowSeconds() default 3600;
    KeyType keyType() default KeyType.IP;
    
    enum KeyType {
        IP, USER
    }
}
```