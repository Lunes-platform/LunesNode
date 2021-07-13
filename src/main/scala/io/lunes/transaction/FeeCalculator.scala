package io.lunes.transaction

import io.lunes.assets.fees.NFTFee
import io.lunes.settings.FeesSettings
import io.lunes.settings.Constants
import io.lunes.settings.FeesSettings
import io.lunes.state2.ByteStr
import io.lunes.transaction.TransactionParser.TransactionType
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.assets.{IssueTransaction, MassTransferTransaction}

/**
  * Class to check, that transaction contains enough fee to put it to UTX pool
  * @param settings
  */
class FeeCalculator(settings: FeesSettings) {

  private val map: Map[String, Long] = {
    settings.fees.flatMap { fs =>
      val transactionType = fs._1
      fs._2.map { v =>
        val maybeAsset =
          if (v.asset.toUpperCase == Constants.CoinName) None
          else ByteStr.decodeBase58(v.asset).toOption
        val fee = v.fee

        TransactionAssetFee(transactionType, maybeAsset).key -> fee
      }
    }
  }

  /**
    *
    * @param tx
    * @tparam T
    * @return
    */
  def enoughFee[T <: Transaction](tx: T): Either[ValidationError, T] = {
    val feeSpec =
      map.get(TransactionAssetFee(tx.transactionType.id, tx.assetFee._1).key)
    val feeValue = tx match {
      case mtt: MassTransferTransaction =>
        val transferFeeSpec = map.get(
          TransactionAssetFee(
            TransactionType.TransferTransaction.id,
            tx.assetFee._1
          ).key
        )
        feeSpec.flatMap(
          mfee => transferFeeSpec.map(tfee => tfee + mfee * mtt.transfers.size)
        )
      case issueTx: IssueTransaction if issueTx.isNFTTransaction =>
        Some(NFTFee.fee)
      case _ => feeSpec
    }

    feeValue match {
      case Some(minimumFee) =>
        if (minimumFee <= tx.assetFee._2) {
          Right(tx)
        } else {
          Left(
            GenericError(
              s"Fee in ${tx.assetFee._1.fold(Constants.CoinName)(_.toString)} for ${tx.transactionType} transaction does not exceed minimal value of $minimumFee"
            )
          )
        }
      case None =>
        Left(
          GenericError(
            s"Minimum fee is not defined for ${TransactionAssetFee(tx.transactionType.id, tx.assetFee._1).key}"
          )
        )
    }
  }
}

/**
  *
  * @param txType
  * @param assetId
  */
case class TransactionAssetFee(txType: Int, assetId: Option[AssetId]) {

  val key = s"TransactionAssetFee($txType, ${assetId.map(_.base58)})"

}
