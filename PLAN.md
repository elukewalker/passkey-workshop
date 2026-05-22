<!-- /autoplan restore point: /home/scion/.gstack/projects/workspace/scion-dep-upgrade-autoplan-restore-20260520-100254.md -->
# Dependency Upgrade Plan

## Problem Statement

The passkey-workshop project is running on outdated dependencies across the stack:
- **Java Backend**: Spring Boot 2.7.12 (EOL May 2024), webauthn-server-core 2.6.0 (current is 2.9.0)
- **React Frontends**: React 19.1.0 is latest, but supporting libraries need updates
- **Infrastructure**: Node Dockerfiles unpinned (pulls latest, unstable)

**User impact**: Security vulnerabilities, missing bug fixes, incompatibility with modern tooling, and unstable builds from unpinned Docker images.

## Proposed Solution

### Phase 1: Java Backend Upgrade (Breaking)

**Spring Boot 2.7 → 3.x Migration**
- Major version bump requires namespace migration: `javax.*` → `jakarta.*`
- Security configuration modernization: `WebSecurityConfigurerAdapter` (deprecated) → `SecurityFilterChain` pattern
- Spring Data API updates for JPA compatibility
- Pin `maven.compiler.release=25` for Java 25 builds

**WebAuthn Library Updates**
- `webauthn-server-core`: 2.6.0 → 2.9.0
- `webauthn-server-attestation`: 2.6.0 → 2.9.0
- `yubico-util`: 2.6.0 → 2.9.0
- Review API changelog for breaking changes (2.6 → 2.9)

**Other Java Dependencies**
- Lombok: 1.18.30 → 1.18.36 (latest)
- MySQL Connector: 8.2.0 → 9.2.0 (latest)
- SpringDoc: 1.6.8 → 2.x (Spring Boot 3 compatibility)
- jackson-databind-nullable: 0.2.2 → 0.2.6
- jsr305: 3.0.2 → latest

### Phase 2: React Frontend Upgrades (Non-Breaking)

**passkey-client** (`examples/clients/web/react/passkey-client/`)
- @github/webauthn-json: ^2.1.1 → ^2.2.1 (latest)
- react-bootstrap: ^2.7.2 → ^2.10.7 (latest)
- react-router-dom: ^6.8.1 → ^7.3.1 (latest, major bump)
- react-scripts: ^5.0.1 → 5.0.1 (exact pin, 5.x is final)
- bootstrap: ^5.2.3 → ^5.3.3 (latest)

**bank-client** (`examples/clients/web/react/bank-client/`)
- @github/webauthn-json: ^2.1.1 → ^2.2.1
- react-bootstrap: ^2.8.0 → ^2.10.7
- react-router-dom: ^6.15.0 → ^7.3.1
- bootstrap: ^5.3.1 → ^5.3.3
- @babel/plugin-proposal-private-property-in-object: ^7.21.0 → ^7.25.9

### Phase 3: Infrastructure (Docker)

**Node Dockerfiles** (deploy/react-app/, deploy/bank-react-app/)
- FROM node → FROM node:20-alpine
- Pin to Node 20 LTS for stability
- Use alpine for smaller image size

## Implementation Steps

### 1. Java Backend Migration

**1.1. Update pom.xml dependencies**
```xml
<!-- Parent: Spring Boot 2.7.12 → 3.4.1 -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.1</version>
</parent>

<!-- Compiler: Pin Java 25 -->
<properties>
  <maven.compiler.release>25</maven.compiler.release>
  <springdoc.version>2.6.0</springdoc.version>
  <!-- ... existing properties ... -->
</properties>

<!-- Add OWASP Dependency Check plugin for CVE scanning -->
<build>
  <plugins>
    <!-- existing plugins ... -->
    <plugin>
      <groupId>org.owasp</groupId>
      <artifactId>dependency-check-maven</artifactId>
      <version>10.0.4</version>
    </plugin>
  </plugins>
</build>

<!-- WebAuthn: 2.6.0 → 2.9.0 -->
<dependency>
  <groupId>com.yubico</groupId>
  <artifactId>webauthn-server-core</artifactId>
  <version>2.9.0</version>
</dependency>
<!-- ... repeat for webauthn-server-attestation and yubico-util ... -->

<!-- Lombok: 1.18.30 → 1.18.36 -->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <version>1.18.36</version>
  <scope>provided</scope>
</dependency>

<!-- MySQL: 8.2.0 → 9.2.0 -->
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>9.2.0</version>
  <scope>runtime</scope>
</dependency>
```

