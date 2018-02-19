package io.lunes.transaction.assets

import cats.implicits._
import com.google.common.primitives.{Bytes, Longs, Shorts}
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{AddressOrAlias, PrivateKeyAccount, PublicKeyAccount}
import scorex.crypto.EllipticCurveImpl
import scorex.serialization.Deser
import io.lunes.transaction.TransactionParser._
import io.lunes.transaction.ValidationError.Validation
import io.lunes.transaction._

import scala.util.{Either, Failure, Success, Try}

case class MassTransferTransaction private(assetId: Option[AssetId],
                                           sender: PublicKeyAccount,
                                           transfers: List[(AddressOrAlias, Long)],
                                           timestamp: Long,
                                           fee: Long,
                                           signature: ByteStr) extends SignedTransaction {
  override val transactionType: TransactionType.Value = TransactionType.MassTransferTransaction

  override val assetFee: (Option[AssetId], Long) = (None, fee)

  val toSign: Coeval[Array[Byte]] = Coeval.evalOnce {
    val assetIdBytes = assetId.map(a => (1: Byte) +: a.arr).getOrElse(Array(0: Byte))
    val transferBytes = transfers
      .map { case (recipient, amount) => recipient.bytes.arr ++ Longs.toByteArray(amount) }
      .fold(Array())(_ ++ _)

    Bytes.concat(
      Array(transactionType.id.toByte),
      sender.publicKey,
      assetIdBytes,
      Shorts.toByteArray(transfers.size.toShort),
      transferBytes,
      Longs.toByteArray(timestamp),
      Longs.toByteArray(fee))
  }

  override val json: Coeval[JsObject] = Coeval.evalOnce {
    val recipientsJson = transfers.map { case (recipient, amount) => Json.obj("recipient" -> recipient.stringRepr, "amount" -> amount) }
    jsonBase() ++ Json.obj(
      "assetId" -> assetId.map(_.base58),
      "transfers" -> recipientsJson)
  }

  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(toSign(), signature.arr))
}

object MassTransferTransaction {
  val MaxTransferCount = 100

  def parseTail(bytes: Array[Byte]): Try[MassTransferTransaction] = Try {
    val sender = PublicKeyAccount(bytes.slice(0, KeyLength))
    val (assetIdOpt, s0) = Deser.parseOption(bytes, KeyLength, AssetIdLength)
    val recipientCount = Shorts.fromByteArray(bytes.slice(s0, s0 + 2))

    def readRecipient(offset: Int): (Validation[(AddressOrAlias, Long)], Int) = {
      AddressOrAlias.fromBytes(bytes, offset) match {
        case Right((addr, ofs)) =>
          val amount = Longs.fromByteArray(bytes.slice(ofs, ofs + 8))
          (Right[ValidationError, (AddressOrAlias, Long)]((addr, amount)), ofs + 8)
        case Left(e) => (Left(e), offset)
      }
    }
    val recipientsList: List[(Validation[(AddressOrAlias, Long)], Int)] =
      List.iterate(readRecipient(s0 + 2), recipientCount) { case (_, offset) => readRecipient(offset) }
    val recipientsEi: Validation[List[(AddressOrAlias, Long)]] = recipientsList.map { case (ei, _) => ei }.sequence

    val s1 = recipientsList.lastOption.map(_._2).getOrElse(s0 + 2)
    val tx: Validation[MassTransferTransaction] = for {
      recipients <- recipientsEi
      timestamp = Longs.fromByteArray(bytes.slice(s1, s1 + 8))
      feeAmount = Longs.fromByteArray(bytes.slice(s1 + 8, s1 + 16))
      (attachment, s2) = Deser.parseArraySize(bytes, s1 + 16)
      signature = ByteStr(bytes.slice(s2, s2 + SignatureLength))
      mtt <- MassTransferTransaction.create(assetIdOpt.map(ByteStr(_)), sender, recipients, timestamp, feeAmount, signature)
    } yield mtt
    tx.fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  def create(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipients: List[(AddressOrAlias, Long)],
             timestamp: Long,
             feeAmount: Long,
             signature: ByteStr): Either[ValidationError, MassTransferTransaction] = {
    Try { recipients.map(_._2).fold(feeAmount)(Math.addExact) }.fold(
      ex => Left(ValidationError.OverflowError),
      totalAmount =>
        if (recipients.lengthCompare(MaxTransferCount) > 0) {
          Left(ValidationError.GenericError(s"Number of recipients is greater than $MaxTransferCount"))
        } else if (recipients.exists(_._2 < 0)) {
          Left(ValidationError.GenericError("One of the transfers has negative value"))
        } else if (feeAmount <= 0) {
          Left(ValidationError.InsufficientFee)
        } else {
          Right(MassTransferTransaction(assetId, sender, recipients, timestamp, feeAmount, signature))
        }
    )
  }

  def create(assetId: Option[AssetId],
             sender: PrivateKeyAccount,
             recipients: List[(AddressOrAlias, Long)],
             timestamp: Long,
             feeAmount: Long): Either[ValidationError, MassTransferTransaction] = {
    create(assetId, sender, recipients, timestamp, feeAmount, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(EllipticCurveImpl.sign(sender, unsigned.toSign())))
    }
  }

  def parseTransfersList(transfers: List[(String, Long)]): Validation[List[(AddressOrAlias, Long)]] = {
    def parseTransfer(address: String, amount: Long): Validation[(AddressOrAlias, Long)] = AddressOrAlias.fromString(address).map((_, amount))
    transfers.traverse(Function.tupled(parseTransfer _))
  }
}
