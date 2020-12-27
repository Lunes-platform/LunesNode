package io.lunes.state2.diffs

import cats._
import io.lunes.features.{BlockchainFeature, BlockchainFeatures, FeatureProvider}
import io.lunes.settings.FunctionalitySettings
import io.lunes.state2.reader.SnapshotStateReader
import io.lunes.state2.{Portfolio, _}
import io.lunes.transaction.ValidationError.{AlreadyInTheState, GenericError, Mistiming}
import io.lunes.transaction._
import io.lunes.transaction.assets._
import io.lunes.transaction.assets.exchange.ExchangeTransaction
import io.lunes.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import scorex.account.Address

import scala.concurrent.duration._
import scala.util.{Left, Right}

/** Common Validation object. */
object CommonValidation {

  val MaxTimeTransactionOverBlockDiff: FiniteDuration = 90.minutes
  val MaxTimePrevBlockOverTransactionDiff: FiniteDuration = 2.hours

  /** Disallows sending Transaction case Greater than Transaction.
    * @param s The Snapshot State Reader object.
    * @param settings The Functionality Settings.
    * @param blockTime The block Time.
    * @param tx The Input Transaction Descendant.
    * @tparam T Type Parameter descendant of [[Transaction]].
    * @return Returns Either an T object (case Success) or a ValidationError (case Failure).
    */
  def disallowSendingGreaterThanBalance[T <: Transaction](s: SnapshotStateReader, settings: FunctionalitySettings, blockTime: Long, tx: T): Either[ValidationError, T] =
    if (blockTime >= settings.allowTemporaryNegativeUntil) {
      def checkTransfer(sender: Address, assetId: Option[AssetId], amount: Long, feeAssetId: Option[AssetId], feeAmount: Long) = {
        val amountDiff = assetId match {
          case Some(aid) => Portfolio(0, LeaseInfo.empty, Map(aid -> -amount))
          case None => Portfolio(-amount, LeaseInfo.empty, Map.empty)
        }
        val feeDiff = feeAssetId match {
          case Some(aid) => Portfolio(0, LeaseInfo.empty, Map(aid -> -feeAmount))
          case None => Portfolio(-feeAmount, LeaseInfo.empty, Map.empty)
        }

        val spendings = Monoid.combine(amountDiff, feeDiff)
        val accountPortfolio = s.partialPortfolio(sender, spendings.assets.keySet)

        lazy val negativeAsset = spendings.assets.find { case (id, amt) => (accountPortfolio.assets.getOrElse(id, 0L) + amt) < 0L }.map { case (id, amt) => (id, accountPortfolio.assets.getOrElse(id, 0L), amt, accountPortfolio.assets.getOrElse(id, 0L) + amt) }
        lazy val newLunesBalance = accountPortfolio.balance + spendings.balance
        lazy val negativeLunes = newLunesBalance < 0
        if (negativeLunes)
          Left(GenericError(s"Attempt to transfer unavailable funds:" +
            s" Transaction application leads to negative lunes balance to (at least) temporary negative state, current balance equals ${accountPortfolio.balance}, spends equals ${spendings.balance}, result is $newLunesBalance"))
        else if (negativeAsset.nonEmpty)
          Left(GenericError(s"Attempt to transfer unavailable funds:" +
            s" Transaction application leads to negative asset '${negativeAsset.get._1}' balance to (at least) temporary negative state, current balance is ${negativeAsset.get._2}, spends equals ${negativeAsset.get._3}, result is ${negativeAsset.get._4}"))
        else Right(tx)
      }

      tx match {
        case ptx: PaymentTransaction if s.partialPortfolio(ptx.sender).balance < (ptx.amount + ptx.fee) =>
          Left(GenericError(s"Attempt to pay unavailable funds: balance " +
            s"${s.partialPortfolio(ptx.sender).balance} is less than ${ptx.amount + ptx.fee}"))
        case ttx: TransferTransaction => checkTransfer(ttx.sender, ttx.assetId, ttx.amount, ttx.feeAssetId, ttx.fee)
        case rdtx: RegistryTransaction => checkTransfer(rdtx.sender, None, rdtx.amount, None, rdtx.fee)
        case mtx: MassTransferTransaction => checkTransfer(mtx.sender, mtx.assetId, mtx.transfers.map(_.amount).sum, None, mtx.fee)
        case _ => Right(tx)
      }
    } else Right(tx)

