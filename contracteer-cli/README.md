# contracteer-cli

Run the Contracteer verifier or mock server from the command line.
Works with any language or stack -- no JVM required.

## Installation

```bash
brew install contracteer-dev/contracteer/contracteer
```

Or download the archive for your platform from the [latest release](https://github.com/contracteer-dev/contracteer/releases/latest).

## Usage

Verify a running server:

```bash
contracteer verify openapi.yaml
```

Start a mock server:

```bash
contracteer mock openapi.yaml
```

The OpenAPI document can be a local file path or an HTTP(S) URL.

## Documentation

See [Use the CLI](https://contracteer.dev/latest/getting-started/cli/) for the full guide -- all options, example output, mock server behavior, and a try-it-now example.

## Native image

The CLI ships as a GraalVM native image. swagger-parser resolves OpenAPI 3.1 documents through Jackson
reflection, so `src/main/resources/META-INF/native-image/reflect-config.json` registers every class of
`io.swagger.v3.oas.models`, the swagger-core Jackson mixins, and the serializers those mixins reference.
Regenerate that list whenever the swagger-parser version changes.
