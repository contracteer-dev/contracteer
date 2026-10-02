package dev.contracteer.cli

import dev.contracteer.cli.LevelConverter.Companion.enableHttpTrafficLogging
import picocli.CommandLine.Option

/** A command that exchanges HTTP requests and responses, and can log them. */
abstract class HttpCliCommand: BaseCliCommand() {

  @Option(
    names = ["-t", "--http-traffic"],
    description = ["Enable HTTP request/response logging."],
    defaultValue = "false"
  )
  private var httpTraffic: Boolean = false

  override fun call(): Int {
    if (httpTraffic) enableHttpTrafficLogging()
    return super.call()
  }
}
