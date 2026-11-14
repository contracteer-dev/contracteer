package dev.contracteer.verifier

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifierConfigurationTest {

  @Test
  fun `defaults the timeout to 10 seconds`() {
    // when
    val configuration = VerifierConfiguration()

    // then
    assertEquals(Duration.ofSeconds(10), configuration.timeout)
  }

  @ParameterizedTest
  @CsvSource("0, 0s", "-1, -1ms")
  fun `rejects a timeout that is not positive`(millis: Long, rendered: String) {
    // when
    val exception = assertFailsWith<IllegalArgumentException> {
      VerifierConfiguration(timeout = Duration.ofMillis(millis))
    }

    // then
    assertEquals("timeout must be positive: $rendered", exception.message)
  }
}
