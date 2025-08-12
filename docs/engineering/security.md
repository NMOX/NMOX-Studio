# Security & Compliance Requirements

## 🔒 Security Principles

1. **Defense in Depth** - Multiple layers of security controls
2. **Least Privilege** - Minimal access rights by default
3. **Zero Trust** - Never trust, always verify
4. **Secure by Default** - Security enabled out of the box
5. **Privacy by Design** - Data protection built into architecture

## 🎯 Security Goals

| Goal | Target | Current | Status |
|------|--------|---------|--------|
| OWASP Top 10 Coverage | 100% | 80% | 🔄 |
| Security Scan Issues | 0 Critical | 2 | ⚠️ |
| Penetration Test Score | A+ | B | 🔄 |
| Compliance Certifications | SOC2, ISO27001 | None | 📋 |
| Security Training | 100% developers | 60% | 🔄 |

## 🛡️ Application Security

### Input Validation

```java
public class InputValidator {
    private static final int MAX_FILE_PATH_LENGTH = 4096;
    private static final Pattern SAFE_FILE_NAME = 
        Pattern.compile("^[a-zA-Z0-9._-]+$");
    
    public void validateFilePath(String path) throws SecurityException {
        // Length check
        if (path.length() > MAX_FILE_PATH_LENGTH) {
            throw new SecurityException("Path too long");
        }
        
        // Path traversal prevention
        if (path.contains("../") || path.contains("..\\")) {
            throw new SecurityException("Path traversal detected");
        }
        
        // Null byte injection prevention
        if (path.contains("\0")) {
            throw new SecurityException("Null byte detected");
        }
        
        // Canonicalize and verify
        Path normalized = Paths.get(path).normalize();
        if (!normalized.startsWith(WORKSPACE_ROOT)) {
            throw new SecurityException("Path outside workspace");
        }
    }
    
    public void validateCode(String code) throws SecurityException {
        // Check for malicious patterns
        if (containsMaliciousPattern(code)) {
            throw new SecurityException("Potentially malicious code detected");
        }
        
        // Size limits
        if (code.length() > MAX_CODE_SIZE) {
            throw new SecurityException("Code too large");
        }
    }
}
```

### Authentication & Authorization

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService())
                )
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .headers(headers -> headers
                .contentSecurityPolicy("default-src 'self'")
                .frameOptions().deny()
                .xssProtection().and()
                .contentTypeOptions()
            )
            .build();
    }
}
```

### JWT Token Security

```java
@Component
public class JwtTokenProvider {
    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS512);
    private final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000; // 15 minutes
    private final long REFRESH_TOKEN_VALIDITY = 7 * 24 * 60 * 60 * 1000; // 7 days
    
    public String createAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + ACCESS_TOKEN_VALIDITY);
        
        return Jwts.builder()
            .setSubject(principal.getId())
            .claim("roles", principal.getRoles())
            .claim("type", "access")
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key)
            .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
                
            // Check token type
            if (!"access".equals(claims.getBody().get("type"))) {
                return false;
            }
            
            // Check if token is blacklisted
            if (tokenBlacklist.contains(token)) {
                return false;
            }
            
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token", e);
            return false;
        }
    }
}
```

## 🔐 Data Security

### Encryption at Rest

```java
@Component
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private final SecretKey masterKey;
    
    public EncryptionService(KeyManagementService kms) {
        this.masterKey = kms.getMasterKey();
    }
    
    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Prepend IV to ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        
        return result;
    }
    
    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        // Extract IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        
        // Extract ciphertext
        byte[] ciphertext = new byte[encrypted.length - iv.length];
        System.arraycopy(encrypted, iv.length, ciphertext, 0, ciphertext.length);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);
        
        return cipher.doFinal(ciphertext);
    }
}
```

### Encryption in Transit

```yaml
# TLS Configuration
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEY_STORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: nmox-studio
    protocols: TLSv1.3
    ciphers:
      - TLS_AES_256_GCM_SHA384
      - TLS_AES_128_GCM_SHA256
      - TLS_CHACHA20_POLY1305_SHA256
```

## 🚨 Threat Model

### STRIDE Analysis

| Threat | Description | Mitigation |
|--------|-------------|------------|
| **Spoofing** | Impersonating users | MFA, strong authentication |
| **Tampering** | Modifying data/code | Integrity checks, signing |
| **Repudiation** | Denying actions | Audit logs, immutable records |
| **Information Disclosure** | Data leaks | Encryption, access controls |
| **Denial of Service** | Service disruption | Rate limiting, DDoS protection |
| **Elevation of Privilege** | Gaining admin access | RBAC, principle of least privilege |

### Attack Vectors

```java
public class ThreatDetector {
    
