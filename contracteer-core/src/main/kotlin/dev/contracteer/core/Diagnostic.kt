package dev.contracteer.core

/**
 * One finding reported by Contracteer: a spec problem found while loading an OpenAPI document, or a
 * contract violation found while validating a request or a response.
 *
 * A diagnostic without a [rule] is freeform: its [message] is the only description of the finding.
 * The shape is experimental and may change until a consumer freezes it.
 *
 * @property message the human-readable description of the finding.
 * @property keyword the OpenAPI or JSON Schema keyword the finding is about (e.g. `maxItems`, `$ref`); `null` when none applies.
 * @property rule the family the finding belongs to; `null` when freeform.
 * @property operation the operation the finding belongs to; `null` when it belongs to none.
 * @property category what kind of finding this is; `null` until assigned (a report assigns one to every finding).
 * @property severity how serious the finding is; `null` until assigned (a report assigns one to every finding).
 */
data class Diagnostic @JvmOverloads constructor(
  val message: String,
  val keyword: String? = null,
  val rule: DiagnosticRule? = null,
  val operation: OperationRef? = null,
  val category: DiagnosticCategory? = null,
  val severity: Severity? = null
) {

  /** Renders [text] (by default the [message]) for humans, prefixed with the [operation] when there is one, e.g. `GET /items/{id}: text`. */
  internal fun render(text: String = message): String =
    operation?.let { "${it.method} ${it.path}: $text" } ?: text
}

/** Identifies an operation by its HTTP [method] (upper case, e.g. `GET`) and its [path] template (e.g. `/items/{id}`). */
data class OperationRef(val method: String, val path: String)

/** What kind of finding a [Diagnostic] reports. */
enum class DiagnosticCategory {
  /** Found while loading the OpenAPI document: a problem in the document, or a part of it Contracteer does not handle. */
  SPEC,
  /** A request or a response does not conform to the OpenAPI document. */
  CONTRACT_VIOLATION,
  /** The check itself could not run (e.g. the server could not be reached). */
  EXECUTION_ERROR
}

/** How serious a [Diagnostic] is. */
enum class Severity {
  ERROR,
  WARNING,
  INFO
}
