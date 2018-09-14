package io.lunes.lang.v1.evaluator.ctx.impl.lunes

import io.lunes.lang.v1.evaluator.ctx.CaseObj
import io.lunes.lang.v1.traits.DataItem.{Bin, Bool, Lng, Str}
import io.lunes.lang.v1.traits.Tx._
import io.lunes.lang.v1.traits._
import scodec.bits.ByteVector

object Bindings {
  import Types._

  private def headerPart(tx: Header): Map[String, Any] = Map(
    "id"        -> tx.id,
    "fee"       -> tx.fee,
    "timestamp" -> tx.timestamp,
    "version"   -> tx.version,
  )

  private def provenTxPart(tx: Proven): Map[String, Any] =
    Map(
      "senderPk"  -> tx.senderPk,
      "bodyBytes" -> tx.bodyBytes,
      "proofs" -> {
        val existingProofs = tx.proofs
        val allProofs      = existingProofs ++ Seq.fill(8 - existingProofs.size)(ByteVector.empty)
        allProofs.toIndexedSeq.asInstanceOf[listByteVector.Underlying]
      }
    ) ++ headerPart(tx.h)

  private def mapRecipient(r: Recipient) =
    "recipient" -> (r match {
      case Recipient.Alias(name)    => CaseObj(aliasType.typeRef, Map("alias"   -> name))
      case Recipient.Address(bytes) => CaseObj(addressType.typeRef, Map("bytes" -> bytes))
    })

  def assetPair(ap: APair): CaseObj =
    CaseObj(
      assetPairType.typeRef,
      Map(
        "amountAsset" -> ap.amountAsset.asInstanceOf[optionByteVector.Underlying],
        "priceAsset"  -> ap.priceAsset.asInstanceOf[optionByteVector.Underlying]
      )
    )

  def ordType(o: OrdType): CaseObj =
    CaseObj((o match {
      case OrdType.Buy  => buyType
      case OrdType.Sell => sellType
    }).typeRef, Map.empty)

  def orderObject(ord: Ord): CaseObj =
    CaseObj(
      orderType.typeRef,
      Map(
        "senderPublicKey"  -> ord.senderPublicKey,
        "matcherPublicKey" -> ord.matcherPublicKey,
        "assetPair"        -> assetPair(ord.assetPair),
        "orderType"        -> ordType(ord.orderType),
        "price"            -> ord.price,
        "amount"           -> ord.amount,
        "timestamp"        -> ord.timestamp,
        "expiration"       -> ord.expiration,
        "matcherFee"       -> ord.matcherFee,
        "signature"        -> ord.signature
      )
    )

  def transactionObject(tx: Tx): CaseObj =
    tx match {
      case Tx.Genesis(h, amount, recipient) =>
        CaseObj(genesisTransactionType.typeRef, Map("amount" -> amount) ++ headerPart(h) + mapRecipient(recipient))
      case Tx.Payment(p, amount, recipient) =>
        CaseObj(genesisTransactionType.typeRef, Map("amount" -> amount) ++ provenTxPart(p) + mapRecipient(recipient))
      case Tx.Transfer(p, feeAssetId, transferAssetId, amount, recipient) =>
        CaseObj(
          transferTransactionType.typeRef,
          Map(
            "amount"          -> amount,
            "feeAssetId"      -> feeAssetId.asInstanceOf[optionByteVector.Underlying],
            "transferAssetId" -> transferAssetId.asInstanceOf[optionByteVector.Underlying],
          ) ++ provenTxPart(p) + mapRecipient(recipient)
        )
      case Issue(p, quantity, name, description, reissuable, decimals, scriptOpt) =>
        CaseObj(
          issueTransactionType.typeRef,
          Map(
            "quantity"    -> quantity,
            "name"        -> name,
            "description" -> description,
            "reissuable"  -> reissuable,
            "decimals"    -> decimals,
            "script"      -> scriptOpt.asInstanceOf[optionByteVector.Underlying]
          ) ++ provenTxPart(p)
        )
      case ReIssue(p, quantity, assetId, reissuable) =>
        CaseObj(
          reissueTransactionType.typeRef,
          Map(
            "quantity"   -> quantity,
            "assetId"    -> assetId,
            "reissuable" -> reissuable,
          ) ++ provenTxPart(p)
        )
      case Burn(p, quantity, assetId) =>
        CaseObj(burnTransactionType.typeRef,
                Map(
                  "quantity" -> quantity,
                  "assetId"  -> assetId
                ) ++ provenTxPart(p))
      case Lease(p, amount, recipient) =>
        CaseObj(
          leaseTransactionType.typeRef,
          Map(
            "amount" -> amount,
          ) ++ provenTxPart(p) + mapRecipient(recipient)
        )
      case LeaseCancel(p, leaseId) =>
        CaseObj(
          leaseCancelTransactionType.typeRef,
          Map(
            "leaseId" -> leaseId,
          ) ++ provenTxPart(p)
        )
      case CreateAlias(p, alias) =>
        CaseObj(
          createAliasTransactionType.typeRef,
          Map(
            "alias" -> alias,
          ) ++ provenTxPart(p)
        )
      case MassTransfer(p, assetId, transferCount, totalAmount, transfers, attachment) =>
        CaseObj(
          massTransferTransactionType.typeRef,
          Map(
            "transfers" -> (transfers
              .map(bv => CaseObj(transfer.typeRef, Map(mapRecipient(bv.recipient), "amount" -> bv.amount)))
              .asInstanceOf[listTransfers.Underlying]),
            "assetId"       -> assetId.asInstanceOf[optionByteVector.Underlying],
            "transferCount" -> transferCount,
            "totalAmount"   -> totalAmount,
            "attachment"    -> attachment
          ) ++ provenTxPart(p)
        )
      case SetScript(p, scriptOpt) =>
        CaseObj(setScriptTransactionType.typeRef, Map("script" -> scriptOpt.asInstanceOf[optionByteVector.Underlying]) ++ provenTxPart(p))
      case Sponsorship(p, assetId, minSponsoredAssetFee) =>
        CaseObj(
          sponsorFeeTransactionType.typeRef,
          Map("assetId" -> assetId, "minSponsoredAssetFee" -> minSponsoredAssetFee.asInstanceOf[optionLong.Underlying]) ++ provenTxPart(p)
        )
      case Data(p, data) =>
        CaseObj(
          dataTransactionType.typeRef,
          Map(
            "data" -> data
              .map {
                case Lng(k, v)  => CaseObj(longDataEntryType.typeRef, Map("key" -> k, "value" -> v))
                case Str(k, v)  => CaseObj(longDataEntryType.typeRef, Map("key" -> k, "value" -> v))
                case Bool(k, v) => CaseObj(longDataEntryType.typeRef, Map("key" -> k, "value" -> v))
                case Bin(k, v)  => CaseObj(longDataEntryType.typeRef, Map("key" -> k, "value" -> v))
              }
              .asInstanceOf[listOfDataEntriesType.Underlying]) ++
            provenTxPart(p)
        )
      case Exchange(p, price, amount, buyMatcherFee, sellMatcherFee, buyOrder, sellOrder) =>
        CaseObj(
          exchangeTransactionType.typeRef,
          Map(
            "buyOrder"       -> orderObject(buyOrder),
            "sellOrder"      -> orderObject(sellOrder),
            "price"          -> price,
            "amount"         -> amount,
            "buyMatcherFee"  -> buyMatcherFee,
            "sellMatcherFee" -> sellMatcherFee,
          ) ++ provenTxPart(p)
        )
    }

}