    @EventListener
    public void detectSQLInjection(RequestEvent event) {
        String input = event.getInput();
        
        // SQL injection patterns
        Pattern[] sqlPatterns = {
            Pattern.compile("(\\b(SELECT|INSERT|UPDATE|DELETE|DROP)\\b)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\bOR\\b\\s*\\d+\\s*=\\s*\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("('\\s*OR\\s*')", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern pattern : sqlPatterns) {
            if (pattern.matcher(input).find()) {
                alertSecurityTeam("SQL Injection attempt detected", event);
                throw new SecurityException("Potential SQL injection");
            }
        }
    }
    
    @EventListener
    public void detectXSS(RequestEvent event) {
        String input = event.getInput();
        
        // XSS patterns
        if (input.matches(".*<script.*>.*</script>.*") ||
            input.matches(".*javascript:.*") ||
            input.matches(".*on\\w+\\s*=.*")) {
            
            alertSecurityTeam("XSS attempt detected", event);
            throw new SecurityException("Potential XSS attack");
        }
    }
}
```

## 🔍 Security Scanning

### Static Application Security Testing (SAST)

```xml
<!-- SpotBugs Security -->
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <configuration>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
            </plugin>
        </plugins>
        <includeFilterFile>security-rules.xml</includeFilterFile>
        <failOnError>true</failOnError>
    </configuration>
</plugin>
```

### Dynamic Application Security Testing (DAST)

```yaml
# OWASP ZAP Configuration
zap:
  target: https://nmox.studio
  scan-policy: Full Scan
  authentication:
    method: oauth2
    login-url: /api/auth/login
  rules:
    - id: 10021  # X-Content-Type-Options Header Missing
      threshold: high
    - id: 10038  # Content Security Policy Header Not Set
      threshold: high
    - id: 10062  # PII Disclosure
      threshold: critical
```

### Dependency Scanning

```yaml
# GitHub Actions Dependency Check
- name: Run OWASP Dependency Check
  uses: dependency-check/Dependency-Check_Action@main
  with:
    project: 'NMOX Studio'
    path: '.'
    format: 'ALL'
    args: >
      --enableRetired
      --enableExperimental
      --failOnCVSS 7
      --suppressionFile dependency-suppressions.xml
```

## 🛡️ Runtime Protection

### Web Application Firewall (WAF)

```java
@Component
public class WAFFilter implements Filter {
    private final RateLimiter rateLimiter = RateLimiter.create(100); // 100 req/s
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // Rate limiting
        if (!rateLimiter.tryAcquire()) {
            ((HttpServletResponse) response).sendError(429, "Too Many Requests");
            return;
        }
        
        // Geo-blocking
        String country = geoIP.getCountry(request.getRemoteAddr());
        if (BLOCKED_COUNTRIES.contains(country)) {
            ((HttpServletResponse) response).sendError(403, "Forbidden");
            return;
        }
        
        // Payload size limit
        if (httpRequest.getContentLength() > MAX_PAYLOAD_SIZE) {
            ((HttpServletResponse) response).sendError(413, "Payload Too Large");
            return;
        }
        
        // ModSecurity rules
        if (modSecurity.checkRequest(httpRequest).isBlocked()) {
            ((HttpServletResponse) response).sendError(403, "Forbidden");
            return;
        }
        
        chain.doFilter(request, response);
    }
}
```

### Intrusion Detection System (IDS)

```java
@Component
public class IntrusionDetector {
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    
    @EventListener
    public void onAuthenticationFailure(AuthFailureEvent event) {
        String ip = event.getIpAddress();
        int attempts = failedAttempts.computeIfAbsent(ip, k -> new AtomicInteger())
                                    .incrementAndGet();
        
        if (attempts >= 5) {
            // Block IP
            firewall.blockIP(ip, Duration.ofHours(1));
            
            // Alert security team
            alertService.send(Alert.critical()
                .title("Brute force attack detected")
                .details("IP: " + ip + ", Attempts: " + attempts)
                .build()
            );
        }
    }
    
