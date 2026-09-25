package dev.contracteer.verifier.junit

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import dev.contracteer.core.Result.Success
import dev.contracteer.core.swagger.OpenApiLoader
import dev.contracteer.verifier.OpenApiVerifier
import dev.contracteer.verifier.VerificationCaseFactory
import dev.contracteer.verifier.VerificationPlan
import dev.contracteer.verifier.VerifierConfiguration
import java.lang.System.lineSeparator
import java.lang.reflect.Modifier
import java.util.stream.Stream

internal class ContractTestExtension: TestTemplateInvocationContextProvider {

  override fun supportsTestTemplate(context: ExtensionContext) =
    context.requiredTestMethod.getAnnotation(ContracteerTest::class.java) != null

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val annotation = context.requiredTestMethod.getAnnotation(ContracteerTest::class.java)
                     ?: throw IllegalStateException("Missing @ContracteerTest on test method")

    val operationsResult = OpenApiLoader.loadOperations(annotation.openApiDoc)

    if (operationsResult !is Success) {
      throw IllegalArgumentException(
        "Failed to load OpenAPI document:${lineSeparator()}" +
        operationsResult.errors().joinToString(prefix = "- ", separator = "${lineSeparator()}- "))
    }

    val verifierProvider = createVerifierProvider(annotation)

    val invocations: List<TestTemplateInvocationContext> = operationsResult.value
      .map { VerificationCaseFactory.plan(it) }
      .flatMap { invocationsFor(it, verifierProvider) }

    return invocations.stream()
  }

  private fun invocationsFor(plan: VerificationPlan,
                             verifierProvider: (ExtensionContext) -> OpenApiVerifier): List<TestTemplateInvocationContext> =
    plan.cases.map { ContractTestInvocationContext(it, verifierProvider) } +
    listOfNotNull(plan.unverifiedPrimaryResponse?.let { UnverifiedPrimaryResponseInvocationContext(it) })

  private fun createVerifierProvider(annotation: ContracteerTest): (ExtensionContext) -> OpenApiVerifier {
    var cached: OpenApiVerifier? = null
    return { extensionContext ->
      cached ?: run {
        val port = resolveServerPort(extensionContext, annotation)
        OpenApiVerifier(VerifierConfiguration("${annotation.serverUrl}:$port")).also { cached = it }
      }
    }
  }

  private fun resolveServerPort(extensionContext: ExtensionContext, annotation: ContracteerTest): Int {
    val annotatedPort = extractAnnotatedServerPort(extensionContext)
    return if (annotatedPort == null || annotatedPort == 0) annotation.serverPort else annotatedPort
  }

  private fun extractAnnotatedServerPort(extensionContext: ExtensionContext): Int? {
    val testClass = extensionContext.requiredTestClass
    val portField = testClass.declaredFields.find { it.isAnnotationPresent(ContracteerServerPort::class.java) }
                    ?: return null

    portField.isAccessible = true

    val value = if (Modifier.isStatic(portField.modifiers))
      portField.get(null)
    else
      portField.get(extensionContext.requiredTestInstance)

    return value as? Int
  }
}