**1.2. Namespace Migration (javax → jakarta) — CRITICAL: Multi-step process**

**Step 1**: Add Jakarta Annotations API dependency
```xml
<dependency>
  <groupId>jakarta.annotation</groupId>
  <artifactId>jakarta.annotation-api</artifactId>
</dependency>
```

**Step 2**: Migrate persistence layer (24 imports)
- Find: `import javax.persistence.` → Replace: `import jakarta.persistence.`
- Compile test: `mvn compile -DskipTests`

**Step 3**: Migrate validation layer (3 imports)
- Find: `import javax.validation.` → Replace: `import jakarta.validation.`
- Compile test: `mvn compile -DskipTests`

**Step 4**: Migrate servlet layer (1 import)
- Find: `import javax.servlet.` → Replace: `import jakarta.servlet.`
- Compile test: `mvn compile -DskipTests`

**Step 5**: Migrate annotations (18 imports) — SELECTIVE
- Find: `import javax.annotation.PostConstruct` → Replace: `import jakarta.annotation.PostConstruct`
- Find: `import javax.annotation.PreDestroy` → Replace: `import jakarta.annotation.PreDestroy`
- **LEAVE UNCHANGED**: `import javax.annotation.Generated` (OpenAPI generator compatibility)

**Verification**:
```bash
# No javax imports should remain except Generated
grep -r "import javax\." src/ --include="*.java" | grep -v "javax.annotation.Generated"
# Should return zero results
```

**1.3. Hibernate 6 Migration (CRITICAL — Spring Boot 3 includes Hibernate 6)**

**Issue**: Hibernate 5.6 (Spring Boot 2.7) → Hibernate 6 (Spring Boot 3) has breaking changes.

**Fix 1**: Pin GenerationType.IDENTITY explicitly
```bash
# Find all @GeneratedValue(strategy = GenerationType.AUTO)
grep -r "GenerationType.AUTO" src/
# Replace with:
@GeneratedValue(strategy = GenerationType.IDENTITY)
```

**Fix 2**: Set schema validation mode (not auto-update)
```properties
# In application.properties or application.yml
spring.jpa.hibernate.ddl-auto=validate
```

**Fix 3**: Generate schema diff BEFORE first run
```bash
# Export current schema
mvn liquibase:diff
# OR manually compare schema before/after
```

**Verification**:
- Run integration test with existing database
- Hibernate should VALIDATE schema, not ALTER it
- No "Unknown column" or "Table already exists" errors

**1.4. Security Configuration Modernization**
- Locate `WebSecurityConfigurerAdapter` usage (if any)
- Refactor to `SecurityFilterChain` bean pattern
- Update `HttpSecurity` configuration API (method chaining changes in Spring Boot 3)

**1.5. Spring Data API Updates**
- Review `JpaRepository` and entity usage
- Check for deprecated `@Query` annotations or pagination APIs

**1.6. SpringDoc 1.6.8 → 2.6.0 Migration**
- Update package imports: `org.springdoc.api.annotations` → `org.springdoc.core.annotations`
- Verify Swagger UI endpoint: `/swagger-ui.html` → `/swagger-ui/index.html`
- Test: `curl http://localhost:8080/swagger-ui/index.html` after startup

### 2. React Frontend Updates

