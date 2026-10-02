package dev.contracteer.cli

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.OPERATION_EXCLUDED
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.Severity.WARNING
import dev.contracteer.core.swagger.LoadReport
import picocli.CommandLine.Help.Ansi.AUTO

/**
 * The text view of a load report, as `lint` prints it: the findings grouped by severity, each with its rule, then a
 * closing line that says what ran. The view never states a bare "clean": a document that loads names what was
 * checked, and a document that does not load says that exclusions were not evaluated.
 */
internal fun lintView(report: LoadReport): List<String> {
  val errors = report.diagnostics.filter { it.severity == ERROR }
  val warnings = report.diagnostics.filter { it.severity == WARNING }
  return listOf("OpenAPI document: ${report.source}", "") +
         section("Errors", "red", errors) +
         section("Warnings", "yellow", warnings) +
         closingLine(report, errors.size, warnings.size)
}

private fun section(title: String, color: String, findings: List<Diagnostic>): List<String> =
  if (findings.isEmpty()) emptyList()
  else listOf(AUTO.string("@|bold,$color $title (${findings.size})|@")) + findings.map { "   ${it.line()}" } + ""

private fun Diagnostic.line(): String =
  listOfNotNull(
    operation?.let { "${it.method} ${it.path}:" },
    location?.let { "'$it':" },
    message,
    rule?.let { "[${it.name.lowercase().replace('_', '-')}]" }
  ).joinToString(" ")

private fun closingLine(report: LoadReport, errorCount: Int, warningCount: Int): String =
  when (report) {
    is LoadReport.Loaded -> "The document loads: ${report.operationsLine()}${findingsLine(errorCount, warningCount)}"
    is LoadReport.Failed -> "The document does not load: ${counts(errorCount, warningCount)}. " +
                            "Exclusions were not evaluated.${report.truncationNote()}"
  }

private fun LoadReport.Loaded.operationsLine(): String {
  val excludedCount = diagnostics.count { it.rule == OPERATION_EXCLUDED }
  val declaredCount = operations.size + excludedCount
  val excluded = if (excludedCount == 0) "none" else "$excludedCount"
  return "$declaredCount ${plural(declaredCount, "operation")} declared, $excluded excluded"
}

private fun findingsLine(errorCount: Int, warningCount: Int) =
  if (errorCount + warningCount == 0) ", no constraint ignored." else ". ${counts(errorCount, warningCount)}."

private fun counts(errorCount: Int, warningCount: Int) =
  "$errorCount ${plural(errorCount, "error")}, $warningCount ${plural(warningCount, "warning")}"

private fun LoadReport.truncationNote() =
  if (truncated == 0) "" else " $truncated more ${plural(truncated, "error")} not shown."
