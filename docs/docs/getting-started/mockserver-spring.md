# Mock an API with Spring Boot

Add `@ContracteerMockServer` to your Spring Boot test class to start a mock server from your OpenAPI document.
No handwritten stubs required.

The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository contains a complete working project.
The [musketeer-spring-boot-client](https://github.com/contracteer-dev/contracteer-examples/tree/main/musketeer-spring-boot-client) demonstrates everything covered on this page.

---

## Prerequisites

- JDK 21 or later
- Gradle or Maven
- Spring Boot test on the classpath
- An OpenAPI 3.0 or 3.1 document (`.yaml` or `.json`)

---

## Add the Dependency

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        testImplementation("dev.contracteer:contracteer-mockserver-spring:<version>")
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>dev.contracteer</groupId>
        <artifactId>contracteer-mockserver-spring</artifactId>
        <version>${contracteer.version}</version>
        <scope>test</scope>
    </dependency>
    ```

---

## Annotate Your Test

Annotate your test class with `@ContracteerMockServer`.
The mock server starts automatically with the Spring test context and stops when the context closes.

Use `baseUrlProperty` to inject the mock server's base URL into a Spring property.
Your client reads this property and automatically points to the mock server.

=== "Kotlin"

    ```kotlin
    @SpringBootTest
    @ContracteerMockServer(
        openApiDoc = "classpath:musketeer-api.yaml",
        baseUrlProperty = "musketeer.api.base-url"
    )
    class MusketeerApiClientTest {

        @Autowired
        private lateinit var client: MusketeerApiClient

        @Test
        fun `retrieve all musketeers`() {
            val musketeers = client.listMusketeers()
            assertThat(musketeers).isNotNull()
        }

        @Test
        fun `enlist a new musketeer`() {
            val createMusketeer = CreateMusketeer("d'Artagnan", "CADET", "Rapier")
            val musketeer = client.enlistMusketeer(createMusketeer)
            assertThat(musketeer).isNotNull()
        }

        @Test
        fun `reject enlistment when rank is invalid`() {
            val createMusketeer = CreateMusketeer("d'Artagnan", "KNIGHT", "Rapier")
            assertThatThrownBy { client.enlistMusketeer(createMusketeer) }
                .isInstanceOf(MusketeerApiException::class.java)
                .extracting("statusCode")
                .isEqualTo(400)
        }

        @Test
        fun `return empty when musketeer does not exist`() {
            val maybeMusketeer = client.getMusketeer(999)
            assertThat(maybeMusketeer).isEmpty()
        }
    }
    ```

=== "Java"

    ```java
    @SpringBootTest
    @ContracteerMockServer(
        openApiDoc = "classpath:musketeer-api.yaml",
        baseUrlProperty = "musketeer.api.base-url"
    )
    class MusketeerApiClientTest {

        @Autowired
        MusketeerApiClient client;

        @Test
        void retrieve_all_musketeers() {
            var musketeers = client.listMusketeers();
            assertThat(musketeers).isNotNull();
        }

        @Test
        void enlist_a_new_musketeer() {
            var createMusketeer = new CreateMusketeer("d'Artagnan", "CADET", "Rapier");
            var musketeer = client.enlistMusketeer(createMusketeer);
            assertThat(musketeer).isNotNull();
        }

        @Test
        void reject_enlistment_when_rank_is_invalid() {
            var createMusketeer = new CreateMusketeer("d'Artagnan", "KNIGHT", "Rapier");
            assertThatThrownBy(() -> client.enlistMusketeer(createMusketeer))
                .isInstanceOf(MusketeerApiException.class)
                .extracting("statusCode")
                .isEqualTo(400);
        }

        @Test
        void return_empty_when_musketeer_does_not_exist() {
            var maybeMusketeer = client.getMusketeer(999);
            assertThat(maybeMusketeer).isEmpty();
        }
    }
    ```

The `MusketeerApiClient` is configured with `@Value("${musketeer.api.base-url}")`.
When the test starts, `@ContracteerMockServer` injects the mock server's URL into that property.
The client connects to the mock server without any manual wiring.

!!! note "Multiple mock servers"
    The annotation is repeatable.
    If your client depends on multiple APIs, annotate the test class once per API with a distinct `portProperty` or `baseUrlProperty`:

    ```java
    @ContracteerMockServer(
        openApiDoc = "classpath:billing-api.yaml",
        baseUrlProperty = "billing.api.base-url"
    )
    @ContracteerMockServer(
        openApiDoc = "classpath:inventory-api.yaml",
        baseUrlProperty = "inventory.api.base-url"
    )
    ```

---

## `@ContracteerMockServer` fields

**`openApiDoc`** *(required)* -- Path to the OpenAPI document.
Accepts a file path, an HTTP(S) URL, or a classpath resource (e.g., `classpath:openapi.yaml`).

**`port`** *(default: `0`)* -- Port for the mock server.
`0` assigns a random available port.

**`portProperty`** *(default: `contracteer.mockserver.port`)* -- Spring property name where the actual port is injected.

**`baseUrlProperty`** *(default: `contracteer.mockserver.baseUrl`)* -- Spring property name where the base URL is injected.
Format: `http://localhost:{port}`.

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
It also occurs when no single response can be served -- several declared success codes, or only status code ranges (`2XX`, `4XX`) and `default` -- and the request matches no scenario.
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

At startup, the mock server logs at WARN every operation that does not answer a valid request with a success when the request matches no scenario: `418` when no single response can be served, or an error status when it is the only response declared.

```
WARN  POST /orders -> answers 418 to any valid request that matches no scenario: 200 and 201 both qualify
WARN  GET /health -> answers 500 to any valid request that matches no scenario: 500 is the only response declared
```

Beyond ten operations, the list ends with a count; set the `dev.contracteer.mockserver` logger to DEBUG to see all of them:

=== "Logback (logback-test.xml)"

    ```xml
    <logger name="dev.contracteer.mockserver" level="DEBUG"/>
    ```

=== "application.yaml (Spring Boot)"

    ```yaml
    logging:
      level:
        dev.contracteer.mockserver: DEBUG
    ```

To see all incoming requests and outgoing responses, set the `dev.contracteer.http` logger to DEBUG:

=== "Logback (logback-test.xml)"

    ```xml
    <logger name="dev.contracteer.http" level="DEBUG"/>
    ```

=== "application.yaml (Spring Boot)"

    ```yaml
    logging:
      level:
        dev.contracteer.http: DEBUG
    ```

---

## Next Steps

- [Testing Your Client](../concepts/testing-your-client.md) -- how the mock server validates requests and generates responses in depth.
- [Creating Scenarios](../concepts/scenarios.md) -- how to write OpenAPI examples that produce the scenarios you want.
- [Mock Server](mockserver.md) -- programmatic mock server setup without Spring Boot.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects with server and client examples.