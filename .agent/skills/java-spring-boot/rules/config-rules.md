# Configuration Rules

## Standards

### Class Definition
- Use `@Configuration` annotation
- Use `@EnableXxx` annotations for feature activation
- Use `@ConditionalOnProperty` for feature toggles
- Externalize configuration with `@Value` or `@ConfigurationProperties`
- Provide sensible defaults

### Bean Definition
- Use `@Bean` for explicit bean creation
- Use `@Primary` for default beans when multiple exist
- Use `@Qualifier` for bean disambiguation
- Handle configuration validation
- Log configuration details

### Security Configuration
- Separate security concerns by domain
- Use method-level security where appropriate
- Configure CORS properly
- Handle authentication and authorization
- Secure sensitive endpoints

## Example Templates

### Basic Configuration Template
```java
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "feature.caching.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CacheConfig {
    
    @Value("${cache.default-ttl:3600}")
    private long defaultTtl;
    
    @Value("${cache.max-size:1000}")
    private long maxSize;
    
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(Duration.ofSeconds(defaultTtl))
            .recordStats());
            
        log.info("Cache manager configured with TTL: {}s, Max size: {}", defaultTtl, maxSize);
        return cacheManager;
    }
    
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler();
    }
}
```

### Database Configuration Template
```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.repository")
@EnableJpaAuditing
@Slf4j
public class DatabaseConfig {
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(datasourceUrl);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        log.info("Database connection pool configured for: {}", datasourceUrl);
        return new HikariDataSource(config);
    }
    
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
    
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }
}
```

### Security Configuration Template
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
                .requestMatchers("/api/health/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwtIssuer + "/.well-known/jwks.json").build();
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
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
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

### Redis Configuration Template
```java
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RedisConfig implements CachingConfigurer {
    
    @Value("${spring.data.redis.host}")
    private String redisHost;
    
    @Value("${spring.data.redis.port}")
    private int redisPort;
    
    @Value("${spring.data.redis.password}")
    private String redisPassword;
    
    @Value("${redis.cache.ttl:3600}")
    private long cacheTtl;
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        log.info("Connecting to Redis: {}:{}", redisHost, redisPort);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        config.setPassword(redisPassword);
        
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .shutdownTimeout(Duration.ofSeconds(5))
            .build();
            
        return new LettuceConnectionFactory(config, clientConfig);
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory());
        
        // Key serialization
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value serialization
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        log.info("Redis template configured successfully");
        return template;
    }
    
    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(cacheTtl))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())));
            
        return RedisCacheManager.builder(lettuceConnectionFactory())
            .cacheDefaults(config)
            .transactionAware()
            .build();
    }
    
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
}
```

### WebSocket Configuration Template
```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final StompAuthChannelInterceptor stompAuthInterceptor;
    private final WebSocketLoggingInterceptor loggingInterceptor;
    
    @Value("${websocket.allowed-origins}")
    private List<String> allowedOrigins;
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        
        log.info("WebSocket message broker configured");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(allowedOrigins.toArray(new String[0]))
            .withSockJS()
            .setSessionCookieNeeded(false);
            
        registry.addEndpoint("/ws-native")
            .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
            
        log.info("WebSocket endpoints registered for origins: {}", allowedOrigins);
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor, loggingInterceptor);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20000);
    }
}
```

### OpenAPI Configuration Template
```java
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {
    
    @Value("${app.name:Application}")
    private String appName;
    
    @Value("${app.version:1.0.0}")
    private String appVersion;
    
    @Value("${app.description:API Documentation}")
    private String appDescription;
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title(appName + " API")
                .version(appVersion)
                .description(appDescription)
                .contact(new Contact()
                    .name("Development Team")
                    .email("dev@example.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
    
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/public/**")
            .build();
    }
    
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")
            .build();
    }
}
```

## Configuration Properties Pattern

### Type-Safe Configuration
```java
@ConfigurationProperties(prefix = "app.feature")
@Data
@Validated
public class FeatureConfigurationProperties {
    
    @NotNull
    @Valid
    private Cache cache = new Cache();
    
    @NotNull
    @Valid
    private Security security = new Security();
    
    @NotNull
    @Valid
    private Integration integration = new Integration();
    
    @Data
    public static class Cache {
        @Min(1)
        private int ttlSeconds = 3600;
        
        @Min(1)
        private int maxSize = 1000;
        
        private boolean enabled = true;
    }
    
    @Data
    public static class Security {
        @NotEmpty
        private List<String> allowedOrigins = List.of("http://localhost:3000");
        
        @NotBlank
        private String jwtIssuer;
        
        @Min(1)
        private int tokenExpirationMinutes = 60;
    }
    
    @Data
    public static class Integration {
        @NotBlank
        private String apiUrl;
        
        @Min(1000)
        private int timeoutMs = 5000;
        
        @Min(1)
        private int retryAttempts = 3;
    }
}

@Configuration
@EnableConfigurationProperties(FeatureConfigurationProperties.class)
public class FeatureConfig {
    
    @Bean
    public FeatureService featureService(FeatureConfigurationProperties properties) {
        return new FeatureService(properties);
    }
}
```

### Environment-Specific Configuration
```yaml
# application.yml
app:
  feature:
    cache:
      enabled: true
      ttl-seconds: 3600
      max-size: 1000
    security:
      allowed-origins:
        - "http://localhost:3000"
        - "https://app.example.com"
      jwt-issuer: "https://auth.example.com"
      token-expiration-minutes: 60
    integration:
      api-url: "https://api.example.com"
      timeout-ms: 5000
      retry-attempts: 3

---
spring:
  config:
    activate:
      on-profile: development
      
app:
  feature:
    cache:
      enabled: false
    security:
      allowed-origins:
        - "http://localhost:3000"
        - "http://localhost:3001"

---
spring:
  config:
    activate:
      on-profile: production
      
app:
  feature:
    cache:
      ttl-seconds: 7200
      max-size: 10000
    integration:
      timeout-ms: 10000
```