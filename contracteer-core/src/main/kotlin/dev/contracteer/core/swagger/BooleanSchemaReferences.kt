package dev.contracteer.core.swagger

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.accumulate

// OAS 3.1 allows a boolean schema (`true`/`false`) as a $ref target, but swagger-parser's 3.1 resolver
// casts every target to an object and fails with a raw Jackson message and a logged stack trace.
// Contracteer rejects such a reference on the raw document, before the parser runs. The walk follows
// only the references the parser resolves: a reference it misses falls back to the parser's failure,
// and one the parser never resolves (a literal value, an annotation) does not fail the load.
internal fun JsonNode.rejectReferencesToBooleanSchemas(): Result<Unit> =
  structureReferences()
    .mapNotNull { ref -> (at(ref.removePrefix("#")) as? BooleanNode)?.let { ref to it.booleanValue() } }
    .distinct()
    .sortedBy { (ref, _) -> ref }
    .toList()
    .accumulate { (ref, value) -> unsupportedBooleanSchemaReference(ref, value) }

private fun JsonNode.structureReferences(): Sequence<String> =
  when {
    has(REF) -> get(REF).localReferenceOrNone()
    isObject -> properties().asSequence().flatMap { (keyword, value) -> structureReferencesUnder(keyword, value) }
    isArray  -> elements().asSequence().flatMap { it.structureReferences() }
    else     -> emptySequence()
  }

private fun structureReferencesUnder(keyword: String, value: JsonNode): Sequence<String> =
  when {
    keyword.holdsLiteralValue()                       -> emptySequence()
    keyword == "schema"                               -> value.schemaReferences()
    keyword == "schemas"                              -> value.schemaMapReferences()
    keyword == "links"                                -> value.elements().asSequence().flatMap { it.linkReference() }
    keyword == "callbacks"                            -> value.elements().asSequence().flatMap { it.callbackReferences() }
    keyword in STRUCTURE_NAME_MAPS && value.isObject  -> value.namedEntries(keyword).flatMap { it.structureReferences() }
    else                                              -> value.structureReferences()
  }

// A Link's `parameters` and `requestBody` hold literal values and runtime expressions, never resolved.
private fun JsonNode.linkReference(): Sequence<String> =
  get(REF)?.localReferenceOrNone().orEmpty()

// The parser never resolves a Callback's own `$ref`.
private fun JsonNode.callbackReferences(): Sequence<String> =
  if (has(REF)) emptySequence() else structureReferences()

private fun JsonNode.schemaReferences(): Sequence<String> =
  when {
    has(REF) -> get(REF).localReferenceOrNone()
    else     -> properties().asSequence().flatMap { (keyword, value) -> schemaReferencesUnder(keyword, value) }
  }

private fun schemaReferencesUnder(keyword: String, value: JsonNode): Sequence<String> =
  when (keyword) {
    in SUBSCHEMA_KEYWORDS       -> value.schemaReferences()
    in SUBSCHEMA_LIST_KEYWORDS  -> value.elements().asSequence().flatMap { it.schemaReferences() }
    in SUBSCHEMA_MAP_KEYWORDS   -> value.schemaMapReferences()
    else                        -> emptySequence()
  }

private fun JsonNode.schemaMapReferences(): Sequence<String> =
  elements().asSequence().flatMap { it.schemaReferences() }

// The keys of a structure map are names, not keywords. Only Paths and Responses read `x-` keys as extensions.
private fun JsonNode.namedEntries(mapKeyword: String): Sequence<JsonNode> =
  properties().asSequence()
    .filterNot { (name, _) -> mapKeyword in EXTENSIBLE_NAME_MAPS && name.isExtension() }
    .map { (_, entry) -> entry }

private fun JsonNode.localReferenceOrNone(): Sequence<String> =
  if (isTextual && asText().startsWith("#/")) sequenceOf(asText()) else emptySequence()

// A literal value can hold an object shaped like a reference; it is data, never resolved.
private fun String.holdsLiteralValue() =
  this in LITERAL_KEYWORDS || isExtension()

private fun String.isExtension() =
  startsWith("x-")

private fun unsupportedBooleanSchemaReference(ref: String, value: Boolean): Result<Unit> =
  failure(Diagnostic(
    $$"$ref '$$ref': boolean schema '$$value' is not supported in Contracteer. Use a schema object instead.",
    rule = UNSUPPORTED
  ))

private const val REF = $$"$ref"

private val LITERAL_KEYWORDS = setOf("example", "examples")

// Maps of user-chosen names to OpenAPI objects: a response named `default` is a response, not a keyword.
// `parameters` is a map only under `components`; elsewhere it is a list.
private val STRUCTURE_NAME_MAPS = setOf(
  "paths", "webhooks",
  "responses", "parameters", "requestBodies", "headers", "securitySchemes", "pathItems",
  "content", "encoding"
)
private val EXTENSIBLE_NAME_MAPS = setOf("paths", "responses")

// The schema keywords swagger-parser 2.1.41 resolves (`OpenAPI31Traverser.traverseSchema`). It leaves out
// `propertyNames`, `$defs` and unknown keywords, and `unevaluatedProperties` unless `additionalProperties`
// is also declared. Its 3.1 deserializer never reads `additionalItems`, so that keyword is never resolved.
private val SUBSCHEMA_KEYWORDS = setOf(
  "additionalProperties", "items", "not", "if", "then", "else", "contains", "contentSchema", "unevaluatedItems"
)
private val SUBSCHEMA_LIST_KEYWORDS = setOf("allOf", "anyOf", "oneOf", "prefixItems")
private val SUBSCHEMA_MAP_KEYWORDS = setOf("properties", "patternProperties", "dependentSchemas")
