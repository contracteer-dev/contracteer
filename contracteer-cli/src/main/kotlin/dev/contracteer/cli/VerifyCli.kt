package dev.contracteer.cli

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.core.joran.spi.ConsoleTarget
import ch.qos.logback.core.joran.spi.ConsoleTarget.SystemErr
import ch.qos.logback.core.joran.spi.ConsoleTarget.SystemOut
import dev.contracteer.cli.VerifyCli.OutputFormat.JSON
import dev.contracteer.cli.VerifyCli.OutputFormat.TEXT
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.swagger.LoadReport
import dev.contracteer.core.swagger.OpenApiLoader
import dev.contracteer.verifier.OpenApiVerifier
import dev.contracteer.verifier.VerificationCaseFactory
import dev.contracteer.verifier.VerificationOutcome
import dev.contracteer.verifier.VerifierConfiguration
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Ansi.AUTO
import picocli.CommandLine.Option

@Command(
  name = "verify",
  synopsisHeading = "\n@|bold,cyan Usage|@:\n  ",
  descriptionHeading = "\n@|bold,cyan Description|@:\n  ",
  description = [
    "Verify that a server's API implementation adheres to its OpenAPI document."
  ],
  optionListHeading = "\n@|bold,cyan Options|@:\n",
  parameterListHeading = "\n@|bold,cyan Parameters|@:\n",
  mixinStandardHelpOptions = true,
  usageHelpAutoWidth = true,
  abbreviateSynopsis = false
)
class VerifyCli: BaseCliCommand() {
  @Option(
    names = ["-u", "--base-url"],
    required = false,
    description = [$$"Base URL of the server to verify (must include scheme, host, and port). Default: @|bold ${DEFAULT-VALUE}|@."],
  )
  private var baseUrl = "http://localhost:8080"

  @Option(
    names = ["--format"],
    required = false,
    description = [$$"Output format: text or json. With json, the report is the only output on stdout and logs go to stderr. Default: @|bold ${DEFAULT-VALUE}|@."],
  )
  private var format = TEXT

  override val logTarget: ConsoleTarget get() = if (format == JSON) SystemErr else SystemOut

  override fun runCommand(): Int =
    when (format) {
      TEXT -> withOperations(path) { runVerification(it) }
      JSON -> runJsonVerification()
    }

  private fun runJsonVerification(): Int =
    when (val loadReport = OpenApiLoader.load(path)) {
      is LoadReport.Failed -> 1.also { println(loadReport.toJson()) }
      is LoadReport.Loaded -> verifyAndPrint(loadReport)
    }

  private fun verifyAndPrint(loaded: LoadReport.Loaded): Int {
    val report = OpenApiVerifier(VerifierConfiguration(baseUrl)).verify(loaded)
    println(report.toJson())
    return if (report.outcomes.any { it.result.isFailure() }) 1 else 0
  }

  private fun runVerification(operations: List<ApiOperation>): Int {
    val plans = operations.map { VerificationCaseFactory.plan(it) }
    val verifier = OpenApiVerifier(VerifierConfiguration(baseUrl))
    println()
    println(AUTO.string("🚀 Starting contract verification..."))
    println(AUTO.string("Target Server: @|bold,green $baseUrl|@"))
    println(AUTO.string("OpenAPI document: @|bold,green ${path}|@"))
    println()

    val outcomes = plans.flatMap { it.cases }.map { verifier.verify(it).also { outcome -> printOutcome(outcome) } }
    val summary = summarize(outcomes, plans.mapNotNull { it.unverifiedPrimaryResponse })

    println()
    println(AUTO.string("@|bold,blue Result Summary:|@"))
    summary.lines.forEach { println(AUTO.string(it)) }
    return summary.exitCode
  }

  private fun printOutcome(outcome: VerificationOutcome) {
    if (logLevel == DEBUG) println()
    if (outcome.result.isSuccess()) {
      println(AUTO.string("   ✅ ${outcome.case.displayName}"))
    } else {
      println(AUTO.string("@|bold,red    ❌ ${outcome.case.displayName}|@"))
      outcome.result.errors().forEach { println(AUTO.string("     ↳ @|yellow $it|@")) }
    }

    if (logLevel == DEBUG) {
      println(AUTO.string("<===========================================================================================>"))
      println()
    }
  }

  internal enum class OutputFormat {
    TEXT, JSON;

    override fun toString() = name.lowercase()
  }
}
