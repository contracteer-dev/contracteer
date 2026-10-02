package dev.contracteer.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Level.*
import ch.qos.logback.core.joran.spi.ConsoleTarget
import ch.qos.logback.core.joran.spi.ConsoleTarget.SystemOut
import picocli.CommandLine.Help.Ansi.AUTO
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import dev.contracteer.cli.LevelConverter.Companion.configureLogging
import dev.contracteer.cli.LevelConverter.Companion.enableHttpTrafficLogging
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.Result.Success
import dev.contracteer.core.swagger.OpenApiLoader
import java.util.concurrent.Callable

abstract class BaseCliCommand: Callable<Int> {

  @Parameters(index = "0",
              description = ["Path or URL of the OpenAPI document that defines the API operations."]
  )
  protected lateinit var path: String

  @Option(
    names = ["-l", "--log-level"],
    description = [$$"Specify the log verbosity. Options: TRACE, DEBUG, INFO, WARN, ERROR, OFF, ALL. Default: @|bold ${DEFAULT-VALUE}|@."],
    converter = [LevelConverter::class],
    defaultValue = "INFO"
  )
  protected var logLevel: Level = INFO

  @Option(
    names = ["-t", "--http-traffic"],
    description = ["Enable HTTP request/response logging."],
    defaultValue = "false"
  )
  private var httpTraffic: Boolean = false

  /** Where the logs are written. Standard output unless a command keeps it for its own result. */
  protected open val logTarget: ConsoleTarget get() = SystemOut

  override fun call(): Int {
    configureLogging(logLevel, logTarget)
    if (httpTraffic) enableHttpTrafficLogging()
    return runCommand()
  }

  /** Runs the command and returns its exit code. */
  protected abstract fun runCommand(): Int

  /** Runs [block] on the operations of the document at [path], or prints the load errors and returns `1`. */
  protected fun withOperations(path: String, block: (List<ApiOperation>) -> Int): Int {
    val result = OpenApiLoader.loadOperations(path)
    if (result !is Success) {
      println(AUTO.string("@|bold,red   ❌ Error while loading Operations:|@"))
      result.errors().forEach { println(AUTO.string("     ↳ @|yellow $it|@")) }
      return 1
    }

    return block(result.value)
  }
}
