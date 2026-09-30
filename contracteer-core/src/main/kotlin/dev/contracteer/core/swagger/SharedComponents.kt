package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Result.Success
import dev.contracteer.core.combineResults
import dev.contracteer.core.normalize

internal class SharedComponents(
  val schemas: Map<String, Schema<*>>,
  private val parameters: Map<String, Parameter>,
  private val requestBodies: Map<String, RequestBody>,
  private val headers: Map<String, Header>,
  private val examples: Map<String, Example>,
  private val responses: Map<String, ApiResponse>
) {

  fun dereference(ref: String): Result<Schema<*>> {
    if (!ref.startsWith("#/")) return unsupported(ref, "external references are not supported")

    val segments = ref.removePrefix("#/").split("/").map(::unescapePointerSegment)
    if (segments.size < 3 || segments[0] != "components") return failure(unresolvable(ref, "expected '#/components/<section>/<name>'"))

    val section = segments[1]
    val name = segments[2]
    val components = componentsFor(section)
                     ?: return unsupported(ref, "section '$section' is not supported in Contracteer")
    val start = components[name]
                ?: return failure(unresolvable(ref, "$section '$name' not found"))

    return walk(start, segments.drop(3), ref).flatMap { ensureSchema(it, ref) }
  }

  fun resolve(parameter: Parameter): Result<Parameter> =
    resolveRef(parameter, parameters, Parameter::shortRef, "Parameter", "components/parameters")

  fun resolve(requestBody: RequestBody): Result<RequestBody> =
    resolveRef(requestBody, requestBodies, RequestBody::shortRef, "Request Body", "components/requestBodies")

  fun resolve(header: Header): Result<Header> =
    resolveRef(header, headers, Header::shortRef, "Response Header", "components/headers")

  fun resolve(example: Example): Result<Example> =
    resolveRef(example, examples, Example::shortRef, "Example", "components/examples")

  fun resolve(response: ApiResponse): Result<ApiResponse> =
    resolveRef(response, responses, ApiResponse::shortRef, "Response", "components/responses")

  fun resolve(examples: Map<String, Example>): Result<Map<String, Example>> =
    examples
      .map { (key, example) -> resolve(example).map { key to it } }
      .combineResults()
      .map { it.toMap() }

  fun resolveExampleValues(examples: Map<String, Example>): Result<Map<String, Any?>> =
    resolve(examples).map { resolved -> resolved.mapValues { (_, example) -> example.value?.normalize() } }

  private fun componentsFor(section: String): Map<String, Any>? =
    when (section) {
      "schemas"    -> schemas
      "parameters" -> parameters
      "responses"  -> responses
      "headers"    -> headers
      else         -> null
    }

  private tailrec fun walk(node: Any, segments: List<String>, originalRef: String): Result<Any> {
    if (segments.isEmpty()) return success(node)
    val step = descend(node, segments, originalRef)
    if (step !is Success) return step
    return walk(step.value.node, segments.drop(step.value.consumed), originalRef)
  }

  private fun descend(node: Any, segments: List<String>, ref: String): Result<Descent> =
    when (node) {
      is Schema<*>   -> descendSchema(node, segments, ref)
      is Parameter   -> descendParameter(node, segments, ref)
      is ApiResponse -> descendResponse(node, segments, ref)
      is Header      -> descendHeader(node, segments, ref)
      is MediaType   -> descendMediaType(node, segments, ref)
      else           -> failure(unresolvable(ref, "cannot descend into '${segments.first()}'"))
    }

  private fun descendSchema(schema: Schema<*>, segments: List<String>, ref: String): Result<Descent> =
    when (val head = segments.first()) {
      "properties"              -> descendByKey(schema.properties, head, segments, ref)
      "allOf", "oneOf", "anyOf" -> descendByIndex(schema.compositionOf(head), head, segments, ref)
      "items"                   -> wrapField(schema.items, "Schema", "items", ref)
      "additionalProperties"    -> descendAdditionalProperties(schema.additionalProperties, ref)
      "not"                     -> wrapField(schema.not, "Schema", "not", ref)
      "definitions", $$"$defs"  -> unsupported(ref, "segment '$head' is not supported in Contracteer")
      else                      -> failure(unresolvable(ref, "Schema has no field '$head'"))
    }

  private fun descendParameter(parameter: Parameter, segments: List<String>, ref: String): Result<Descent> =
    when (val head = segments.first()) {
      "schema"  -> wrapField(parameter.schema, "Parameter", "schema", ref)
      "content" -> descendContent(parameter.content, segments, ref)
      else      -> failure(unresolvable(ref, "Parameter has no field '$head'"))
    }

  private fun descendResponse(response: ApiResponse, segments: List<String>, ref: String): Result<Descent> =
    when (val head = segments.first()) {
      "content" -> descendContent(response.content, segments, ref)
      "headers" -> descendByKey(response.headers, "headers", segments, ref)
      else      -> failure(unresolvable(ref, "Response has no field '$head'"))
    }

  private fun descendHeader(header: Header, segments: List<String>, ref: String): Result<Descent> =
    when (val head = segments.first()) {
      "schema" -> wrapField(header.schema, "Header", "schema", ref)
      else     -> failure(unresolvable(ref, "Header has no field '$head'"))
    }

  private fun descendMediaType(mediaType: MediaType, segments: List<String>, ref: String): Result<Descent> =
    when (val head = segments.first()) {
      "schema" -> wrapField(mediaType.schema, "MediaType", "schema", ref)
      else     -> failure(unresolvable(ref, "MediaType has no field '$head'"))
    }

  private fun descendContent(content: Content?, segments: List<String>, ref: String): Result<Descent> {
    if (content.isNullOrEmpty()) return failure(unresolvable(ref, "'content' is empty"))
    val mediaType = segments.getOrNull(1) ?: return failure(unresolvable(ref, "'content' requires a media type"))
    val media = content[mediaType] ?: return failure(unresolvable(ref, "content has no media type '$mediaType'"))
    return success(Descent(media, 2))
  }

  private fun <T : Any> descendByKey(map: Map<String, T>?, parent: String, segments: List<String>, ref: String): Result<Descent> {
    if (map.isNullOrEmpty()) return failure(unresolvable(ref, "no '$parent' available"))
    val key = segments.getOrNull(1) ?: return failure(unresolvable(ref, "'$parent' requires a key"))
    val target = map[key] ?: return failure(unresolvable(ref, "no '$parent' entry '$key'"))
    return success(Descent(target, 2))
  }

  private fun descendByIndex(list: List<Schema<*>>?, parent: String, segments: List<String>, ref: String): Result<Descent> {
    if (list.isNullOrEmpty()) return failure(unresolvable(ref, "Schema has no '$parent'"))
    val idxStr = segments.getOrNull(1) ?: return failure(unresolvable(ref, "'$parent' requires an index"))
    val idx = idxStr.toIntOrNull() ?: return failure(unresolvable(ref, "non-numeric index '$idxStr' for '$parent'"))
    if (idx !in list.indices) return failure(unresolvable(ref, "index $idx out of range for '$parent' (size ${list.size})"))
    return success(Descent(list[idx], 2))
  }

  private fun wrapField(field: Any?, typeName: String, fieldName: String, ref: String): Result<Descent> =
    if (field == null) failure(unresolvable(ref, "$typeName has no '$fieldName'"))
    else success(Descent(field, 1))

  private fun descendAdditionalProperties(value: Any?, ref: String): Result<Descent> =
    when (value) {
      null         -> failure(unresolvable(ref, "Schema has no 'additionalProperties'"))
      is Schema<*> -> success(Descent(value, 1))
      else         -> failure(unresolvable(ref, "'additionalProperties' is a boolean, not a sub-schema"))
    }

  private fun ensureSchema(node: Any, ref: String): Result<Schema<*>> =
    if (node is Schema<*>) success(node) else failure(unresolvable(ref, "target is not a Schema"))

  private fun <T> unsupported(ref: String, reason: String): Result<T> =
    failure(Diagnostic(unresolvable(ref, reason), keyword = $$"$ref", rule = UNSUPPORTED))

  private fun unresolvable(ref: String, reason: String) =
    $$"$ref '$$ref': cannot resolve JSON Pointer — $$reason"

  private fun unescapePointerSegment(segment: String) =
    segment.replace("~1", "/").replace("~0", "~")

  private fun Schema<*>.compositionOf(name: String): List<Schema<*>>? = when (name) {
    "allOf" -> allOf
    "oneOf" -> oneOf
    "anyOf" -> anyOf
    else    -> null
  }

  private fun <T> resolveRef(component: T,
                             sharedComponents: Map<String, T>,
                             getRef: (T) -> String?,
                             componentName: String,
                             section: String,
                             maxDepth: Int = MAX_DEPTH): Result<T> {
    val ref = getRef(component)
    return when {
      maxDepth < 0                               -> failure("Maximum recursive depth reached while resolving $componentName")
      ref == null                                -> success(component)
      sharedComponents[ref]?.let(getRef) != null -> resolveRef(sharedComponents[ref]!!, sharedComponents, getRef, componentName, section, maxDepth - 1)
      sharedComponents[ref] != null              -> success(sharedComponents[ref]!!)
      else                                       -> failure("$componentName '$ref' not found in '$section' section")
    }
  }

  private data class Descent(val node: Any, val consumed: Int)

  private companion object {
    private const val MAX_DEPTH = 10
  }
}
