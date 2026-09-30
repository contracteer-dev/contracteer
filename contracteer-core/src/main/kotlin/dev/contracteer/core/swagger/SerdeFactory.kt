package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.media.Encoding
import io.swagger.v3.oas.models.media.MediaType
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSERIALIZABLE_CONTENT
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.accumulate
import dev.contracteer.core.codec.DecodeView
import dev.contracteer.core.codec.EncodingShape
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.Base64DataType
import dev.contracteer.core.datatype.BinaryDataType
import dev.contracteer.core.datatype.CompositeDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.operation.ParameterElement.QueryParam
import dev.contracteer.core.serde.*

internal class SerdeFactory(private val codecFactory: CodecFactory) {

  fun buildSerde(contentType: ContentType,
                 mediaType: MediaType,
                 dataType: DataType<out Any>): Result<Serde> =
    EncodingShape.of(dataType).flatMap { shape ->
      val shapeIsStructural = shape is EncodingShape.Object || shape is EncodingShape.Array
      when {
        contentType.isFormUrlEncoded() -> requireObjectShape(contentType, shape) { buildFormUrlEncodedSerde(it, mediaType) }
        contentType.isMultipart()      -> requireObjectShape(contentType, shape) { buildMultipartSerde(it, mediaType) }
        contentType.isJson()           -> success(JsonSerde)
        contentType.isXml()            -> success(PlainTextSerde)
        shapeIsStructural              -> unserializable("Content type ${contentType.value} supports only primitive schemas (string, integer, number, boolean and their formats)")
        else                           -> success(PlainTextSerde)
      }
    }

  private fun requireObjectShape(contentType: ContentType,
                                 shape: EncodingShape,
                                 build: (EncodingShape.Object) -> Result<Serde>): Result<Serde> =
    if (shape is EncodingShape.Object) build(shape)
    else unserializable("Content type ${contentType.value} requires object schema")

  private fun buildFormUrlEncodedSerde(shape: EncodingShape.Object, mediaType: MediaType): Result<Serde> =
    validateFormUrlEncodedProperties(shape.properties).flatMap {
      val encodingMap = mediaType.encoding ?: emptyMap()
      shape.properties
        .map { (name, view) -> buildPropertyEncodingEntry(name, view, encodingMap[name]) }
        .combineResults()
        .map<Serde> { FormUrlEncodedSerde(it.toMap()) }
    }

  private fun buildPropertyEncodingEntry(name: String,
                                         view: DecodeView,
                                         encoding: Encoding?): Result<Pair<String, PropertyEncoding>> {
    val allowReserved = encoding?.allowReserved == true
    return codecFactory
      .createCodec(QueryParam(name), encoding?.style?.toString(), encoding?.explode, view.source, name)
      .map { name to PropertyEncoding(it, view, allowReserved) }
  }

  private fun validateFormUrlEncodedProperties(properties: Map<String, DecodeView>): Result<Unit> =
    properties.entries.accumulate { (name, view) -> validateFormUrlEncodedProperty(name, view) }

  private fun validateFormUrlEncodedProperty(name: String, view: DecodeView): Result<Unit> =
    view.shape().flatMap { shape ->
      when (shape) {
        is EncodingShape.Object -> unserializableProperty(name, "Form-urlencoded does not support nested object properties $UNDEFINED_BEHAVIOR")
        is EncodingShape.Array  -> rejectIfArrayOfComplexItems(name, shape)
        else                    -> success()
      }
    }

  private fun rejectIfArrayOfComplexItems(name: String, array: EncodingShape.Array): Result<Unit> =
    array.itemType.shape().flatMap { itemShape ->
      if (itemShape is EncodingShape.Object || itemShape is EncodingShape.Array)
        unserializableProperty(name, "Form-urlencoded does not support arrays of complex types (item type: '${array.itemType.source.openApiType}') $UNDEFINED_BEHAVIOR")
      else success()
    }

  private fun buildMultipartSerde(shape: EncodingShape.Object, mediaType: MediaType): Result<Serde> {
    val encodingMap = mediaType.encoding ?: emptyMap()
    return shape.properties.entries
      .map { (name, view) -> buildPartConfigEntry(name, view, encodingMap[name]?.contentType) }
      .combineResults()
      .map<Serde> { MultipartSerde(it.toMap()) }
  }

  private fun buildPartConfigEntry(name: String,
                                   view: DecodeView,
                                   explicitContentType: String?): Result<Pair<String, PartConfig>> =
    view.shape().flatMap { shape ->
      val isFileArray = (shape as? EncodingShape.Array)?.itemType?.source?.isBinary() == true
      resolveContentType(name, view, shape, isFileArray, explicitContentType)
        .map { contentType -> name to toPartConfig(contentType, view, isFileArray) }
    }

  private fun resolveContentType(name: String,
                                 view: DecodeView,
                                 shape: EncodingShape,
                                 isFileArray: Boolean,
                                 explicit: String?): Result<String> =
    if (explicit != null) success(explicit)
    else defaultPartContentType(name, view, shape, isFileArray)

  private fun toPartConfig(contentType: String, view: DecodeView, isFileArray: Boolean): PartConfig {
    val isFile = view.source.isBinary()
    return PartConfig(contentType, serdeForContentType(contentType), view, isFile || isFileArray, isFileArray)
  }

  private fun defaultPartContentType(name: String,
                                     view: DecodeView,
                                     shape: EncodingShape,
                                     isFileArray: Boolean): Result<String> =
    when {
      view.source.isBinary() || isFileArray                         -> success("application/octet-stream")
      shape is EncodingShape.Mixed                                  -> unserializableProperty(name, "Cannot determine default content type for multipart part: schema branches have incompatible shapes. Specify 'contentType' explicitly in the encoding to resolve.")
      shape is EncodingShape.Array || shape is EncodingShape.Object -> success("application/json")
      else                                                          -> success("text/plain")
    }

  private fun serdeForContentType(contentType: String): Serde =
    if ("json" in contentType.lowercase()) JsonSerde else PlainTextSerde

  private fun <T> unserializableProperty(name: String, message: String): Result<T> =
    unserializable<T>(message).forProperty(name)

  private fun <T> unserializable(message: String): Result<T> =
    failure(Diagnostic(message, rule = UNSERIALIZABLE_CONTENT))
}

private fun DataType<out Any>.isBinary(): Boolean = when (this) {
  is BinaryDataType, is Base64DataType -> true
  is CompositeDataType<*>              -> subTypes.all { it.isBinary() }
  else                                 -> false
}

private const val UNDEFINED_BEHAVIOR = "(undefined behavior in the OpenAPI document)"