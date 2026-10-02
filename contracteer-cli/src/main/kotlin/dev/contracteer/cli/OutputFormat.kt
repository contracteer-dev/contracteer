package dev.contracteer.cli

/** How a command writes its result: as text for a reader, or as one JSON document for a program. */
internal enum class OutputFormat {
  TEXT, JSON;

  override fun toString() = name.lowercase()
}
