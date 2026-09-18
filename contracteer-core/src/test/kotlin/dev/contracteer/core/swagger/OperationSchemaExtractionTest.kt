package dev.contracteer.core.swagger

import dev.contracteer.core.assertSuccess
import dev.contracteer.core.assertSingle
import dev.contracteer.core.codec.ContentCodec
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.datatype.*
import dev.contracteer.core.operation.ParameterElement
import dev.contracteer.core.serde.JsonSerde
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import kotlin.test.Test

class OperationSchemaExtractionTest {

  @Test
  fun `extracts request parameter schemas`() {
    // when
    val operation = loadSingleOperation("schema_without_examples.yaml")

    // then
    val pathParam = operation.requestSchema.pathParameters.single()
    assert(pathParam.element == ParameterElement.PathParam("id"))
    assert(pathParam.isRequired)
    assert(pathParam.dataType is IntegerDataType)

    val queryParam = operation.requestSchema.queryParameters.single()
    assert(queryParam.element == ParameterElement.QueryParam("query"))
    assert(!queryParam.isRequired)
    assert(queryParam.dataType is StringDataType)

    val headerParam = operation.requestSchema.headers.single()
    assert(headerParam.element == ParameterElement.Header("header"))
    assert(headerParam.isRequired)
    assert(headerParam.dataType is StringDataType)

    val cookieParam = operation.requestSchema.cookies.single()
    assert(cookieParam.element == ParameterElement.Cookie("cookie"))
    assert(!cookieParam.isRequired)
    assert(cookieParam.dataType is StringDataType)
  }

  @Test
  fun `extracts request body schema`() {
    // when
    val operation = loadSingleOperation("schema_without_examples.yaml")

    // then
    val requestBody = operation.requestSchema.bodies.single()
    assert(requestBody.contentType == ContentType("application/json"))
    assert(requestBody.dataType is ObjectDataType)
    assert((requestBody.dataType as ObjectDataType).properties["prop1"] is StringDataType)
  }

  @Test
  fun `extracts non required request body when not specified`() {
    // when
    val operation = loadSingleOperation("schema_without_examples.yaml")

    // then
    assert(!operation.requestSchema.bodies.single().isRequired)
  }

  @Test
  fun `extracts required request body`() {
    // when
    val operation = loadSingleOperation("required_request_body.yaml")

    // then
    assert(operation.requestSchema.bodies.single().isRequired)
  }

  @Test
  fun `extracts response header schemas`() {
    // when
    val operation = loadSingleOperation("schema_without_examples.yaml")

    // then
    val responseSchema = operation.responseSchemas.responseFor(200)!!
    val headers = responseSchema.headers.associateBy { it.element }
    assert(headers.size == 2)
    assert(!headers[ParameterElement.Header("x-optional")]!!.isRequired)
    assert(headers[ParameterElement.Header("x-optional")]!!.dataType is IntegerDataType)
    assert(headers[ParameterElement.Header("x-required")]!!.isRequired)
    assert(headers[ParameterElement.Header("x-required")]!!.dataType is IntegerDataType)
  }

  @Test
  fun `ignores Content-Type Accept and Authorization in request headers`() {
    // when
    val operation = loadSingleOperation("ignored_headers.yaml")

    // then
    val headers = operation.requestSchema.headers
    assert(headers.size == 1)
    assert(headers.single().element == ParameterElement.Header("x-request-custom"))
  }

  @Test
  fun `extracts response header declared with content application json`() {
    // when
    val operation = loadSingleOperation("response_header_content_json.yaml")

    // then
    val header = operation.responseSchemas.responseFor(200)!!.headers.single()
    assert(header.element == ParameterElement.Header("X-Pagination"))
    assert(header.isRequired)
    assert(header.dataType is ObjectDataType)
    assert(header.codec is ContentCodec)
    assert((header.codec as ContentCodec).serde is JsonSerde)
  }

