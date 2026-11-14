package dev.contracteer.core.swagger

import com.fasterxml.jackson.databind.JsonNode
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.accumulate

// swagger-parser drops every field beside a path item `$ref` except `summary` and `description` when it reads the
// document, operations included, so the model cannot tell they were there. Contracteer rejects them on the raw
// document, before the parser runs, both under `paths` and in `components/pathItems`.
// `summary`, `description` and `servers` are allowed: Contracteer reads none of them, so dropping them loses nothing.
// A `$ref` that is not a string is rejected here too: the parser turns that path item into null.
internal fun JsonNode.rejectUnsupportedPathItemReferences(): Result<Unit> =
  (pathItemsUnderPaths() + pathItemComponents())
    .filter { (_, pathItem) -> pathItem.has(REF) }
    .accumulate { (location, pathItem) -> pathItem.unsupportedReference(location) }

private fun JsonNode.pathItemsUnderPaths(): List<Pair<String, JsonNode>> =
  path("paths").properties()
    .filterNot { (path, _) -> path.isExtension() }
    .map { (path, pathItem) -> path to pathItem }

private fun JsonNode.pathItemComponents(): List<Pair<String, JsonNode>> =
  path("components").path("pathItems").properties()
    .map { (name, pathItem) -> "components/pathItems/$name" to pathItem }

private fun JsonNode.unsupportedReference(location: String): Result<Unit> =
  (nonStringReference() + unsupportedSiblings())
    .accumulate { (keyword, message) -> unsupported(location, keyword, message) }

private fun JsonNode.nonStringReference(): List<Pair<String, String>> =
  when {
    get(REF).isTextual -> emptyList()
    else               -> listOf(REF to $$"A path item '$ref' must be a string.")
  }

private fun JsonNode.unsupportedSiblings(): List<Pair<String, String>> =
  properties().map { (field, _) -> field }
    .filterNot { it in ALLOWED_SIBLINGS || it.isExtension() }
    .map { field ->
      field to $$"'$$field' beside a path item '$ref' is not supported in Contracteer. Declare it in the referenced path item instead."
    }

private fun unsupported(location: String, keyword: String, message: String): Result<Unit> =
  failure(Diagnostic(message, location = location, keyword = keyword, rule = UNSUPPORTED))

private fun String.isExtension() =
  startsWith("x-")

private const val REF = $$"$ref"

private val ALLOWED_SIBLINGS = setOf(REF, "summary", "description", "servers")
