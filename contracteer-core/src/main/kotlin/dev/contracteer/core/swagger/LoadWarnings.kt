package dev.contracteer.core.swagger

import io.github.oshai.kotlinlogging.KotlinLogging
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.OperationRef
import dev.contracteer.core.Severity.WARNING

/**
 * Collects the warnings of one load: findings that do not stop the load but that its report must carry.
 * Each warning is also logged when it is first reported. A warning identical to one already collected is dropped:
 * without a location, distinct schemas sharing a name produce identical warnings that carry no extra information.
 */
internal class LoadWarnings {
  private val logger = KotlinLogging.logger {}
  private val collected = linkedSetOf<Diagnostic>()

  val diagnostics: List<Diagnostic>
    get() = collected.toList()

  fun warn(message: String, operation: OperationRef? = null) {
    val diagnostic = Diagnostic(message, operation = operation, category = SPEC, severity = WARNING)
    if (collected.add(diagnostic)) logger.warn { diagnostic.render() }
  }
}
