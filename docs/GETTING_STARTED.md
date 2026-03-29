# Getting Started

This guide walks you through running the WireMock Banking POC from scratch.

## Step 1: Start WireMock

```bash
./scripts/start.sh
```

Expected output:
```
Starting WireMock Banking POC...
[+] Running 1/1
 ✔ Container wiremock-banking-poc  Started
Waiting for WireMock to be ready...
WireMock is ready at http://localhost:8080
Admin UI: http://localhost:8080/__admin/mappings
```

## Step 2: Try the Stubs with curl

### Customer Onboarding

```bash
# Submit KYC application
curl -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Smith","dateOfBirth":"1985-08-20","idNumber":"ID-123","address":{"street":"123 Main St","city":"Chicago","state":"IL","zipCode":"60601"}}' | jq .

# Expected: 202 with applicationId, status: "PENDING"

# Check application status
curl http://localhost:8080/api/v1/customers/applications/my-app-123 | jq .
# Expected: applicationId echoed back, status: "PENDING"

# Stateful scenario - call 3 times to watch status change
curl http://localhost:8080/api/v1/customers/applications/onb-app-001 | jq .status
# "PENDING"
curl http://localhost:8080/api/v1/customers/applications/onb-app-001 | jq .status
# "UNDER_REVIEW"
curl http://localhost:8080/api/v1/customers/applications/onb-app-001 | jq .status
# "APPROVED"
```

### Authentication

```bash
# Login with valid credentials
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john.doe","password":"SecureP@ss123"}' | jq .
# Expected: 200 with accessToken, refreshToken

# Login with invalid credentials
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"unknown","password":"wrong"}' | jq .
# Expected: 401 with error: "INVALID_CREDENTIALS"

# Simulate auth service outage
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trigger.outage","password":"any"}' -v 2>&1 | grep -E "< HTTP|Retry-After|error"
# Expected: 503 with Retry-After: 30

# Get current user (requires Bearer token)
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTAwMSIsIm5hbWUiOiJKb2huIERvZSIsInJvbGUiOiJDVVNUT01FUiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDAzNjAwfQ.mock-signature"
curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq .
# Expected: 200 with user profile

# Without token
curl http://localhost:8080/api/v1/auth/me | jq .
# Expected: 401
```

### Fund Transfer

```bash
# Check balance
curl http://localhost:8080/api/v1/accounts/ACC-001/balance \
  -H "Authorization: Bearer $TOKEN" | jq .
# Expected: 200 with balance, accountId echoed

# Initiate a transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC-001","toAccount":"ACC-002","amount":500.00,"currency":"USD","reference":"INV-001"}' | jq .
# Expected: 202 with transferId, all request fields echoed back

# Insufficient funds
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC-001","toAccount":"ACC-002","amount":99999.99,"currency":"USD","reference":"OVERSPEND"}' | jq .
# Expected: 422 with error: "INSUFFICIENT_FUNDS"

# Invalid account
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC-INVALID","toAccount":"ACC-002","amount":100,"currency":"USD","reference":"TEST"}' | jq .
# Expected: 404 with error: "ACCOUNT_NOT_FOUND"

# Transfer lifecycle scenario (reset first, then call 3 times)
curl -X POST http://localhost:8080/__admin/scenarios/reset  # reset
curl http://localhost:8080/api/v1/transfers/lifecycle-tf-001 | jq .status  # "INITIATED"
curl http://localhost:8080/api/v1/transfers/lifecycle-tf-001 | jq .status  # "PROCESSING"
curl http://localhost:8080/api/v1/transfers/lifecycle-tf-001 | jq .status  # "COMPLETED"
```

## Step 3: Modify a Stub

WireMock hot-reloads mappings. To change a stub:

1. Edit any file in `wiremock/mappings/`
2. Call the reload endpoint:
   ```bash
   curl -X POST http://localhost:8080/__admin/mappings/reset
   ```
3. The change takes effect immediately — no restart needed.

**Example:** Change the login success response to include an additional field:

```bash
# Edit wiremock/__files/auth/login-success-response.json
# Add "customField": "hello" to the JSON
# Then reload:
curl -X POST http://localhost:8080/__admin/mappings/reset
# Test it:
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john.doe","password":"any"}' | jq .customField
```

## Step 4: Run the Test Suite

```bash
# Make sure WireMock is running first
./scripts/start.sh

# Run all integration tests
mvn test

# Run a specific test class
mvn test -Dtest=AuthenticationTest

# Run embedded tests (no Docker needed)
mvn test -Pembedded -Dtest=EmbeddedWireMockTest
```

## Step 5: Explore the Admin API

```bash
# See all loaded stub mappings
curl http://localhost:8080/__admin/mappings | jq '.mappings | length'

# See current scenario states
curl http://localhost:8080/__admin/scenarios | jq '.scenarios[] | {name: .name, state: .state}'

# See recent unmatched requests (useful for debugging)
curl http://localhost:8080/__admin/requests/unmatched | jq .
```

## Step 6: Stop WireMock

```bash
./scripts/stop.sh
```

## Troubleshooting

**Port 8080 already in use:**
```bash
WIREMOCK_PORT=9090 ./scripts/start.sh
mvn test -Dwiremock.port=9090
```

**Stub not matching:**
```bash
# Check what WireMock received vs what it matched
curl http://localhost:8080/__admin/requests | jq '.requests[-1]'
# Look at "wasMatched": false entries
```

**Scenario state stuck:**
```bash
curl -X POST http://localhost:8080/__admin/scenarios/reset
```

**Rebuild stubs without restart:**
```bash
curl -X POST http://localhost:8080/__admin/mappings/reset
```
