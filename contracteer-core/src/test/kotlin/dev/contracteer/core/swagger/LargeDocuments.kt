package dev.contracteer.core.swagger

import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

// Pads a fixture to about 4 MB, past SnakeYAML's default limit of 3 MB and under the loader's 10 MB. The padding is an
// extension rather than comments, which SnakeYAML does not count toward its limit.
internal fun Path.largeCopyOf(fixture: String): String {
  val padded = File("src/test/resources/$fixture").readText() + "x-padding:\n" + "  - padding\n".repeat(350_000)
  return resolve(fixture.substringAfterLast('/')).apply { writeText(padded) }.toString()
}
