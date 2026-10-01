package dev.contracteer.verifier

import dev.contracteer.core.DiagnosticRule.OPERATION_EXCLUDED
import dev.contracteer.core.OperationRef
import dev.contracteer.core.operation.ParameterElement
import dev.contracteer.core.operation.ParameterElement.Cookie
import dev.contracteer.core.operation.ParameterElement.Header
import dev.contracteer.core.operation.ParameterElement.PathParam
import dev.contracteer.core.operation.ParameterElement.QueryParam
import dev.contracteer.verifier.VerificationCase.ScenarioBased
import dev.contracteer.verifier.VerificationCase.SchemaBased
import dev.contracteer.verifier.VerificationCase.TypeMismatch

/** The version of the JSON shape [toJsonMap] projects. */
private const val JSON_VERSION = 1

/** Projects the report onto its JSON shape: absent values as `null` keys, as a diagnostic does. */
internal fun VerificationReport.toJsonMap(): Map<String, Any?> = linkedMapOf(
  "version" to JSON_VERSION,
  "source" to loadReport.source,
  "summary" to linkedMapOf(
    "cases" to outcomes.size,
    "passed" to outcomes.count { it.result.isSuccess() },
    "failed" to outcomes.count { it.result.isFailure() }
  ),
  "load" to linkedMapOf("diagnostics" to loadReport.diagnostics.map { it.toJsonMap() }),
  "operations" to operationsJsonMap(),
  "cases" to outcomes.map { it.toJsonMap() }
)

/**
 * Declared operations are the loaded ones plus those excluded at load. A gap is an operation excluded at load,
 * or one whose primary response no case asserts.
 */
private fun VerificationReport.operationsJsonMap(): Map<String, Any?> {
  val excluded = excludedAtLoad()
  return linkedMapOf(
    "declared" to loadReport.operations.size + excluded.size,
    "gaps" to excluded + unverifiedPrimaryResponses.map {
      gap(OperationRef(it.method.uppercase(), it.path), "primary-response-unverified", it.explanation())
    }
  )
}

private fun VerificationReport.excludedAtLoad(): List<Map<String, Any?>> =
  loadReport.diagnostics
    .filter { it.rule == OPERATION_EXCLUDED }
    .mapNotNull { diagnostic -> diagnostic.operation?.let { gap(it, "excluded-at-load", diagnostic.message) } }

private fun gap(operation: OperationRef, reason: String, message: String): Map<String, Any?> =
  linkedMapOf("operation" to operation.toJsonMap(), "reason" to reason, "message" to message)

private fun VerificationOutcome.toJsonMap(): Map<String, Any?> = linkedMapOf(
  "name" to case.displayName,
  "kind" to case.kind(),
  "operation" to case.operation().toJsonMap(),
  "status" to if (result.isSuccess()) "passed" else "failed",
  "mutatedElement" to (case as? TypeMismatch)?.mutatedElement?.toJsonMap(),
  "diagnostics" to result.diagnostics().map { it.toJsonMap() },
  "truncated" to result.truncated()
)

private fun VerificationCase.kind(): String = when (this) {
  is ScenarioBased -> "scenario-based"
  is SchemaBased   -> "schema-based"
  is TypeMismatch  -> "type-mismatch"
}

private fun VerificationCase.operation(): OperationRef = when (this) {
  is ScenarioBased -> OperationRef(scenario.method.uppercase(), scenario.path)
  is SchemaBased   -> OperationRef(method.uppercase(), path)
  is TypeMismatch  -> OperationRef(method.uppercase(), path)
}

private fun MutatedElement.toJsonMap(): Map<String, Any?> = when (this) {
  is MutatedElement.Parameter -> linkedMapOf("in" to element.location(), "name" to element.name)
  is MutatedElement.Body      -> linkedMapOf("in" to "body", "name" to null)
}

private fun ParameterElement.location(): String = when (this) {
  is PathParam  -> "path"
  is QueryParam -> "query"
  is Header     -> "header"
  is Cookie     -> "cookie"
}

private fun OperationRef.toJsonMap(): Map<String, Any?> = linkedMapOf("method" to method, "path" to path)
