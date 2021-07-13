package io.lunes.transaction

import com.google.common.base.Throwables
import io.lunes.assets.fees.{NFTFee, RegularAssetFee}
import io.lunes.settings.Constants
import io.lunes.state2.ByteStr
import io.lunes.transaction.assets.exchange.Order
import scorex.account.{Address, Alias}
import scorex.block.{Block, MicroBlock}

import scala.util.Either

/**
  *
  */
trait ValidationError

/**
  *
  */
object ValidationError {

  type Validation[T] = Either[ValidationError, T]

  case class InvalidAddress(reason: String) extends ValidationError
  case class NegativeAmount(amount: Long, of: String) extends ValidationError
  case object InsufficientFee extends ValidationError
  case object TooBigArray extends ValidationError
  case object InvalidName extends ValidationError
  case object OverflowError extends ValidationError
  case object ToSelf extends ValidationError
  case object MissingSenderPrivateKey extends ValidationError
  case object UnsupportedTransactionType extends ValidationError
  case object InvalidRequestSignature extends ValidationError
  case class BlockFromFuture(ts: Long) extends ValidationError
  case class InvalidSignature(s: Signed,
                              details: Option[InvalidSignature] = None)
      extends ValidationError {
    override def toString: String =
      s"InvalidSignature(${s.toString + " reason: " + details})"
  }
  case class TransactionNotAllowedByScript(t: Transaction)
      extends ValidationError {
    override def toString: String = s"TransactionNotAllowedByScript($t)"
  }
  case class ScriptParseError(m: String) extends ValidationError
  case class GenericError(err: String) extends ValidationError
  object GenericError {
    def apply(ex: Throwable): GenericError =
      new GenericError(Throwables.getStackTraceAsString(ex))
  }
  case class AlreadyInTheState(txId: ByteStr, txHeight: Int)
      extends ValidationError
  case class AccountBalanceError(errs: Map[Address, String])
      extends ValidationError
  case class AliasNotExists(a: Alias) extends ValidationError
  case class OrderValidationError(order: Order, err: String)
      extends ValidationError
  case class SenderIsBlacklisted(addr: String) extends ValidationError
  case class Mistiming(err: String) extends ValidationError
  case class BlockAppendError(err: String, b: Block) extends ValidationError
  case class MicroBlockAppendError(err: String, microBlock: MicroBlock)
      extends ValidationError {
    override def toString: String =
      s"MicroBlockAppendError($err, ${microBlock.totalResBlockSig} ~> ${microBlock.prevResBlockSig.trim}])"
  }
  case class ActivationError(err: String) extends ValidationError
  case class FrozenAssetTransaction(address: String) extends ValidationError {
    override def toString: String =
      s"Address $address cannot perform this transaction."
  }
  case class InsufficientLunesInStake(address: String, balance: Long)
      extends ValidationError {
    private def convertBalance(balance: Long): Double = balance / 100000000
    override def toString: String =
      s"Address $address does not have enough balance (${convertBalance(balance)} Lunes). Minimum balance is ${convertBalance(RegularAssetFee.minimalBalance)}."
  }
  case class InsufficientLunesInStakeForNFT(address: String, balance: Long)
      extends ValidationError {
    private def convertBalance(balance: Long): Double = balance / 100000000
    override def toString: String =
      s"Address $address does not have enough balance (${convertBalance(balance)} Lunes) for NFT type of Asset. Minimum balance is ${convertBalance(NFTFee.minimalBalance)}."
  }
}
