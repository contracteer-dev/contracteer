# Creating Scenarios

A **scenario** is a named pairing of specific request values with an expected response for a given status code.
This page explains how to write OpenAPI examples that produce the scenarios you want.

All examples come from the [Musketeer API](https://github.com/contracteer-dev/contracteer-examples).

---

## OpenAPI Examples: `examples` and `example`

The OpenAPI Specification defines two ways to provide example values: the `examples` keyword and the `example` keyword.

### The `examples` keyword

The `examples` keyword is a map where each entry has a name and a value.
Contracteer uses the names as **example keys** to create scenarios.

Here is the `GET /musketeers/{id}` operation with `examples` on the path parameter and the response body:

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
            PORTHOS:
              value: 2
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
                PORTHOS:
                  value:
                    id: 2
                    name: Porthos
                    rank: MUSKETEER
                    weapon: Musket
```

The `examples` keyword can appear on any parameter (path, query, header, cookie), on request body media types, on response headers, and on response body media types.

### The `example` keyword

The `example` keyword provides a single value instead of a named map.
Contracteer treats it the same way as `examples` -- it participates in the same intersection logic used to create scenarios.

The `POST /missions` operation uses `example` on the request body and the response `Location` header:

```yaml
# Excerpt from musketeer-api.yaml
  /missions:
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateMission'
            example:
              title: Rescue Constance
              description: >-
                Free Constance Bonacieux from
                the clutches of Milady de Winter
              status: PLANNED
              musketeers:
                - d'Artagnan
                - Athos
      responses:
        '201':
          description: Mission created
          headers:
            Location:
              schema:
                type: string
              example: /missions/3
```

Both the request body and the response header carry an `example`.
Contracteer creates one scenario: send the mission data, expect a `201` with `Location: /missions/3`.

!!! danger "`example` and `examples` are mutually exclusive"
    The OpenAPI Specification declares `example` and `examples` mutually exclusive on the same element.
    If both are present on the same parameter or media type, Contracteer rejects the OpenAPI document.

---

## How Contracteer Creates Scenarios

Contracteer creates scenarios by matching example keys across request and response elements.

For each response status code, Contracteer:

1. Collects all example keys from request elements (parameters and request body).
2. Collects all example keys from response elements (headers and response body).
3. Computes the **intersection** -- example keys that appear on both sides.
4. Creates one scenario per shared key.

From the `GET /musketeers/{id}` operation above, the request side has keys `ATHOS` and `PORTHOS` on the path parameter.
The response side has the same keys on the response body.
Contracteer creates two scenarios:

| Scenario | Request | Expected response |
|----------|---------|-------------------|
| ATHOS | `GET /musketeers/1` | `200` with Athos's data |
| PORTHOS | `GET /musketeers/2` | `200` with Porthos's data |

---

## You Specify What Matters, Contracteer Fills the Rest

A scenario requires at least one request element and one response element sharing the same example key.
Not every element needs an example for that key.
Elements without an example are filled with random values from the schema.

This keeps your OpenAPI document light.
You only define the values that are relevant for a specific case -- Contracteer generates the rest.

This principle applies to all scenario types: intersection-based scenarios, status-code-prefixed scenarios, and scenarios from the `example` keyword.

Not every operation needs scenarios.
When request values do not affect which response the server returns, Contracteer handles verification with random values generated from the schema.
See [Testing Your Server](testing-your-server.md) for how schema-only verification works.

The `GET /musketeers/{id}/missions` operation has two parameters -- a path parameter `id` and an optional query parameter `status`.
Consider a version where only the path parameter and the response body carry the `ATHOS_MISSIONS` key:

```yaml
# Adapted from musketeer-api.yaml
  /musketeers/{id}/missions:
    get:
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
          examples:
            ATHOS_MISSIONS:
              value: 1
        - name: status          # no example for ATHOS_MISSIONS
          in: query
          required: false
          schema:
            type: string
            enum: [PLANNED, IN_PROGRESS, COMPLETED]
      responses:
        '200':
          description: List of missions
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Mission'
              examples:
                ATHOS_MISSIONS:
                  value:
                    - id: 1
                      title: The Diamond Studs
                      status: COMPLETED
                      # remaining fields omitted for brevity
```

The key `ATHOS_MISSIONS` appears on the path parameter and on the response body -- that is enough to create a scenario.
The `status` query parameter has no example for this key, so Contracteer fills it with a random value from the schema.

---

## Status-Code-Prefixed Keys

The intersection rule requires at least one response element to carry the example key.
This does not work for responses with no body and no headers -- a `404 Not Found` with only a description, for example.

Status-code-prefixed keys are also useful when you only care about the status code for a given request.
You do not need to specify particular response values.

An example key that matches the pattern `{3 digits}_{description}` targets a specific response status code directly, bypassing the intersection rule.

From the `GET /musketeers/{id}` operation:

```yaml
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
          examples:
            ATHOS:
              value: 1
            PORTHOS:
              value: 2
            404_UNKNOWN_MUSKETEER:
              value: 999
      responses:
        # ...
        '404':
          description: Musketeer not found
```

The key `404_UNKNOWN_MUSKETEER` starts with `404_`, which tells Contracteer to target the 404 response.
The scenario sends `GET /musketeers/999` and expects a `404`.
No response examples are needed -- if the 404 response has a body schema, Contracteer generates random values from it.

The same principle applies: you specify only what matters.
Here, the request value (`999`) is what defines the case.
The response is entirely handled by Contracteer.

!!! note "Prefixed keys are exclusive"
    A status-code-prefixed key is exclusive to its status code.
    `404_UNKNOWN_MUSKETEER` is only eligible for the 404 response -- it never accidentally creates a scenario for a 200 response.

A status-code-prefixed key does not require an exact status code in the responses.
If the operation defines a `4XX` class response or a `default` response, Contracteer uses it as a fallback.
`404_UNKNOWN_MUSKETEER` works whether the operation defines `404`, `4XX`, or `default`.
Contracteer resolves the response using the same priority as for automatic 400 testing: exact status code, then class response, then default.

---

## Example Validation

Contracteer validates example values against their schemas during extraction.
A mismatch between an example value and its schema -- a string where an integer is expected, a missing required field -- would cause unexpected failures.
Catching these errors early, at extraction time, prevents confusing behavior at runtime.

The exception is **400 scenarios**.
Their request values are intentionally invalid -- that is the whole point of a 400 scenario.
Contracteer skips request validation for any scenario targeting a 400 response.

From `POST /musketeers`:

```yaml
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateMusketeer'
            examples:
              D_ARTAGNAN_JOINS:
                value:
                  name: d'Artagnan
                  rank: CADET
                  weapon: Rapier
              400_INVALID_MUSKETEER:
                value:
                  name: d'Artagnan
                  rank: KNIGHT
                  weapon: Rapier
      responses:
        # ...
        '400':
          description: Invalid input
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
```

The value for `D_ARTAGNAN_JOINS` is validated against the CreateMusketeer schema -- `CADET` must be a valid enum value.
The value for `400_INVALID_MUSKETEER` is not validated -- `KNIGHT` is intentionally not in the enum, and Contracteer accepts it.

---

## Multiple Content Types

When an operation defines multiple request or response content types, Contracteer produces one verification case per combination -- a **cartesian product**.

If a request body supports `application/json` and `text/plain`, and a response body supports the same two, a single example key produces four verification cases.
Each combination of request and response content type becomes its own case.

---

## Common Mistakes

**Example key on one side only.**
If a key appears only on request elements or only on response elements, no scenario is created.
Contracteer needs at least one request element and one response element sharing the key.

**`example` under `schema` properties.**
Contracteer reads examples from parameters and media types.
An `example` on a schema property is not used for scenario creation:

```yaml
# This does NOT produce a scenario
schema:
  type: object
  properties:
    name:
      type: string
      example: Athos    # not used by Contracteer
```

**Confusing `example` and `examples`.**
`example` is a single value.
`examples` is a map where each entry has a name and a `value` field:

```yaml
# Single value
example: 42

# Named map — each name is an example key
examples:
  ATHOS:
    value: 42
  PORTHOS:
    value: 43
```

---

## Key Takeaways

- The OpenAPI `examples` keyword provides named example values on parameters and media types.
  Contracteer uses the names as example keys to create scenarios by intersecting request and response keys.
- The single `example` keyword is treated the same way as `examples` and participates in the same intersection logic.
- A scenario requires at least one request element and one response element sharing the same example key.
  You specify only the values that matter -- Contracteer generates the rest from the schema.
- Status-code-prefixed keys (`404_NOT_FOUND`, `400_INVALID_INPUT`) target a specific status code directly, bypassing the intersection rule.
- Contracteer validates example values against their schemas at extraction time.
  400 scenarios are exempt -- their request values are intentionally invalid.
- Multiple content types produce a cartesian product of verification cases.

---

## Further Reading

- Swagger's guide to `examples` and `example`:
    - [OpenAPI 3.0](https://swagger.io/docs/specification/v3_0/adding-examples/)
    - [OpenAPI 3.1](https://swagger.io/docs/specification/v3_1/adding-examples/)
- Parameter Object, where `examples` and `example` are defined for parameters:
    - [OpenAPI 3.0.4](https://spec.openapis.org/oas/v3.0.4#parameter-object)
    - [OpenAPI 3.1.2](https://spec.openapis.org/oas/v3.1.2#parameter-object)
- Media Type Object, where `examples` and `example` are defined for request and response bodies:
    - [OpenAPI 3.0.4](https://spec.openapis.org/oas/v3.0.4#media-type-object)
    - [OpenAPI 3.1.2](https://spec.openapis.org/oas/v3.1.2#media-type-object)

---

## Next Steps

- [How Contracteer Works](how-contracteer-works.md) -- the high-level principles behind the verifier and the mock server.
- [Testing Your Server](testing-your-server.md) -- how the verifier uses scenarios and verification cases to test your server.
- [Testing Your Client](testing-your-client.md) -- how the mock server uses scenarios to match requests and generate responses.
- [Getting Started](../getting-started/index.md) -- set up Contracteer in your project.
