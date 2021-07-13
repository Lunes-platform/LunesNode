package io.lunes.assets.fees

object NFTFee extends Fee {
  val fee = (2.0e6).toLong // 2 Uni

  override def dontSatisfyMinimalFee(balance: Long): Boolean = true

  override val minimalBalance: Long = (2e12).toLong
}
