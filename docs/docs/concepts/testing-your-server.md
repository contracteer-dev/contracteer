# Testing Your Server

The **verifier** tests your server against your OpenAPI document.
It builds requests from the OpenAPI document, sends them to your running server, and validates that responses conform to the contract.

---

## What the Verifier Sends and Checks

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

This operation contains two [scenarios](scenarios.md) -- named pairings of request values with expected responses, derived from OpenAPI examples.
Each scenario produces a **verification case**: a request the verifier builds, sends, and validates.
Contracteer also generates verification cases automatically when the OpenAPI document provides enough information.

From this single operation, the verifier creates three verification cases:

**ATHOS** -- The example key `ATHOS` appears on both the path parameter and the response body, creating a scenario.
The verifier sends `GET /musketeers/1` and validates that the response has status `200` with a body matching the Musketeer schema.

**404_UNKNOWN_MUSKETEER** -- The key starts with `404_`, creating a scenario that targets the 404 response directly.
The verifier sends `GET /musketeers/999` and validates that the response has status `404`.

**Type-mismatch** -- The `id` parameter has `type: integer` and a `400` response is defined.
Contracteer generates this verification case automatically: it sends `GET /musketeers/<<not a integer>>` -- a string where an integer is expected -- and validates that the server rejects it.
This operation declares `400` and `404`, so either is accepted, and the body is validated against the schema declared for whichever status came back.

---

## Shape, Not Values

In the ATHOS verification case, the verifier sends `GET /musketeers/1` and expects a `200` response with a body containing `id` (integer), `name` (string), `rank` (string), and `weapon` (string).
It does not check that `id` is `1`, that `name` is `"Athos"`, or that any specific value is returned.
The server can return any Musketeer that satisfies the schema.

For every verification case, the verifier checks:

| Element | What the verifier checks |
|---------|--------------------------|
| Status code | Must match the expected status code exactly |
| Required response headers | Must be present and correctly typed |
| Optional response headers | If present, must be correctly typed |
| Response body | Must match the schema's structure and types |
| Response body **values** | Not checked |

This is a deliberate choice.
A contract test verifies **externally observable guarantees** -- the status code and response shape -- not which data the server returned.
If the verifier compared values, it would over-constrain the server: the server would have to return exactly the example data to pass the test.
That turns a contract test into a functional test.

---

## Automatic 400 Testing

When an operation defines a `400` response, Contracteer generates **type-mismatch verification cases** automatically.

For each request parameter or request body whose type can be meaningfully violated, the verifier sends a value of the wrong type.
It expects the server to reject the request with `400`, `422`, or `404` -- and only with a status the OpenAPI document covers.

In the Musketeer API, the `id` parameter is `type: integer`.
The verifier sends the string `<<not a integer>>` where an integer is expected.
A server that validates its inputs rejects this with a `400` response, or with the `404` this operation also declares.

### What triggers automatic 400 testing

Two conditions must be met:

1. The operation defines a `400` response, a `4XX` class response, or a `default` response.
2. At least one request parameter or request body has a type that can be mutated.

Contracteer resolves the 400 response schema using the following priority: exact `400` → `4XX` class → `default`.
Many real-world APIs define error responses via `4XX` or `default` instead of listing each error status code explicitly.

### Which status codes the verifier accepts

A malformed request settles the class of the response -- the client erred -- but not the exact status code.
Frameworks disagree: Spring and ASP.NET answer `400`, FastAPI and Laravel answer `422`, and Jakarta RESTful Web Services requires `404` for a path or query parameter it cannot convert.

The verifier accepts all three, under two conditions:

1. `404` counts only when the mutated element is a path or query parameter, because that is the only place a framework produces it.
2. The status must be covered by the OpenAPI document -- declared exactly, through a class response such as `4XX`, or through `default`.

Any other status fails the case, `401`, `403`, and `429` included.
Those mean the request was refused before the input was judged, so accepting them would let the case pass without testing validation at all.

The response body is validated against the schema declared for the status the server returned.
The case name lists the accepted codes: `GET /musketeers/{id} -> 400|404 (auto: path 'id' type mismatch)`.

### Which types can be mutated

Not all types can produce a meaningful type mismatch.
A `string` parameter accepts any value -- there is no string value that is "not a string."
Contracteer generates mutations for types where a wrong-type value is unambiguous:

