@file:JvmName("Results")

package dev.contracteer.core

import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Result.Failure
import dev.contracteer.core.Result.Success
import dev.contracteer.core.ResultScope.bind

/**
 * Outcome of a validation or creation operation that is either a [Success] carrying a [value][Success.value],
 * or a [Failure] carrying one or more property-scoped [Diagnostic]s.
 *
 * Errors are tracked with dotted property paths (e.g. `address.street`) so that validation messages
 * can pinpoint the exact location of a problem inside nested structures.
 */
sealed class Result<out T> {

  /** Returns `true` when this result is a [Success]. */
  fun isSuccess(): Boolean = this is Success

  /** Returns `true` when this result is a [Failure]. */
  fun isFailure(): Boolean = this is Failure

  /** Returns a new result with every error path prefixed by [propertyName]. */
  fun forProperty(propertyName: String): Result<T> = when (this) {
    is Success -> this
    is Failure -> Failure(propertyErrors.map { it.prependProperty(propertyName) }, truncated)
  }

  /** Returns a new result with every error path prefixed by the array index `[index]`. */
  fun forIndex(index: Int): Result<T> = when (this) {
    is Success -> this
    is Failure -> Failure(propertyErrors.map { it.prependIndex(index) }, truncated)
  }

  /** Returns a new result with every error path prefixed by the named key `[key]`. Used for parameter names, status codes, content types — selections from a named set. */
  fun forKey(key: String): Result<T> = forProperty("[$key]")

  /** Returns the list of human-readable error messages, each prefixed with its property path, followed by a truncation notice when errors were dropped past the cap. */
  fun errors(): List<String> = when (this) {
    is Success -> emptyList()
    is Failure -> propertyErrors.map { it.errorMessage() } + listOfNotNull(truncationNotice(truncated))
  }

  /** Returns the diagnostics carried by this result; empty on success. Property paths are not part of the diagnostics:
   * they appear only in [errors]. The truncation notice is not a diagnostic.
   * */
  fun diagnostics(): List<Diagnostic> = when (this) {
    is Success -> emptyList()
    is Failure -> propertyErrors.map { it.diagnostic }
  }

  /** Returns a new result with [operation] set on every diagnostic; rendered errors are prefixed with it. */
  internal fun forOperation(operation: OperationRef): Result<T> = when (this) {
    is Success -> this
    is Failure -> Failure(propertyErrors.map { it.withOperation(operation) }, truncated)
  }

  /** Transforms the success value with [transform]; propagates errors unchanged on failure. */
  fun <R> map(transform: (T) -> R): Result<R> = when (this) {
    is Success -> Success(transform(value))
    is Failure -> this
  }

  /** Transforms the success value with [transform] which itself returns a [Result]; propagates errors unchanged on failure. */
  fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
    is Success -> transform(value)
    is Failure -> this
  }

  /** Transforms every error message, including its property path but not its operation prefix, with [transform]; returns this result
   * unchanged on success. Property paths are flattened into the message text — subsequent [forProperty] calls
   * will not compose with the original path. Each transformed message becomes a freeform diagnostic that keeps
   * its operation. The truncation notice is not transformed.
   * */
  fun mapErrors(transform: (String) -> String): Result<T> = when (this) {
    is Success -> this
    is Failure -> Failure(propertyErrors.map { it.mapMessage(transform) }, truncated)
  }

  /** Re-types a failure result to a different value type. Throws on success. */
  fun <R> retypeError(): Result<R> = when (this) {
    is Failure -> this
    is Success -> error("retypeError() called on a Success result")
  }

  /** Merges this result with [other], succeeding only if both succeed, and accumulating all errors otherwise. */
  infix fun combineWith(other: Result<*>): Result<Unit> =
    if (this is Success && other is Success) Success(Unit)
    else cappedFailure(this, other)

  /** Chains a follow-up validation: on success, runs [next]; on failure, accumulates errors from both this result and [next]. */
  infix fun <E> andThen(next: () -> Result<E>): Result<E> = when (this) {
    is Success -> next()
    is Failure -> cappedFailure(this, next())
  }

  override fun toString() = when (this) {
    is Success -> "Result(success, value=$value)"
    is Failure -> "Result(failure, errors=${errors()})"
  }

  private fun propertyErrors(): List<PropertyError> = when (this) {
    is Success -> emptyList()
    is Failure -> propertyErrors
  }

  private fun truncated(): Int = when (this) {
    is Success -> 0
    is Failure -> truncated
  }

  /** A successful result carrying a [value]. */
  class Success<out T>(val value: T): Result<T>()

  /** A failed result carrying one or more property-scoped diagnostics. */
  class Failure internal constructor(
    internal val propertyErrors: List<PropertyError>,
    internal val truncated: Int = 0
  ): Result<Nothing>()

  companion object {
    private const val MAX_ERRORS = 25

    private fun cappedFailure(left: Result<*>, right: Result<*>): Failure {
      val combined = left.propertyErrors() + right.propertyErrors()
      val overflow = (combined.size - MAX_ERRORS).coerceAtLeast(0)
      return Failure(combined.take(MAX_ERRORS), left.truncated() + right.truncated() + overflow)
    }

    private fun truncationNotice(truncated: Int): String? = when (truncated) {
      0    -> null
      1    -> "1 additional error was truncated"
      else -> "$truncated additional errors were truncated"
    }

    /** Creates a successful result carrying [value]. */
    @JvmStatic
    fun <T> success(value: T): Result<T> = Success(value)

    /** Creates a successful result carrying [Unit]. */
    @JvmStatic
    fun success(): Result<Unit> = Success(Unit)

    /** Creates a failed result with the given error messages, not scoped to any property. */
    @JvmStatic
    fun <T> failure(vararg errors: String): Result<T> =
      Failure(errors.map { PropertyError(diagnostic = Diagnostic(it)) })

    /** Creates a failed result with [diagnostic], not scoped to any property. */
    @JvmStatic
    fun <T> failure(diagnostic: Diagnostic): Result<T> =
      Failure(listOf(PropertyError(diagnostic = diagnostic)))

    /** Creates a failed result with [error] scoped to the array element at [propertyIndex]. */
    @JvmStatic
    fun <T> failure(propertyIndex: Int, error: String): Result<T> =
      Failure(listOf(PropertyError(propertyIndex, Diagnostic(error))))

    /** Creates a failed result with [error] scoped to the property named [propertyName]. */
    @JvmStatic
    fun <T> failure(propertyName: String, error: String): Result<T> =
      Failure(listOf(PropertyError(propertyName, Diagnostic(error))))

    /** Creates a failed result with [error] scoped to the named key `[key]`. Used for parameter names, status codes, content types. */
    @JvmStatic
    fun <T> failureForKey(key: String, error: String): Result<T> =
      Failure(listOf(PropertyError("[$key]", Diagnostic(error))))
  }

  internal class PropertyError(val path: String = "", val diagnostic: Diagnostic) {
    constructor(index: Int, diagnostic: Diagnostic): this("[$index]", diagnostic)

    fun prependProperty(propertyName: String) =
      PropertyError(buildPath(propertyName), diagnostic)

    fun prependIndex(index: Int) = prependProperty("[$index]")

    fun withOperation(operation: OperationRef) =
      PropertyError(path, diagnostic.copy(operation = operation))

    fun mapMessage(transform: (String) -> String) =
      PropertyError(diagnostic = Diagnostic(transform(locatedMessage()), operation = diagnostic.operation))

    fun errorMessage() = operationPrefix() + locatedMessage()

    private fun locatedMessage() = if (path.isEmpty()) diagnostic.message else "'$path': ${diagnostic.message}"

    private fun operationPrefix() = diagnostic.operation?.let { "${it.method} ${it.path}: " } ?: ""

    private fun buildPath(propertyName: String) = when {
      propertyName.isEmpty() -> path
      path.isEmpty()         -> propertyName
      path.startsWith("[")   -> "$propertyName${path}"
      else                   -> "$propertyName.${path}"
    }
  }

}