  @Test
  fun `ignores Content-Type in response headers`() {
    // when
    val operation = loadSingleOperation("ignored_headers.yaml")

    // then
    val headers = operation.responseSchemas.responseFor(200)!!.headers
    assert(headers.size == 1)
    assert(headers.single().element == ParameterElement.Header("x-response-custom"))
  }

  @Test
  fun `extracts response body schema`() {
    // when
    val operation = loadSingleOperation("schema_without_examples.yaml")

    // then
    val responseSchema = operation.responseSchemas.responseFor(200)!!
    val responseBody = responseSchema.bodies.single()
    assert(responseBody.contentType == ContentType("application/json"))
    assert(responseBody.dataType is ObjectDataType)
    val properties = (responseBody.dataType as ObjectDataType).properties
    assert(properties["id"] is IntegerDataType)
    assert(properties["name"] is StringDataType)
  }

  @Test
  fun `extracts 400 response schema`() {
    // when
    val operation = loadSingleOperation("with_400_response_schema.yaml")

    // then
    val successSchema = operation.responseSchemas.responseFor(201)!!
    assert(successSchema.bodies.single().contentType == ContentType("application/json"))
    assert(successSchema.bodies.single().dataType is ObjectDataType)

    val errorSchema = operation.responseSchemas.badRequestResponse()!!
    assert(errorSchema.bodies.single().contentType == ContentType("application/json"))
    val errorProperties = (errorSchema.bodies.single().dataType as ObjectDataType).properties
    assert(errorProperties["error"] is StringDataType)
    assert(errorProperties["details"] is StringDataType)
  }

  @Test
  fun `resolves references for parameters request bodies response headers and response bodies`() {
    // when
    val operation = loadSingleOperation("references_30.yaml")

    // then
    assert(operation.path == "/products/{id}")
    assert(operation.method == "GET")

    val pathParam = operation.requestSchema.pathParameters.single()
    assert(pathParam.element == ParameterElement.PathParam("id"))
    assert(pathParam.isRequired)
    assert(pathParam.dataType is IntegerDataType)

    val requestBody = operation.requestSchema.bodies.single()
    assert(requestBody.contentType == ContentType("application/json"))
    assert(requestBody.dataType is ObjectDataType)
    assert((requestBody.dataType as ObjectDataType).properties["prop1"] is StringDataType)

    val responseHeaders = operation.responseSchemas.responseFor(200)!!.headers.associateBy { it.element }
    assert(!responseHeaders[ParameterElement.Header("x-optional")]!!.isRequired)
    assert(responseHeaders[ParameterElement.Header("x-optional")]!!.dataType is IntegerDataType)

    val responseBody = operation.responseSchemas.responseFor(200)!!.bodies.single()
    assert(responseBody.contentType == ContentType("application/json"))
    assert(responseBody.dataType is ObjectDataType)
    val responseProperties = (responseBody.dataType as ObjectDataType).properties
    assert(responseProperties.keys == setOf("id", "name", "quantity"))
  }

  @Test
  fun `resolves 3 1 operation-level parameter refs for all parameter kinds`() {
    // when
    val operation = loadSingleOperation("operation_level_refs_31.yaml")

    // then
    val pathParam = operation.requestSchema.pathParameters.single()
    assert(pathParam.element == ParameterElement.PathParam("id"))
    assert(pathParam.dataType is IntegerDataType)

    val queryParam = operation.requestSchema.queryParameters.single()
    assert(queryParam.element == ParameterElement.QueryParam("filter"))

    val headerParam = operation.requestSchema.headers.single()
    assert(headerParam.element == ParameterElement.Header("x-trace"))

    val cookieParam = operation.requestSchema.cookies.single()
    assert(cookieParam.element == ParameterElement.Cookie("session"))
  }

