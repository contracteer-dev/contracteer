# contracteer-verifier

Verify that a running server implements its OpenAPI document.
Use this module when you need programmatic control -- for Kotest, TestNG, or a custom test harness.

If you use JUnit 5, consider [contracteer-verifier-junit](../contracteer-verifier-junit/) for a simpler annotation-based setup.

## Dependency

Gradle (Kotlin DSL):

```kotlin
dependencies {
    testImplementation("dev.contracteer:contracteer-verifier:<version>")
}
```

Maven:

```xml
<dependency>
    <groupId>dev.contracteer</groupId>
    <artifactId>contracteer-verifier</artifactId>
    <version>${contracteer.version}</version>
    <scope>test</scope>
</dependency>
```

## Usage

```kotlin
val result = OpenApiLoader.loadOperations("classpath:openapi.yaml")
check(result is Result.Success) { "Failed to load OpenAPI document: ${result.errors()}" }

val plans = result.value.map { VerificationCaseFactory.plan(it) }
plans.mapNotNull { it.unverifiedPrimaryResponse }.forEach { println(it.message) }
val cases = plans.flatMap { it.cases }

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

To verify every operation in one call, load the document with `OpenApiLoader.load()` and pass the result to `verifier.verify(loaded)`.
It returns a report of every case, which `toJson()` renders as JSON.

## Documentation

See [Verify Your API Programmatically](https://contracteer.dev/latest/getting-started/verifier/) for the full guide -- result interpretation, test data preparation, and debugging.