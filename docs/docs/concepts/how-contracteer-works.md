# How Contracteer Works

Contracteer reads your OpenAPI document and provides two tools.
The **verifier** tests your server against the document.
The **mock server** gives your client tests a strict, spec-compliant API to run against.
Both derive their behavior entirely from the document -- no handwritten mocks, no manually maintained test cases.

---

## The Musketeer API

Throughout this documentation, we use the [Musketeer API](https://github.com/contracteer-dev/contracteer-examples) -- a small REST API for managing musketeers and their missions.

!!! note "Why musketeers?"
    Contracteer is a portmanteau of *Contract* and *Musketeer* -- because every API deserves a loyal guard defending its contract.

Here is the `GET /musketeers/{id}` operation from its OpenAPI document:

```yaml
# Excerpt from musketeer-api.yaml
# Full OpenAPI document: https://github.com/contracteer-dev/contracteer-examples
paths:
  /musketeers/{id}:
    get:
      summary: Get a musketeer by ID
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
        '404':
          description: Musketeer not found
        '400':
          description: Invalid musketeer ID
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
# Musketeer and ProblemDetail schemas omitted — see full spec
```

---

## From Document to Test Cases

From that one `GET /musketeers/{id}` operation, Contracteer derives two **scenarios** and one automatic **verification case**.

A **scenario** is a named pairing of specific request values with an expected response for a given status code, derived from OpenAPI examples.
Two scenarios come from this operation:

- **ATHOS** -- the example key `ATHOS` appears on both the request parameter (value `1`) and the response body (Athos's data).
  Contracteer pairs them: send `GET /musketeers/1`, expect a `200` with a response matching the Musketeer schema.
- **404_UNKNOWN_MUSKETEER** -- the example key starts with `404_`, which tells Contracteer to target the 404 response directly.
  Send `GET /musketeers/999`, expect a `404`.

A **verification case** is what Contracteer actually executes.
Every scenario produces a verification case, but Contracteer also generates verification cases automatically.
Here, the OpenAPI document declares that `id` is an integer and that a `400` response exists.
Contracteer creates an automatic **type-mismatch verification case**: it sends an invalid type (a string instead of an integer) and expects the server to reject it.
This operation declares `400` and `404`, so either is accepted, and the response is validated against the schema declared for it.

Three verification cases from one operation, covering the happy path, a not-found case, and input validation -- all derived from the OpenAPI document.

Contracteer generates a second kind of automatic case -- a **schema-only** case -- when no scenario targets the response an operation is expected to answer with.
`GET /musketeers/{id}` produces none, because the `ATHOS` scenario already covers its `200`.

The full mechanics of how scenarios are created are covered in [Creating Scenarios](scenarios.md).

---

## The Primary Response

The **primary response** is the single response Contracteer targets for an operation when no scenario applies -- the one the verifier asserts and the mock server serves.
For `GET /musketeers/{id}` it is the `200`: of the three declared responses, the only one between 200 and 299.

For any operation, it resolves from the declared responses alone.
Some of them can never be the primary response.
Status code ranges (`2XX`, `4XX`) and `default` cannot: they describe a class of outcomes, not the one response the operation answers with.
Codes between 100 and 199 and `304` cannot either: no ordinary request produces them.

The primary response is chosen among the rest:

- When the operation declares one of them and nothing else at all, it is the primary response, whatever its status code.
  A lone `302` or `403` is treated exactly like a lone `200`.
- Otherwise, it is the single remaining code between 200 and 299.
  When none of the remaining codes falls between 200 and 299, it is the single remaining code between 300 and 399.

When a primary response resolves, the operation needs no scenario.
The verifier sends a request generated from the schema and validates the answer against the primary response's schema.
The mock server serves that same response, with values generated the same way.
Write a scenario only when specific request values decide which response comes back.

When nothing resolves, the document still loads -- Contracteer rejects nothing.
The verifier generates no schema-only case for that operation and reports its primary response as unverified.
The mock server answers `418` to any request no scenario matches, and lists the operation when it starts.
Declaring the exact status code the operation answers with fixes both.
When several codes compete -- `200` and `201`, say -- a scenario for each of them covers the verifier, but the mock server still answers `418` to requests those scenarios do not match.

---

## The Verifier: Testing Your Server

For each verification case, the verifier builds a request, sends it to your real, running server, and validates the response.
It checks the status code, required headers, and response body against the schema's structure and types.
The verifier does **not** check response values against the example values.

This is a deliberate choice.
A contract test verifies **externally observable guarantees** -- the status code and response shape -- not which data the server returned.
If the verifier compared values, it would over-constrain the server: the server would have to return exactly the example data to pass the test.
That turns a contract test into a functional test.
The contract guarantees the **shape** of the response, not its content.
Your server is free to return any data that satisfies the schema.

How the verifier works in depth is covered in [Testing Your Server](testing-your-server.md).

---

## The Mock Server: Testing Your Client

The mock server is a **strict reference implementation** of the API defined by your OpenAPI document.
It validates every incoming request against the OpenAPI schema and returns spec-compliant responses.

If a request matches a scenario, the mock server returns that scenario's response values.
If the request is valid but matches no scenario, it returns a response generated from the schema.
If the request violates the OpenAPI document -- wrong types, missing required fields -- it rejects it.

This strictness is what makes the mock server useful for testing.
It enforces the contract on every request.
Your client tests run against an accurate representation of the real API -- fast, in-process, with no external infrastructure.

How the mock server works in depth -- including the 418 diagnostic response and how it fits into your test strategy -- is covered in [Testing Your Client](testing-your-client.md).

---

## An Intentional Asymmetry

The verifier and the mock server are deliberately different in what they enforce.

The verifier tests **representative cases**.
It cannot be exhaustive -- testing every possible request for every operation is not feasible.
Instead, it tests the named scenarios from your examples and automatic cases for error handling.
Its claim is:

> For each verification case, the server responds as the OpenAPI document says it should.

The mock server validates **every request**.
It cannot afford to be lenient.
If it accepted invalid requests, your client tests would pass locally but fail against the real server.
That would be false confidence.
Its guarantee is:

> Any request the mock server accepts would also be accepted by a correctly-implemented real server.

This asymmetry is required, not accidental.
The verifier proves that your server honors the contract for specific cases.
The mock server proves that your client respects the contract in all cases.

---

## Validate What Is Defined, Ignore What Is Not

Contracteer follows [Postel's Law](https://en.wikipedia.org/wiki/Robustness_principle): *be conservative in what you send, liberal in what you accept*.

The **mock server** validates request elements declared in the OpenAPI document.
If your client sends an extra query parameter or header that the OpenAPI document doesn't mention, the mock server does not reject the request.
There is no schema to validate it against.

The **verifier** validates response elements declared in the OpenAPI document.
If your server returns additional fields beyond what the OpenAPI document declares, the verifier does not fail.
Those fields are outside the contract's scope.

This tolerance applies when the OpenAPI document is silent.
When the OpenAPI document explicitly declares a constraint, Contracteer enforces it.
For example, `additionalProperties: false` on a response schema means the verifier rejects responses with extra fields.
The OpenAPI document's author deliberately restricted the contract.

This tolerance makes contract tests stable across API evolution.
A server can add new response fields without breaking the verifier (unless `additionalProperties: false` is declared).
A client can send additional parameters without breaking the mock server.
Only changes to what the OpenAPI document **defines** are caught -- which is exactly what contract tests should detect.

---

## Key Takeaways

- Contracteer reads your OpenAPI document and provides two tools: a verifier (tests your server) and a mock server (tests your client).
- Scenarios are named pairings of request and response values, derived from OpenAPI examples or status-code-prefixed keys.
  Verification cases are what Contracteer executes -- they include scenarios and automatic type-mismatch cases.
- The verifier checks structure and types, not values -- the contract guarantees the shape, not the content.
- The mock server is a strict reference implementation. It enforces the contract on every request, giving your client tests an accurate representation of the real API.
- The two are intentionally asymmetric: the verifier tests representative cases, the mock server validates every request.
- Contracteer follows Postel's Law: it validates what the OpenAPI document defines and tolerates what it doesn't.

---

## Further Reading

- Martin Fowler, [ContractTest](https://martinfowler.com/bliki/ContractTest.html) -- contract tests verify format consistency, not exact data values.
- [Postel's Law](https://en.wikipedia.org/wiki/Robustness_principle) -- the robustness principle that guides Contracteer's tolerance design.

---

## Next Steps

- [Testing Your Server](testing-your-server.md) -- how the verifier works in depth, including automatic 400 testing.
- [Testing Your Client](testing-your-client.md) -- how the mock server works in depth, including the 418 diagnostic response.
- [Creating Scenarios](scenarios.md) -- how to create scenarios from your OpenAPI document.
- [Getting Started](../getting-started/index.md) -- set up Contracteer in your project.
