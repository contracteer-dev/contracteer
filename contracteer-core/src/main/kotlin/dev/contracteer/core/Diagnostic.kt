package dev.contracteer.core

/**
 * One finding reported by Contracteer: a spec problem found while loading an OpenAPI document, or a
 * contract violation found while validating a request or a response.
 *
 * A diagnostic without a [rule] is freeform: its [message] is the only description of the finding.
 * The shape is experimental and may change until a consumer freezes it.
 *
 * @property message the human-readable description of the finding.
 * @property location the element the finding is about, as a dotted path (e.g. `response[404].body[application/json]`, `address.street`, `items[0]`); `null` when it is the whole value.
 * @property keyword the OpenAPI or JSON Schema keyword the finding is about (e.g. `maxItems`, `$ref`); `null` when none applies.
 * @property rule the family the finding belongs to; `null` when freeform.
 * @property operation the operation the finding belongs to; `null` when it belongs to none.
 * @property category what kind of finding this is; `null` until assigned (a report assigns one to every finding).
 * @property severity how serious the finding is; `null` until assigned (a report assigns one to every finding).
 */
data class Diagnostic @JvmOverloads constructor(
  val message: String,
  val location: String? = null,
  val keyword: String? = null,
  val rule: DiagnosticRule? = null,
  val operation: OperationRef? = null,
  val category: DiagnosticCategory? = null,
  val severity: Severity? = null
) {

  /** Renders this finding as JSON. The shape is experimental and may change until a consumer freezes it. */
  fun toJson(): String = Mappers.jsonMapper.writeValueAsString(toJsonMap())

  /** Renders the finding:
   * - the [operation] when there is one,
   * - then the [location] when there is one,
   * - then the [message]
   *
   * e.g. `GET /items/{id}: 'request.body': message`. */
  internal fun render(): String =
    operation?.let { "${it.method} ${it.path}: ${locatedMessage()}" } ?: locatedMessage()

  /** Returns a copy located under [propertyName]: `propertyName` when this finding is about the whole value, `propertyName.location` or `propertyName[...]` otherwise; this finding unchanged when [propertyName] is empty. */
  internal fun at(propertyName: String): Diagnostic = copy(location = when {
    propertyName.isEmpty()   -> location
    location == null         -> propertyName
    location.startsWith("[") -> "$propertyName$location"
    else                     -> "$propertyName.$location"
  })

  /** Returns a copy with [category] and [severity] set where this finding has none. */
  internal fun withDefaults(category: DiagnosticCategory, severity: Severity): Diagnostic =
    copy(category = this.category ?: category, severity = this.severity ?: severity)

  /** Returns a freeform copy whose message is [transform] applied to the located message; the location is flattened into the message. */
  internal fun mapMessage(transform: (String) -> String): Diagnostic =
    Diagnostic(transform(locatedMessage()), operation = operation)

  /** Projects the finding onto the JSON wire shape: every property as a key, absent ones as `null`, enums in kebab-case. */
  internal fun toJsonMap(): Map<String, Any?> = linkedMapOf(
    "message" to message,
    "location" to location,
    "keyword" to keyword,
    "rule" to rule?.wireName(),
    "operation" to operation?.let { linkedMapOf("method" to it.method, "path" to it.path) },
    "category" to category?.wireName(),
    "severity" to severity?.wireName()
  )

  private fun locatedMessage() = location?.let { "'$it': $message" } ?: message
}

/** The JSON wire name of an enum constant: its name in kebab-case, e.g. `UNSERIALIZABLE_CONTENT` becomes `unserializable-content`. */
internal fun Enum<*>.wireName() = name.lowercase().replace('_', '-')

/** Identifies an operation by its HTTP [method] (upper case, e.g. `GET`) and its [path] template (e.g. `/items/{id}`). */
data class OperationRef(val method: String, val path: String)

/** What kind of finding a [Diagnostic] reports. */
enum class DiagnosticCategory {
  /** Found while loading the OpenAPI document: a problem in the document, or a part of it Contracteer does not handle. */
  SPEC,

  /** A request or a response does not conform to the OpenAPI document. */
  CONTRACT_VIOLATION,

  /** The check itself could not run (e.g. the server could not be reached). */
  EXECUTION_ERROR
}

/** How serious a [Diagnostic] is. */
enum class Severity {
  ERROR,
  WARNING,
  INFO
}
