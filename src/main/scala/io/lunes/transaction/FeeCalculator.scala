package io.lunes.transaction

import io.lunes.settings.{FeesSettings, FunctionalitySettings}
import io.lunes.state._
import io.lunes.transaction.FeeCalculator._
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.transfer._

class FeeCalculator(settings: FeesSettings, blockchain: Blockchain) {

  private val Kb = 1024

  private val map: Map[String, Long] = {
    settings.fees.flatMap { fs =>
      val transactionType = fs._1
      fs._2.map { v =>
        val maybeAsset =
          if (v.asset.toUpperCase == "LUNES") None
          else Some(ByteStr.decodeBase58(v.asset).get)
        val fee = v.fee

        TransactionAssetFee(transactionType, maybeAsset).key -> fee
      }
    }
  }

//  private val minimumFee = map("minimum-fee")

  def enoughFee[T <: Transaction](
      tx: T,
      blockchain: Blockchain,
      fs: FunctionalitySettings): Either[ValidationError, T] =
    if (blockchain.height >= Sponsorship.sponsoredFeesSwitchHeight(blockchain,
                                                                   fs))
      Right(tx)
    else enoughFee(tx)

  def enoughFee[T <: Transaction](tx: T): Either[ValidationError, T] = {
    val (txFeeAssetId, txFeeValue) = tx.assetFee
    val txAssetFeeKey = TransactionAssetFee(tx.builder.typeId, txFeeAssetId).key

    for {
      txMinBaseFee <- Either.cond(
        map.contains(txAssetFeeKey),
        map(txAssetFeeKey),
        GenericError(s"Minimum fee is not defined for $txAssetFeeKey")
      )
      minTxFee = minFeeFor(tx, txFeeAssetId, txMinBaseFee)
      _ <- Either.cond(
        txFeeValue >= minTxFee,
        (),
        GenericError {
          s"Fee in ${txFeeAssetId.fold("LUNES")(_.toString)} for ${tx.builder.classTag} transaction does not exceed minimal value of $minTxFee"
        }
      )
    } yield tx
  }

//  private def maximum(v1: Long, v2: Long) = {
//    if (v1 > v2) v1 else v2
//  }

  /**
    * Check for minimum fee for the asset.
    * @param tx
    * @param txFeeAssetId
    * @param txMinBaseFee
    * @return
    */
  private def minFeeFor(tx: Transaction,
                        txFeeAssetId: Option[AssetId],
                        txMinBaseFee: Long): Long = {
    val fee = tx match {
      case tx: DataTransaction =>
        val sizeInKb = 1 + (tx.bytes().length - 1) / Kb
        txMinBaseFee * sizeInKb
      case tx: MassTransferTransaction =>
        val transferFeeSpec = map.getOrElse(
          TransactionAssetFee(TransferTransactionV1.typeId, txFeeAssetId).key,
          throw new IllegalStateException(
            "Can't find spec for TransferTransaction")
        )
        transferFeeSpec + txMinBaseFee * tx.transfers.size
      case _ => txMinBaseFee
    }

    fee
  }
}

object FeeCalculator {

  private case class TransactionAssetFee(txType: Int,
                                         assetId: Option[AssetId]) {
    val key = s"TransactionAssetFee($txType, ${assetId.map(_.base58)})"
  }

}
