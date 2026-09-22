# Mock an API Programmatically

Use the mock server library directly when you need programmatic control over the mock server.
This is the right choice for custom test harnesses, non-Spring frameworks, or standalone usage.

If you use Spring Boot, consider [Mock Server (Spring Boot)](mockserver-spring.md) for an annotation-based setup.

The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository contains complete working projects.

---

## Prerequisites

- JDK 21 or later
- Gradle or Maven
- An OpenAPI 3.0 or 3.1 document (`.yaml` or `.json`)

---

## Add the Dependency

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        testImplementation("dev.contracteer:contracteer-mockserver:<version>")
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>dev.contracteer</groupId>
        <artifactId>contracteer-mockserver</artifactId>
        <version>${contracteer.version}</version>
        <scope>test</scope>
    </dependency>
    ```

---

## Start the Mock Server

Three steps: load the OpenAPI document, create the mock server, start it.

=== "Kotlin"

    ```kotlin
    // 1. Load the OpenAPI document
    val result = OpenApiLoader.loadOperations("classpath:openapi.yaml")
    if (result.isFailure()) {
        fail("Failed to load OpenAPI document: ${result.errors()}")
    }

    // 2. Create the mock server
    val mockServer = MockServer(
        operations = result.value!!,
        port = 0 // 0 for random port, or a fixed port
    )

    // 3. Start and use
    mockServer.start()
    val baseUrl = "http://localhost:${mockServer.port()}"

    // Make HTTP requests to baseUrl...

    mockServer.stop()
    ```

=== "Java"

    ```java
    // 1. Load the OpenAPI document
    var result = OpenApiLoader.loadOperations("classpath:openapi.yaml");
    if (result.isFailure()) {
        fail("Failed to load OpenAPI document: " + result.errors());
    }

    // 2. Create the mock server
    var mockServer = new MockServer(result.getValue(), 0);

    // 3. Start and use
    mockServer.start();
    var baseUrl = "http://localhost:" + mockServer.port();

    // Make HTTP requests to baseUrl...

    mockServer.stop();
    ```

`OpenApiLoader.loadOperations()` accepts a file path, an HTTP(S) URL, or a classpath resource (e.g., `classpath:openapi.yaml`).

Set `port` to `0` for a random available port, or to a fixed value if your test setup requires it.
Call `mockServer.port()` after `start()` to get the actual port.

!!! tip "Treat the OpenAPI document as a shared artifact"
    Contracteer encourages [specification-driven contract testing](../concepts/contract-testing.md#the-openapi-document-as-source-of-truth): the OpenAPI document exists independently of both server and client.
    Package it as a Maven or Gradle dependency and reference it with `classpath:openapi.yaml`.
    This ensures that the server, client, and contract tests all use the same OpenAPI document.
    The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository demonstrates this pattern with the `musketeer-spec` module.

---

## How the Mock Server Responds

The mock server is not a hand-written stub.
It validates every incoming request against the OpenAPI schema and determines the response from the OpenAPI document.

### Request validation

The mock server validates request parameters and body against the schema.

The client sends `{name: "d'Artagnan", rank: "KNIGHT", weapon: "Rapier"}` to `POST /musketeers`.
The mock server checks the `rank` field against the schema:

```yaml
rank:
  type: string
  enum:
    - CADET
    - MUSKETEER
    - CAPTAIN
```

`KNIGHT` is not in the enum.
The operation defines a `400` response, so the mock server returns `400`.
No one wrote a mock rule for this -- the schema drives the rejection.

### Scenario matching

If the request is valid, the mock server compares it against the scenarios defined in the OpenAPI document.

The client sends `{name: "d'Artagnan", rank: "CADET", weapon: "Rapier"}` to `POST /musketeers`.
This matches the `D_ARTAGNAN_JOINS` scenario:

```yaml
post:
  requestBody:
    content:
      application/json:
        examples:
          D_ARTAGNAN_JOINS:
            value:
              name: d'Artagnan
              rank: CADET
              weapon: Rapier
  responses:
    '201':
      headers:
        Location:
          examples:
            D_ARTAGNAN_JOINS:
              value: /musketeers/4
```

The mock server returns `201` with `Location: /musketeers/4`.

### Status-code-prefixed scenarios

The client calls `GET /musketeers/999`.
The request is valid (999 is an integer), and the value matches the `404_UNKNOWN_MUSKETEER` scenario:

```yaml
examples:
  404_UNKNOWN_MUSKETEER:
    value: 999
```

The key's prefix (`404_`) targets the 404 response directly.
The mock server returns `404`.

### Schema-only response

If the request is valid but matches no scenario, the mock server generates a response from the schema.

`GET /musketeers` has no examples in the OpenAPI document.
The mock server returns `200` with an array of randomly generated `Musketeer` objects.
The values satisfy the schema but are different on each run.

### The 418 diagnostic

If the mock server cannot determine the correct response, it returns `418` with diagnostic information.
The 418 is not a status code from your API -- it is Contracteer telling you that something is ambiguous or undefined.

This happens when multiple scenarios match the same request.
It also occurs when no single response can be served -- several declared success codes, or only status code ranges (`2XX`, `4XX`) and `default` -- and no scenario disambiguates.
An invalid request with no `400`, `4XX`, or `default` response defined also triggers a 418.

The 418 body explains what went wrong.
Read it before investigating further -- it usually points directly to the cause.

---

## Assert Structure, Not Values

Contract tests verify that your client handles the documented response structure -- not that the server returns specific data.
Assert that fields are present and correctly typed.
Do not assert on example values from the OpenAPI document.

A test that asserts a response field equals a specific example value is a functional test, not a contract test.
Your client must handle any valid response, not just the example data.

See [Assert Structure, Not Values](../concepts/testing-your-client.md#assert-structure-not-values) for the full rationale.

---

## Debugging

When the mock server returns a `418` diagnostic response, Contracteer logs the request at WARN level automatically.
No configuration is needed.

To see all incoming requests and outgoing responses, set the `dev.contracteer.http` logger to DEBUG.

---

## Next Steps

- [Testing Your Client](../concepts/testing-your-client.md) -- how the mock server validates requests and generates responses in depth.
- [Creating Scenarios](../concepts/scenarios.md) -- how to write OpenAPI examples that produce the scenarios you want.
- [Mock Server (Spring Boot)](mockserver-spring.md) -- annotation-based setup for Spring Boot.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects with server and client examples.