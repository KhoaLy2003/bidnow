# CI/CD Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the minimal build-only CI with a full quality, security, and deployment pipeline using five separate GitHub Actions workflow files.

**Architecture:** Separate workflow per concern (Option B). Backend CI adds a full test run (unit + BDD/Cucumber) with coverage reporting via JaCoCo. Frontend CI adds an ESLint gate. Security workflow runs CodeQL, OWASP Dependency-Check, and dependency-review in parallel with a weekly schedule. Docker and deploy workflows are fully defined but disabled with `if: false` until infrastructure is ready.

**Tech Stack:** GitHub Actions, Maven 3 / Java 17 Temurin, Node 20 / npm, JaCoCo 0.8.11, OWASP Dependency-Check Action, GitHub CodeQL v3, dorny/test-reporter v1, actions/dependency-review-action v4

## Global Constraints

- Java version: 17, distribution: temurin
- Node version: 20
- Maven cache: enabled via `actions/setup-java` with `cache: maven`
- npm cache: enabled via `actions/setup-node` with `cache: npm` and `cache-dependency-path: frontend/package-lock.json`
- Workflow triggers: `pull_request` and `push` to `main` and `develop` only (the `ci-test` branch from the old workflow is dropped)
- All workflow files use `actions/checkout@v4`, `actions/setup-java@v4`, `actions/setup-node@v4`, `actions/upload-artifact@v4`
- OWASP CVSS failure threshold: 7
- Disabled jobs must use `if: false` at the **job** level (not workflow level) so the file is valid and the job definition is visible

---

## File Map

| Action | Path |
|---|---|
| Modify | `backend/pom.xml` |
| Modify | `.github/workflows/backend-ci.yml` |
| Modify | `.github/workflows/frontend-ci.yml` |
| Create | `.github/workflows/security.yml` |
| Create | `.github/dependabot.yml` |
| Create | `.github/workflows/docker-build.yml` |
| Create | `.github/workflows/cd-deploy.yml` |

---

### Task 1: Add JaCoCo to parent pom.xml

**Files:**
- Modify: `backend/pom.xml` (add plugin to `<build><plugins>`)

**Why:** JaCoCo must be in the Maven build before the `test` phase runs so the agent instruments bytecode and the report generates automatically after `mvn test`. Without this, no coverage data is produced.

- [ ] **Step 1: Add JaCoCo plugin to `backend/pom.xml`**

  Inside the existing `<build><plugins>` block (after the `maven-compiler-plugin` entry), add:

  ```xml
  <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.11</version>
      <executions>
          <execution>
              <goals>
                  <goal>prepare-agent</goal>
              </goals>
          </execution>
          <execution>
              <id>report</id>
              <phase>test</phase>
              <goals>
                  <goal>report</goal>
              </goals>
          </execution>
      </executions>
  </plugin>
  ```

