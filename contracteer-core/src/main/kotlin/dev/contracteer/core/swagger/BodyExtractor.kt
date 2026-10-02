package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.BinaryDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.operation.BodySchema
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.result
import dev.contracteer.core.serde.PlainTextSerde
import dev.contracteer.core.swagger.datatype.DataTypeConverter

internal class BodyExtractor(
  private val sharedComponents: SharedComponents,
  private val dataTypeConverter: DataTypeConverter,
  private val serdeFactory: SerdeFactory
) {

  fun extractRequestBodies(operation: Operation): Result<List<ExtractedBodySchema>> =
    operation.requestBody?.let { convertRequestBodies(it) } ?: success(emptyList())

  fun extractResponseBodies(response: ApiResponse): Result<List<ExtractedBodySchema>> =
    convertBodySchemas(response.content ?: emptyMap(), isRequired = false) { it.asResponseType() }

  private fun convertRequestBodies(requestBody: RequestBody): Result<List<ExtractedBodySchema>> =
    sharedComponents.resolve(requestBody).flatMap { body ->
      convertBodySchemas(body.content ?: emptyMap(), body.safeRequired()) { it.asRequestType() }
    }

  private fun convertBodySchemas(content: Map<String, MediaType>,
                                 isRequired: Boolean,
                                 asType: (DataType<out Any>) -> DataType<out Any>): Result<List<ExtractedBodySchema>> =
    if (content.isEmpty()) success(emptyList())
    else {
      val multiContent = content.size > 1
      content
        .map { (key, mediaType) ->
          val contentType = ContentType(key)
          convertBodySchema(contentType, mediaType, isRequired, asType)
            .let { if (multiContent) it.forKey(contentType.value) else it }
        }
        .combineResults()
    }

  private fun convertBodySchema(contentType: ContentType,
                                mediaType: MediaType,
                                isRequired: Boolean,
                                asType: (DataType<out Any>) -> DataType<out Any>): Result<ExtractedBodySchema> =
    result {
      val dataType =
        if (contentType.isBinary() && (mediaType.schema == null || mediaType.schema.isAnyType()))
          BinaryDataType.create(name = "binary").bind()
        else
          dataTypeConverter.convertMediaTypeSchema(mediaType, reportsEmptySchema = false).bind()

      dataType.ensureNotStandaloneNull("Body '${contentType.value}'").bind()
      val examples = sharedComponents.resolveExampleValues(mediaType.safeExamples()).bind()
      val (bodyType, serde) =
        if (dataType is AnyDataType) dataType to PlainTextSerde
        else asType(dataType) to serdeFactory.buildSerde(contentType, mediaType, dataType).bind()
      ExtractedBodySchema(BodySchema(contentType, bodyType, isRequired, serde), examples)
    }
}
