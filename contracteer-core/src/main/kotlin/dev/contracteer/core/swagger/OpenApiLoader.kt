package dev.contracteer.core.swagger

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import io.swagger.v3.parser.util.DeserializationUtils
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.EXECUTION_ERROR
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Result.Failure
import dev.contracteer.core.Result.Success
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.result
import java.io.File
import java.net.*

/**
 * Loads and parses an OpenAPI 3.0.x or 3.1.x document into a list of [ApiOperation] instances.
 *
 * Accepts a file path, an HTTP(S) URL, or a `classpath:` resource pointing to an OpenAPI document.
 */
object OpenApiLoader {

  init {
    DeserializationUtils.getOptions().maxYamlCodePoints = 10 * 1024 * 1024
  }

  /**
   * Parses the OpenAPI document at the given [path] and reports the outcome: the extracted operations, or the
   * diagnostics that stopped the load, along with the load's warnings. A failure to read the document is an
   * [EXECUTION_ERROR]; any other failure, and every warning, is a [SPEC] finding.
   *
   * @param path a local file path, an HTTP(S) URL, or a `classpath:` resource path to an OpenAPI 3.0.x or 3.1.x document
   */
  @JvmStatic
  fun load(path: String): LoadReport {
    val warnings = LoadWarnings()
    val operations = extractOperations(path, warnings)
    return operations.toLoadReport(path, warnings.diagnostics)
  }

  /**
   * Parses the OpenAPI document at the given [path] and extracts all API operations.
   *
   * @param path a local file path, an HTTP(S) URL, or a `classpath:` resource path to an OpenAPI 3.0.x or 3.1.x document
   * @return a [Result] containing the list of extracted [ApiOperation] instances,
   *         or errors if the document is invalid or cannot be loaded
   */
  @JvmStatic
  fun loadOperations(path: String): Result<List<ApiOperation>> =
    extractOperations(path, LoadWarnings())

  private fun extractOperations(path: String, warnings: LoadWarnings): Result<List<ApiOperation>> =
    result {
      val content = path.loadOpenApiDocument().withDefaults(EXECUTION_ERROR, ERROR).bind()
      checkSupportedVersion(content).bind()
      val openAPI = parse(content).bind()
      val sharedComponents = SharedComponents(
        schemas = openAPI.components.safeSchemas(),
        parameters = openAPI.components.safeParameters(),
        requestBodies = openAPI.components.safeRequestBodies(),
        headers = openAPI.components.safeHeaders(),
        examples = openAPI.components.safeExamples(),
        responses = openAPI.components.safeResponses()
      )
      ApiOperationExtractor(sharedComponents, warnings).extract(openAPI).bind()
    }

  private fun Result<List<ApiOperation>>.toLoadReport(source: String, warnings: List<Diagnostic>): LoadReport =
    when (this) {
      is Success -> LoadReport.Loaded(source, value, warnings)
      is Failure -> LoadReport.Failed(source, withDefaults(SPEC, ERROR).diagnostics() + warnings, truncated)
    }

  private fun checkSupportedVersion(content: String): Result<Unit> =
    when (val declared = readDeclaredVersion(content)) {
      Missing                                                 -> failure("OpenAPI document does not declare a version. $SUPPORTED_HINT")
      is Declared if SUPPORTED_VERSION matches declared.value -> success()
      is Declared                                             -> failure("OpenAPI version '${declared.value}' is not supported. $SUPPORTED_HINT")
      Unparseable                                             -> success()
    }

  private fun readDeclaredVersion(content: String): VersionRead =
    try {
      val versionNode = YAML_MAPPER.readTree(content)?.get("openapi")
      when {
        versionNode == null || versionNode.isNull -> Missing
        else                                      -> Declared(versionNode.asText())
      }
    } catch (_: Throwable) {
      Unparseable
    }

  private fun String.loadOpenApiDocument() =
    when {
      isClasspath() -> loadFromClasspath(this)
      isUrl()       -> loadFromUrl(this)
      else          -> loadFromFile(this)
    }

