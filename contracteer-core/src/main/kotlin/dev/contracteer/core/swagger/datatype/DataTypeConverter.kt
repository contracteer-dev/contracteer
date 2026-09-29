package dev.contracteer.core.swagger.datatype

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Result.Success
import dev.contracteer.core.datatype.*
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.swagger.*

internal class DataTypeConverter(private val sharedComponents: SharedComponents,
                                 private val warnings: LoadWarnings) {

  private val logger = KotlinLogging.logger {}
  private val dataTypeCache: MutableMap<String, DataType<out Any>> = mutableMapOf()
  private val refsBeingResolved: MutableMap<String, ProxyDataType> = mutableMapOf()
  private val refsBeingMerged: MutableSet<String> = linkedSetOf()
  private val discriminatorCache: MutableMap<String, Discriminator> = mutableMapOf()

  init {
    addDiscriminators(sharedComponents.schemas)
  }

  fun convertToDataType(schema: Schema<*>, defaultName: String): Result<DataType<out Any>> {
    val ref = schema.`$ref` ?: return convertSchema(schema, defaultName)
    val siblings = schema.effectiveNonAnnotationSiblings()
    val cached = dataTypeCache[ref]
    val proxy = refsBeingResolved[ref]
    return when {
      siblings.isNotEmpty() -> convertRefWithSiblings(schema, ref, defaultName)
      cached != null        -> success(cached).also { logger.debug { "DataType already cached for Schema '$ref'" } }
      proxy != null         -> success(proxy).also { logger.debug { "Circular reference detected for Schema '$ref', returning proxy" } }
      else                  -> resolveReferencedSchema(ref)
    }
  }

  private fun convertRefWithSiblings(schema: Schema<*>, ref: String, defaultName: String): Result<DataType<out Any>> {
    if (!refsBeingMerged.add(ref)) return cycleError(ref)
    return sharedComponents
      .dereference(ref)
      .flatMap { resolved -> mergeSchemaAndSiblings(resolved, schema, displayName(ref)) }
      .flatMap { merged -> convertToDataType(merged, defaultName) }
      .also { refsBeingMerged.remove(ref) }
  }

  private fun cycleError(ref: String): Result<DataType <out Any>> {
    val path = (refsBeingMerged.toList() + ref).joinToString(" → ", transform = ::displayName)
    return failure($$"Schema '$${displayName(ref)}': '$ref' with sibling keywords forms a cycle ($$path). Cyclic merges cannot be resolved.")

  }

  fun convertMediaTypeSchema(mediaType: MediaType): Result<DataType<out Any>> =
    if (mediaType.schema == null) success(AnyDataType)
    else convertToDataType(mediaType.schema, "")

  fun convertToDiscriminator(schema: Schema<*>) =
    if (schema.`$ref` != null) discriminatorCache[schema.`$ref`]
    else schema.discriminator?.let {
      Discriminator(
        it.propertyName,
        it.safeMapping().mapValues { mapping -> mapping.value.replace(Components.COMPONENTS_SCHEMAS_REF, "") }
      )
    }

  private fun resolveReferencedSchema(ref: String): Result<DataType<out Any>> {
    val name = displayName(ref)
    val proxy = ProxyDataType(name)
    refsBeingResolved[ref] = proxy

    return sharedComponents
      .dereference(ref)
      .flatMap { resolved -> convertToDataType(resolved, name) }
      .also { refsBeingResolved.remove(ref) }
      .flatMap { dataType ->
        proxy.delegate = dataType
        validateNoInfiniteCycle(proxy, ref, dataType)
      }
  }

  private fun displayName(ref: String): String =
    ref.removePrefix(Components.COMPONENTS_SCHEMAS_REF)

  private fun convertSchema(schema: Schema<*>,
                            schemaName: String,
                            localRequiredOnly: Boolean = false): Result<DataType<out Any>> {
    schema.name = schemaName
    logger.debug { "Creating Datatype for Schema '${schema.name}'" }
    val convert = { s: Schema<*>, name: String -> convertToDataType(s, name) }
    val discriminator = { s: Schema<*> -> convertToDiscriminator(s) }
    val type = schema.effectiveType()
    val contentEncoding = schema.effectiveContentEncoding()
    val contentMediaType = schema.effectiveContentMediaType()?.let(::ContentType)
    val unsupportedFeature = schema.unsupportedFeature()
    return when {
      schema.hasComposition()                                       -> convertComposedSchema(schema, convert, discriminator)
      unsupportedFeature != null                                    -> unsupported(schema, unsupportedFeature.description, unsupportedFeature.suggestion)
      contentEncoding == "base64"                                   -> Base64DataTypeConverter.convert(schema, warnings)
      contentEncoding != null                                       -> unsupportedContentEncoding(schema, contentEncoding)
      contentMediaType?.isBinary() == true                          -> BinaryDataTypeConverter.convert(schema, warnings)
      schema.isObjectLike()                                         -> ObjectDataTypeConverter.convert(schema, convert, warnings, localRequiredOnly)
      schema.isArrayLike()                                          -> ArrayDataTypeConverter.convert(schema, convert)
      type == "boolean"                                             -> BooleanDataTypeConverter.convert(schema)
      type == "integer"                                             -> IntegerDataTypeConverter.convert(schema, warnings)
      type == "number"                                              -> NumberDataTypeConverter.convert(schema, warnings)
      schema.hasStructuredTextContent()                             -> unsupportedStructuredTextContentMediaType(schema)
      type == "string"                                              -> convertStringSchema(schema)
      schema.isNullOnly()                                           -> success(NullDataType)
      else                                                          -> tryToInferSchemaType(schema)
    }
  }

  private data class UnsupportedFeature(val description: String, val suggestion: String? = null)

  private fun Schema<*>.unsupportedFeature(): UnsupportedFeature? =
    when {
      booleanSchemaValue() != null -> UnsupportedFeature("boolean schema '${booleanSchemaValue()}'", "Use a schema object instead.")
      hasNonNullableMultiType()    -> UnsupportedFeature("non-nullable multi-type 'types: $types'", "Use 'oneOf' or 'anyOf' to express a union of types.")
      hasPrefixItems()             -> UnsupportedFeature("'prefixItems'", "Use 'items' if all positions share a single type.")
      hasContains()                -> UnsupportedFeature("'contains/minContains/maxContains'")
      hasConditional()             -> UnsupportedFeature("'if/then/else'")
      hasNot()                     -> UnsupportedFeature("'not'")
      hasUnevaluatedProperties()   -> UnsupportedFeature("'unevaluatedProperties'")
      hasUnevaluatedItems()        -> UnsupportedFeature("'unevaluatedItems'")
      hasPatternProperties()       -> UnsupportedFeature("'patternProperties'")
      hasDependentRequired()       -> UnsupportedFeature("'dependentRequired'")
      hasDependentSchemas()        -> UnsupportedFeature("'dependentSchemas'")
      hasContentSchema()           -> UnsupportedFeature("'contentSchema'")
      else                         -> null
    }

  private fun convertStringSchema(schema: Schema<*>): Result<DataType<out Any>> =
    when (schema.format) {
      "date"          -> DateDataTypeConverter.convert(schema, warnings)
      "date-time"     -> DateTimeDataTypeConverter.convert(schema, warnings)
      "email"         -> EmailDataTypeConverter.convert(schema, warnings)
      "uuid"          -> UuidDataTypeConverter.convert(schema, warnings)
      "binary"        -> BinaryDataTypeConverter.convert(schema, warnings)
      "byte"          -> Base64DataTypeConverter.convert(schema, warnings)
      "password"      -> StringDataTypeConverter.convert(schema, "string/password", warnings)
      "hostname"      -> HostnameDataTypeConverter.convert(schema, warnings)
      "uri"           -> UriDataTypeConverter.convert(schema, warnings)
      "uri-reference" -> UriReferenceDataTypeConverter.convert(schema, warnings)
      else            -> StringDataTypeConverter.convert(schema, "string", warnings)
    }

  private fun convertComposedSchema(schema: Schema<*>,
                                    convert: (Schema<*>, String) -> Result<DataType<out Any>>,
                                    discriminator: (Schema<*>) -> Discriminator?): Result<DataType<out Any>> {
    val keywords = listOfNotNull(
      if (schema.allOf != null) "allOf" else null,
      if (schema.anyOf != null) "anyOf" else null,
      if (schema.oneOf != null) "oneOf" else null,
    )

    if (keywords.size > 1)
      return failure("Schema '${schema.name}' combines multiple composition keywords (${keywords.joinToString(", ")}). Only one of 'allOf', 'anyOf', or 'oneOf' per schema is supported.")

    if (schema.allOf != null)
      return AllOfDataTypeConverter.convert(schema, convert, ::convertAllOfBranch, discriminator, warnings)

    val compositionResult =
      if (schema.anyOf != null) AnyOfDataTypeConverter.convert(schema, convert, discriminator)
      else OneOfDataTypeConverter.convert(schema, convert, discriminator)

    val siblingResult = ObjectDataTypeConverter.convertSiblingObject(schema, convert, warnings)
                        ?: return compositionResult

    if (compositionResult !is Success || siblingResult !is Success)
      return (compositionResult combineWith siblingResult).retypeError()

    return AllOfDataType.create(
      name = schema.name,
      subTypes = listOf(compositionResult.value, siblingResult.value))
  }

  private fun convertAllOfBranch(schema: Schema<*>, schemaName: String): Result<DataType<out Any>> =
    convertSchema(schema, schemaName, localRequiredOnly = true)

  private fun unsupportedContentEncoding(schema: Schema<*>, value: String): Result<DataType<out Any>> =
    failure("Schema '${schema.name}': contentEncoding='$value' is not supported. Only 'base64' is supported in OAS 3.1.")

  private fun unsupported(schema: Schema<*>, what: String, suggestion: String? = null): Result<DataType<out Any>> {
    val base = "Schema '${schema.name}': $what is not supported in Contracteer."
    return failure(if (suggestion != null) "$base $suggestion" else base)
  }

  private fun unsupportedStructuredTextContentMediaType(schema: Schema<*>): Result<DataType<out Any>> =
    failure("Schema '${schema.name}': contentMediaType='${schema.effectiveContentMediaType()}' is not yet supported in Contracteer.")

  private fun tryToInferSchemaType(schema: Schema<*>): Result<DataType<out Any>> =
    if (schema.isAnyType())
      success(AnyDataType).also { warnings.warn("Schema '${schema.name}' is empty (anyType) and will be interpreted as accepting any type.") }
    else
      failure("Error while interpreting schema '${schema.name}'. The schema might be misconfigured or incomplete.")

  private fun validateNoInfiniteCycle(proxy: ProxyDataType, ref: String, dataType: DataType<out Any>): Result<DataType<out Any>> {
    val cyclePath = findNonBreakablePath(proxy.delegate, proxy, listOf(proxy.name))
    return when {
      cyclePath != null ->
        failure("Circular reference with no optional, nullable, or collection exit point: ${cyclePath.joinToString(" → ")}")

      else              -> {
        dataTypeCache[ref] = dataType
        success(dataType)
      }
    }
  }

  private fun findNonBreakablePath(current: DataType<out Any>,
                                   target: ProxyDataType,
                                   path: List<String>): List<String>? =
    when (current) {
      is ProxyDataType  -> followProxy(current, target, path)
      is ObjectDataType -> followRequiredNonNullableProperties(current, target, path)
      is AllOfDataType  -> followAllOfSubTypes(current, target, path)
      else              -> null
    }

  private fun followProxy(current: ProxyDataType, target: ProxyDataType, path: List<String>): List<String>? =
    when {
      current === target  -> path + current.name
      !current.isResolved -> null
      else                -> findNonBreakablePath(current.delegate, target, path)
    }

  private fun followRequiredNonNullableProperties(current: ObjectDataType,
                                                  target: ProxyDataType,
                                                  path: List<String>): List<String>? =
    current.properties
      .filter { (name, _) -> name in current.requiredProperties }
      .filterValues { it.isNonBreakableEdge() }
      .entries
      .firstNotNullOfOrNull { (name, dataType) -> findNonBreakablePath(dataType, target, path + name) }

  private fun followAllOfSubTypes(current: AllOfDataType, target: ProxyDataType, path: List<String>): List<String>? =
    current.subTypes.firstNotNullOfOrNull { findNonBreakablePath(it, target, path) }

  private fun DataType<out Any>.isNonBreakableEdge() =
    (this is ProxyDataType && !isResolved) || !isNullable

  @Suppress("UNCHECKED_CAST")
  private fun addDiscriminators(schemas: Map<String, Schema<*>>) {
    discriminatorCache.putAll(
      schemas
        .map { (name, schema) -> "${Components.COMPONENTS_SCHEMAS_REF}$name" to convertToDiscriminator(schema) }
        .filter { it.second != null }
        .toMap() as Map<String, Discriminator>
    )
  }
}
