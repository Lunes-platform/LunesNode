package io.lunes.assets.fees

trait Fee {
  def dontSatisfyMinimalFee(balance: Long): Boolean

  val minimalBalance: Long
}