  @Test
  fun `resolves $ref into a parameter's schema sub-element`() {
    // when
    val operation = loadSingleOperation("ref_into_parameter_schema.yaml")

    // then
    val requestBody = operation.requestSchema.bodies.single()
    val accountId = (requestBody.dataType as ObjectDataType).properties["accountId"]
    assert(accountId is IntegerDataType)
  }

  @Test
  fun `resolves $ref into an allOf member of a schema`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_allof_member.yaml")

    // then
    val requestBody = operation.requestSchema.bodies.single()
    val amount = (requestBody.dataType as ObjectDataType).properties["amount"]
    assert(amount is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a schema property`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_property.yaml")

    // then
    val age = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["age"]
    assert(age is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a schema items`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_items.yaml")

    // then
    val count = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["count"]
    assert(count is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a schema additionalProperties`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_additional_properties.yaml")

    // then
    val value = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["value"]
    assert(value is StringDataType)
  }

  @Test
  fun `resolves $ref into a oneOf member of a schema`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_oneof_member.yaml")

    // then
    val tag = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["tag"]
    assert(tag is StringDataType)
  }

  @Test
  fun `resolves $ref into an anyOf member of a schema`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_anyof_member.yaml")

    // then
    val code = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["code"]
    assert(code is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a parameter content media-type schema`() {
    // when
    val operation = loadSingleOperation("ref_into_parameter_content_schema.yaml")

    // then
    val filter = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["filter"]
    assert(filter is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a response content media-type schema`() {
    // when
    val operation = loadSingleOperation("ref_into_response_content_schema.yaml")

    // then
    val forbiddenCode = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["forbiddenCode"]
    assert(forbiddenCode is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a response header schema`() {
    // when
    val operation = loadSingleOperation("ref_into_response_header_schema.yaml")

    // then
    val traceId = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["traceId"]
    assert(traceId is IntegerDataType)
  }

  @Test
  fun `resolves $ref into a header schema`() {
    // when
    val operation = loadSingleOperation("ref_into_header_schema.yaml")

    // then
    val trace = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["trace"]
    assert(trace is IntegerDataType)
  }

  @Test
  fun `resolves chained JSON Pointer segments through schema fields`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_composed_pointer.yaml")

    // then
    val tag = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["tag"]
    assert(tag is IntegerDataType)
  }

  @Test
  fun `resolves $ref into an allOf member of a schema in 3 1 spec`() {
    // when
    val operation = loadSingleOperation("ref_into_schema_allof_member_31.yaml")

    // then
    val amount = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["amount"]
    assert(amount is IntegerDataType)
  }

  @Test
  fun `resolves a $ref whose nested-pointer target is itself a $ref`() {
    // when
    val operation = loadSingleOperation("ref_chain_through_nested_pointer.yaml")

    // then
    val nested = ((operation.requestSchema.bodies.single().dataType) as ObjectDataType).properties["nested"]
    assert(nested is IntegerDataType)
  }

  @Test
  fun `resolves references in 3 1 spec for parameters request bodies response headers and response bodies`() {
    // when
    val operation = loadSingleOperation("references_31.yaml")

    // then
    assert(operation.path == "/products/{id}")
    assert(operation.method == "GET")

    val pathParam = operation.requestSchema.pathParameters.single()
    assert(pathParam.element == ParameterElement.PathParam("id"))
    assert(pathParam.isRequired)
    assert(pathParam.dataType is IntegerDataType)

    val requestBody = operation.requestSchema.bodies.single()
    assert(requestBody.contentType == ContentType("application/json"))
    assert(requestBody.dataType is ObjectDataType)
    assert((requestBody.dataType as ObjectDataType).properties["prop1"] is StringDataType)

    val responseHeaders = operation.responseSchemas.responseFor(200)!!.headers.associateBy { it.element }
    assert(!responseHeaders[ParameterElement.Header("x-optional")]!!.isRequired)
    assert(responseHeaders[ParameterElement.Header("x-optional")]!!.dataType is IntegerDataType)

    val responseBody = operation.responseSchemas.responseFor(200)!!.bodies.single()
    assert(responseBody.contentType == ContentType("application/json"))
    assert(responseBody.dataType is ObjectDataType)
    val responseProperties = (responseBody.dataType as ObjectDataType).properties
    assert(responseProperties.keys == setOf("id", "name", "quantity"))
  }

  @Test
  fun `extracts multiple request and response content types`() {
    // when
    val operation = loadSingleOperation("multiple_content_types.yaml")

    // then
    val requestContentTypes = operation.requestSchema.bodies.map { it.contentType.value }.toSet()
    assert(requestContentTypes == setOf("application/json", "application/vnd.mycompany.myapp.v2+json"))

    val responseContentTypes = operation.responseSchemas.responseFor(201)!!.bodies.map { it.contentType.value }.toSet()
    assert(responseContentTypes == setOf("application/json", "application/vnd.mycompany.myapp.v2+json"))
  }

  @Test
  fun `accepts JSON content type with object and array body schema`() {
    // when
    val operations = loadOperations("json_body_accepts_object_and_array.yaml")

    // then
    assert(operations.size == 2)
    val objectOperation = operations.first { it.path == "/products/{id}" }
    assert(objectOperation.responseSchemas.responseFor(200)!!.bodies.single().dataType is ObjectDataType)

    val arrayOperation = operations.first { it.path == "/products" }
    assert(arrayOperation.responseSchemas.responseFor(200)!!.bodies.single().dataType is ArrayDataType)
  }

  @Test
  fun `accepts JSON content type with primitive body schema`() {
    // when
    val operations = loadOperations("json_body_accepts_primitive.yaml")

    // then
    val stringBody = operations.first { it.path == "/products/name" }
      .responseSchemas.responseFor(200)!!.bodies.single()
    assert(stringBody.dataType is StringDataType)
    assert(stringBody.serde === JsonSerde)

    val integerBody = operations.first { it.path == "/products/count" }
      .responseSchemas.responseFor(200)!!.bodies.single()
    assert(integerBody.dataType is IntegerDataType)
    assert(integerBody.serde === JsonSerde)

    val booleanBody = operations.first { it.path == "/products/in-stock" }
      .responseSchemas.responseFor(200)!!.bodies.single()
    assert(booleanBody.dataType is BooleanDataType)
    assert(booleanBody.serde === JsonSerde)
  }

  @Test
  fun `rejects text plain body with composite of arrays schema`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/operation/text_plain_composite_array_body.yaml")

    // then
    assert(result.isFailure())
    assert(result.errors().any { it.contains("text/plain") && it.contains("primitive") })
  }

  @Test
  fun `extracts readOnly and writeOnly properties into request and response variants`() {
    // when
    val operation = loadSingleOperation("readonly_writeonly.yaml")
    val requestBody = operation.requestSchema.bodies.single().dataType as ObjectDataType
    val responseBody = operation.responseSchemas.responseFor(201)!!.bodies.single().dataType as ObjectDataType

    // then — request body excludes readOnly, includes writeOnly
    assert(!requestBody.properties.containsKey("id"))
    assert(requestBody.properties.containsKey("name"))
    assert(requestBody.properties.containsKey("password"))
    assert(!requestBody.requiredProperties.contains("id"))
    assert(requestBody.requiredProperties.contains("name"))
    assert(requestBody.requiredProperties.contains("password"))

    // then — response body includes readOnly, excludes writeOnly
    assert(responseBody.properties.containsKey("id"))
    assert(responseBody.properties.containsKey("name"))
    assert(!responseBody.properties.containsKey("password"))
    assert(responseBody.requiredProperties.contains("id"))
    assert(responseBody.requiredProperties.contains("name"))
    assert(!responseBody.requiredProperties.contains("password"))
  }

  @Test
  fun `extracts default response`() {
    // when
    val operation = loadSingleOperation("default_response.yaml")

    // then
    assert(operation.responseSchemas.responseFor(200) != null)
    val defaultResponse = operation.responseSchemas.responseFor(500)
    assert(defaultResponse != null)
    val defaultBody = defaultResponse!!.bodies.single()
    assert(defaultBody.dataType is ObjectDataType)
    val properties = (defaultBody.dataType as ObjectDataType).properties
    assert(properties.containsKey("error"))
    assert(properties.containsKey("details"))
  }

  @Test
  fun `extracts status code class response`() {
    // when
    val operation = loadSingleOperation("status_code_class_response.yaml")

    // then
    assert(operation.responseSchemas.responseFor(200) != null)
    assert(operation.responseSchemas.badRequestResponse() != null)
    assert(operation.responseSchemas.responseFor(404) != null)
    assert(operation.responseSchemas.responseFor(404) === operation.responseSchemas.responseFor(403))
    val classBody = operation.responseSchemas.responseFor(404)!!.bodies.single()
    assert(classBody.dataType is ObjectDataType)
    assert((classBody.dataType as ObjectDataType).properties.containsKey("error"))
  }

  @Test
  fun `extracts string pattern`() {
    // when
    val operation = loadSingleOperation("string_pattern.yaml")

    // then
    val requestBody = operation.requestSchema.bodies.single().dataType as ObjectDataType
    val codeType = requestBody.properties["code"] as StringDataType
    assert(codeType.validate("AB-1234").isSuccess())
    assert(codeType.validate("no-match").isFailure())
  }

  @ParameterizedTest(name = "string path parameter (OAS {0}) enforces minimum length of 1")
  @ValueSource(strings = ["3.0", "3.1"])
  fun `string path parameter enforces minimum length of 1`(version: String) {
    // when
    val operation = loadSingleOperation("string_path_parameter_${version.replace(".", "")}.yaml")

    // then
    val dataType = operation.requestSchema.pathParameters.single().dataType as StringDataType
    assert(dataType.lengthRange.contains(BigDecimal.ZERO).isFailure())
    assert(dataType.lengthRange.contains(BigDecimal.ONE).isSuccess())
  }

  @Test
  fun `allows bodyless status code without body declaration`() {
    // when
    val operation = loadSingleOperation("bodyless_status_code_without_body.yaml")

    // then
    val responseSchema = operation.responseSchemas.responseFor(204)!!
    assert(responseSchema.bodies.isEmpty())
    assert(responseSchema.headers.size == 1)
  }

  @Test
  fun `allows HEAD response without body declaration`() {
    // when
    val operation = loadSingleOperation("head_without_response_body.yaml")

    // then
    assert(operation.method == "HEAD")
    val responseSchema = operation.responseSchemas.responseFor(200)!!
    assert(responseSchema.bodies.isEmpty())
    assert(responseSchema.headers.size == 1)
  }

  @Test
  fun `extracts an operation declaring only a status code class response`() {
    // when
    val operation = loadSingleOperation("status_code_class_only_response.yaml")

    // then
    assert(operation.responseSchemas.summary() == "4XX")
    assert(operation.responseSchemas.responseFor(404) != null)
    assert(operation.responseSchemas.responseFor(200) == null)
  }

  @Test
  fun `extracts an operation declaring only a default response`() {
    // when
    val operation = loadSingleOperation("default_only_response.yaml")

    // then
    assert(operation.responseSchemas.summary() == "default")
    assert(operation.responseSchemas.responseFor(500) != null)
    assert(operation.responseSchemas.responseFor(200) === operation.responseSchemas.responseFor(500))
  }

  // --- Helpers ---

  private fun loadSingleOperation(yamlFile: String) =
    loadOperations(yamlFile).assertSingle()

  private fun loadOperations(yamlFile: String) =
    OpenApiLoader.loadOperations("src/test/resources/operation/$yamlFile").assertSuccess()
}
