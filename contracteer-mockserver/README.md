# contracteer-mockserver

Start a mock server from an OpenAPI document.
Use this module when you need programmatic control -- for a custom test harness or a non-Spring framework.

If you use Spring Boot, consider [contracteer-mockserver-spring](../contracteer-mockserver-spring/) for an annotation-based setup.

## Dependency

Gradle (Kotlin DSL):

```kotlin
dependencies {
    testImplementation("dev.contracteer:contracteer-mockserver:<version>")
}
```

Maven:

```xml
<dependency>
    <groupId>dev.contracteer</groupId>
    <artifactId>contracteer-mockserver</artifactId>
    <version>${contracteer.version}</version>
    <scope>test</scope>
</dependency>
```

## Usage

```kotlin
val result = OpenApiLoader.loadOperations("classpath:openapi.yaml")
check(result is Result.Success) { "Failed to load OpenAPI document: ${result.errors()}" }

val mockServer = MockServer(
    operations = result.value,
    port = 0 // 0 for random port, or a fixed port
)

mockServer.start()
val baseUrl = "http://localhost:${mockServer.port()}"

// Make HTTP requests to baseUrl...

mockServer.stop()
```

## Documentation

See [Mock an API Programmatically](https://contracteer.dev/latest/getting-started/mockserver/) for the full guide -- mock server behavior, the 418 diagnostic, and debugging.