/**
 * Runs [block] with [ResultScope.bind] available to sequentially unwrap [Result] values.
 *
 * Inside the block, `result.bind()` returns the [Success] value or short-circuits the block
 * with the first [Failure]. This flattens nested [Result.flatMap] chains into straight-line code
 * for cases where each step depends on the previous value.
 *
 * For independent operations whose errors should accumulate, use [combineWith] or [combineResults] instead.
 */
inline fun <T> result(block: ResultScope.() -> T): Result<T> =
  try {
    success(ResultScope.block())
  } catch (e: BindException) {
    e.failure
  }

/** Scope providing [bind] to unwrap [Result] values inside a [result] block. */
object ResultScope {
  /** Returns the [Success] value or short-circuits the enclosing [result] block with this [Failure]. */
  fun <T> Result<T>.bind(): T = when (this) {
    is Success -> value
    is Failure -> throw BindException(this)
  }
}

@PublishedApi
internal class BindException(val failure: Failure): RuntimeException() {
  override fun fillInStackTrace(): Throwable = this
}

/** Applies [transform] to each element, accumulating all errors across the collection into a single result. */
fun <E, R> Collection<E>.accumulate(transform: (E) -> Result<R>): Result<Unit> =
  fold(success()) { acc, element -> acc combineWith transform(element) }

/** Applies [transform] to each entry, accumulating all errors. On success, returns a map of transformed values. */
fun <K, V, R> Map<out K, V>.accumulate(transform: (Map.Entry<K, V>) -> Result<R>): Result<Map<K, R>> =
  entries.fold(success(emptyMap<K, R>())) { acc, entry ->
    val result = transform(entry)
    when (acc) {
      is Success if result is Success -> success(acc.value + (entry.key to result.value))
      is Failure if result is Failure -> (acc combineWith result).retypeError()
      is Failure                      -> acc
      else                            -> result.retypeError()
    }
  }

/** Applies [transform] to each element with its index, accumulating all errors. On success, returns a list of transformed values. */
inline fun <E, reified R> List<E>.accumulateWithIndex(transform: (index: Int, E) -> Result<R>): Result<List<R>> =
  indices.fold(success(emptyList<R>())) { acc, index ->
    val result = transform(index, this[index])
    when (acc) {
      is Success if result is Success -> success(acc.value + result.value)
      is Failure if result is Failure -> (acc combineWith result).retypeError()
      is Failure                      -> acc
      else                            -> result.retypeError()
    }
  }

/** Combines all results in this collection, succeeding with a list of values only if every result is a success. */
fun <E> Collection<Result<E>>.combineResults(): Result<List<E>> =
  fold(success(emptyList<E>())) { acc, current ->
    when (acc) {
      is Success if current is Success -> success(acc.value + current.value)
      is Failure if current is Failure -> (acc combineWith current).retypeError()
      is Failure                       -> acc
      else                             -> current.retypeError()
    }
  }
