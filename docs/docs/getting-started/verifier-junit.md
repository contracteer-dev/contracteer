# Verify Your API with JUnit 5

Add a single annotation to your JUnit tests to verify that your API matches your OpenAPI document.

The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository contains a complete working project.
The [musketeer-spring-boot-server](https://github.com/contracteer-dev/contracteer-examples/tree/main/musketeer-spring-boot-server) demonstrates everything covered on this page.

---

## Prerequisites

- JDK 21 or later
- Gradle or Maven
- JUnit 5 on the test classpath
- An OpenAPI 3.0 or 3.1 document (`.yaml` or `.json`)

---

## Add the Dependency

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        testImplementation("dev.contracteer:contracteer-verifier-junit:<version>")
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>dev.contracteer</groupId>
        <artifactId>contracteer-verifier-junit</artifactId>
        <version>${contracteer.version}</version>
        <scope>test</scope>
    </dependency>
    ```

---

## Write the Test

Annotate a test method with `@ContracteerTest`.
Contracteer reads the OpenAPI document, generates verification cases, and runs each as an individual JUnit test.
Operations whose primary response goes unverified appear as skipped tests.

=== "Kotlin"

    ```kotlin
    class MyApiContractTest {

        @ContracteerTest(
            openApiDoc = "src/test/resources/openapi.yaml",
            serverUrl = "http://localhost",
            serverPort = 8080
        )
        fun `verify API contracts`() {
            // Runs before each verification case.
            // Seed test data here.
        }
    }
    ```

=== "Java"

    ```java
    class MyApiContractTest {

        @ContracteerTest(
            openApiDoc = "src/test/resources/openapi.yaml",
            serverUrl = "http://localhost",
            serverPort = 8080
        )
        void verifyApiContracts() {
            // Runs before each verification case.
            // Seed test data here.
        }
    }
    ```

The method body executes before each verification case.
After it returns, Contracteer sends the request and validates the response.

### `@ContracteerTest` fields

**`openApiDoc`** *(required)* -- Path to the OpenAPI document.
Accepts a file path, an HTTP(S) URL, or a classpath resource (e.g., `classpath:openapi.yaml`).

**`serverUrl`** *(default: `http://localhost`)* -- Base URL of the server under test.

**`serverPort`** *(default: `8080`)* -- Port of the server under test.
Overridden by `@ContracteerServerPort` if the annotated field has a non-zero value.

!!! tip "Treat the OpenAPI document as a shared artifact"
    Contracteer encourages [specification-driven contract testing](../concepts/contract-testing.md#the-openapi-document-as-source-of-truth): the OpenAPI document exists independently of both server and client.
    Package it as a Maven or Gradle dependency and reference it with `classpath:openapi.yaml`.
    This ensures that the server, client, and contract tests all use the same OpenAPI document.
    The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository demonstrates this pattern with the `musketeer-spec` module.

### Dynamic server port

When your server starts on a random port, use `@ContracteerServerPort` on a field to capture the actual port.
If the field value is non-zero, it overrides `serverPort`.

=== "Kotlin"

    ```kotlin
    class MyApiContractTest {

        companion object {
            @field:ContracteerServerPort
            @JvmField
            var serverPort: Int = 0

            @JvmStatic
            @BeforeAll
            fun startServer() {
                // Start server on random port, assign to serverPort.
            }
        }

        @ContracteerTest(openApiDoc = "src/test/resources/openapi.yaml")
        fun `verify API contracts`() { }
    }
    ```

=== "Java"

    ```java
    class MyApiContractTest {

        @ContracteerServerPort
        static int serverPort = 0;

        @BeforeAll
        static void startServer() {
            // Start server on random port, assign to serverPort.
        }

        @ContracteerTest(openApiDoc = "src/test/resources/openapi.yaml")
        void verifyApiContracts() { }
    }
    ```

### Spring Boot example

With Spring Boot, `@LocalServerPort` captures the random port.
Annotate the same field with `@ContracteerServerPort` to wire it into Contracteer.

This is the pattern used in the [musketeer-spring-boot-server](https://github.com/contracteer-dev/contracteer-examples/tree/main/musketeer-spring-boot-server) example:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractTest {

    @ContracteerServerPort
    @LocalServerPort
    int port;

    @ContracteerTest(openApiDoc = "classpath:musketeer-api.yaml")
    void verifyContracts() {
        // Seed test data here.
    }
}
```

---

## Prepare Test Data

The method body runs before **each** verification case, not once for all of them.
This is where you set up the data your server needs to return the expected responses.

From the Musketeer example:

```java
@ContracteerTest(openApiDoc = "classpath:musketeer-api.yaml")
void verifyContracts() {
    musketeerRepository.clear();
    missionRepository.clear();

    musketeerRepository.save(new Musketeer(1, "Athos", MUSKETEER, "Rapier"));
    musketeerRepository.save(new Musketeer(2, "Porthos", MUSKETEER, "Musket"));
    musketeerRepository.save(new Musketeer(3, "Aramis", MUSKETEER, "Rapier"));

    missionRepository.save(new Mission(1,
        "The Diamond Studs",
        "Retrieve the Queen's diamond studs from the Duke of Buckingham",
        MissionStatus.COMPLETED,
        List.of("Athos", "Porthos", "Aramis", "d'Artagnan")));
}
```

### Clear before each case

Verification cases may modify data.
A `POST` creates a resource, a `DELETE` removes one.
If one case changes the database, the next case sees unexpected state.
Clearing and re-seeding before each case ensures every case starts from the same known state.

### Match request examples, not response values

The seeded IDs must match the OpenAPI example values.
The `ATHOS` scenario sends `GET /musketeers/1`.
If no musketeer with id `1` exists, the server returns `404` instead of the expected `200`.

The verifier checks schema conformance, not value equality.
If Athos existed with a different weapon, the test would still pass.
What matters is that the right resources exist at the right IDs so the server returns the expected status code.

### Operations that create or modify resources

For `POST`, `PUT`, or `DELETE` operations, the seeded data sets up the precondition.
A `POST /musketeers` scenario needs no existing musketeer for the resource being created.
But if the server validates references, the referenced data must exist.
A mission that references musketeers by name, for example, requires those musketeers to be seeded.

---

## What Happens

Contracteer generates one JUnit test per verification case, and one skipped test per unverified primary response:

![JUnit test results showing verification cases](../assets/images/junit-verifier-test-results.png)

The test tree shows four kinds of verification cases:

- **Named scenarios** -- from OpenAPI example keys shared between request and response (e.g., `ATHOS`, `PORTHOS`).
- **Status-code-prefixed scenarios** -- from keys like `404_UNKNOWN_MUSKETEER` that target a specific status code.
- **Automatic type-mismatch** -- Contracteer sends a wrong type (e.g., a string for an integer parameter) and expects the server to reject it with a `400`, `422`, or `404` the OpenAPI document declares.
- **Schema-only** -- when no scenario targets the operation's primary response, Contracteer generates random values and validates the response structure.

For each case, the verifier checks the status code, required headers, and response body structure.
It does not check response values.

When Contracteer cannot determine an operation's [primary response](../concepts/how-contracteer-works.md#the-primary-response), no verification case asserts it.
Contracteer adds a skipped test after that operation's cases; its name gives the reason:
`POST /orders -> primary response not verified: 200 and 201 both qualify; declare a scenario for each of them`
The test method body does not run for it.
A skipped test does not fail the build: the OpenAPI document is valid, and Contracteer reports what it could not assert.

![JUnit test results showing a skipped test for an unverified primary response](../assets/images/junit-verifier-test-results-no-primary-response-test.png)

See [Testing Your Server](../concepts/testing-your-server.md) for a detailed explanation of what the verifier checks.

---

## Debugging Failures

When a verification case fails, Contracteer logs the HTTP request and response at WARN level automatically.
No configuration is needed -- failed cases are always visible.

To see all HTTP traffic -- including successful cases -- set the `dev.contracteer.http` logger to DEBUG:

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

- [Testing Your Server](../concepts/testing-your-server.md) -- what the verifier checks in depth, including automatic 400 testing.
- [Creating Scenarios](../concepts/scenarios.md) -- how to write OpenAPI examples that produce the scenarios you want.
- [Verifier](verifier.md) -- programmatic verifier setup without JUnit.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects with server and client examples.
