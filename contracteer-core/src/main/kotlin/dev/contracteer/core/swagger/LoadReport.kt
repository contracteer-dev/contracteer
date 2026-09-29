package dev.contracteer.core.swagger

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.operation.ApiOperation

/**
 * Outcome of loading an OpenAPI document with [OpenApiLoader.load]: either [Loaded] with the extracted operations,
 * or [Failed] with the diagnostics that stopped the load. Either outcome also carries the load's warnings: findings
 * that did not stop it, such as an operation excluded from the contract or a constraint ignored.
 *
 * The shape is experimental and may change until a consumer freezes it.
 *
 * @property source the location the document was loaded from, as given to [OpenApiLoader.load].
 * @property diagnostics the findings of the load, each with its category and severity assigned.
 * @property truncated how many errors were dropped past the error cap; warnings are never dropped.
 */
sealed class LoadReport(
  val source: String,
  val diagnostics: List<Diagnostic>,
  val truncated: Int
) {

  /** The document loaded; [operations] are the operations extracted from it, and [diagnostics] are its warnings. */
  class Loaded internal constructor(
    source: String,
    val operations: List<ApiOperation>,
    diagnostics: List<Diagnostic>
  ): LoadReport(source, diagnostics, 0)

  /** The document could not be loaded; [diagnostics] list the errors that say why, followed by the warnings. */
  class Failed internal constructor(
    source: String,
    diagnostics: List<Diagnostic>,
    truncated: Int
  ): LoadReport(source, diagnostics, truncated)
}