  /** Disallows Duplicated IDs.
    * @param state The Snapshot State Reader.
    * @param settings The Functionality Settings.
    * @param height The height of the Block.
    * @param tx The input Transaction Descendant.
    * @tparam T Type Parameter descendant of [[Transaction]].
    * @return Returns Either an T object (case Success) or a ValidationError (case Failure).
    */
  def disallowDuplicateIds[T <: Transaction](state: SnapshotStateReader, settings: FunctionalitySettings, height: Int, tx: T): Either[ValidationError, T] = tx match {
    case ptx: PaymentTransaction => Right(tx)
    case _ =>
      state.transactionInfo(tx.id()) match {
        case Some((txHeight, _)) => Left(AlreadyInTheState(tx.id(), txHeight))
        case None => Right(tx)
      }
  }

  /** Disallows Before Activation Time.
    * @param featureProvider The Feature Provider.
    * @param height The height of the Block.
    * @param tx The input Transaction Descendant.
    * @tparam T Type Parameter descendant of [[Transaction]].
    * @return Returns Either a T object (case Success) or a ValidationError (case Failure).
    */
  def disallowBeforeActivationTime[T <: Transaction](featureProvider: FeatureProvider, height: Int, tx: T): Either[ValidationError, T] = {

    def activationBarrier(b: BlockchainFeature) =
      Either.cond(featureProvider.isFeatureActivated(b, height),
        tx, ValidationError.ActivationError(s"${tx.getClass.getSimpleName} transaction has not been activated yet"))

    tx match {
      case _: BurnTransaction => Right(tx)
      case _: PaymentTransaction => Right(tx)
      case _: GenesisTransaction => Right(tx)
      case _: TransferTransaction => Right(tx)
      case _: RegistryTransaction => Right(tx)
      case _: IssueTransaction => Right(tx)
      case _: ReissueTransaction => Right(tx)
      case _: ExchangeTransaction => Right(tx)
      case _: LeaseTransaction => Right(tx)
      case _: LeaseCancelTransaction => Right(tx)
      case _: CreateAliasTransaction => Right(tx)
      case _: MassTransferTransaction => activationBarrier(BlockchainFeatures.MassTransfer)
      case _ => Left(GenericError("Unknown transaction must be explicitly activated"))
    }
  }

  /** Disallows Transaction from a Future.
    * @param settings The Functionality Settings.
    * @param time The Time of the Transaction.
    * @param tx The input Transaction Descendant.
    * @tparam T Type Parameter descendant of [[Transaction]].
    * @return Returns Either a T object (case Success) or a ValidationError (case Failure).
    */
  def disallowTxFromFuture[T <: Transaction](settings: FunctionalitySettings, time: Long, tx: T): Either[ValidationError, T] = {
    val allowTransactionsFromFutureByTimestamp = tx.timestamp < settings.allowTransactionsFromFutureUntil
    if (!allowTransactionsFromFutureByTimestamp && tx.timestamp - time > MaxTimeTransactionOverBlockDiff.toMillis)
      Left(Mistiming(s"Transaction ts ${tx.timestamp} is from far future. BlockTime: $time"))
    else Right(tx)
  }

  /** Disallows Transaction from the Past.
    * @param prevBlockTime The Previous Block Time.
    * @param tx The input Transaction Descendant.
    * @tparam T Type Parameter descendant of [[Transaction]].
    * @return Returns Either a T object (case Success) or a ValidationError.
    */
  def disallowTxFromPast[T <: Transaction](prevBlockTime: Option[Long], tx: T): Either[ValidationError, T] =
    prevBlockTime match {
      case Some(t) if (t - tx.timestamp) > MaxTimePrevBlockOverTransactionDiff.toMillis =>
        Left(Mistiming(s"Transaction ts ${tx.timestamp} is too old. Previous block time: $prevBlockTime"))
      case _ => Right(tx)
    }
}


