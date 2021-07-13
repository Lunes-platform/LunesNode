package io.lunes.assets.fees

import io.lunes.settings.Constants

object RegularAssetFee extends Fee {
  def dontSatisfyMinimalFee(balance: Long): Boolean = balance < Constants.MinimalStakeForIssueOrReissue

  override val minimalBalance: Long = (2e12).toLong
}