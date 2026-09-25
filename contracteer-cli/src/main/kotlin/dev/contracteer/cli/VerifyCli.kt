package dev.contracteer.cli

import ch.qos.logback.classic.Level.DEBUG
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Ansi.AUTO
import picocli.CommandLine.Option
import dev.contracteer.verifier.OpenApiVerifier
import dev.contracteer.verifier.VerificationCaseFactory
import dev.contracteer.verifier.VerificationOutcome
import dev.contracteer.verifier.VerificationPlan
import dev.contracteer.verifier.VerifierConfiguration
import kotlin.system.exitProcess

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

  override fun runCommand() {
    val plans = loadOperations(path).map { VerificationCaseFactory.plan(it) }
    runVerification(plans).also { exitProcess(it) }
  }

  private fun runVerification(plans: List<VerificationPlan>): Int {
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
}
