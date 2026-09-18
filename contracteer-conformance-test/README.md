# contracteer-conformance-test

Internal test module. It publishes no artifact and is not meant to be consumed.

## The invariant it encodes

**For any OpenAPI document Contracteer accepts, running the verifier against the mock server
started from that same document must pass.**

A response shape is supported by both consumers or by neither -- never one alone. The verifier
asserting a response the mock refuses to emit is a defect even when each side is internally
consistent.

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

Each row asserts three things:

- the number of verification cases `VerificationCaseFactory.create(...)` produces,
- the outcome of each of those cases, run against the mock server started from the same operation,
- the status code the mock emits for a direct request to the operation.

The case count is the load-bearing assertion. A harness checking only "nothing failed" goes green
when no case is generated at all, which is the failure mode this module exists to catch. The
direct mock probe matters for the same reason: rows generating zero cases would otherwise never
exercise the mock.

Rows that violate the invariant today are marked as such. They are characterization tests: they
pass, because they assert the behaviour that currently exists, and their expectations change when
the behaviour is fixed.

## Not a corpus round trip

Running the verifier against the mock across a whole spec corpus is a separate exercise -- spec
selection, runtime budget, generation flakiness and reporting. This module is a fixed matrix, and
would be the natural home for such a suite if one is ever built.
