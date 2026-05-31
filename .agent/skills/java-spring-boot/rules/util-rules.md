# Utility Rules

## Standards

### Utility Class Design
- Use `@UtilityClass` from Lombok or make class final with private constructor
- Make all methods static and stateless
- Focus on reusable, generic functionality
- Validate input parameters
- Provide constants for common values
- Use descriptive method names

### Common Utility Categories
- Pagination utilities
- Security utilities
- Date/Time utilities
- String utilities
- Collection utilities
- Validation utilities
- Conversion utilities

### Best Practices
- Keep utilities focused on single responsibility
- Avoid dependencies on Spring context
- Make utilities easily testable
- Handle null inputs gracefully
- Provide overloaded methods for convenience

## Example Templates

### Pagination Utilities
```java
@UtilityClass
public class PaginationUtils {
    
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_PAGE_SIZE = 1;
    
    public static Pageable createPageable(int page, int size) {
        return createPageable(page, size, Sort.unsorted());
    }
    
    public static Pageable createPageable(int page, int size, Sort sort) {
        int validatedSize = validatePageSize(size);
        int validatedPage = validatePageNumber(page);
        return PageRequest.of(validatedPage, validatedSize, sort);
    }
    
    public static Pageable createPageable(int page, int size, String sortBy, String sortDirection) {
        Sort sort = createSort(sortBy, sortDirection);
        return createPageable(page, size, sort);
    }
    
    public static Sort createSort(String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            return Sort.unsorted();
        }
        
        Sort.Direction direction = Sort.Direction.ASC;
        if ("DESC".equalsIgnoreCase(sortDirection) || "desc".equalsIgnoreCase(sortDirection)) {
            direction = Sort.Direction.DESC;
        }
        
        return Sort.by(direction, sortBy);
    }
    
    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .isFirst(page.isFirst())
            .isLast(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> toPageResponse(Page<T> page, List<R> mappedContent) {
        return PageResponse.<R>builder()
            .content(mappedContent)
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .isFirst(page.isFirst())
            .isLast(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    private static int validatePageSize(int size) {
        if (size < MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
    
    private static int validatePageNumber(int page) {
        return Math.max(page, 0);
    }
}
```

### Security Utilities
```java
@UtilityClass
public class SecurityUtils {
    
    public static Optional<String> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return Optional.of(userDetails.getUsername());
        }
        
        if (authentication.getPrincipal() instanceof String) {
            return Optional.of((String) authentication.getPrincipal());
        }
        
        return Optional.empty();
    }
    
    public static Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
            String userId = jwtToken.getToken().getClaimAsString("sub");
            
            if (StringUtils.isNotBlank(userId)) {
                try {
                    return Optional.of(UUID.fromString(userId));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        
        return Optional.empty();
    }
    
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
    
    public static boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        Set<String> roleSet = Arrays.stream(roles)
            .map(role -> "ROLE_" + role)
            .collect(Collectors.toSet());
            
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> roleSet.contains(authority.getAuthority()));
    }
    
    public static boolean isCurrentUser(UUID userId) {
        return getCurrentUserId()
            .map(currentUserId -> currentUserId.equals(userId))
            .orElse(false);
    }
    
    public static void requireAuthentication() {
        if (getCurrentUserId().isEmpty()) {
            throw new AccessDeniedException("Authentication required");
        }
    }
    
    public static void requireRole(String role) {
        if (!hasRole(role)) {
            throw new AccessDeniedException("Role " + role + " required");
        }
    }
}
```

### Date/Time Utilities
```java
@UtilityClass
public class DateTimeUtils {
    
    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    
    public static Instant now() {
        return Instant.now();
    }
    
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(UTC).toInstant();
    }
    
    public static Instant endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX).atZone(UTC).toInstant();
    }
    
    public static Instant startOfMonth(YearMonth yearMonth) {
        return yearMonth.atDay(1).atStartOfDay(UTC).toInstant();
    }
    
    public static Instant endOfMonth(YearMonth yearMonth) {
        return yearMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(UTC).toInstant();
    }
    
    public static boolean isBetween(Instant instant, Instant start, Instant end) {
        if (instant == null || start == null || end == null) {
            return false;
        }
        return !instant.isBefore(start) && !instant.isAfter(end);
    }
    
    public static boolean isToday(Instant instant) {
        if (instant == null) {
            return false;
        }
        
        LocalDate today = LocalDate.now(UTC);
        LocalDate instantDate = instant.atZone(UTC).toLocalDate();
        return today.equals(instantDate);
    }
    
    public static boolean isWithinLast(Instant instant, Duration duration) {
        if (instant == null || duration == null) {
            return false;
        }
        
        Instant threshold = Instant.now().minus(duration);
        return instant.isAfter(threshold);
    }
    
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "0 seconds";
        }
        
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%d day%s", days, days == 1 ? "" : "s");
        } else if (hours > 0) {
            return String.format("%d hour%s", hours, hours == 1 ? "" : "s");
        } else if (minutes > 0) {
            return String.format("%d minute%s", minutes, minutes == 1 ? "" : "s");
        } else {
            return String.format("%d second%s", seconds, seconds == 1 ? "" : "s");
        }
    }
    
    public static String formatRelativeTime(Instant instant) {
        if (instant == null) {
            return "unknown";
        }
        
        Duration duration = Duration.between(instant, Instant.now());
        
        if (duration.isNegative()) {
            return "in " + formatDuration(duration.abs());
        } else {
            return formatDuration(duration) + " ago";
        }
    }
}
```

