package dev.contracteer.verifier.junit

import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Marks a test method as a dynamic Contract Test Template.
 *
 * When a method is annotated with `@ContracteerTest`, it is treated as a test template that will be
 * invoked multiple times—once for each verification case derived from the API operations discovered
 * in the OpenAPI 3 Document specified by [openApiDoc]. For each verification case, the following
 * process occurs:
 *
 * 1. **Preparation:** The body of the annotated test method is executed. This allows you to prepare
 *    the server, configure mocks, or perform any necessary setup before contract verification.
 *
 * 2. **Verification:** After the method body is executed, the verification case is
 *    automatically verified against the server using the specified connection parameters.
 *
 * When the primary response of an operation cannot be determined, no verification case asserts it.
 * The template then adds a skipped invocation after that operation's cases, named after the reason;
 * the method body does not run for it.
 *
 * @property serverUrl The base URL where the service under test is running. *Default*: `"http://localhost"`
 *
 * @property serverPort The static port for the service. Use this parameter when the service is started on a fixed port.
 *   For servers started on a random (ephemeral) port, you must capture the actual port in a field annotated
 *   with [ContracteerServerPort]. If both are provided (i.e. a non-zero value is obtained from the field annotated
 *   with [ContracteerServerPort]), that value will override this static port. **Default:** `8080`.
 *
 * @property openApiDoc Path of the OpenAPI 3 Document from which API operations are loaded
 **/
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestTemplate
@ExtendWith(ContractTestExtension::class)
annotation class ContracteerTest(
  val serverUrl: String = "http://localhost",
  val serverPort: Int = 8080,
  val openApiDoc: String,
)