**2.1. passkey-client**
```bash
cd examples/clients/web/react/passkey-client
npm install @github/webauthn-json@^2.2.1 \
  react-bootstrap@^2.10.7 \
  react-router-dom@^7.3.1 \
  bootstrap@^5.3.3
```

**2.2. bank-client**
```bash
cd examples/clients/web/react/bank-client
npm install @github/webauthn-json@^2.2.1 \
  react-bootstrap@^2.10.7 \
  react-router-dom@^7.3.1 \
  bootstrap@^5.3.3 \
  @babel/plugin-proposal-private-property-in-object@^7.25.9
```

**2.3. Breaking Change Review: react-router-dom 6 → 7 (HIGH RISK)**

**Known breaking changes in v7**:
- Route configuration may use new `RouterProvider` pattern (opt-in)
- Data loading/actions API overhauled (opt-in)
- Relative path resolution changed in nested routes
- `Navigate` component behavior for relative paths

**Migration steps**:
1. **Review migration guide**: https://reactrouter.com/en/main/upgrading/v6-to-v7
2. **Identify affected code**:
   ```bash
   # Check for relative navigation
   grep -r "to='./" src/
   grep -r "to=\"./" src/
   # Check for useNavigate with relative paths
   grep -r "navigate('." src/
   ```
3. **Test all routes** (manual smoke test):
   - passkey-client: /, /register, /authenticate
   - bank-client: /, /auth-callback, /transactions (if exists)
   - Test browser back/forward buttons
4. **Fallback**: If migration requires refactoring, STAY ON v6 (react-router-dom@^6.15.0)

**Success criterion**: All navigation flows work identically to current behavior

### 3. Docker Updates

**3.1. Update Node Dockerfiles (CRITICAL — Fix version skew)**

**Pre-upgrade verification**:
```bash
# Check current Node version in existing images
docker images | grep passkey
docker run <existing-image> node --version || echo "Image doesn't exist yet"
```

**Updated Dockerfile**:
```dockerfile
# Before: FROM node (pulls Node 23.x latest)
# After: Pin to Node 20 LTS + pin serve version
FROM node:20-alpine

WORKDIR /usr/src/app

# Copy package files first (layer caching)
COPY /source/package*.json ./

# Pin serve version for reproducibility
RUN npm install && \
    npm install -g serve@14.2.4

# Copy source
COPY /source .

# Build
RUN npm run build

EXPOSE 3000
ENTRYPOINT [ "serve", "-s", "build" ]
```

**Add .dockerignore** (create in each client directory):
```
node_modules
.git
.env
npm-debug.log
.DS_Store
```

Apply to:
- `deploy/react-app/Dockerfile`
- `deploy/bank-react-app/Dockerfile`

**Build with --no-cache**:
```bash
docker build --no-cache -t passkey-workshop-react:latest .
```

**3.2. Pin MySQL Docker Image (CRITICAL — MySQL Connector 9.2 compatibility)**

**Current**: `deploy/mysql/Dockerfile` likely uses `FROM mysql` (unpinned)

**Pre-upgrade verification**:
```bash
# Check deployed MySQL version
docker exec <mysql-container> mysql --version
# Verify >= 8.0.33 (required for MySQL Connector 9.2)
```

**Updated Dockerfile**:
```dockerfile
# Pin to MySQL 8.0.39 (current stable, compatible with Connector 9.2)
FROM mysql:8.0.39

# ... existing configuration ...
```

**docker-compose.yml update** (if exists):
```yaml
services:
  mysql:
    image: mysql:8.0.39  # Pin version
    # ... rest of config ...
```

**Validation**:
```bash
# After upgrade, verify connection
docker exec java-app java -jar app.jar --spring.datasource.url=jdbc:mysql://mysql:3306/passkey
# Should connect without protocol errors
```

### 4. Validation (CRITICAL)

**4.1. Java Backend**
```bash
cd examples/relyingParties/java-spring
JAVA_HOME=/opt/java-25 mvn clean test -B -Dmaven.repo.local=/tmp/m2
```
- **Success criteria**: BUILD SUCCESS, all tests pass
- **Failure action**: Capture full output, investigate failures, fix

