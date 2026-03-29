# WireMock Banking API POC

A proof-of-concept demonstrating [WireMock](https://wiremock.org/) for API automation testing across two paradigms, focused on three core banking flows: **Customer Onboarding**, **Authentication**, and **Fund Transfer**.

## Two Paradigms

| | Paradigm 1 | Paradigm 2 |
|---|---|---|
| **What is WireMock?** | The system under test (mock server) | A test double for a real HTTP client |
| **What is tested?** | That WireMock stubs return the right responses | That `BankingApiClient` sends correct requests and handles responses |
| **Test runner** | REST Assured fires requests at WireMock (Docker) | WireMock runs embedded in JVM; tests drive the real client code |
| **Docker required?** | Yes | No |
| **Where to look** | `src/test/java/com/example/wiremock/` | `src/test/java/com/example/banking/` |
| **Key benefit** | Validates stub contract definitions | Validates client contract compliance (consumer-driven) |

## Quick Start

```bash
# 1. Start WireMock
./scripts/start.sh

# 2. Try a stub
curl http://localhost:8080/api/v1/customers/cust-001

# 3. Run tests (requires Java 17 + Maven)
mvn test

# 4. Stop WireMock
./scripts/stop.sh
```

## Prerequisites

| Tool | Version |
|------|---------|
| Docker + Docker Compose | Any recent version |
| Java | 17+ (for tests) |
| Maven | 3.8+ (for tests) |

## Features Demonstrated

### Paradigm 1 — WireMock as Mock Server

| Feature | Where to look |
|---------|---------------|
| Basic request/response stubs | `wiremock/mappings/` |
| URL path regex matching | All stubs with `urlPathPattern` |
| JSON body matching (`matchesJsonPath`) | `auth/01-login-*.json`, `transfer/02-*.json` |
| Response templating (`{{request.pathSegments.[n]}}`) | All response files in `wiremock/__files/` |
| Request body echoing (`{{jsonPath request.body '$.field'}}`) | `transfer/__files/transfer-accepted-response.json` |
| Header matching (Authorization Bearer) | `auth/04-me-*.json`, `security/` |
| Stateful scenarios (state machines) | `mappings/*/scenario-*.json` |
| Fixed delay (timeout simulation) | `transfer/01-get-balance-slow.json` |
| Fault injection (`MALFORMED_RESPONSE_CHUNK`) | `transfer/02-initiate-transfer-fault.json` |
| 503 + Retry-After header | `auth/01-login-service-unavailable.json` |
| Stub priority / fallback | Auth login stubs (priority 1 vs 5) |
| Embedded WireMock (no Docker) | `EmbeddedWireMockTest.java` |

### Paradigm 2 — WireMock as Automation Test Suite (Consumer-Driven Contract Testing)

| Feature | Where to look |
|---------|---------------|
| Embedded WireMock (`@RegisterExtension`) | `BankingApiClientTest.java` |
| Verifying request headers (`Authorization: Bearer`) | `getBalance_validToken_*` test |
| Verifying request body fields (`matchingJsonPath`) | `initiateTransfer_validRequest_*` test |
| Exception handling for 401 / 422 / 404 | All `*_throwsBankingApiException` tests |
| Fault injection (`CONNECTION_RESET_BY_PEER`) | `initiateTransfer_serverDown_*` test |
| Real Java HTTP client as SUT (`java.net.http.HttpClient`) | `BankingApiClient.java` |

## Project Structure

```
.
├── docker-compose.yml              # WireMock Standalone 3.x container
├── pom.xml                         # Maven project (Java 17, JUnit 5, REST Assured, AssertJ)
├── scripts/
│   ├── start.sh                    # Start WireMock and wait for health
│   ├── stop.sh                     # Stop WireMock
│   └── run-tests.sh                # Start + run tests + stop
├── wiremock/
│   ├── mappings/
│   │   ├── onboarding/             # Customer onboarding stubs
│   │   ├── auth/                   # Login, refresh, logout, /me stubs
│   │   ├── transfer/               # Balance, transfer, cancel stubs + scenarios
│   │   └── security/               # Auth header + correlation ID stubs
│   └── __files/
│       ├── onboarding/             # Response body templates
│       ├── auth/                   # Response body templates
│       └── transfer/               # Response body templates
├── src/main/java/com/example/banking/   # Paradigm 2: real client (SUT)
│   ├── BankingApiClient.java       # HTTP client wrapping login, getBalance, initiateTransfer
│   ├── BankingApiException.java    # Typed exception (statusCode + errorCode)
│   ├── LoginResponse.java          # JWT login response record
│   ├── BalanceResponse.java        # Account balance record
│   ├── TransferRequest.java        # Transfer request payload record
│   └── TransferResponse.java       # Transfer response record
└── src/test/java/com/example/
    ├── wiremock/                    # Paradigm 1: REST Assured → Docker WireMock
    │   ├── BaseWireMockTest.java        # Shared config + scenario reset
    │   ├── CustomerOnboardingTest.java  # Onboarding flow tests
    │   ├── AuthenticationTest.java      # Auth flow tests
    │   ├── FundTransferTest.java        # Transfer flow tests
    │   ├── TransferLifecycleScenarioTest.java  # Transfer state machine tests
    │   ├── OnboardingLifecycleScenarioTest.java # Onboarding state machine tests
    │   ├── BankingFaultSimulationTest.java      # Fault injection tests
    │   ├── SecurityHeadersTest.java     # Header matching tests
    │   └── EmbeddedWireMockTest.java    # Programmatic (no Docker) tests
    └── banking/                     # Paradigm 2: client tests → embedded WireMock
        └── BankingApiClientTest.java    # Consumer-driven contract tests for BankingApiClient
```

## Banking API Flows

### Flow 1: Customer Onboarding

```
POST   /api/v1/customers                                    # Submit KYC
GET    /api/v1/customers/applications/{applicationId}       # Poll status
POST   /api/v1/customers/applications/{applicationId}/documents  # Upload doc
GET    /api/v1/customers/{customerId}                        # Get profile
```

**Stateful scenario** (`onb-app-001`): PENDING → UNDER_REVIEW → APPROVED

### Flow 2: Authentication

```
POST   /api/v1/auth/login                                   # Login (john.doe / trigger.outage)
POST   /api/v1/auth/refresh                                 # Refresh token
POST   /api/v1/auth/logout                                  # Logout
GET    /api/v1/auth/me                                      # Current user
```

### Flow 3: Fund Transfer

```
GET    /api/v1/accounts/{accountId}/balance                 # Get balance (slow-account = 5s delay)
POST   /api/v1/transfers                                    # Initiate (ACC-001 / ACC-INVALID / ACC-FAULT)
GET    /api/v1/transfers/{transferId}                       # Poll status
POST   /api/v1/transfers/{transferId}/cancel                # Cancel
```

**Stateful scenario** (`lifecycle-tf-001`): INITIATED → PROCESSING → COMPLETED
**Failure scenario** (`failed-tf-001`): INITIATED → FAILED

## Test Execution

```bash
# Paradigm 1: integration tests (requires Docker WireMock running)
mvn test

# Paradigm 2: client tests only (no Docker needed — embedded WireMock)
mvn test -Pembedded

# All tests: Paradigm 1 integration + Paradigm 2 client tests
mvn test -Pall-tests

# Skip fault tests (slow / connection-drop)
mvn test -Dgroups='!fault'

# Custom WireMock host/port (Paradigm 1 only)
mvn test -Dwiremock.host=192.168.1.100 -Dwiremock.port=9090
```

> **Note:** `BankingApiClientTest` (Paradigm 2) is excluded from the default `mvn test` run because it uses an embedded WireMock and does not require Docker. It is included in the `embedded` and `all-tests` Maven profiles.

## Stub Trigger Values

| Endpoint | Trigger | Behavior |
|----------|---------|----------|
| `POST /auth/login` | `username: john.doe` | 200 + JWT tokens |
| `POST /auth/login` | `username: trigger.outage` | 503 + Retry-After: 30 |
| `POST /auth/login` | any other username | 401 Invalid Credentials |
| `POST /transfers` | `fromAccount: ACC-001` | 202 Accepted |
| `POST /transfers` | `fromAccount: ACC-001, amount: 99999.99` | 422 Insufficient Funds |
| `POST /transfers` | `fromAccount: ACC-INVALID` | 404 Account Not Found |
| `POST /transfers` | `fromAccount: ACC-FAULT` | Connection dropped (fault) |
| `GET /accounts/slow-account/balance` | — | 200 after 5s delay |
| `POST /transfers/completed-transfer-001/cancel` | — | 409 Conflict |

## WireMock Admin API

```bash
# List all registered mappings
curl http://localhost:8080/__admin/mappings | jq .

# View scenario states
curl http://localhost:8080/__admin/scenarios | jq .

# Reset all scenarios to 'Started'
curl -X POST http://localhost:8080/__admin/scenarios/reset

# Health check
curl http://localhost:8080/__admin/health
```
