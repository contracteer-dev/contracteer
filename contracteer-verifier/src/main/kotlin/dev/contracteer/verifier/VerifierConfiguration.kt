package dev.contracteer.verifier

import java.net.URI
import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * Configuration for [OpenApiVerifier].
 *
 * @property baseUrl Absolute http(s) URL of the server to verify, without query string or fragment.
 * @property timeout How long a case waits for the response headers; a case that gets none in time fails as an
 *   execution error. Must be positive. It does not limit reading the response body.
 */
data class VerifierConfiguration @JvmOverloads constructor(
  val baseUrl: String = "http://localhost:8080",
  val timeout: Duration = Duration.ofSeconds(10)
) {
  init {
    val uri = runCatching { URI(baseUrl) }.getOrElse { throw IllegalArgumentException("Invalid baseUrl: $baseUrl", it) }

    require(uri.isAbsolute && uri.scheme in setOf("http", "https")) {
      "baseUrl must be an absolute http(s) URL: $baseUrl"
    }
    require(uri.rawQuery == null) {
      "baseUrl must not contain a query string: $baseUrl"
    }
    require(uri.rawFragment == null) {
      "baseUrl must not contain a fragment: $baseUrl"
    }
    require(timeout.isPositive) {
      "timeout must be positive: ${timeout.toKotlinDuration()}"
    }
  }
}
