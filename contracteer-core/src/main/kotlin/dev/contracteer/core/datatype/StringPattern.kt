package dev.contracteer.core.datatype

import com.github.curiousoddman.rgxgen.RgxGen
import com.github.curiousoddman.rgxgen.nodes.Choice
import com.github.curiousoddman.rgxgen.nodes.FinalSymbol
import com.github.curiousoddman.rgxgen.nodes.Group
import com.github.curiousoddman.rgxgen.nodes.GroupRef
import com.github.curiousoddman.rgxgen.nodes.NotSymbol
import com.github.curiousoddman.rgxgen.nodes.Repeat
import com.github.curiousoddman.rgxgen.nodes.SymbolSet
import com.github.curiousoddman.rgxgen.visitors.NodeVisitor
import com.github.curiousoddman.rgxgen.nodes.Sequence as SequenceNode
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

  /**
   * Returns the lowest character that [randomValue] can emit and [isAllowed] rejects, or `null`
   * when every character the generator can emit is allowed. Reads the generator's node tree
   * rather than sampling generated values: it never misses a character the generator can emit,
   * but may report one from a part of the pattern that emits nothing (such as `x{0}`).
   */
  fun lowestCharOutside(isAllowed: (Char) -> Boolean): Char? =
    RgxGenCharCollector.collect(generator).filterNot(isAllowed).minOrNull()

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

/**
 * Collects every character [RgxGen] can emit for a pattern by walking its node tree with
 * RgxGen's own generation rules. Under a negative lookaround ([NotSymbol]) RgxGen emits a
 * non-matching value: a character set yields its inverted set, and a literal or a
 * back-reference yields characters from RgxGen's printable ASCII range. A negative
 * lookaround nested in another one emits matching text again.
 *
 * Collects a node's characters even when it can emit nothing (such as `x{0}`), so the
 * result can be a superset of what RgxGen emits, never a subset.
 */
private class RgxGenCharCollector(private val negated: Boolean,
                                  private val chars: MutableSet<Char>): NodeVisitor {

  override fun visit(node: SymbolSet) {
    chars += (if (negated) node.invertedNode else node).symbolSetIndexer.all.asList()
  }

  override fun visit(node: FinalSymbol) {
    chars += if (negated) RGXGEN_PRINTABLE_ASCII else node.value.asIterable()
  }

  override fun visit(node: Choice) = node.nodes.forEach { it.visit(this) }

  override fun visit(node: SequenceNode) = node.nodes.forEach { it.visit(this) }

  override fun visit(node: Repeat) = node.node.visit(this)

  override fun visit(node: Group) = node.node.visit(this)

  override fun visit(node: NotSymbol) = node.node.visit(RgxGenCharCollector(negated = !negated, chars))

  // A back-reference repeats text its group already emitted; under negation it is random text.
  override fun visit(node: GroupRef) {
    if (negated) chars += RGXGEN_PRINTABLE_ASCII
  }

  companion object {
    private val RGXGEN_PRINTABLE_ASCII = (' '..'~').toList()

    fun collect(generator: RgxGen): Set<Char> =
      mutableSetOf<Char>().also { generator.visit(RgxGenCharCollector(negated = false, it)) }
  }
}