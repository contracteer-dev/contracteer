<div align="center">
<picture>
<source media="(prefers-color-scheme: dark)" srcset=".github/images/contracteer_white.png">
<img alt="Contracteer" src=".github/images/contracteer_black.png" width="180">
</picture>
<h3>Contracteer<br><sub>The loyal guard of your API contracts.</sub></h3>
</div>

<br>

![Build Status](https://img.shields.io/github/actions/workflow/status/contracteer-dev/contracteer/tests.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/dev.contracteer/contracteer-core)
![License](https://img.shields.io/github/license/contracteer-dev/contracteer)
[![Documentation](https://img.shields.io/badge/docs-contracteer.dev-blue)](https://contracteer.dev/latest/)

Contracteer verifies that your API implementation matches your OpenAPI document and provides a mock server that behaves exactly as your document defines.
Your OpenAPI document is the single source of truth -- Contracteer turns it into executable tests and a faithful mock.

## Why Contracteer?

OpenAPI documents drift from their implementations.
A field gets renamed, a status code changes, a required parameter becomes optional -- and nothing catches it until a consumer breaks in production.

Contract tests catch this drift early.
They run in the build, with the speed of unit tests, and verify that the boundary between services works as documented.

Contracteer is **specification-driven**.
Unlike consumer-driven tools where consumers define their expectations, Contracteer takes the OpenAPI document you already have and tests conformance to it.
If your OpenAPI document includes named examples, Contracteer uses them as scenarios for targeted, deterministic testing.
If not, it generates values from the schema.

## Quick Start

### Verify Your API (JUnit 5)

Add the dependency:

Gradle (Kotlin DSL):

```kotlin
dependencies {
    testImplementation("dev.contracteer:contracteer-verifier-junit:<version>")
}
```

Maven:

```xml
<dependency>
    <groupId>dev.contracteer</groupId>
    <artifactId>contracteer-verifier-junit</artifactId>
    <version>${contracteer.version}</version>
    <scope>test</scope>
</dependency>
```

Write the test:

```java
class ContractTest {

    @ContracteerTest(
        openApiDoc = "src/test/resources/openapi.yaml",
        serverPort = 8080
    )
    void verifyContracts() {
        // Runs before each verification case.
        // Seed test data here.
    }
}
```

Contracteer reads the OpenAPI document, generates one JUnit test per verification case, and validates that your server responds as documented.
Works with any server -- Spring Boot, Quarkus, Micronaut, or plain Java.

### Mock an API (Spring Boot)

Add the dependency:

Gradle (Kotlin DSL):

```kotlin
dependencies {
    testImplementation("dev.contracteer:contracteer-mockserver-spring:<version>")
}
```

Maven:

```xml
<dependency>
    <groupId>dev.contracteer</groupId>
    <artifactId>contracteer-mockserver-spring</artifactId>
    <version>${contracteer.version}</version>
    <scope>test</scope>
</dependency>
```

Annotate your test:

```java
@SpringBootTest
@ContracteerMockServer(
    openApiDoc = "src/test/resources/openapi.yaml",
    baseUrlProperty = "api.base-url"
)
class MyClientTest {

    @Autowired
    MyApiClient client;

    @Test
    void callsTheApi() {
        var result = client.listProducts();
        assertThat(result).isNotNull();
    }
}
```

The mock server starts automatically, validates every request against the OpenAPI schema, and returns spec-compliant responses.

### CLI

Install:

```bash
brew install contracteer-dev/contracteer/contracteer
```

Verify a running server:

```bash
contracteer verify openapi.yaml
```

Start a mock server:

```bash
contracteer mock openapi.yaml
```

Check an OpenAPI document without a server:

```bash
contracteer lint openapi.yaml
```

The CLI works with any language or stack -- no JVM required.

### Other integrations

Contracteer also provides framework-agnostic libraries for programmatic use:

- [`contracteer-verifier`](contracteer-verifier/) -- verify a server from any JVM test framework.
- [`contracteer-mockserver`](contracteer-mockserver/) -- start a mock server from any JVM project.

All modules:

| Module | Description |
|--------|-------------|
| [`contracteer-core`](contracteer-core/) | Domain model and OpenAPI extraction engine |
| [`contracteer-verifier`](contracteer-verifier/) | Programmatic verifier |
| [`contracteer-verifier-junit`](contracteer-verifier-junit/) | JUnit 5 integration |
| [`contracteer-mockserver`](contracteer-mockserver/) | Programmatic mock server |
| [`contracteer-mockserver-spring`](contracteer-mockserver-spring/) | Spring Boot integration |
| [`contracteer-cli`](contracteer-cli/) | CLI (native binary) |

See the [documentation site](https://contracteer.dev/latest/) for setup guides.

## Requirements

- Java 21 or higher (for JVM modules)
- Spring Boot 3.x (for the Spring Boot module only)

## Documentation

- [Documentation site](https://contracteer.dev/latest/) -- concepts, getting started guides, and troubleshooting.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects demonstrating the specification-as-artifact pattern.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get involved.

## License

Contracteer is licensed under the [Apache License 2.0](LICENSE).