    @EventListener
    public void onAnomalousActivity(ActivityEvent event) {
        // Machine learning-based anomaly detection
        double anomalyScore = mlModel.calculateAnomalyScore(event);
        
        if (anomalyScore > THRESHOLD) {
            quarantine(event.getUserId());
            investigate(event);
        }
    }
}
```

## 📋 Compliance Requirements

### GDPR Compliance

```java
@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {
    
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportUserData(Authentication auth) {
        String userId = auth.getName();
        UserData data = privacyService.collectAllUserData(userId);
        
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=userdata.json")
            .body(data.toJson().getBytes());
    }
    
    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUserData(Authentication auth) {
        String userId = auth.getName();
        
        // Right to be forgotten
        privacyService.deleteAllUserData(userId);
        
        // Audit log
        auditLog.record("USER_DELETION", userId, "GDPR request");
        
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/consent")
    public ResponseEntity<Void> updateConsent(@RequestBody ConsentRequest request) {
        consentService.updateConsent(request.getUserId(), request.getConsents());
        return ResponseEntity.ok().build();
    }
}
```

### SOC 2 Type II Controls

| Control | Implementation | Evidence |
|---------|---------------|----------|
| Access Control | RBAC, MFA | Access logs, permissions matrix |
| Change Management | Code review, CI/CD | Git history, deployment logs |
| Risk Assessment | Threat modeling, pen testing | Reports, remediation records |
| Incident Response | SIEM, runbooks | Incident tickets, post-mortems |
| Business Continuity | Backups, DR plan | Recovery tests, RTO/RPO metrics |

## 🔑 Secrets Management

### HashiCorp Vault Integration

```java
@Configuration
public class VaultConfig {
    
    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.create("vault.nmox.studio", 8200);
        endpoint.setScheme("https");
        
        ClientAuthentication auth = new AppRoleAuthentication(
            AppRoleAuthenticationOptions.builder()
                .roleId(RoleId.provided(System.getenv("VAULT_ROLE_ID")))
                .secretId(SecretId.provided(System.getenv("VAULT_SECRET_ID")))
                .build(),
            restOperations()
        );
        
        return new VaultTemplate(endpoint, auth);
    }
    
    @Bean
    public DataSource dataSource(VaultTemplate vault) {
        VaultResponse response = vault.read("database/creds/nmox-studio");
        
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://db.nmox.studio/nmox")
            .username(response.getData().get("username").toString())
            .password(response.getData().get("password").toString())
            .build();
    }
}
```

## 📊 Security Monitoring

### Security Information and Event Management (SIEM)

```java
@Component
public class SecurityEventLogger {
    private final SiemClient siem;
    
    @EventListener
    public void logSecurityEvent(SecurityEvent event) {
        SiemEvent siemEvent = SiemEvent.builder()
            .timestamp(Instant.now())
            .severity(event.getSeverity())
            .category(event.getCategory())
            .source(event.getSource())
            .user(event.getUserId())
            .ip(event.getIpAddress())
            .action(event.getAction())
            .result(event.getResult())
            .details(event.getDetails())
            .build();
            
        siem.send(siemEvent);
        
        // Real-time alerting for critical events
        if (event.getSeverity() == Severity.CRITICAL) {
            alertingService.sendImmediate(event);
        }
    }
}
```

### Security Metrics Dashboard

```javascript
const securityMetrics = {
    vulnerabilities: {
        critical: 0,
        high: 2,
        medium: 8,
        low: 15
    },
    incidents: {
        last24h: 3,
        last7d: 12,
        last30d: 45
    },
    compliance: {
        gdpr: 98,
        soc2: 95,
        iso27001: 92
    },
    scanning: {
        lastSAST: "2025-01-15T10:30:00Z",
        lastDAST: "2025-01-14T22:00:00Z",
        lastPenTest: "2024-12-20T14:00:00Z"
    }
};
```

## 🚨 Incident Response

### Incident Response Plan

```yaml
incident-response:
  phases:
    1-detection:
      - Monitor SIEM alerts
      - Check IDS/IPS logs
      - Review user reports
      
    2-containment:
      - Isolate affected systems
      - Block malicious IPs
      - Disable compromised accounts
      
    3-eradication:
      - Remove malware
      - Patch vulnerabilities
      - Reset credentials
      
    4-recovery:
      - Restore from backups
      - Verify system integrity
      - Resume normal operations
      
    5-lessons-learned:
      - Document timeline
      - Identify root cause
      - Update security controls
      
  contacts:
    security-team: security@nmox.studio
    legal: legal@nmox.studio
    ciso: ciso@nmox.studio
    external-soc: soc@security-partner.com
```

---

**Last Updated:** January 2025  
**Security Team:** security@nmox.studio  
**Security Hotline:** +1-555-SEC-NMOX