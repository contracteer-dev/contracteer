# Verify Your API Programmatically

Use the verifier library directly when you need programmatic control over contract verification.
This is the right choice for Kotest, TestNG, a custom test harness, or any setup where `@ContracteerTest` does not fit.

If you use JUnit 5, consider [Verifier (JUnit 5)](verifier-junit.md) for a simpler annotation-based setup.

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
        testImplementation("dev.contracteer:contracteer-verifier:<version>")
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>dev.contracteer</groupId>
        <artifactId>contracteer-verifier</artifactId>
        <version>${contracteer.version}</version>
        <scope>test</scope>
    </dependency>
    ```

---

## Load, Generate, Verify

Three steps: load the OpenAPI document, plan each operation, run the verification cases against your server.

=== "Kotlin"

    ```kotlin
    // 1. Load the OpenAPI document
    val result = OpenApiLoader.loadOperations("classpath:openapi.yaml")
    if (result.isFailure()) {
        fail("Failed to load OpenAPI document: ${result.errors()}")
    }

    // 2. Plan each operation, and report primary responses no case can verify
    val plans = result.value!!.map { VerificationCaseFactory.plan(it) }
    plans.mapNotNull { it.unverifiedPrimaryResponse }.forEach { println(it.message) }
    val cases = plans.flatMap { it.cases }

    // 3. Verify each case against the server
    val verifier = OpenApiVerifier(VerifierConfiguration(
        baseUrl = "http://localhost:8080"
    ))

    val failures = cases
        .map { verifier.verify(it) }
        .filter { it.result.isFailure() }

    assertThat(failures)
        .withFailMessage {
            failures.joinToString("\n") {
                "${it.case.displayName}: ${it.result.errors()}"
            }
        }
        .isEmpty()
    ```

=== "Java"

    ```java
    // 1. Load the OpenAPI document
    var result = OpenApiLoader.loadOperations("classpath:openapi.yaml");
    if (result.isFailure()) {
        fail("Failed to load OpenAPI document: " + result.errors());
    }

    // 2. Plan each operation, and report primary responses no case can verify
    var plans = result.getValue().stream()
        .map(VerificationCaseFactory::plan)
        .toList();
    plans.stream()
        .map(VerificationPlan::getUnverifiedPrimaryResponse)
        .filter(Objects::nonNull)
        .forEach(unverified -> System.out.println(unverified.getMessage()));
    var cases = plans.stream()
        .flatMap(plan -> plan.getCases().stream())
        .toList();

    // 3. Verify each case against the server
    var verifier = new OpenApiVerifier(new VerifierConfiguration(
        "http://localhost:8080"
    ));

    var failures = cases.stream()
        .map(verifier::verify)
        .filter(outcome -> outcome.getResult().isFailure())
        .toList();

    assertThat(failures)
        .withFailMessage(() ->
            failures.stream()
                .map(f -> f.getCase().getDisplayName() + ": " + f.getResult().errors())
                .collect(Collectors.joining("\n")))
        .isEmpty();
    ```

`OpenApiLoader.loadOperations()` accepts a file path, an HTTP(S) URL, or a classpath resource (e.g., `classpath:openapi.yaml`).

`VerifierConfiguration` fields:

- **`baseUrl`** *(default: `http://localhost:8080`)* -- Absolute base URL of the server under test.
  Must include scheme (`http` or `https`), host, and port.
  May include a path prefix (e.g., `https://api.example.com/v1`); query strings and fragments are rejected.

!!! tip "Treat the OpenAPI document as a shared artifact"
    Contracteer encourages [specification-driven contract testing](../concepts/contract-testing.md#the-openapi-document-as-source-of-truth): the OpenAPI document exists independently of both server and client.
    Package it as a Maven or Gradle dependency and reference it with `classpath:openapi.yaml`.
    This ensures that the server, client, and contract tests all use the same OpenAPI document.
    The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository demonstrates this pattern with the `musketeer-spec` module.

---

## Interpret the Results

Each call to `verifier.verify()` returns a `VerificationOutcome` with two fields:

**`case`** -- The verification case that was executed.
`case.displayName` provides a human-readable description suitable for test output:
`GET /musketeers/{id} -> 200 (application/json) with scenario 'ATHOS'`

**`result`** -- The validation result.
`result.isSuccess()` returns `true` if the response matches the expected schema.
`result.errors()` returns a list of error messages when validation fails.

**Unverified primary responses** -- `plan.unverifiedPrimaryResponse` is set when Contracteer cannot determine an operation's [primary response](../concepts/how-contracteer-works.md#the-primary-response), so no verification case asserts it.
The operation may still produce cases, from its scenarios or a type mismatch.
`message` names the operation and the reason:
`Primary response of POST /orders not verified: 200 and 201 both qualify; declare a scenario for each of them`
An unverified primary response is not a failure: the OpenAPI document is valid, and the verifier reports what it could not assert.

---

## Prepare Test Data

Before each verification case, your server must have the right data to return the expected responses.

### Clear before each case

Verification cases may modify data.
A `POST` creates a resource, a `DELETE` removes one.
Clearing and re-seeding before each case ensures every case starts from the same known state.

### Match request examples, not response values

The seeded IDs must match the OpenAPI example values.
If the `ATHOS` scenario sends `GET /musketeers/1`, a musketeer with id `1` must exist.
If no musketeer with that ID exists, the server returns `404` instead of the expected `200`.

The verifier checks schema conformance, not value equality.
If Athos existed with a different weapon, the test would still pass.
What matters is that the right resources exist at the right IDs so the server returns the expected status code.

### Operations that create or modify resources

For `POST`, `PUT`, or `DELETE` operations, the seeded data sets up the precondition.
A `POST /musketeers` scenario needs no existing musketeer for the resource being created.
But if the server validates references, the referenced data must exist.
A mission that references musketeers by name, for example, requires those musketeers to be seeded.

---

## What Gets Verified

The verifier generates four kinds of verification cases from each operation:

- **Named scenarios** -- from OpenAPI example keys shared between request and response (e.g., `ATHOS`, `PORTHOS`).
- **Status-code-prefixed scenarios** -- from keys like `404_UNKNOWN_MUSKETEER` that target a specific status code.
- **Automatic type-mismatch** -- Contracteer sends a wrong type (e.g., a string for an integer parameter) and expects the server to reject it with a `400`, `422`, or `404` the OpenAPI document declares.
- **Schema-only** -- when no scenario targets the operation's primary response, Contracteer generates random values and validates the response structure.

For each case, the verifier checks the status code, required headers, and response body structure.
It does not check response values.

See [Testing Your Server](../concepts/testing-your-server.md) for a detailed explanation of what the verifier checks.

---

## Debugging Failures

When a verification case fails, Contracteer logs the HTTP request and response at WARN level automatically.
No configuration is needed -- failed cases are always visible.

To see all HTTP traffic -- including successful cases -- set the `dev.contracteer.http` logger to DEBUG.

---

## Next Steps

- [Testing Your Server](../concepts/testing-your-server.md) -- what the verifier checks in depth, including automatic 400 testing.
- [Creating Scenarios](../concepts/scenarios.md) -- how to write OpenAPI examples that produce the scenarios you want.
- [Verifier (JUnit 5)](verifier-junit.md) -- annotation-based setup for JUnit 5.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects with server and client examples.