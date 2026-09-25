package dev.contracteer.mockserver

import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.PrimaryResponse.Unresolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoPreferredResponse
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoResponsesDeclared
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoSelectableResponse

internal fun ApiOperation.describe(): String = "${method.uppercase()} $path"

/** Why the declared responses resolve no primary response, worded the same at startup and at request time. */
internal fun Unresolved.explanation(): String = when (this) {
  is NoSelectableResponse -> "declares only ${declared.joinAsProse()}; no exact status code a request can target"
  is NoPreferredResponse  -> "declares ${declared.joinAsProse()}; no exact 2xx or 3xx a request can target"
  NoResponsesDeclared     -> "declares no response"
  is Ambiguous            -> "${candidates.joinAsProse()} ${if (candidates.size == 2) "both" else "all"} qualify"
}

private fun List<Any>.joinAsProse(): String =
  if (size < 2) joinToString()
  else "${dropLast(1).joinToString(", ")} and ${last()}"