| Type | Mutation |
|------|----------|
| `integer` | A non-numeric string |
| `number` | A non-numeric string |
| `boolean` | A non-boolean string |
| `object` | A non-object value |
| `array` | A non-array value |
| String formats (`date`, `date-time`, `uuid`, `email`, `byte`) | A string that violates the format |

`string` parameters without a format constraint are not mutated.
`array` parameters whose items are non-mutable (e.g., array of strings) are also
skipped, because the mutated value is indistinguishable from a valid single-element
array.

Contracteer generates one type-mismatch case per parameter category (path, query, header, cookie) and one for the request body.
It mutates the first eligible parameter in each category.

Type mismatch is also skipped in the following cases:

- **`application/x-www-form-urlencoded` request bodies** with all optional
  properties and `additionalProperties` not set to `false` -- the form parser
  accepts any string as valid form data.
- **`deepObject` query parameters** with all optional properties and
  `additionalProperties` not set to `false` -- the deep object decoder ignores
  non-matching keys.

!!! note
    Automatic 400 testing validates that your server rejects structurally invalid input.
    It does not test business validation rules (e.g., "age must be positive").
    For business validation, define explicit 400 scenarios with intentionally invalid example values in your OpenAPI document.

---

## Schema-Only Verification

Not every operation needs scenarios.
When an operation has no 2xx scenario, the verifier generates a fully random request from the schema.
Parameter values satisfy the type and format constraints, and a random request body is included if one is defined.
It sends this request and validates that the response matches the 2xx response schema.

This is the right choice when request values do not affect which response the server returns.
A `GET /health` endpoint, a `POST` that creates a resource from any valid body -- these operations need no examples in the OpenAPI document.
Contracteer generates valid requests and verifies the contract, keeping the OpenAPI document lean.

Schema-only verification has one constraint: the operation must define exactly one 2xx response status code.
If multiple 2xx responses exist and no scenario disambiguates which one to expect, the verifier cannot determine the correct status code.
It skips the operation.

When multiple request or response content types exist, the verifier generates one verification case per combination -- a cartesian product.

---

## What the Verifier Does Not Test

The verifier is precise in scope.
It validates what the OpenAPI document defines and ignores what it does not.

**No value comparison.**
Response values are not compared to example values.
The server is free to return any data that satisfies the schema.

**No undeclared fields** (by default).
If your server returns fields that the OpenAPI document does not define, the verifier ignores them.
This follows [Postel's Law](https://en.wikipedia.org/wiki/Robustness_principle): validate what is defined, tolerate what is not.
A server can add new response fields without breaking the verifier.
However, if the schema declares `additionalProperties: false`, the verifier enforces it -- extra fields are rejected because the constraint is explicitly declared.

**No business logic.**
A contract test does not verify that the server computed the right result.
If the server returns a wrong price in the correct format, the contract test passes.

**No authentication or authorization.**
Multi-step flows involving tokens, sessions, or permissions are outside the scope of contract testing.

For a broader discussion of what contract tests do and do not catch, see [What Is Contract Testing?](contract-testing.md).

---

## Key Takeaways

- The verifier creates verification cases from your OpenAPI document: scenario-based (from your examples), status-code-prefixed (from keys like `404_NOT_FOUND`), automatic type-mismatch (when a 400 response is defined), and schema-only (when no examples exist).
- It checks status code, required headers, and response body structure -- not response values.
- Automatic 400 testing sends intentionally wrong types and expects the server to reject them.
  It covers types that can be meaningfully violated (integers, booleans, dates, objects, arrays) but not plain strings.
- Schema-only verification handles operations where request values do not matter.
  It generates random requests and requires exactly one 2xx response.
  Define scenarios only when specific request values affect the expected response.
- The verifier follows Postel's Law: it validates what the OpenAPI document defines and ignores additional fields.

---

## Further Reading

- Martin Fowler, [ContractTest](https://martinfowler.com/bliki/ContractTest.html) -- contract tests verify format consistency, not exact data values.

---

## Next Steps

- [Testing Your Client](testing-your-client.md) -- how the mock server validates requests and generates responses.
- [Creating Scenarios](scenarios.md) -- how to write OpenAPI examples that produce the scenarios you want.
- Getting started with the verifier: [Verifier](../getting-started/verifier.md) | [Verifier (JUnit 5)](../getting-started/verifier-junit.md)