### String Utilities
```java
@UtilityClass
public class StringUtils {
    
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
    
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }
    
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    public static String slugify(String input) {
        if (isBlank(input)) {
            return "";
        }
        
        return input.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
    
    public static String maskEmail(String email) {
        if (isBlank(email) || !email.contains("@")) {
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
    
    // ❌ Never use new Random() for security-sensitive values
    // Random is not cryptographically secure and not thread-safe
    // ✅ Use SecureRandom for tokens, keys, or any value requiring unpredictability
    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        
        return random.ints(length, 0, chars.length())
            .mapToObj(chars::charAt)
            .map(Object::toString)
            .collect(Collectors.joining());
    }
    
    // For opaque tokens (e.g., email verification, password reset), prefer:
    // UUID.randomUUID().toString() — simpler and universally unique
    // Or: HexFormat.of().formatHex(SecureRandom.getInstanceStrong().generateSeed(32))
    
    public static String camelToSnakeCase(String camelCase) {
        if (isBlank(camelCase)) {
            return camelCase;
        }
        
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    public static String snakeToCamelCase(String snakeCase) {
        if (isBlank(snakeCase)) {
            return snakeCase;
        }
        
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase());
        
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (isNotBlank(part)) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase());
            }
        }
        
        return result.toString();
    }
}
```

### Collection Utilities
```java
@UtilityClass
public class CollectionUtils {
    
    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }
    
    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return !isEmpty(collection);
    }
    
    public static <T> List<T> nullSafeList(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }
    
    public static <T> Set<T> nullSafeSet(Set<T> set) {
        return set != null ? set : new HashSet<>();
    }
    
    public static <K, V> Map<K, V> nullSafeMap(Map<K, V> map) {
        return map != null ? map : new HashMap<>();
    }
    
    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (isEmpty(list) || size <= 0) {
            return new ArrayList<>();
        }
        
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        
        return partitions;
    }
    
    public static <T> Optional<T> getFirst(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        
        return collection.stream().findFirst();
    }
    
    public static <T> Optional<T> getLast(List<T> list) {
        if (isEmpty(list)) {
            return Optional.empty();
        }
        
        return Optional.of(list.get(list.size() - 1));
    }
    
    public static <T> List<T> intersection(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1) || isEmpty(collection2)) {
            return new ArrayList<>();
        }
        
        return collection1.stream()
            .filter(collection2::contains)
            .collect(Collectors.toList());
    }
    
    public static <T> List<T> difference(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1)) {
            return new ArrayList<>();
        }
        
        Set<T> set2 = nullSafeSet(collection2 instanceof Set ? (Set<T>) collection2 : new HashSet<>(collection2));
        
        return collection1.stream()
            .filter(item -> !set2.contains(item))
            .collect(Collectors.toList());
    }
    
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> collection, Function<T, K> keyExtractor) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        
        return collection.stream()
            .collect(Collectors.groupingBy(keyExtractor));
    }
    
    public static <T> List<T> removeDuplicates(List<T> list) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        
        return list.stream()
            .distinct()
            .collect(Collectors.toList());
    }
}
```

### Validation Utilities
```java
@UtilityClass
public class ValidationUtils {
    
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    
    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static final Pattern UUID_PATTERN = Pattern.compile(UUID_REGEX);
    
    public static boolean isValidEmail(String email) {
        return StringUtils.isNotBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }
    
    public static boolean isValidUUID(String uuid) {
        return StringUtils.isNotBlank(uuid) && UUID_PATTERN.matcher(uuid).matches();
    }
    
    public static boolean isValidUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
    
    public static boolean isPositive(Number number) {
        return number != null && number.doubleValue() > 0;
    }
    
    public static boolean isNonNegative(Number number) {
        return number != null && number.doubleValue() >= 0;
    }
    
    public static boolean isBetween(Number value, Number min, Number max) {
        if (value == null || min == null || max == null) {
            return false;
        }
        
        double val = value.doubleValue();
        double minVal = min.doubleValue();
        double maxVal = max.doubleValue();
        
        return val >= minVal && val <= maxVal;
    }
    
    public static boolean hasMinLength(String str, int minLength) {
        return StringUtils.isNotBlank(str) && str.length() >= minLength;
    }
    
    public static boolean hasMaxLength(String str, int maxLength) {
        return str == null || str.length() <= maxLength;
    }
    
    public static boolean isLengthBetween(String str, int minLength, int maxLength) {
        return hasMinLength(str, minLength) && hasMaxLength(str, maxLength);
    }
    
    public static void requireNonNull(Object obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }
    
    public static void requireNonBlank(String str, String message) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(message);
        }
    }
    
    public static void requirePositive(Number number, String message) {
        if (!isPositive(number)) {
            throw new IllegalArgumentException(message);
        }
    }
}
```