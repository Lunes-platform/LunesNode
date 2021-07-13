package io.lunes.settings

import scorex.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val VersionStr = "0.1.2"
  val VersionTuple = (0, 1, 2)
  val MinimalVersion = (0, 1, 2)
  val ApplicationName = "lunesnode"
  val CoinName = "LUNES"
  val CoinAbr = "LNS"
  val AgentName = s"${ApplicationName} v${VersionStr}"
  val InitialBalance = 15072853761500800L

  val MainSchemeCharacter = '1'
  val MainDelay = 60
  val MainTimestamp = 1528077600000L
  val MainSignature =
    "soKTPcsb5cD97jnm64zF3pVuVUqUYx3caaDvuPyM6PXPY7eWCxeHeYvKSE2aJwZwRpXdRFdW1g5BQMFpYkHcf85"

  val MainTransactions = List(
    GenesisTransactionSettings(
      "37oqFHsx1cRtLWtnp6YyQpud5WJk4v79VPu",
      5072853761500800L
    ),
    GenesisTransactionSettings(
      "3826zwbgHauHXAoppU4we3hsJc9GtRCpSvz",
      10000000000000000L
    ),
  )

  val TestSchemeCharacter = '0'
  val TestDelay = 60
  val TestTimestamp = 1528300800000L
  val TestSignature =
    "3MHtECVRc3qTe9tmvds7aQBLzX879YTdq6iYDnXwKGXrzrYZdESoYokQMGcWWSVKKZDjfocMBFPk7vBc1t8iAkBc"

  val TestTransactions = List(
    GenesisTransactionSettings(
      "37aF3eL4tsZ6YpqViXpYAmRQAi7ehtDdBmG",
      15072853761500800L
    )
  )

  val MinimalStakeForIssueOrReissue: Long = 2000000000000L
  val IssueAssetTransactionFee = 100000000
}
