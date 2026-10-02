package dev.contracteer.cli

import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.TypeConversionException

/** The lowest severity of finding that makes `lint` exit with `1`. */
internal enum class FailOn {
  ERROR, WARNING;

  override fun toString() = name.lowercase()

  /** Reads a `--fail-on` value, ignoring case; `warn` is accepted for `warning`. */
  class Converter: ITypeConverter<FailOn> {
    override fun convert(value: String): FailOn =
      when (value.lowercase()) {
        "error"           -> ERROR
        "warning", "warn" -> WARNING
        else              -> throw TypeConversionException("expected error or warning, but was '$value'")
      }
  }
}