  private fun loadFromFile(path: String): Result<String> {
    val file = File(path)
    return if (file.exists())
      success(file.readText())
    else
      failure("Invalid file: file not found at ${file.absoluteFile}")
  }

  private fun loadFromUrl(path: String) =
    path.toUrl().flatMap { it.loadOpenApiDocument() }

  private fun parse(content: String): Result<OpenAPI> {
    return try {
      val parseResult = OpenAPIV3Parser().readContents(content, emptyList(), ParseOptions().apply { isResolve = true })
      val fatalMessages = parseResult?.messages.orEmpty().filterNot(::isNonFatalRefWarning)
      when {
        parseResult == null        -> failure("Failed to parse OpenAPI 3 Document")
        fatalMessages.isNotEmpty() -> failure(*fatalMessages.toTypedArray())
        else                       -> success(parseResult.openAPI)
      }
    } catch (t: Throwable) {
      failure("Failed to parse OpenAPI 3 Document: ${t::class.simpleName}: ${t.message}")
    }
  }

  // swagger-parser (OpenAPIDeserializer.java:2838 and :3968) emits this warning for $refs that target
  // a JSON Pointer outside #/components/schemas/. Contracteer's resolver handles those refs, so the
  // warning is non-fatal. The suffix match below is canaried by OperationSchemaExtractionTest's
  // `ref_into_*` fixtures — if swagger-parser changes the wording, those tests fail at load and the
  // suffix here needs to be updated.
  private fun isNonFatalRefWarning(message: String): Boolean =
    message.endsWith("is not of expected type Schema")

  private fun String.isClasspath() = lowercase().startsWith("classpath:")

  private fun loadFromClasspath(path: String): Result<String> {
    val resourceName = path.removePrefix("classpath:").removePrefix("/")
    val inputStream = Thread.currentThread().contextClassLoader?.getResourceAsStream(resourceName)
                      ?: OpenApiLoader::class.java.classLoader.getResourceAsStream(resourceName)
    return if (inputStream != null)
      success(inputStream.bufferedReader().use { it.readText() })
    else
      failure("Classpath resource not found: $resourceName")
  }

  private fun String.isUrl() =
    this.lowercase().startsWith("http://") || this.lowercase().startsWith("https://")

  private fun String.toUrl(): Result<URL> =
    try {
      success(URI(this).toURL())
    } catch (_: Exception) {
      failure("Invalid URL: the URL '$this' is malformed")
    }

  private fun URL.loadOpenApiDocument(): Result<String> {
    val connection = openConnection() as? HttpURLConnection
                     ?: return failure("Invalid URL: unable to open HTTP connection for URL: $this")

    connection.requestMethod = "GET"
    connection.connectTimeout = 3_000
    connection.readTimeout = 3_000
    connection.instanceFollowRedirects = true
    return try {
      when (val responseCode = connection.responseCode) {
        in 200..299 -> success(connection.inputStream.bufferedReader().use { it.readText() })
        in 400..499 -> failure("Client error: $responseCode ${connection.responseMessage} when fetching OpenAPI Document from URL: $this")
        in 500..599 -> failure("Server error: $responseCode ${connection.responseMessage} when fetching OpenAPI Document from URL: $this")
        else        -> failure("Unexpected response: $responseCode ${connection.responseMessage} when fetching OpenAPI Document from URL: $this")
      }
    } catch (_: SocketTimeoutException) {
      failure("Request timed out: $this")
    } catch (exception: ConnectException) {
      failure("Connection exception for '$this': $exception")
    } catch (exception: Exception) {
      failure("Unexpected exception for '$this': $exception")
    } finally {
      connection.disconnect()
    }
  }

}

private const val SUPPORTED_HINT = "Contracteer currently supports OpenAPI 3.0.x and 3.1.x."
private val YAML_MAPPER = YAMLMapper()
private val SUPPORTED_VERSION = Regex("^3\\.[01]\\.\\d+$")

private sealed interface VersionRead
private data object Missing : VersionRead
private data object Unparseable : VersionRead
private data class Declared(val value: String) : VersionRead
