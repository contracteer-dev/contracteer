package dev.contracteer.core

/** The family a [Diagnostic] belongs to. The set is experimental and grows as more findings are given a rule. */
enum class DiagnosticRule {
  /** The OpenAPI document uses a keyword, a schema form or a reference Contracteer does not handle. */
  UNSUPPORTED,
  /** A content type cannot serialize the schema declared for it. */
  UNSERIALIZABLE_CONTENT,
  /** A circular reference has no optional, nullable or collection exit point, so no finite value exists. */
  INFINITE_CYCLE,
  /** No value can be generated for a pattern. */
  PATTERN_UNCERTIFIABLE
}