- [ ] **Step 2: Verify locally**

  Run from the `backend/` directory:
  ```bash
  mvn test -pl identity-service -DskipTests=false 2>&1 | tail -20
  ```
  Expected: `BUILD SUCCESS` and a `target/site/jacoco/index.html` file created inside `identity-service/`.

  If you don't have the full environment locally, skip this step — the CI job in Task 2 will catch any issues.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/pom.xml
  git commit -m "build: add JaCoCo coverage plugin to parent pom"
  ```

---

### Task 2: Update backend-ci.yml

**Files:**
- Modify: `.github/workflows/backend-ci.yml`

**What changes:** Replaces the single `mvn package -DskipTests` job with a two-job pipeline: `build` (compile check) → `test` (full test run + coverage artifact upload).

The `test` job includes a PostgreSQL service container because multiple services (`identity-service`, `user-service`, `auction-service`, etc.) require a live database when Spring context loads. If tests fail with Kafka connection errors, add the Kafka service container from the comment block below.

- [ ] **Step 1: Replace `.github/workflows/backend-ci.yml` entirely**

  ```yaml
  name: Backend CI

  on:
    pull_request:
      branches: [main, develop]
      paths:
        - "backend/**"
    push:
      branches: [main, develop]
      paths:
        - "backend/**"

  jobs:
    build:
      name: Build
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - name: Set up JDK 17
          uses: actions/setup-java@v4
          with:
            java-version: "17"
            distribution: "temurin"
            cache: maven
        - name: Build with Maven
          run: mvn package -DskipTests
          working-directory: backend

    test:
      name: Test
      runs-on: ubuntu-latest
      needs: build
      permissions:
        checks: write
        contents: read
      services:
        postgres:
          image: postgres:16
          env:
            POSTGRES_PASSWORD: ci_password
            POSTGRES_USER: ci_user
            POSTGRES_DB: bidnow_ci
          ports:
            - 5432:5432
          options: >-
            --health-cmd pg_isready
            --health-interval 10s
            --health-timeout 5s
            --health-retries 5
        # Uncomment if BDD tests fail with Kafka connection errors:
        # kafka:
        #   image: confluentinc/cp-kafka:7.6.0
        #   env:
        #     KAFKA_BROKER_ID: 1
        #     KAFKA_ZOOKEEPER_CONNECT: ""
        #     KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
        #     KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
        #     KAFKA_PROCESS_ROLES: broker,controller
        #     KAFKA_NODE_ID: 1
        #     KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
        #     CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
        #   ports:
        #     - 9092:9092
      env:
        DB_URL: jdbc:postgresql://localhost:5432/bidnow_ci
        DB_USERNAME: ci_user
        DB_PASSWORD: ci_password
        KAFKA_BOOTSTRAP_SERVERS: localhost:9092
        JWT_SECRET: ci-test-secret-for-github-actions-only
      steps:
        - uses: actions/checkout@v4
        - name: Set up JDK 17
          uses: actions/setup-java@v4
          with:
            java-version: "17"
            distribution: "temurin"
            cache: maven
        - name: Run all tests
          run: mvn test
          working-directory: backend
        - name: Publish test results
          uses: dorny/test-reporter@v1
          if: always()
          with:
            name: Maven Tests
            path: "backend/**/target/surefire-reports/*.xml"
            reporter: java-junit
            fail-on-error: true
        - name: Upload coverage report
          uses: actions/upload-artifact@v4
          if: always()
          with:
            name: jacoco-coverage-report
            path: backend/**/target/site/jacoco/
            retention-days: 7
  ```

- [ ] **Step 2: Push to a test branch and verify**

  Push to a branch that touches `backend/` and open a PR targeting `develop`. In the GitHub Actions tab, confirm:
  - `Build` job appears and passes
  - `Test` job appears after `Build` completes
  - Test results are visible as a check on the PR (from `dorny/test-reporter`)
  - `jacoco-coverage-report` artifact appears in the Actions run summary

- [ ] **Step 3: Commit**

  ```bash
  git add .github/workflows/backend-ci.yml
  git commit -m "ci: add full test run and JaCoCo coverage to backend CI"
  ```

---

### Task 3: Update frontend-ci.yml

**Files:**
- Modify: `.github/workflows/frontend-ci.yml`

**What changes:** Adds a `lint` job that runs ESLint before the existing `build` job. If lint fails, the build is skipped entirely.

- [ ] **Step 1: Replace `.github/workflows/frontend-ci.yml` entirely**

  ```yaml
  name: Frontend CI

  on:
    pull_request:
      branches: [main, develop]
      paths:
        - "frontend/**"
    push:
      branches: [main, develop]
      paths:
        - "frontend/**"

  jobs:
    lint:
      name: Lint
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - name: Set up Node.js
          uses: actions/setup-node@v4
          with:
            node-version: "20"
            cache: "npm"
            cache-dependency-path: frontend/package-lock.json
        - name: Install dependencies
          run: npm ci
          working-directory: frontend
        - name: Run ESLint
          run: npm run lint
          working-directory: frontend

    build:
      name: Build
      runs-on: ubuntu-latest
      needs: lint
      steps:
        - uses: actions/checkout@v4
        - name: Set up Node.js
          uses: actions/setup-node@v4
          with:
            node-version: "20"
            cache: "npm"
            cache-dependency-path: frontend/package-lock.json
        - name: Install dependencies
          run: npm ci
          working-directory: frontend
        - name: Build frontend
          run: npm run build
          working-directory: frontend
  ```

- [ ] **Step 2: Push and verify**

  Push a change to `frontend/` and open a PR. In the GitHub Actions tab, confirm:
  - `Lint` job runs first
  - `Build` job is skipped when `Lint` fails, runs when `Lint` passes

- [ ] **Step 3: Commit**

  ```bash
  git add .github/workflows/frontend-ci.yml
  git commit -m "ci: add ESLint gate before frontend build"
  ```

---

### Task 4: Create security.yml and dependabot.yml

**Files:**
- Create: `.github/workflows/security.yml`
- Create: `.github/dependabot.yml`

**What this does:**
- `security.yml`: Three parallel jobs — CodeQL (code-level vulnerability scanning for Java + JS/TS), OWASP Dependency-Check (CVE scanning for Maven dependencies), and dependency-review (blocks PRs adding high-severity deps). Runs on PR/push and weekly.
- `dependabot.yml`: Weekly automated PRs for outdated Maven, npm, and GitHub Actions dependencies.

- [ ] **Step 1: Create `.github/workflows/security.yml`**

  ```yaml
  name: Security

  on:
    pull_request:
      branches: [main, develop]
    push:
      branches: [main, develop]
    schedule:
      - cron: "0 0 * * 1"

  jobs:
    codeql:
      name: CodeQL Analysis
      runs-on: ubuntu-latest
      permissions:
        actions: read
        contents: read
        security-events: write
      strategy:
        fail-fast: false
        matrix:
          language: [java, javascript]
      steps:
        - uses: actions/checkout@v4
        - name: Initialize CodeQL
          uses: github/codeql-action/init@v3
          with:
            languages: ${{ matrix.language }}
        - name: Set up JDK 17
          if: matrix.language == 'java'
          uses: actions/setup-java@v4
          with:
            java-version: "17"
            distribution: "temurin"
            cache: maven
        - name: Build backend for CodeQL
          if: matrix.language == 'java'
          run: mvn package -DskipTests
          working-directory: backend
        - name: Autobuild (frontend)
          if: matrix.language == 'javascript'
          uses: github/codeql-action/autobuild@v3
        - name: Perform CodeQL Analysis
          uses: github/codeql-action/analyze@v3
          with:
            category: "/language:${{ matrix.language }}"

    owasp-dependency-check:
      name: OWASP Dependency Check
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - name: Run OWASP Dependency Check
          uses: dependency-check/Dependency-Check_Action@main
          id: depcheck
          with:
            project: "BidNow"
            path: "."
            format: "HTML"
            out: "owasp-report"
            args: >
              --failOnCVSS 7
              --enableRetired
        - name: Upload OWASP report
          uses: actions/upload-artifact@v4
          if: always()
          with:
            name: owasp-dependency-check-report
            path: owasp-report/
            retention-days: 30

    dependency-review:
      name: Dependency Review
      runs-on: ubuntu-latest
      if: github.event_name == 'pull_request'
      steps:
        - uses: actions/checkout@v4
        - name: Dependency Review
          uses: actions/dependency-review-action@v4
          with:
            fail-on-severity: high
  ```

- [ ] **Step 2: Create `.github/dependabot.yml`**

  ```yaml
  version: 2
  updates:
    - package-ecosystem: "maven"
      directory: "/backend"
      schedule:
        interval: "weekly"
        day: "monday"
      labels:
        - "dependencies"
        - "backend"

    - package-ecosystem: "npm"
      directory: "/frontend"
      schedule:
        interval: "weekly"
        day: "monday"
      labels:
        - "dependencies"
        - "frontend"

    - package-ecosystem: "github-actions"
      directory: "/"
      schedule:
        interval: "weekly"
        day: "monday"
      labels:
        - "dependencies"
        - "ci"
  ```

- [ ] **Step 3: Push and verify**

  Push to `develop`. In the GitHub Actions tab, confirm:
  - Three `Security` jobs appear: `CodeQL Analysis (java)`, `CodeQL Analysis (javascript)`, `OWASP Dependency Check`
  - `Dependency Review` does NOT appear on a push (only on PRs)
  - CodeQL results appear in the **Security → Code scanning alerts** tab of the repository
  - OWASP HTML report is available as a downloadable artifact
  - In **Settings → Code security → Dependabot**, confirm Dependabot is active for all three ecosystems

- [ ] **Step 4: Commit**

  ```bash
  git add .github/workflows/security.yml .github/dependabot.yml
  git commit -m "ci: add CodeQL, OWASP dependency check, and Dependabot"
  ```

---

### Task 5: Create docker-build.yml (disabled)

**Files:**
- Create: `.github/workflows/docker-build.yml`

**What this does:** Defines a matrix build for all 8 backend microservices that builds Docker images and pushes to GitHub Container Registry (GHCR). Disabled with `if: false`. To enable: remove `if: false` and ensure each service has a `Dockerfile`.

**Prerequisites when enabling:** Each `backend/<service>/` directory needs a `Dockerfile`. The `GITHUB_TOKEN` automatically has `packages: write` permission when granted in the job.

- [ ] **Step 1: Create `.github/workflows/docker-build.yml`**

  ```yaml
  name: Docker Build

  on:
    pull_request:
      branches: [main, develop]
    push:
      branches: [main, develop]

  jobs:
    docker-build:
      name: Docker Build - ${{ matrix.service }}
      if: false  # Remove this line when Docker infrastructure is ready
      runs-on: ubuntu-latest
      permissions:
        contents: read
        packages: write
      strategy:
        matrix:
          service:
            - api-gateway
            - auction-service
            - bidding-service
            - discovery-service
            - identity-service
            - media-service
            - user-service
            - wallet-service
      steps:
        - uses: actions/checkout@v4
        - name: Log in to GitHub Container Registry
          uses: docker/login-action@v3
          with:
            registry: ghcr.io
            username: ${{ github.actor }}
            password: ${{ secrets.GITHUB_TOKEN }}
        - name: Build and push image
          uses: docker/build-push-action@v5
          with:
            context: backend/${{ matrix.service }}
            push: ${{ github.event_name == 'push' }}
            tags: |
              ghcr.io/${{ github.repository_owner }}/${{ matrix.service }}:${{ github.sha }}
              ghcr.io/${{ github.repository_owner }}/${{ matrix.service }}:latest
  ```

- [ ] **Step 2: Verify the file is valid YAML and jobs do not run**

  Push to a branch. In the GitHub Actions tab, confirm the `Docker Build` workflow does **not** appear (because `if: false` prevents the job from being queued and GitHub hides workflows with no runnable jobs).

  If the workflow does appear with a skipped status, that is also acceptable — the key requirement is that no Docker build actually executes.

- [ ] **Step 3: Commit**

  ```bash
  git add .github/workflows/docker-build.yml
  git commit -m "ci: add docker build workflow (disabled)"
  ```

---

### Task 6: Create cd-deploy.yml (disabled)

**Files:**
- Create: `.github/workflows/cd-deploy.yml`

**What this does:** Defines a deploy job that SSHes into the production server and runs `docker compose up`. Disabled with `if: false`. To enable: remove `if: false` and add three repository secrets — `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` — in GitHub Settings → Secrets and variables → Actions.

- [ ] **Step 1: Create `.github/workflows/cd-deploy.yml`**

  ```yaml
  name: CD Deploy

  on:
    push:
      branches: [main]

  jobs:
    deploy:
      name: Deploy to Production
      if: false  # Remove this line when deployment infrastructure is ready
      runs-on: ubuntu-latest
      environment: production
      steps:
        - uses: actions/checkout@v4
        - name: Deploy via SSH
          uses: appleboy/ssh-action@v1
          with:
            host: ${{ secrets.DEPLOY_HOST }}
            username: ${{ secrets.DEPLOY_USER }}
            key: ${{ secrets.DEPLOY_SSH_KEY }}
            script: |
              cd /opt/bidnow
              docker compose pull
              docker compose up -d --remove-orphans
              docker image prune -f
  ```

- [ ] **Step 2: Verify the file is valid YAML and jobs do not run**

  Push to `main`. In the GitHub Actions tab, confirm `CD Deploy` either does not appear or shows as skipped — no actual deploy runs.

- [ ] **Step 3: Commit**

  ```bash
  git add .github/workflows/cd-deploy.yml
  git commit -m "ci: add cd deploy workflow (disabled)"
  ```

---

## Enabling Deferred Features

| Feature | Action |
|---|---|
| Docker builds | Delete `if: false` from `docker-build.yml`; add `Dockerfile` to each `backend/<service>/` |
| CD deployment | Delete `if: false` from `cd-deploy.yml`; add `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` secrets in GitHub Settings |
| Frontend tests | Add `test` job to `frontend-ci.yml` blocked by `build`; install a test runner (Jest, Playwright) |
| Coverage badge | Add `codecov/codecov-action@v4` step to the `test` job in `backend-ci.yml` after uploading the coverage report |

## Self-Review Notes

- **Spec coverage:** All five workflow files from the spec are present. JaCoCo is in the pom as a prerequisite for the backend CI coverage step. Dependabot covers Maven, npm, and GitHub Actions. CVSS threshold of 7 is explicit in the OWASP job. `dorny/test-reporter` format (java-junit) matches surefire XML output.
- **Disabled jobs:** Both `docker-build.yml` and `cd-deploy.yml` use `if: false` at the **job** level, consistent with the spec and the Global Constraints above.
- **Permissions:** `checks: write` is scoped to the `test` job only (not the whole workflow). `security-events: write` is scoped to the `codeql` job only.
- **No placeholders:** All YAML values are concrete. The CD deploy script assumes `/opt/bidnow` as the server directory — this is a reasonable default that the implementer should update when enabling the job.
