package dev.contracteer.core.datatype

import com.github.curiousoddman.rgxgen.RgxGen
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.PATTERN_UNCERTIFIABLE
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.result

/**
 * Encapsulates an OpenAPI `pattern` value end-to-end: validation of string values against
 * the pattern, and generation of random values guaranteed to match it.
 *
 * Validation uses the original pattern compiled as a Java [Regex]. Generation uses
 * [RgxGen], possibly fed a rewritten pattern so RgxGen can handle constructs it would
 * otherwise fail on. Creation performs a sample-based check to ensure the generator
 * actually produces values matching the original pattern; patterns that parse successfully
 * but silently corrupt their output are rejected at creation time.
 */
internal class StringPattern private constructor(
  val source: String,
  private val compiledRegex: Regex,
  private val generator: RgxGen
) {

  /** Validates that [value] matches the pattern. Uses `containsMatchIn` semantics. */
  fun validate(value: String): Result<String> =
    if (compiledRegex.containsMatchIn(value)) success(value)
    else failure("Value '$value' does not match pattern '$source'.")

  /** Generates a random value guaranteed to match the pattern (verified at creation time). */
  fun randomValue(): String = generator.generate()

  companion object {
    private const val SAMPLE_COUNT = 50

    fun create(pattern: String) = result {
      val compiledRegex = compileJavaRegex(pattern).bind()
      val generator = parseRgxGen(pattern).bind()
      verifySamples(pattern, compiledRegex, generator).bind()
    }

    private fun compileJavaRegex(pattern: String): Result<Regex> =
      try {
        success(Regex(pattern))
      } catch (e: Throwable) {
        failure("pattern", "'$pattern' is not a valid regular expression (${shortCause(e)})")
      }

    private fun parseRgxGen(pattern: String): Result<RgxGen> =
      try {
        success(RgxGen.parse(PatternRewriter.rewrite(pattern)))
      } catch (_: Throwable) {
        patternNotSupported(pattern)
      }

    private fun verifySamples(pattern: String, compiledRegex: Regex, generator: RgxGen): Result<StringPattern> =
      generateSamples(generator, pattern).flatMap { samples ->
        try {
          val firstBad = samples.firstOrNull { !compiledRegex.containsMatchIn(it) }
          if (firstBad != null) patternNotSupported(pattern)
          else success(StringPattern(pattern, compiledRegex, generator))
        } catch (_: StackOverflowError) {
          patternNotSupported(pattern)
        }
      }

    private fun generateSamples(generator: RgxGen, pattern: String): Result<List<String>> =
      runCatching {
        (1..SAMPLE_COUNT).map { generator.generate() }
      }.fold(
        onSuccess = { success(it) },
        onFailure = { _ -> patternNotSupported(pattern) }
      )

    private fun <T> patternNotSupported(pattern: String): Result<T> =
      failure<T>(Diagnostic(
        "'$pattern' is not supported for value generation. Use OpenAPI examples to provide explicit values for this property (creating Scenarios), or simplify the pattern.",
        keyword = "pattern",
        rule = PATTERN_UNCERTIFIABLE
      )).forProperty("pattern")

    private fun shortCause(e: Throwable): String =
      e.message?.lines()?.firstOrNull() ?: e::class.simpleName.orEmpty()
  }
}