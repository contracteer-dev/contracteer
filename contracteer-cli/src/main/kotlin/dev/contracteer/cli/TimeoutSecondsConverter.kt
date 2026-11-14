package dev.contracteer.cli

import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.TypeConversionException
import java.time.Duration

/** Converts a positive whole number of seconds into a [Duration]. */
class TimeoutSecondsConverter: ITypeConverter<Duration> {
  override fun convert(value: String): Duration =
    value.toLongOrNull()?.takeIf { it > 0 }?.let { Duration.ofSeconds(it) }
      ?: throw TypeConversionException("'$value' is not a positive whole number of seconds")
}