**4.2. React Frontends**
```bash
# passkey-client
cd examples/clients/web/react/passkey-client
npm install
npm test -- --watchAll=false

# bank-client
cd examples/clients/web/react/bank-client
npm install
npm test -- --watchAll=false
```
- **Success criteria**: All tests pass, no errors
- **Failure action**: Fix test failures, check for breaking API changes

**4.3. Docker Build Validation**
```bash
# Test Node 20 alpine compatibility
cd deploy/react-app
docker build -t passkey-workshop-react:test .
docker run --rm passkey-workshop-react:test echo "Build successful"

cd ../bank-react-app
docker build -t passkey-workshop-bank:test .
docker run --rm passkey-workshop-bank:test echo "Build successful"
```
- **Success criteria**: Both images build without errors
- **Failure action**: Check for Node 20 incompatibilities, adjust Dockerfile

**4.4. Integration Test (Backend + Frontend + Database)**
```bash
# Start full stack via docker-compose
docker-compose up -d

# Wait for services to be healthy (NOT fixed sleep — poll until ready)
echo "Waiting for backend health check..."
timeout 120 bash -c 'until curl -f http://localhost:8080/actuator/health 2>/dev/null; do echo "Waiting..."; sleep 3; done'

echo "Waiting for MySQL readiness..."
timeout 60 bash -c 'until docker exec mysql-container mysqladmin ping -h localhost --silent; do echo "Waiting..."; sleep 2; done'

# Minimal API health check (not full WebAuthn flow — no automated tests exist)
echo "Testing API endpoints..."
curl -X GET http://localhost:8080/api/v1/health || echo "Health check failed"
curl -X POST http://localhost:8080/api/v1/register/start \
  -H "Content-Type: application/json" \
  -d '{"username":"test-user"}' || echo "Registration endpoint failed"

# Manual WebAuthn testing (no Playwright tests exist)
echo "MANUAL TEST REQUIRED: Open http://localhost:3000 and test:"
echo "  1. Register new passkey"
echo "  2. Authenticate with passkey"
echo "  3. Check browser console for errors"

# Cleanup
docker-compose down
```
- **Success criteria**: Health checks pass, API responds, manual WebAuthn flow succeeds
- **Failure action**: Check logs (`docker logs <container>`), verify MySQL protocol errors, WebAuthn 2.9 API errors

### 5. Lombok Safety Check

**NEVER remove these annotations:**
- `@Builder` (object construction)
- `@Value` (immutable classes)
- `@Data` (mutable classes with getters/setters)
- `@Getter` / `@Setter`
- `@AllArgsConstructor` / `@NoArgsConstructor`

After any Java file changes:
```bash
mvn clean  # Clear old Lombok-generated code
```

### 6. Documentation Updates

**Files to review and update**:
- `README.md`: Update Java minimum version to "Java 17+" (Spring Boot 3 requirement)
- `README.md`: Document Node 20 LTS requirement for Docker builds
- `docs/setup.md` (if exists): Update Spring Boot 2 → 3 config examples
- `examples/relyingParties/java-spring/README.md` (if exists): Update dependency versions
- Docker Compose documentation: Note MySQL 8.0.33+ requirement

**Search for stale references**:
```bash
grep -r "Spring Boot 2" docs/ README.md
grep -r "Java 11" docs/ README.md
grep -r "FROM node[^:]" deploy/
```

## Rollback Procedure

**Rollback trigger**: Any of these within 48 hours of merge:
- Production WebAuthn flow breaks (registration, authentication)
- Test suite fails in deployed environment
- Database connectivity issues
- Critical user-reported bugs

**Rollback steps**:
1. `git revert <merge-commit-sha>` (single commit revert)
2. CI/CD redeploy from reverted commit
3. **Estimated time**: <2 hours from decision to deployed rollback

