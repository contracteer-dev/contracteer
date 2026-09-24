# contracteer-conformance-test

Internal test module. It publishes no artifact and is not meant to be consumed.

## The invariant it encodes

**For any OpenAPI document Contracteer accepts, running the verifier against the mock server
started from that same document must pass.**

A response shape is supported by both consumers or by neither -- never one alone. The verifier
asserting a response the mock refuses to emit is a defect even when each side is internally
consistent.

When neither consumer supports an operation's primary response (a `2XX`-only operation, or `200`
and `201` with nothing to choose between them), the round trip still has one correct outcome: the
verifier reports the primary response unverified, and the mock server answers 418. If one side is
silent while the other refuses, that is a defect.

## Why it is a module of its own

The invariant is a property of the verifier/mock *pair*, not of either one, and the two are
siblings: `contracteer-mockserver` and `contracteer-verifier` both depend on `contracteer-core`
and never on each other. Hosting the test inside one of them would create a horizontal dependency
the architecture forbids, and would bias attribution -- a failure can come from either side, and a
red test in `contracteer-mockserver` reads as "the mock is wrong" when it may not be.

Before this module existed, nothing in the repository ran the verifier against the mock server.
`contracteer-cli` is the only module depending on both, and it has no test source set.

## Why `kotlin-conventions` and not `library-conventions`

`library-conventions` applies `maven-publish`. Releasing is `./gradlew publish`, so applying it
here would silently add an artifact to Maven Central for a module no user consumes. This module
applies `kotlin-conventions` only, and must keep doing so.

## What a row asserts

Fixtures come from the `contracteer-core` test-fixtures DSL -- one `apiOperation(...)` per row.
No OpenAPI document is parsed here; loader behaviour is covered in `contracteer-core`.

Each row asserts four things:

- the number of verification cases `VerificationCaseFactory.plan(...)` produces,
- the outcome of each of those cases, run against the mock server started from the same operation,
- the status code the mock emits for a direct request to the operation,
- whether the plan reports the operation's primary response unverified.

The case count is the load-bearing assertion. A harness checking only "nothing failed" goes green
when no case is generated at all, which is the failure mode this module exists to catch. The
direct mock probe matters for the same reason: rows generating zero cases would otherwise never
exercise the mock.

The last assertion runs in both directions. Reporting an operation the mock answers is as wrong as
staying silent about one it refuses.

From these, each row derives the state of the round trip:

- `HOLDS`: at least one case, none fails, no report, and the mock answers the probe.
- `REPORTED`: the plan reports the primary response unverified, no case fails, and the mock
  answers 418.
- `VIOLATED`: anything else.

No row is `VIOLATED` today. A row marked `VIOLATED` is a characterization test: it passes because
it asserts the behaviour that currently exists, and its expectations change when that behaviour is
fixed.

## Not a corpus round trip

Running the verifier against the mock across a whole spec corpus is a separate exercise -- spec
selection, runtime budget, generation flakiness and reporting. This module is a fixed matrix, and
would be the natural home for such a suite if one is ever built.
