package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.codec.ContentCodec
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.GenerationOutcome
import dev.contracteer.core.datatype.GenerationContext
import dev.contracteer.core.datatype.StringDataType
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.operation.HttpHeaderValue
import dev.contracteer.core.operation.ParameterElement
import dev.contracteer.core.operation.ParameterElement.*
import dev.contracteer.core.operation.ParameterSchema
import dev.contracteer.core.result
import dev.contracteer.core.serde.PlainTextSerde
import dev.contracteer.core.swagger.datatype.DataTypeConverter

internal class ParameterExtractor(
  private val sharedComponents: SharedComponents,
  private val dataTypeConverter: DataTypeConverter,
  private val codecFactory: CodecFactory,
  private val serdeFactory: SerdeFactory
) {

  fun extractPath(operation: Operation): Result<List<ExtractedParameterSchema>> =
    operation.safeParameters()
      .filter { it.`in` == "path" }
      .map {
        it.ensureNonBlankName().flatMap { param ->
          if (param.safeIsRequired()) success(param) else failure("Path parameter ${param.name} is required")
        }
      }
      .combineResults()
      .flatMap { parameters ->
        parameters
          .onEach { enforceNonEmptyPathParameter(it) }
          .map { it.toParameterSchema(PathParam(it.name)) }
          .combineResults()
      }

  fun extractQuery(operation: Operation): Result<List<ExtractedParameterSchema>> =
    operation.safeParameters()
      .filter { it.`in` == "query" }
      .map {
        it.ensureNonBlankName()
          .flatMap { param ->
            param.toParameterSchema(QueryParam(param.name), param.safeAllowReserved())
          }
      }
      .combineResults()

  fun extractRequestHeaders(operation: Operation): Result<List<ExtractedParameterSchema>> =
    operation.safeParameters()
      .filter { it.`in` == "header" && IGNORED_REQUEST_HEADERS.none { h -> h.equals(it.name, ignoreCase = true) } }
      .map { param ->
        param.ensureNonBlankName()
          .flatMap { param -> param.toParameterSchema(Header(param.name)) }
          .flatMap { ensureHeaderValuesAreHttpSafe(it) }
      }
      .combineResults()

  fun extractCookies(operation: Operation): Result<List<ExtractedParameterSchema>> =
    operation.safeParameters()
      .filter { it.`in` == "cookie" }
      .map {
        it.ensureNonBlankName()
          .flatMap { param -> param.toParameterSchema(Cookie(param.name)) }
      }
      .combineResults()

  fun extractResponseHeaders(response: ApiResponse): Result<List<ExtractedParameterSchema>> =
    response.safeHeaders()
      .filterKeys { !it.equals("Content-Type", ignoreCase = true) }
      .map { (name, header) ->
        if (name.isBlank()) failure("Response header has a blank name")
        else toResponseHeaderSchema(header, name).flatMap { ensureHeaderValuesAreHttpSafe(it) }
      }
      .combineResults()

  private fun Parameter.ensureNonBlankName(): Result<Parameter> =
    if (name.isNullOrBlank()) failure("Parameter has a blank name (in: $`in`)") else success(this)

  private fun enforceNonEmptyPathParameter(param: Parameter) {
    param.schema
      ?.takeIf { it.effectiveType() == "string" && (it.minLength == null || it.minLength < 1) }
      ?.apply { minLength = 1 }
  }

  private fun Parameter.toParameterSchema(
    element: ParameterElement,
    allowReserved: Boolean = false
  ): Result<ExtractedParameterSchema> =
    sharedComponents.resolve(this)
      .flatMap { resolved ->
        if (resolved.content != null && resolved.content.isNotEmpty())
          extractParameterFromContentForm(resolved, element, allowReserved)
        else
          extractParameterFromSchemaForm(resolved, element, allowReserved)
      }

  private fun extractParameterFromContentForm(parameter: Parameter,
                                              element: ParameterElement,
                                              allowReserved: Boolean): Result<ExtractedParameterSchema> {
    val (mediaTypeString, mediaTypeObj) = parameter.content.entries.first()
    val contentType = ContentType(mediaTypeString)
    return result {
      val dataType = dataTypeConverter.convertMediaTypeSchema(mediaTypeObj, reportsEmptySchema = false).bind()
      dataType.ensureNotStandaloneNull("Parameter '${parameter.name}' (in: ${parameter.`in`})").bind()
      val examples = sharedComponents.resolveExampleValues(parameter.safeExamples()).bind()

      if (dataType is AnyDataType)
        ExtractedParameterSchema(
          ParameterSchema(element,
                          dataType,
                          parameter.safeIsRequired(),
                          ContentCodec(parameter.name, PlainTextSerde, allowReserved)),
          examples)
      else {
        val serde = serdeFactory.buildSerde(contentType, mediaTypeObj, dataType).bind()
        ExtractedParameterSchema(
          ParameterSchema(element,
                          dataType,
                          parameter.safeIsRequired(),
                          ContentCodec(parameter.name, serde, allowReserved)),
          examples)
      }
    }
  }

  private fun extractParameterFromSchemaForm(parameter: Parameter,
                                             element: ParameterElement,
                                             allowReserved: Boolean): Result<ExtractedParameterSchema> =
    result {
      val dataType = dataTypeConverter.convertToDataType(parameter.schema, "").bind()
      dataType.ensureNotStandaloneNull("Parameter '${parameter.name}' (in: ${parameter.`in`})").bind()
      val examples = sharedComponents.resolveExampleValues(parameter.safeExamples()).bind()
      val codec = codecFactory
        .createCodec(element, parameter.style?.toString(), parameter.explode, dataType, parameter.name, allowReserved)
        .bind()

      ExtractedParameterSchema(ParameterSchema(element, dataType, parameter.safeIsRequired(), codec), examples)
    }

  private fun toResponseHeaderSchema(header: Header, name: String): Result<ExtractedParameterSchema> =
    sharedComponents.resolve(header)
      .flatMap { resolved ->
        if (resolved.content != null && resolved.content.isNotEmpty())
          extractResponseHeaderFromContentForm(resolved, name)
        else
          extractResponseHeaderFromSchemaForm(resolved, name)
      }

  private fun extractResponseHeaderFromContentForm(header: Header, name: String): Result<ExtractedParameterSchema> {
    val (mediaTypeString, mediaTypeObj) = header.content.entries.first()
    val contentType = ContentType(mediaTypeString)
    return result {
      val dataType = dataTypeConverter.convertMediaTypeSchema(mediaTypeObj, reportsEmptySchema = true).bind()
      dataType.ensureNotStandaloneNull("Response header '$name'").bind()
      val examples = sharedComponents.resolveExampleValues(header.safeExamples()).bind()

      val element = Header(name)
      if (dataType is AnyDataType)
        ExtractedParameterSchema(
          ParameterSchema(element, dataType, header.safeIsRequired(), ContentCodec(name, PlainTextSerde)),
          examples)
      else {
        val serde = serdeFactory.buildSerde(contentType, mediaTypeObj, dataType).bind()
        ExtractedParameterSchema(
          ParameterSchema(element, dataType, header.safeIsRequired(), ContentCodec(name, serde)),
          examples)
      }
    }
  }

  private fun extractResponseHeaderFromSchemaForm(header: Header, name: String): Result<ExtractedParameterSchema> =
    result {
      val dataType = dataTypeConverter.convertToDataType(header.schema, "").bind()
      dataType.ensureNotStandaloneNull("Response header '$name'").bind()
      val codec = codecFactory
        .createCodec(Header(name), header.style?.toString(), header.explode, dataType, name)
        .bind()
      val examples = sharedComponents.resolveExampleValues(header.safeExamples()).bind()

      ExtractedParameterSchema(ParameterSchema(Header(name), dataType, header.safeIsRequired(), codec), examples)
    }

  private fun ensureHeaderValuesAreHttpSafe(extracted: ExtractedParameterSchema): Result<ExtractedParameterSchema> {
    val schema = extracted.schema
    val name = (schema.element as? ParameterElement.Header)?.name ?: return success(extracted)
    val invalid = candidateHeaderValues(extracted)
      .firstNotNullOfOrNull { candidate -> firstInvalidHeaderChar(schema, candidate) }
    return if (invalid != null) failure(headerCharsetError(name, invalid)) else success(extracted)
  }

  private fun firstInvalidHeaderChar(schema: ParameterSchema, candidate: Any?): Char? =
    schema.codec.encode(candidate).firstNotNullOfOrNull { (_, encoded) ->
      HttpHeaderValue.firstInvalidChar(encoded)
    }

  private fun candidateHeaderValues(extracted: ExtractedParameterSchema): Sequence<Any?> {
    val examples = extracted.examples.values.filterNotNull().asSequence()
    val schemaCandidates = schemaCandidates(extracted.schema.dataType)
    return examples + schemaCandidates
  }

  private fun schemaCandidates(dataType: DataType<out Any>): Sequence<Any?> = when {
    dataType.allowedValues != null       -> dataType.allowedValues!!.asSequence()
    isHeaderSafeByConstruction(dataType) -> emptySequence()
    else                                 -> generateSequence {
      when (val result = dataType.randomValue(GenerationContext.default())) {
        is GenerationOutcome.Value    -> result.value
        is GenerationOutcome.Boundary -> null
      }
    }.take(HEADER_SAMPLE_COUNT)
  }

  private fun isHeaderSafeByConstruction(dataType: DataType<out Any>): Boolean =
    dataType is StringDataType && dataType.pattern == null && dataType.allowedValues == null

  private fun headerCharsetError(name: String, invalid: Char): String {
    val codePoint = "U+%04X".format(invalid.code)
    return "Header '$name' may carry character $codePoint not valid in HTTP header values per RFC 7230 §3.2.6. " +
           "Tighten the schema (pattern, enum) or correct example values to exclude control and non-ASCII characters."
  }

  companion object {
    private val IGNORED_REQUEST_HEADERS = setOf("Accept", "Content-Type", "Authorization")
    private const val HEADER_SAMPLE_COUNT = 50
  }
}