**Rollback testing**: Before merging, verify `git revert` cleanly undoes all changes (test in local branch)

## CVE Validation

**IMPORTANT**: Run baseline scans BEFORE starting upgrade to enable before/after comparison.

**Pre-upgrade baseline** (run NOW, save output):
```bash
# Java dependencies (requires OWASP plugin added to pom.xml)
cd examples/relyingParties/java-spring
mvn dependency-check:check -DfailBuildOnCVSS=7 > cve-baseline-java.txt 2>&1

# Node dependencies (both clients)
cd examples/clients/web/react/passkey-client
npm audit --production --audit-level=high > cve-baseline-passkey.txt 2>&1

cd ../bank-client
npm audit --production --audit-level=high > cve-baseline-bank.txt 2>&1
```

**Post-upgrade scan**: Re-run same commands, compare output:
```bash
# Example diff
diff cve-baseline-java.txt cve-post-upgrade-java.txt
```

**Document**:
- CVEs closed (list CVE IDs)
- New CVEs introduced (list CVE IDs)
- Net security improvement score

**Known issue — react-scripts 5.0.1 CVEs**:
- react-scripts 5.0.1 is **FINAL VERSION** (Create React App is in maintenance mode)
- May accumulate unfixable transitive dependency CVEs
- **Mitigation strategy**: Accept risk (workshop/demo project) OR migrate to Vite (out of scope)

**Success criterion**: 
- Java backend: Zero new high/critical CVEs, OR all have documented mitigations
- React frontends: No NEW CVEs introduced (existing react-scripts CVEs acknowledged)

## Minimum Requirements

**Java**: Spring Boot 3.x requires **Java 17 or later**. Current setup uses Java 21 (satisfies requirement). Builds with `JAVA_HOME=/opt/java-25` also work (forward compatible).

**Node**: React frontends require Node 14+ (Node 20 LTS satisfies requirement).

**MySQL**: MySQL Connector 9.x requires MySQL 8.0.33+ server. Verify deployed MySQL version before upgrading connector.

## NOT in Scope

- Java 21 → 25 migration (compiler already supports 25, no code changes needed)
- React 19 upgrade (already on 19.1.0)
- MySQL database schema changes
- Keycloak SPI upgrades (separate pom.xml, not in task scope)
- High Assurance Bank App pom.xml (examples/high_assurance/bank_app/, separate module)

## Success Criteria

1. ✅ All Java tests pass with JAVA_HOME=/opt/java-25
2. ✅ All React tests pass in both frontends
3. ✅ No Lombok annotations removed
4. ✅ Spring Boot 3.x running successfully
5. ✅ WebAuthn 2.9.0 integrated without errors
6. ✅ Docker builds succeed with Node 20 pinned
7. ✅ No regression in existing functionality

## Risks & Mitigations

**Risk 1: javax → jakarta migration incomplete**
- Mitigation: Comprehensive grep for remaining `javax.*` imports
- Test: Full mvn clean + test cycle

**Risk 2: WebAuthn 2.6 → 2.9 API breaking changes**
- Mitigation: Review Yubico changelog, test all WebAuthn flows
- Fallback: Pin to 2.8.x if 2.9 is incompatible

**Risk 3: react-router-dom 6 → 7 navigation breaks**
- Mitigation: Manual testing of all routes in both clients
- Fallback: Stay on 6.x if migration requires refactoring

**Risk 4: Lombok code generation fails after Spring Boot 3 upgrade**
- Mitigation: Always run `mvn clean` before tests
- Verification: Inspect target/ for generated sources

## Timeline Estimate

- Phase 1 (Java): ~4 hours (migration + testing)
- Phase 2 (React): ~1 hour (straightforward updates)
- Phase 3 (Docker): ~15 minutes (simple pin)
- Validation: ~1 hour (comprehensive testing)
- **Total (human)**: ~6-7 hours
- **Total (CC + gstack)**: ~20-30 minutes
