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
  PATTERN_UNCERTIFIABLE,
  /** An operation is excluded from the load: a parameter's content has no schema, or none of its request body or response content types is supported. */
  OPERATION_EXCLUDED,
  /** A request body, a response body, or a whole response with no supported body, is excluded from the load because its content cannot be handled. */
  BODY_EXCLUDED,
  /** A scenario is excluded from the load because a body or a response it uses is excluded. */
  SCENARIO_EXCLUDED,
  /** Two constraints apply to the same schema and only one can be honored; the keyword names the one ignored. */
  CONFLICTING_CONSTRAINTS,
  /** A numeric `format` Contracteer does not know. */
  UNKNOWN_FORMAT,
  /** A schema declares no constraint at all and accepts any value. */
  EMPTY_SCHEMA,
  /** An example key targets a status code the operation declares no response for. */
  UNRESOLVED_EXAMPLE_KEY
}
