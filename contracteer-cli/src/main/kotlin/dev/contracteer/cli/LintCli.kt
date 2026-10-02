package dev.contracteer.cli

import ch.qos.logback.core.joran.spi.ConsoleTarget
import ch.qos.logback.core.joran.spi.ConsoleTarget.SystemErr
import dev.contracteer.cli.LevelConverter.Companion.muteLoadWarningLogs
import dev.contracteer.cli.OutputFormat.JSON
import dev.contracteer.cli.OutputFormat.TEXT
import dev.contracteer.core.Severity.WARNING
import dev.contracteer.core.swagger.LoadReport
import dev.contracteer.core.swagger.OpenApiLoader
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
  name = "lint",
  synopsisHeading = "\n@|bold,cyan Usage|@:\n  ",
  descriptionHeading = "\n@|bold,cyan Description|@:\n  ",
  description = [
    "Report what Contracteer cannot execute in an OpenAPI document, and what it skips or ignores."
  ],
  optionListHeading = "\n@|bold,cyan Options|@:\n",
  parameterListHeading = "\n@|bold,cyan Parameters|@:\n",
  mixinStandardHelpOptions = true,
  usageHelpAutoWidth = true,
  abbreviateSynopsis = false
)
class LintCli: BaseCliCommand() {
  @Option(
    names = ["--format"],
    required = false,
    description = [$$"Output format: text or json. Logs go to stderr in both formats. Default: @|bold ${DEFAULT-VALUE}|@."],
  )
  private var format = TEXT

  @Option(
    names = ["--fail-on"],
    required = false,
    converter = [FailOn.Converter::class],
    description = [$$"Lowest severity that makes the command exit with 1: error or warning. Default: @|bold ${DEFAULT-VALUE}|@."],
  )
  private var failOn = FailOn.ERROR

  /** The report is the only output on stdout, in both formats. */
  override val logTarget: ConsoleTarget get() = SystemErr

  override fun runCommand(): Int {
    muteLoadWarningLogs()
    val report = OpenApiLoader.load(path)
    when (format) {
      JSON -> println(report.toJson())
      TEXT -> lintView(report).forEach { println(it) }
    }
    return if (report.fails()) 1 else 0
  }

  private fun LoadReport.fails() =
    this is LoadReport.Failed || (failOn == FailOn.WARNING && diagnostics.any { it.severity == WARNING })
}
