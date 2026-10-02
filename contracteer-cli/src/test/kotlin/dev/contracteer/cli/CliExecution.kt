package dev.contracteer.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/** What one run of the `contracteer` command produced. */
internal data class Execution(val exitCode: Int, val stdout: String, val stderr: String)

/** Runs the `contracteer` command tree with [args], as the binary would, and captures its output. */
internal fun execute(vararg args: String): Execution {
  val stdout = ByteArrayOutputStream()
  val stderr = ByteArrayOutputStream()
  val originalOut = System.out
  val originalErr = System.err
  System.setOut(PrintStream(stdout, true))
  System.setErr(PrintStream(stderr, true))
  return try {
    val exitCode = commandLine().execute(*args)
    Execution(exitCode, stdout.toString(), stderr.toString())
  } finally {
    System.setOut(originalOut)
    System.setErr(originalErr)
  }
}
