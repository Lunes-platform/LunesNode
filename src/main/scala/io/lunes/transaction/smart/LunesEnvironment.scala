package io.lunes.transaction.smart

import io.lunes.lang.v1.traits.{DataType, Environment, Recipient, Tx => ContractTransaction}
import io.lunes.state._
import monix.eval.Coeval
import scodec.bits.ByteVector
import scorex.account.{Address, AddressOrAlias, Alias}
import io.lunes.transaction.Transaction

class LunesEnvironment(nByte: Byte, tx: Coeval[Transaction], h: Coeval[Int], blockchain: Blockchain) extends Environment {
  override def height: Int = h()

  override def transaction: ContractTransaction = RealTransactionWrapper(tx())

  override def transactionById(id: Array[Byte]): Option[ContractTransaction] =
    blockchain
      .transactionInfo(ByteStr(id))
      .map(_._2)
      .map(RealTransactionWrapper(_))

  override def data(addressBytes: Array[Byte], key: String, dataType: DataType): Option[Any] = {
    val address = Address.fromBytes(addressBytes).explicitGet()
    val data    = blockchain.accountData(address, key)
    data.map((_, dataType)).flatMap {
      case (LongDataEntry(_, value), DataType.Long)        => Some(value)
      case (BooleanDataEntry(_, value), DataType.Boolean)  => Some(value)
      case (BinaryDataEntry(_, value), DataType.ByteArray) => Some(ByteVector(value.arr))
      case (StringDataEntry(_, value), DataType.String)    => Some(value)
      case _                                               => None
    }
  }
  override def resolveAlias(name: String): Either[String, Recipient.Address] =
    blockchain
      .resolveAlias(Alias.buildWithCurrentNetworkByte(name).explicitGet())
      .left
      .map(_.toString)
      .right
      .map(a => Recipient.Address(ByteVector(a.bytes.arr)))

  override def networkByte: Byte = nByte

  override def accountBalanceOf(addressOrAlias: Array[Byte], maybeAssetId: Option[Array[Byte]]): Either[String, Long] = {
    (for {
      aoa     <- AddressOrAlias.fromBytes(bytes = addressOrAlias, position = 0)
      address <- blockchain.resolveAlias(aoa._1)
      balance = blockchain.balance(address, maybeAssetId.map(ByteStr(_)))
    } yield balance).left.map(_.toString)
  }
  override def transactionHeightById(id: Array[Byte]): Option[Int] =
    blockchain.transactionHeight(ByteStr(id))
}
