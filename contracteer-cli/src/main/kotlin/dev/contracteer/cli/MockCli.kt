package dev.contracteer.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import dev.contracteer.mockserver.MockServer

@Command(
  name = "mock",
  synopsisHeading = "\n@|bold,cyan Usage|@:\n  ",
  descriptionHeading = "\n@|bold,cyan Description|@:\n  ",
  description = ["Launch a Contracteer Mock Server that simulates API responses based on an OpenAPI document."],
  optionListHeading = "\n@|bold,cyan Options|@:\n",
  parameterListHeading = "\n@|bold,cyan Parameters|@:\n",
  mixinStandardHelpOptions = true,
  usageHelpAutoWidth = true,
  abbreviateSynopsis = false
)
class MockCli: HttpCliCommand() {

  @Option(
    names = ["-p", "--port"],
    required = false,
    description = ["Port number for the Contracteer Mock Server. Default: @|bold \${DEFAULT-VALUE}|@."]
  )
  private var port = 8080

  override fun runCommand(): Int =
    withOperations(path) { operations ->
      MockServer(operations, port).start()
      serveUntilKilled()
    }

  private fun serveUntilKilled(): Int {
    Thread.currentThread().join()
    return 0
  }
}