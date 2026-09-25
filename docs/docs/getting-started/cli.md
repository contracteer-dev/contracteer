# Use the CLI

The Contracteer CLI runs verification and starts mock servers from the command line.
It is a standalone native binary -- no JVM installation required.

**Develop against an OpenAPI document before the server exists.**
Start a mock server from the OpenAPI document and build your client against it.
Every request is validated against the schema.
Client-side bugs are caught during development -- not after the real server is deployed.

**Verify any server, regardless of language.**
The CLI verifies that a running server conforms to its OpenAPI document.
It does not matter whether the server is built in Node.js, Python, Go, or any other language.
If it speaks HTTP and has an OpenAPI document, Contracteer can verify it.

**Integrate into CI/CD pipelines.**
For non-JVM projects, add a verification step to your pipeline without any build tool integration.
The CLI exits with code `0` when all cases pass and `1` when any case fails -- standard CI behavior.

---

## Installation

### Homebrew (macOS / Linux)

```bash
brew install contracteer-dev/contracteer/contracteer
```

### GitHub Releases (all platforms)

Download the archive for your platform from the [latest release](https://github.com/contracteer-dev/contracteer/releases/latest) and extract it.

### Verify the installation

```bash
contracteer --version
```

---

## Verify a Server

`contracteer verify` sends requests to a running server and validates that responses conform to the OpenAPI document.

```bash
contracteer verify openapi.yaml
```

The OpenAPI document can be a local file path or an HTTP(S) URL.

The command exits with code `0` if all verification cases pass, `1` if any case fails.
Unverified primary responses do not change the exit code.

**Options:**

- **`-u`, `--base-url`** *(default: `http://localhost:8080`)* -- Absolute base URL of the server (scheme, host, and port).
- **`-l`, `--log-level`** *(default: `INFO`)* -- Log verbosity: TRACE, DEBUG, INFO, WARN, ERROR, OFF.
- **`-t`, `--http-traffic`** -- Enable HTTP request/response logging.

Example with a custom base URL:

```bash
contracteer verify openapi.yaml -u http://localhost:3000
```

To see every HTTP request and response:

```bash
contracteer verify openapi.yaml -t
```

Example output:

```
🚀 Starting contract verification...
Target Server: http://localhost:8080
OpenAPI document: openapi.yaml

   ✅ GET /musketeers -> 200 (application/json) (generated)
   ✅ GET /musketeers/{id} -> 200 (application/json) with scenario 'ATHOS'
   ✅ GET /musketeers/{id} -> 404 with scenario '404_UNKNOWN_MUSKETEER'
   ✅ GET /musketeers/{id} -> 400|404 (auto: path 'id' type mismatch)
   ❌ POST /musketeers (application/json) -> 400 (auto: body type mismatch)
     ↳ Status code does not match. Expected: 400, Actual: 500

Result Summary:
   ❌ 1 error found during verification.
   ✅ 4 verification cases passed.
```

Each line shows the verification case and its result.
Failed cases include the reason -- here, the server returned `500` instead of the expected `400`.

When Contracteer cannot determine an operation's [primary response](../concepts/how-contracteer-works.md#the-primary-response), no verification case asserts it.
The summary lists the operation with the reason:

```
Result Summary:
   ✅ 3 verification cases passed.
   ⚠️ POST /orders -> primary response not verified: 200 and 201 both qualify; declare a scenario for each of them
```

An unverified primary response is not a failure: the OpenAPI document is valid, and Contracteer reports what it could not assert.

---

## Start a Mock Server

`contracteer mock` starts a mock server that validates requests and returns spec-compliant responses.
It runs until terminated (`Ctrl+C`).

```bash
contracteer mock openapi.yaml
```

The OpenAPI document can be a local file path or an HTTP(S) URL.

**Options:**

- **`-p`, `--port`** *(default: `8080`)* -- Port for the mock server.
- **`-l`, `--log-level`** *(default: `INFO`)* -- Log verbosity: TRACE, DEBUG, INFO, WARN, ERROR, OFF.
- **`-t`, `--http-traffic`** -- Enable HTTP request/response logging.

Example on a custom port with traffic logging:

```bash
contracteer mock openapi.yaml -p 9090 -t
```

The mock server validates every incoming request against the OpenAPI schema.
If the request matches a scenario defined in the OpenAPI document, it returns that scenario's response.
If the request is valid but matches no scenario, it generates a response from the schema.
If the request violates the OpenAPI document and the operation defines a 400 response, the mock server returns `400`.
Otherwise, it returns `418` with diagnostic information.

The `418` is not a status code from your API.
It is Contracteer telling you that something is wrong or ambiguous.
The 418 body explains what happened -- read it before investigating further.

See [Testing Your Client](../concepts/testing-your-client.md) for a detailed explanation of mock server behavior.

---

## Try It Now

Start a mock server from the [Musketeer API](https://github.com/contracteer-dev/contracteer-examples) OpenAPI document without cloning anything:

```bash
contracteer mock https://raw.githubusercontent.com/contracteer-dev/contracteer-examples/main/musketeer-spec/src/main/resources/musketeer-api.yaml -t
```

Then send a request:

```bash
curl http://localhost:8080/musketeers
```

The mock server returns a generated response conforming to the Musketeer schema.

---

## Next Steps

- [Testing Your Server](../concepts/testing-your-server.md) -- what the verifier checks in depth, including automatic 400 testing.
- [Testing Your Client](../concepts/testing-your-client.md) -- how the mock server validates requests and generates responses.
- [contracteer-examples](https://github.com/contracteer-dev/contracteer-examples) -- complete working projects with server and client examples.
