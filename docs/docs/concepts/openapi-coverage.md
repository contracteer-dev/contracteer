# OpenAPI Coverage

Contracteer supports OpenAPI 3.0 and 3.1.
Behavior is the same across both versions except where noted below.

- Using OpenAPI 3.0? Jump to [OpenAPI 3.0-specific behavior](#openapi-30-specific-behavior).
- Using OpenAPI 3.1? Jump to [OpenAPI 3.1-specific behavior](#openapi-31-specific-behavior) and [Unsupported OpenAPI 3.1 keywords](#unsupported-openapi-31-keywords).
- Migrating an OpenAPI 3.0 document to 3.1?
  See [OpenAPI 3.1-specific behavior](#openapi-31-specific-behavior) for what becomes available and [Unsupported OpenAPI 3.1 keywords](#unsupported-openapi-31-keywords) for what Contracteer rejects.
  For syntax rewrites required by 3.1 itself, consult the [OpenAPI Specification 3.1.2](https://spec.openapis.org/oas/v3.1.2).

---

## Limitations

Contracteer handles unsupported features in one of two ways.
Some are accepted but degraded -- the OpenAPI document still loads, and the table below explains the impact (for example, unsupported content types skip the operation with a warning).
Others -- chiefly unsupported JSON Schema 2020-12 keywords -- are rejected at load with an explanatory message; see [Unsupported OpenAPI 3.1 keywords](#unsupported-openapi-31-keywords).

| Feature                                     | Impact                                                                                                                                                                                                                                                                                                                                                |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `application/xml`                           | Operations with XML-only content types are skipped with a warning.                                                                                                                                                                                                                                                                                    |
| Missing schema on body or parameter content | Operations with request/response bodies or parameter `content` entries that have no schema are skipped with a warning. Exception: bodies declared with a binary content type (`application/octet-stream`, `image/*`, etc.) default to binary content.                                                                                                |
| Empty schema (`schema: {}`)                 | Treated as unconstrained ("any type"). Validation accepts any value; random generation produces a placeholder value that may not be meaningful for contract testing.                                                                                                                                                                                  |
| `externalValue` (Example Objects)           | Only inline `value` is read. External references are not fetched.                                                                                                                                                                                                                                                                                     |
| Pattern generation (regex)                  | Common Java-specific constructs (POSIX classes, `\p{Is...}` aliases) are rewritten automatically. Patterns that the generator cannot handle are rejected at load time. See [Troubleshooting](../guides/troubleshooting.md#pattern-not-supported-for-value-generation) for details.                                                                                |
| Unknown integer/number formats              | Ignored with a warning. Only `int32`, `int64`, `float`, `double` apply range constraints.                                                                                                                                                                                                                                                             |

If Contracteer skips an operation or ignores a keyword, it logs a warning when loading the OpenAPI document.

---

## Deviations from the Specification

The OpenAPI Specification is sometimes ambiguous or impractical for contract testing.
In these cases, Contracteer makes explicit choices.

### String constraint precedence

OpenAPI treats `format`, `pattern`, `minLength`, and `maxLength` as independent constraints applied simultaneously.
Contracteer applies them in a strict precedence order:

1. **`format`** (email, uuid, date, date-time, byte, binary) -- the format defines the type entirely.
   `pattern` is always ignored with a warning.
   `minLength`/`maxLength` are supported by email, base64, and binary formats, but ignored by uuid, date, and date-time.
2. **`pattern`** -- the regex pattern defines the constraint.
   `minLength` and `maxLength` are ignored with a warning.
3. **`minLength` / `maxLength`** -- used only when neither `format` nor `pattern` is set.

Generating a random value that satisfies both a regex pattern and a length range is not reliably possible.
Contracteer uses the same constraint for both validation and generation to ensure consistency.
What Contracteer validates is what it can generate.

`enum` is always validated against the active constraint when loading the OpenAPI document.
If all enum values satisfy the active constraint, the OpenAPI document is accepted.
If any enum value violates it, the document is rejected.

Random value generation for patterns uses the [RgxGen](https://github.com/curious-odd-man/rgxgen) library.
RgxGen supports most common regex features, but lookaheads and lookbehinds do not generate correctly.
If Contracteer generates invalid values for your pattern, use `enum` values instead, or provide explicit `examples` in your OpenAPI document.

### Multipart default content types

The OpenAPI Specification defaults arrays of primitives to `text/plain` in multipart parts.
Contracteer uses `application/json` for all arrays.
The OpenAPI Specification does not define how to serialize an array as plain text in a multipart part.

| Property type                                       | Contracteer default        |
|-----------------------------------------------------|----------------------------|
| Primitive                                           | `text/plain`               |
| Array of primitives                                 | `application/json`         |
| Array of `format: binary` or `format: base64` items | `application/octet-stream` |
| Object or composite                                 | `application/json`         |
| `format: binary` or `format: base64`                | `application/octet-stream` |

Use the `encoding` object to override the default content type for specific properties.

### `allOf` with non-structured sub-schemas

A common real-world pattern wraps a `$ref` in a single-element `allOf` for documentation or tooling reasons:

```yaml
components:
  schemas:
    NonEmptyString:
      type: string
      minLength: 1
    ProductName:
      allOf:
        - $ref: '#/components/schemas/NonEmptyString'
```

Contracteer handles single-element `allOf` with any sub-schema type -- string, integer, object, or composite.

**Multi-element `allOf`** requires all sub-schemas to be structured types (object, `allOf`, `anyOf`, or `oneOf`):

```yaml
# Supported: multi-element allOf with objects
allOf:
  - $ref: '#/components/schemas/Pet'
  - type: object
    properties:
      huntingSkill:
        type: string

# Not supported: multi-element allOf with primitives
allOf:
  - type: string
    minLength: 5
  - type: string
    maxLength: 10
```

The OpenAPI Specification allows `allOf` sub-schemas of any type, but Contracteer generates random values for each sub-schema and merges the results.
For objects, this works -- different sub-schemas contribute different properties that combine cleanly.
For primitives, constraints overlap on the same value.
A `minLength: 5` from one sub-schema and a `maxLength: 10` from another would require computing the constraint intersection, which Contracteer does not do.

If your OpenAPI document uses multi-element `allOf` with primitives, combine the constraints into a single schema:

```yaml
# Equivalent single schema
type: string
minLength: 5
maxLength: 10
```

### Multiple composition keywords on the same schema

JSON Schema allows `allOf`, `anyOf`, and `oneOf` to appear on the same schema, with each keyword applying independently:

```yaml
# Valid per the OpenAPI Specification, but rejected by Contracteer
ProductOrService:
  allOf:
    - $ref: '#/components/schemas/Purchasable'
  oneOf:
    - $ref: '#/components/schemas/Product'
    - $ref: '#/components/schemas/Service'
```

Contracteer rejects schemas that combine multiple composition keywords.
Each composition keyword produces a different validation strategy (all must match, exactly one must match, at least one must match).
Combining them would require a compound validation that Contracteer does not implement.

This pattern is extremely rare in real-world OpenAPI documents and is almost always a mistake.
If your OpenAPI document combines composition keywords, restructure it to use a single keyword:

```yaml
# Use allOf to combine the base schema with a oneOf
ProductOrService:
  allOf:
    - $ref: '#/components/schemas/Purchasable'
    - oneOf:
        - $ref: '#/components/schemas/Product'
        - $ref: '#/components/schemas/Service'
```

### Composed schemas with incompatible branches

A composition (`allOf`, `oneOf`, `anyOf`) whose branches are all objects or all arrays is accepted wherever a bare object or array schema is accepted -- including request and response bodies in `application/x-www-form-urlencoded` and `multipart/*`, and parameter styles `deepObject`, `simple`, `form`, `label`, `matrix`, `spaceDelimited`, and `pipeDelimited`.

Two cases are rejected at load time.

#### Cross-kind property collisions on type-blind wire formats

Two branches of a `oneOf` or `anyOf` may declare the same property with different kinds:

```yaml
oneOf:
  - type: object
    properties:
      value:
        type: string
  - type: object
    properties:
      value:
        type: object
        properties:
          x: { type: integer }
```

On JSON, the wire bytes disambiguate -- a string is `"foo"`, an object is `{...}`.
On `application/x-www-form-urlencoded`, `multipart/*`, `deepObject`, and the flat parameter styles, the wire format carries no information about whether `value=foo` should be decoded as a string or as a serialized object.
Contracteer rejects this combination at load time:

```
composed schemas define incompatible kinds: 'primitive' vs 'object'
```

The OpenAPI Specification permits the composition.
Contracteer rejects it for type-blind wire formats because silently selecting one branch would break the trust model of contract testing: a green run must mean the whole OpenAPI document was exercised, not one arbitrary interpretation of it.

The same composition is accepted in `application/json` request and response bodies, where the wire format disambiguates.

#### Multipart part default content type for mixed-shape branches

The OpenAPI Specification defaults a multipart part's content type based on its schema -- `text/plain` for primitives, `application/json` for objects and arrays, `application/octet-stream` for binary.
When a part's schema is a composition whose branches have incompatible encoding shapes (e.g., a primitive branch and an object branch), no single default applies.

Contracteer rejects the OpenAPI document at load time.
The OpenAPI document's author must declare the part's content type explicitly via the `encoding` object:

```yaml
requestBody:
  content:
    multipart/form-data:
      schema:
        type: object
        properties:
          payload:
            oneOf:
              - type: string
              - type: object
                properties:
                  count: { type: integer }
      encoding:
        payload:
          contentType: application/json
```

The OpenAPI Specification does not define what to default to in this case.
Contracteer chooses explicit declaration over a silent guess.

---

## Not Applicable to Contract Testing

These features are defined in the OpenAPI Specification but do not affect the structural contract between client and server.
Contracteer does not process them.

| Feature                          | Reason                                                                                                                                 |
|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `servers` / server variables     | Connection configuration. The verifier accepts the server URL as external configuration.                                               |
| `security` / `securitySchemes`   | Authentication is runtime configuration, not contract structure.                                                                       |
| `tags`, `summary`, `description` | Documentation metadata.                                                                                                                |
| `operationId`                    | Client code generation hint.                                                                                                           |
| `externalDocs`                   | Documentation link.                                                                                                                    |
| `deprecated`                     | Informational flag. A deprecated operation has the same structural contract.                                                           |
| `callbacks`                      | Webhook definitions -- a separate concern from request/response contract testing.                                                      |
| `links`                          | Hypermedia navigation hints between operations.                                                                                        |
| `default` (property values)      | The verifier always sends values. The mock server validates against the schema, not against assumed defaults.                          |
| `example` on Schema Object       | Contracteer uses the `examples` keyword on Parameter and Media Type Objects for scenario creation. Schema-level `example` is not read. |
| `xml` (Schema Object)            | XML serialization hints. Only relevant if XML content type were supported.                                                             |

---

## Detailed Coverage

### Data types

| Type      | Notes                                                                                                                                           |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `string`  | With `minLength`, `maxLength`, `pattern`, `enum`, `nullable`                                                                                    |
| `integer` | With `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `enum`, `nullable`                                             |
| `number`  | With `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `enum`, `nullable`                                             |
| `boolean` | With `enum`, `nullable`                                                                                                                         |
| `array`   | With `minItems`, `maxItems`, `uniqueItems`. Including array parameters with style/explode encoding                                              |
| `object`  | With `minProperties`, `maxProperties`, `additionalProperties`, `readOnly`, `writeOnly`. Including object parameters with style/explode encoding |

### String formats

| Format          | Status                              |
|-----------------|-------------------------------------|
| `date`          | Supported (ISO 8601 `YYYY-MM-DD`)   |
| `date-time`     | Supported (ISO 8601 with offset)    |
| `email`         | Supported                           |
| `uuid`          | Supported                           |
| `byte` (base64) | Supported                           |
| `binary`        | Supported                           |
| `password`      | Supported (treated as plain string) |
| `uri`           | Supported (RFC 3986)                |
| `uri-reference` | Supported (RFC 3986)                |
| `hostname`      | Supported (RFC 1034)                |
| Custom formats  | Ignored                             |

### Integer and number formats

| Format   | Status                                                                         |
|----------|--------------------------------------------------------------------------------|
| `int32`  | Supported. Applies 32-bit signed range; rejects explicit min/max outside range |
| `int64`  | Supported. Applies 64-bit signed range; rejects explicit min/max outside range |
| `float`  | Supported. Applies 32-bit float range; rejects explicit min/max outside range  |
| `double` | Supported. Applies 64-bit double range; rejects explicit min/max outside range |
| Other    | Ignored with a warning. No range constraint applied                            |

### Schema keywords

| Keyword                                 | Notes                                                                                                       |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `nullable`                              | All data types. Both `nullable: true` (OpenAPI 3.0) and `type: [..., "null"]` (OpenAPI 3.1) are recognized. |
| `enum`                                  | All data types                                                                                              |
| `minimum` / `maximum`                   | Integer and number types                                                                                    |
| `exclusiveMinimum` / `exclusiveMaximum` | Integer and number types. Accepts the boolean form (OpenAPI 3.0) and the numeric form (OpenAPI 3.1).        |
| `minLength` / `maxLength`               | String, email, base64, binary types                                                                         |
| `pattern`                               | Plain strings only. See [string constraint precedence](#string-constraint-precedence)                       |
| `multipleOf`                            | Integer and number types                                                                                    |
| `minItems` / `maxItems`                 | Array types                                                                                                 |
| `uniqueItems`                           | Array types                                                                                                 |
| `minProperties` / `maxProperties`       | Object types                                                                                                |
| `required` (properties)                 | Object types                                                                                                |
| `additionalProperties`                  | Both boolean and typed schema forms                                                                         |
| `readOnly` / `writeOnly`                | readOnly properties excluded from request schemas, writeOnly from response schemas                          |
| `discriminator`                         | `propertyName` and `mapping`. See [Discriminator validation](#discriminator-validation)                     |

**Type inference.**
When a schema declares no `type` but uses `properties`, `additionalProperties`, or `items`, Contracteer treats it as an object or array schema accordingly.
This is consistent with the OpenAPI Specification, which permits typeless schemas while defining these keywords only for objects or arrays.

### Schema composition

| Feature         | Notes                                                                                                                                                                                     |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `allOf`         | Single-element accepts any sub-schema type; multi-element requires structured types. Sibling `properties`, `required`, and `additionalProperties` are folded in as an implicit sub-schema |
| `oneOf`         | Validates that exactly one sub-schema matches. Sibling `properties`, `required`, and `additionalProperties` are supported                                                                 |
| `anyOf`         | Validates that at least one sub-schema matches. Sibling `properties`, `required`, and `additionalProperties` are supported                                                                |
| `discriminator` | `propertyName` and `mapping` on `oneOf`/`anyOf`/`allOf`. See [Discriminator validation](#discriminator-validation)                                                                        |

A composition whose branches are all objects is accepted wherever a bare object schema is accepted: request and response bodies, parameter styles, content types.
The same holds for compositions whose branches are all arrays.
See [Composed schemas with incompatible branches](#composed-schemas-with-incompatible-branches) for the two cases Contracteer rejects at load time.

### Discriminator validation

Contracteer uses the discriminator as a hint to select which sub-schema in a `oneOf`, `anyOf`, or `allOf` composition validates the body.
When the discriminator cannot identify a single sub-schema, Contracteer follows the OpenAPI rule that "discriminator MUST NOT change the validation outcome of the schema".

Three runtime cases:

**Property is absent.**
When the discriminator property is missing from the body, Contracteer validates the body against every sub-schema as if no discriminator were declared.
The OpenAPI Specification states: "This property SHOULD be required in the payload schema, as the behavior when the property is absent is undefined."
The body is accepted if exactly one sub-schema matches (`oneOf`) or at least one sub-schema matches (`anyOf`).

**Property is not a string.**
When the discriminator property is present but not a string value, Contracteer falls back to the same plain composite matching.
The OpenAPI Specification states: "Mapping keys MUST be string values, but tooling MAY convert response values to strings for comparison."
Contracteer does not coerce non-string values.
Sub-schemas usually declare the discriminator property as `type: string`, so a non-string value fails the string type check within each branch.

**Value does not match any mapping or schema name.**
When the discriminator property is a string that matches no entry in `mapping` and no schema name under `components/schemas`, Contracteer rejects the body with `No schema found for discriminator property '<name>' with value: <value>`.
The OpenAPI Specification states: "If the discriminating value does not match an implicit or explicit mapping, no schema can be determined and validation SHOULD fail".

### Discriminator on a parent schema used via `$ref`

A common polymorphic pattern uses a parent schema that declares the discriminator and child schemas that extend the parent with `allOf`:

```yaml
components:
  schemas:
    Pet:
      type: object
      required: [petType]
      properties:
        petType: { type: string }
      discriminator:
        propertyName: petType
    Cat:
      allOf:
        - $ref: '#/components/schemas/Pet'
        - type: object
          properties:
            huntingSkill: { type: string }
    Dog:
      allOf:
        - $ref: '#/components/schemas/Pet'
        - type: object
          properties:
            packSize: { type: integer }

paths:
  /pets:
    post:
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
```

When `Pet` is referenced directly via `$ref` (without wrapping it in `oneOf` or `anyOf` at the usage site), Contracteer validates the body against `Pet` only.
Child-specific properties (`huntingSkill`, `packSize`) are not checked.
This matches the OpenAPI rule: "The `allOf` form of `discriminator` is _only_ useful for non-validation use cases; validation with the parent schema with this form of `discriminator` _does not_ perform a search for child schemas or use them in validation in any way".

To get child-specific validation, list the children at the usage site:

```yaml
requestBody:
  content:
    application/json:
      schema:
        oneOf:
          - $ref: '#/components/schemas/Cat'
          - $ref: '#/components/schemas/Dog'
        discriminator:
          propertyName: petType
```

With this form, Contracteer uses the discriminator to select the matching child schema and validates the body against both the parent and the child.

### Parameters

| Feature                         | Notes                                                                                                                                                                                                                                                                                                               |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `in: path`                      | Primitive, array, and object types. Styles: `simple`, `label`, `matrix`                                                                                                                                                                                                                                             |
| `in: query`                     | Primitive, array, and object types. Styles: `form`, `spaceDelimited`, `pipeDelimited`, `deepObject`. `deepObject` only supports flat objects with primitive properties.                                                                                                                                             |
| `in: header`                    | Primitive, array, and object types. Style: `simple`                                                                                                                                                                                                                                                                 |
| `in: cookie`                    | Primitive, array, and object types. Style: `form`                                                                                                                                                                                                                                                                   |
| `style` / `explode`             | All style/explode combinations with correct defaults per location. Flat styles (`simple`, `form`, `label`, `matrix`) reject nested non-primitive values -- arrays of objects, arrays of arrays, and objects with object/array properties -- because the OpenAPI Specification leaves their serialization undefined. |
| Composed schemas                | `allOf`/`oneOf`/`anyOf` whose branches are all objects (or all arrays) are accepted wherever a bare object schema (or array schema) is accepted. The merged shape must still satisfy the per-style restrictions above.                                                                                              |
| `content` (instead of `schema`) | Parameter value serialized via content type (e.g., JSON-encoded query parameter)                                                                                                                                                                                                                                    |
| `allowReserved`                 | Query parameters and `application/x-www-form-urlencoded` encoding properties                                                                                                                                                                                                                                        |

### Request and response bodies

| Feature                                      | Notes                                                                                                                                                             |
|----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `application/json`                           | Primitive, object, array, and composition schemas.                                                                                                                |
| `text/plain`, `text/html`, `application/jwt` | Primitive schemas only. Object, array, and composition schemas are rejected at load time because no standard defines how to serialize them for these media types. |
| `multipart/*` (form-data, mixed, etc.)       | Object schemas and `allOf`/`oneOf`/`anyOf` of objects. Per-part content type via the `encoding` object.                                                                                                                                                  |
| `application/x-www-form-urlencoded`          | Object schemas and `allOf`/`oneOf`/`anyOf` of objects. Per-property encoding via the `encoding` object. Only primitive and array-of-primitive properties; nested objects and arrays of objects are rejected.             |
| Multiple content types                       | Produces one verification per content type combination                                                                                                            |
| `required` (request body)                    | Supported                                                                                                                                                         |
| Content negotiation (Accept header)          | RFC 7231 support with quality factors and wildcard subtypes                                                                                                       |

### Examples

| Feature                                         | Notes     |
|-------------------------------------------------|-----------|
| `examples` map (parameter / media type level)   | Supported |
| Single `example` (parameter / media type level) | Supported |

See [Creating Scenarios](scenarios.md) for how examples drive scenario creation.

### References ($ref)

| Feature                                            | Notes                                                                                                                                                                                                                                            |
|----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `$ref` to `#/components/schemas`                   | Supported                                                                                                                                                                                                                                        |
| `$ref` to `#/components/parameters`                | Supported                                                                                                                                                                                                                                        |
| `$ref` to `#/components/requestBodies`             | Supported                                                                                                                                                                                                                                        |
| `$ref` to `#/components/responses`                 | Supported                                                                                                                                                                                                                                        |
| `$ref` to `#/components/headers`                   | Supported                                                                                                                                                                                                                                        |
| `$ref` to `#/components/examples`                  | Supported                                                                                                                                                                                                                                        |
| `$ref` via JSON Pointer to nested Schema locations | Resolves pointers into `properties`, `items`, `additionalProperties`, `allOf`/`oneOf`/`anyOf`, and `not`. Example: `#/components/schemas/User/properties/address`. Pointers into non-Schema sections (`examples`, `requestBodies`) are rejected. |
| Recursive / chained `$ref`                         | Supported, including circular references. Infinite cycles (all properties in the cycle are required and non-nullable) are rejected at load time                                                                                                  |
| External `$ref` (other files)                      | Resolved by the OpenAPI parser before Contracteer processes the model                                                                                                                                                                            |

### Responses

| Feature                                  | Notes                                                                                                                                                                                                           |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Exact status codes (`200`, `404`, etc.)  | Supported                                                                                                                                                                                                       |
| Status code ranges (`2XX`, `4XX`, `5XX`) | Used as fallback when the exact status code is not defined. Never resolves an operation's [primary response](how-contracteer-works.md#the-primary-response); a `4XX` still drives the type-mismatch expectation |
| `default` response                       | Used as fallback when neither an exact status code nor a status code range is defined. Never resolves an operation's primary response                                                                           |
| `100`-`199` and `304` responses          | Loaded, but never the primary response -- no ordinary request produces them. A scenario can still target `304`                                                                                                  |
| Body on a bodyless status code           | Rejected at load. HTTP forbids a message body for `100`-`199`, `204`, `205` and `304`                                                                                                                           |
| Response headers                         | Supported                                                                                                                                                                                                       |
| Response body                            | Supported                                                                                                                                                                                                       |

---

## OpenAPI 3.0-specific behavior

These behaviors apply only to OpenAPI 3.0 documents.

### `nullable` on composition schemas

The OpenAPI Specification states that `nullable` only takes effect when `type` is explicitly defined on the same schema.
Composition schemas (`oneOf`, `anyOf`, `allOf`) typically do not declare `type`, which means `nullable: true` should technically have no effect:

```yaml
MySchema:
  nullable: true
  oneOf:
    - $ref: '#/components/schemas/Cat'
    - $ref: '#/components/schemas/Dog'
```

Contracteer honors `nullable: true` on composition schemas regardless of whether `type` is present.
This matches the behavior of most OpenAPI tooling and what users expect.
The `nullable` rule is widely considered a specification design flaw, which OpenAPI 3.1 resolved by replacing `nullable` with `type` arrays (e.g., `type: ["object", "null"]`).

### `allowEmptyValue`

The `allowEmptyValue` parameter flag indicates that a query parameter may be present with an empty value.
Contracteer ignores the flag.
The keyword was deprecated in OpenAPI 3.0 and dropped from OpenAPI 3.1.

---

## OpenAPI 3.1-specific behavior

These behaviors apply only to OpenAPI 3.1 documents.

### Type arrays

OpenAPI 3.1 allows `type` to be an array of type strings.
Contracteer supports two forms:

- **Single-type array** (e.g., `type: ["string"]`) -- equivalent to `type: string`.
- **Nullable form** (one type plus `"null"`, e.g., `type: ["string", "null"]`) -- equivalent to a nullable string.

Multi-type arrays with multiple non-null types (e.g., `type: ["string", "integer"]`) are rejected at load time.
See [Unsupported OpenAPI 3.1 keywords](#unsupported-openapi-31-keywords).

To express a union of types, use `anyOf`:

```yaml
# Not supported -- multi-type with non-null types
ProductId:
  type: ["string", "integer"]

# Supported equivalent
ProductId:
  anyOf:
    - type: string
    - type: integer
```

### `type: null`

OpenAPI 3.1 represents "null is a valid value" with the JSON Schema 2020-12 form `type: "null"`.
A `{type: "null"}` schema accepts only `null` and rejects every other value with `Type mismatch, expected type 'null'`.

Contracteer supports `{type: "null"}` wherever it describes a coherent contract:

- **Property, array-item, and `additionalProperties` schemas.**
  Use it wherever `null` is the only valid value in that position.
- **A branch inside `anyOf` or `oneOf`.**
  A `{type: "null"}` branch makes the composition nullable -- the way to make a `$ref` or composed schema nullable.
  For a single named type, `type: [Type, "null"]` is the more concise equivalent (see [Type arrays](#type-arrays)).

Composite nullability rules:

- `anyOf` accepts null if the outer schema is nullable or any branch admits null.
- `oneOf` accepts null if the outer is nullable or exactly one branch admits null.
  Two or more null-admitting branches make the composite non-nullable -- the OpenAPI document is ambiguous, so Contracteer treats it as non-null.
- `allOf` accepts null if the outer is nullable or every branch admits null.

The three positions below are rejected at load time because the construct has no coherent meaning there, not because the feature is missing:

- **Standalone body or parameter schema.**
  A `{type: "null"}` body or parameter conveys no meaningful constraint and is almost always an author error.
  Use `anyOf: [Type, {type: "null"}]` for a nullable value or HTTP status `204` for an empty response.
  See [`type: null` is rejected as a body or parameter schema](../guides/troubleshooting.md#type-null-is-rejected-as-a-body-or-parameter-schema).
- **`anyOf` or `oneOf` whose outer `type` array excludes `"null"` but a branch declares `type: "null"`.**
  The outer-type constraint and the null branch are contradictory.
  See [Outer `type` excludes null but a composition branch declares it](../guides/troubleshooting.md#outer-type-excludes-null-but-a-composition-branch-declares-it).
- **`allOf` with any `type: "null"` branch.**
  Unsatisfiable: a value cannot match both `{type: "null"}` and any non-null branch.
  See [`allOf` includes a `type: null` branch](../guides/troubleshooting.md#allof-includes-a-type-null-branch).

```yaml
components:
  schemas:
    # Nullable property: anyOf with the actual type and a null branch
    User:
      type: object
      properties:
        name:
          type: string
        nickname:
          anyOf:
            - type: string
            - type: "null"
    # Null-only property: accepts only null
    Marker:
      type: object
      properties:
        tombstone:
          type: "null"
```

### `const`

OpenAPI 3.1 introduces `const` for schemas with a single allowed value.
Contracteer supports `const` on `string`, `integer`, `number`, and `boolean` schemas.
The schema's `type` must be declared explicitly -- Contracteer does not infer the type from the `const` value.

```yaml
components:
  schemas:
    OrderStatus:
      type: string
      const: ACTIVE
```

A schema with `const: null` is not representable.

### `propertyNames`

OpenAPI 3.1 introduces `propertyNames` to constrain the names of properties in an object schema.
`propertyNames` takes a schema that must resolve to a string type.

Contracteer applies the constraint at three points:

- **Validation.** Every property name in the body -- both declared properties and additional properties -- must satisfy the constraint.
- **Generation.** When generating values for `additionalProperties`, Contracteer draws candidate names that satisfy the constraint.
- **Load time.** Contracteer rejects a schema whose declared property names violate the constraint.

```yaml
components:
  schemas:
    Metadata:
      type: object
      propertyNames:
        type: string
        pattern: "^[a-z][a-z0-9_]*$"
      additionalProperties:
        type: string
```

### `contentEncoding` and `contentMediaType` for binary bodies

Contracteer recognizes two OpenAPI 3.1 keywords for declaring binary content in string schemas:

- `contentEncoding: base64` -- the body is base64-encoded binary data (replacing OpenAPI 3.0's `format: byte`).
- `contentMediaType: <media type>` -- the body is raw binary content of the named media type (replacing OpenAPI 3.0's `format: binary`).

```yaml
# Base64-encoded binary content
components:
  schemas:
    Avatar:
      type: string
      contentEncoding: base64
```

```yaml
# Raw binary content
components:
  schemas:
    UploadedImage:
      type: string
      contentMediaType: image/png
```

Other `contentEncoding` values (`base16`, `base32`, `quoted-printable`, etc.) and structured-text `contentMediaType` values (`application/json`, `application/xml`, etc.) are rejected at load time.
See [Unsupported OpenAPI 3.1 keywords](#unsupported-openapi-31-keywords).

For request and response bodies declared with a binary content type (`application/octet-stream`, `image/*`, etc.) but no schema or an empty schema, Contracteer infers binary content automatically.
This applies to OpenAPI 3.0 documents as well.

### `$ref` with sibling keywords

OpenAPI 3.1 follows JSON Schema 2020-12, which allows other keywords alongside `$ref` in a Schema Object.
The resolved target and the siblings must both be satisfied -- this is implicit `allOf` semantics.

Contracteer supports this by resolving the `$ref` one hop, merging the sibling keywords onto the resolved target per the rules below, and converting the merged schema once.
Chains of `$ref` with siblings recurse naturally and intermediate siblings are preserved.

The following sibling patterns are supported:

```yaml
# Sibling type redeclaration -- must match the target's type
config:
  $ref: '#/components/schemas/Settings'
  type: object

# Narrowing a primitive target
name:
  $ref: '#/components/schemas/Name'
  maxLength: 80
  minLength: 1

# Restricting an enum -- sibling values must be a subset of the target's enum
status:
  $ref: '#/components/schemas/Status'
  enum: [active, pending]

# Extending an object target with additional required fields
contact:
  $ref: '#/components/schemas/Contact'
  required: [email]

# Extending an object target with additional properties (no overlap with target)
shipment:
  $ref: '#/components/schemas/Order'
  properties:
    tracking_id:
      type: string
```

Per-keyword merge rules:

| Sibling keyword                                                                                                  | Merge rule                                                                                  |
|------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `type` / `types`                                                                                                 | Must equal the target's single type. Mismatch is rejected at load time.                     |
| `minLength` / `maxLength` / `minimum` / `maximum` / `exclusiveMinimum` / `exclusiveMaximum` / `multipleOf`        | Take the tighter bound. `multipleOf` is rejected if both are set and differ.                |
| `minProperties` / `maxProperties` / `minItems` / `maxItems`                                                      | Take the tighter bound.                                                                     |
| `uniqueItems`                                                                                                    | OR (`true` wins, because uniqueness narrows the contract).                                  |
| `pattern`                                                                                                        | Sibling fills if the target has none. Both defined is rejected at load time.                |
| `enum`                                                                                                           | Sibling values must be a subset of the target's `enum`. The merged result uses the sibling. |
| `const`                                                                                                          | Sibling value must equal the target's `const` (if any) or be in the target's `enum`.        |
| `required`                                                                                                       | Union with the target's `required`.                                                         |
| `properties`                                                                                                     | Merged map. Overlapping property names are rejected at load time.                           |
| `items` / `additionalProperties`                                                                                 | Sibling fills if the target has none. Both defined is rejected at load time.                |
| Any other keyword (`not`, `allOf`/`anyOf`/`oneOf` as siblings, `if`/`then`/`else`, JSON Schema applicators, etc.) | Rejected at load time with a message naming the unsupported keyword.                        |

Reference Object siblings (under `parameters`, `requestBodies`, `responses`, `headers`, `examples`) are separate from Schema Object siblings.
The OpenAPI 3.1 Specification states that only `summary` and `description` are allowed on a Reference Object; any other keyword is ignored.
Contracteer follows that rule.

If two schemas reference each other via `$ref` with siblings (for example `A → B → A`), the merge fails at load time with a message naming the cycle path.
Regular `$ref` chains without siblings still permit circular references with the usual rules (see [Circular schema reference rejected](../guides/troubleshooting.md#circular-schema-reference-rejected)).

---

## Unsupported OpenAPI 3.1 keywords

Contracteer does not support the following JSON Schema 2020-12 keywords.
Most are rejected at load time with an explanatory message.
A few do not produce an error -- they are simply not honored, and references that rely on them will not resolve.

| Keyword                                      | Behavior                            | Reason / alternative                                                                                    |
|----------------------------------------------|-------------------------------------|---------------------------------------------------------------------------------------------------------|
| `prefixItems`                                | Rejected at load time               | Tuple validation requires per-position type dispatch. Use `items` if all positions share a type.        |
| `contains` / `minContains` / `maxContains`   | Rejected at load time               | Element-existence semantics not implemented.                                                            |
| `patternProperties`                          | Rejected at load time               | Regex-keyed property validation not implemented.                                                        |
| `dependentRequired`                          | Rejected at load time               | Conditional-required behavior not implemented.                                                          |
| `dependentSchemas`                           | Rejected at load time               | Conditional-schema behavior not implemented.                                                            |
| `unevaluatedItems` / `unevaluatedProperties` | Rejected at load time               | Adjacent-keyword evaluation not implemented.                                                            |
| `if` / `then` / `else`                       | Rejected at load time               | Conditional validation not implemented.                                                                 |
| `not`                                        | Rejected at load time               | Subschema negation not implemented. Also rejected in OAS 3.0.                                           |
| `contentSchema`                              | Rejected at load time               | Nested-content validation not implemented.                                                              |
| Non-base64 `contentEncoding`                 | Rejected at load time               | Only `base64` is supported today.                                                                       |
| Structured-text `contentMediaType`           | Rejected at load time               | Inline content validation for `application/json`, `application/xml`, etc. is not implemented.           |
| Multi-type schemas (multiple non-null types) | Rejected at load time               | Union disambiguation requires runtime type dispatch. Use `anyOf`.                                       |
| `$defs`                                      | Rejected when referenced via `$ref` | Use `#/components/schemas` instead.                                                                     |
| `$anchor` / `$id`                            | Not honored                         | Use `$ref` to `#/components/schemas` paths instead.                                                     |
| `$dynamicRef` / `$dynamicAnchor`             | Not honored                         | Use `$ref`.                                                                                             |

---

## Specification references

Quotes throughout this page come from the OpenAPI Specification:

- [OpenAPI Specification 3.0.4](https://spec.openapis.org/oas/v3.0.4)
- [OpenAPI Specification 3.1.2](https://spec.openapis.org/oas/v3.1.2)

---

## Next Steps

- [Troubleshooting](../guides/troubleshooting.md) -- common issues caused by unsupported features and how to work around them.
- [Creating Scenarios](scenarios.md) -- how OpenAPI examples become scenarios and verification cases.
- [Testing Your Server](testing-your-server.md) -- what the verifier checks.
- [Testing Your Client](testing-your-client.md) -- how the mock server validates requests and generates responses.