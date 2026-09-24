# CI/CD Integration

Contract tests catch API drift at build time.
This guide shows how to run them in your CI/CD pipeline.

---

## JVM Projects

If you use `contracteer-verifier-junit` or `contracteer-mockserver-spring`, contract tests are regular JUnit tests.
They run as part of your normal test suite:

=== "Gradle"

    ```bash
    ./gradlew test
    ```

=== "Maven"

    ```bash
    mvn test
    ```

No extra CI configuration is needed.
Your existing test step already runs contract tests.

---

## Non-JVM Projects: Use the CLI

For projects not on the JVM, install the Contracteer CLI in your pipeline and run `contracteer verify` against your server.

### Verify a server

The following GitHub Actions workflow installs the CLI, starts the server, and runs verification:

```yaml
name: Contract Tests

on: [push, pull_request]

jobs:
  contract-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install Contracteer
        run: |
          curl -sL https://github.com/contracteer-dev/contracteer/releases/download/4.0.0/contracteer-4.0.0-linux-x86_64.zip -o contracteer.zip
          unzip contracteer.zip
          sudo mv contracteer /usr/local/bin/

      - name: Start server
        run: |
          # Start your server in the background
          ./start-server.sh &
          # Wait for it to be ready
          timeout 30 bash -c 'until curl -s http://localhost:8080/health; do sleep 1; done'

      - name: Verify contracts
        run: contracteer verify openapi.yaml -u http://localhost:8080
```

The `contracteer verify` command exits with code `0` when all cases pass and `1` when any case fails.
GitHub Actions treats a non-zero exit code as a failed step.

### Start a mock server for client tests

For client-side pipelines, start a mock server as a background service and run your client tests against it:

```yaml
name: Client Contract Tests

on: [push, pull_request]

jobs:
  contract-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install Contracteer
        run: |
          curl -sL https://github.com/contracteer-dev/contracteer/releases/download/4.0.0/contracteer-4.0.0-linux-x86_64.zip -o contracteer.zip
          unzip contracteer.zip
          sudo mv contracteer /usr/local/bin/

      - name: Start mock server
        run: |
          contracteer mock openapi.yaml -p 8080 &
          # Wait for the mock server to be ready
          timeout 10 bash -c 'until curl -s http://localhost:8080; do sleep 1; done'

      - name: Run client tests
        run: npm test
        env:
          API_BASE_URL: http://localhost:8080
```

The mock server runs in the background for the duration of the job.
Your client tests point to it via the `API_BASE_URL` environment variable (or however your client is configured).

---

## The OpenAPI Document as a Shared Artifact

The strongest CI setup treats the OpenAPI document as a **versioned, published artifact**.
Both server and client pipelines consume it.

The pattern:

1. The OpenAPI document lives in its own repository or module.
2. It is published as a versioned artifact (e.g., a Maven dependency).
3. Server and client projects declare a dependency on it.
4. A change to the OpenAPI document triggers both pipelines.

This ensures that server and client always test against the same version of the contract.
If the OpenAPI document changes and either side fails to conform, CI catches it immediately.

The [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) repository demonstrates this pattern.
The `musketeer-spec` module publishes the OpenAPI document as a dependency.
The server and client projects both consume it via `classpath:musketeer-api.yaml`.

See [The OpenAPI Document as Source of Truth](../concepts/contract-testing.md#the-openapi-document-as-source-of-truth) for the rationale behind this approach.

---

## Next Steps

- [Use the CLI](../getting-started/cli.md) -- CLI installation and commands.
- [Verify Your API with JUnit 5](../getting-started/verifier-junit.md) -- JVM verifier setup.
- [Mock an API with Spring Boot](../getting-started/mockserver-spring.md) -- JVM mock server setup.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects demonstrating the shared OpenAPI document pattern.
