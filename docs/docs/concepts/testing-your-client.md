# Testing Your Client

The **mock server** is a strict reference implementation of your OpenAPI document.
It validates every incoming request, returns spec-compliant responses, and rejects anything that violates the contract.

---

## How the Mock Server Processes a Request

The mock server processes each request in three steps: validate, match, respond.

Here is the `GET /musketeers/{id}` operation from the [Musketeer API](https://github.com/contracteer-dev/contracteer-examples):

```yaml
# Excerpt from musketeer-api.yaml
# Full OpenAPI document: https://github.com/contracteer-dev/contracteer-examples
paths:
  /musketeers/{id}:
    get:
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
          examples:
            ATHOS:
              value: 1
            404_UNKNOWN_MUSKETEER:
              value: 999
      responses:
        '200':
          description: Musketeer found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Musketeer'
              examples:
                ATHOS:
                  value:
                    id: 1
                    name: Athos
                    rank: MUSKETEER
                    weapon: Rapier
        '400':
          description: Invalid musketeer ID
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '404':
          description: Musketeer not found
```

### Step 1: Request validation

The mock server validates the request against the operation's schema.
It checks that required parameters are present and that values match the declared types.

If the request is invalid:

- If the operation defines a `400` response, a `4XX` class response, or a `default` response, the mock server returns `400` with a body generated from that response schema.
- If none of these are defined, the mock server returns `418` with a diagnostic message explaining what failed.

`GET /musketeers/abc` sends a string where `id` expects an integer.
The operation defines a `400` response, so the mock server returns `400` with a ProblemDetail body.

If the request is valid, the mock server proceeds to step 2.

### Step 2: Scenario matching

The mock server compares the request against each scenario defined for the operation.

For elements where the scenario provides an example value, the request value must match exactly.
For elements where the scenario has no example, the mock server checks type and structure only.

`GET /musketeers/1` matches the ATHOS scenario -- the path parameter `id` equals the example value `1`.
The mock server returns `200` with Athos's response data from the OpenAPI document.

`GET /musketeers/999` matches the 404_UNKNOWN_MUSKETEER scenario.
The mock server returns `404`.

If exactly one scenario matches, the mock server returns that scenario's response.
If multiple scenarios match, it returns `418` to signal the ambiguity.
If no scenario matches, the mock server proceeds to step 3.

### Step 3: Schema-only response

The request is valid but matches no scenario.

`GET /musketeers/42` passes validation (42 is a valid integer) but does not match any scenario.
The mock server returns `200` with a body generated from the Musketeer schema.
The values are random but satisfy the type and format constraints.

This step serves the operation's [primary response](how-contracteer-works.md#the-primary-response).
When no primary response resolves -- several declared codes between 200 and 299, say, or only status code ranges (`2XX`, `4XX`) and `default` -- the mock server returns `418`.
It cannot determine which response to serve.

!!! note "One scenario does not cover every request"
    An operation declaring `200` and `201` resolves no primary response.
    A scenario on `201` makes the mock server answer that scenario's request with `201` -- every other valid request still gets `418`, because nothing says which of the two to serve.

When the operation defines multiple response content types, the mock server uses the `Accept` header to select one.
If the `Accept` header is absent or `*/*` and multiple content types exist, it returns `418`.

---

## The 418 Diagnostic Response

HTTP 418 is Contracteer's diagnostic signal.
It means: "I received your request, but I cannot determine the correct response."

The 418 is not a client error.
It is the mock server telling you that something is ambiguous or undefined.
The response body explains what went wrong.

The mock server returns 418 in five situations:

| Situation | What the 418 body tells you |
|-----------|----------------------------|
| Request is invalid, no `400`, `4XX`, or `default` response defined | Which validation rules the request violated |
| Multiple scenarios match the request | Which scenarios matched, so you can make your examples more specific |
| Several responses could be served, and no scenario chooses between them | Which status codes are declared, so you can add a scenario for the one you expect |
| No declared response can be served on its own -- only status code ranges (`2XX`, `4XX`), `default`, or no response at all | Which responses the operation declares, and that an explicit status code is missing |
| Multiple response content types, no `Accept` header to disambiguate | Which content types are available |

!!! tip
    The 418 response body is your best debugging tool when the mock server does not respond as expected.
    Read it before investigating further -- it usually points directly to the cause.

---

## A Strict Reference Implementation

The mock server validates every incoming request against the full OpenAPI schema.
This surprises developers accustomed to simpler mock tools that accept any request and return canned responses.

The strictness is intentional.
If the mock server accepted invalid requests, your client tests would pass locally.
They would fail against a correctly-implemented real server.
That is false confidence -- the worst kind of test result.

The mock server catches client-side bugs that lenient mocks miss:

- A missing required header.
- A request body with the wrong field types.
- A path parameter in the wrong format.

If the mock server rejects your request, a correctly-implemented real server would reject it too.

Like the verifier, the mock server follows [Postel's Law](https://en.wikipedia.org/wiki/Robustness_principle).
It validates request elements declared in the OpenAPI document.
Extra query parameters or headers that the OpenAPI document does not mention are ignored.
There is no schema to validate them against.

---

## The Mock Server in Your Test Strategy

The mock server serves as a [test double](https://martinfowler.com/bliki/TestDouble.html) for your client's integration tests.

In Martin Fowler's [distinction](https://martinfowler.com/bliki/IntegrationTest.html), a **narrow integration test** exercises your code's interaction with a separate component.
It replaces the real component with a test double.
The mock server fills this role.
Your client sends real HTTP requests to a server that behaves according to the OpenAPI document.

This gives you three properties that end-to-end tests against a real server cannot:

**Speed.**
The mock server runs in-process.
No deployment, no network latency, no waiting for external infrastructure.

**Reliability.**
The mock server's behavior is deterministic when scenarios are defined.
Tests do not fail because of external server state, data changes, or network issues.

**Contract confidence.**
The mock server's strictness means that a passing test is a genuine signal.
Your client sends valid requests and handles the documented responses.
If the real server conforms to the same OpenAPI document -- which the [verifier](testing-your-server.md) ensures -- the integration will work.

Unit tests verify that your business logic is correct.
The mock server verifies that your client respects the contract at the HTTP boundary.
Your client sends well-formed requests and handles the documented responses.
Together, they cover the two concerns that integration and end-to-end tests traditionally caught -- internal correctness and external conformance.
They do so at the speed and reliability of the base of the testing pyramid.

This drastically reduces the number of integration and end-to-end tests you need.
The expensive top of the pyramid shrinks because the contract boundary is already verified.
The remaining integration tests can focus on concerns that neither unit tests nor contract tests cover.
These include real database behavior, third-party service quirks, or deployment-specific configuration.

---

## Assert Structure, Not Values

A contract test verifies that your client respects the contract -- it sends valid requests and handles the documented response structure.
It does not verify that the server returns the right data.
That is a functional test, and mixing the two defeats the purpose of contract testing.

When your client receives a response, assert that it correctly parses the structure: fields are present, types are correct, enums are handled.
Do not assert that specific values match the OpenAPI examples.

```java
// Contract test -- asserts structure
assertThat(musketeer.getName()).isNotNull();
assertThat(musketeer.getRank()).isIn("CADET", "MUSKETEER", "CAPTAIN");

// Functional test -- depends on specific data
assertThat(musketeer.getName()).isEqualTo("Athos");
```

If your test asserts `"Athos"`, it is no longer testing the contract.
It is testing that the mock server returns the example value -- which is not the client's concern.
Your client must handle **any** musketeer, not just Athos.

This mirrors the verifier's philosophy.
The verifier checks that the server returns the right **shape**, not the right **data**.
Client tests should do the same.

The mock server reinforces this naturally.
When no scenario matches, it returns random values generated from the schema.
Value-based assertions would be flaky against these responses.
Structure-based assertions pass regardless of whether the response comes from a scenario or from the schema.

If you need to test how your client handles a specific value, that belongs in a unit test for the parsing logic -- not in a contract test against the mock server.

---

## Key Takeaways

- The mock server processes each request in three steps: validate against the schema, match against scenarios, fall back to a generated response.
- It returns `418` when it cannot determine the correct response -- read the 418 body for diagnostics.
- The mock server is strict by design.
  If it rejects your request, a correctly-implemented real server would too.
- It follows Postel's Law: it validates what the OpenAPI document defines and ignores undeclared elements.
- Assert structure, not values -- your client should handle any valid response, not just the example data.
- Use the mock server as a test double for narrow integration tests -- fast, reliable, and contract-enforcing.

---

## Further Reading

- Martin Fowler, [IntegrationTest](https://martinfowler.com/bliki/IntegrationTest.html) -- the narrow vs. broad distinction, and why test doubles matter for integration tests.
- Martin Fowler, [TestDouble](https://martinfowler.com/bliki/TestDouble.html) -- the general pattern of replacing a real component with a controlled substitute in tests.

---

## Next Steps

- [Testing Your Server](testing-your-server.md) -- how the verifier tests your server against the OpenAPI document.
- [Creating Scenarios](scenarios.md) -- how to write OpenAPI examples that produce the scenarios you want.
- Getting started with the mock server: [Mock Server](../getting-started/mockserver.md) | [Mock Server (Spring Boot)](../getting-started/mockserver-spring.md)