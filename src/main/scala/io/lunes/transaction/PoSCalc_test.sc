import com.google.common.base.Throwables
import io.lunes.crypto
import io.lunes.features.FeatureProvider
import io.lunes.settings.FunctionalitySettings
import io.lunes.state2.StateReader
import io.lunes.state2.reader.SnapshotStateReader
import scorex.account.{Address, PublicKeyAccount}
import scorex.block.Block
import scorex.consensus.nxt.NxtLikeConsensusBlockData
import scorex.utils.ScorexLogging

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object PoSCalc extends ScorexLogging {

  val MinimalEffectiveBalanceForGenerator: Long = 500000000000L

  private val AvgBlockTimeDepth: Int = 3

  // Min BaseTarget value is 9 because only in this case it is possible to get to next integer value (10)
  // then increasing base target by 11% and casting it to Long afterward (see lines 55 and 59)
  private val MinBaseTarget: Long = 9

  private val MinBlockDelaySeconds = 53
  private val MaxBlockDelaySeconds = 67
  private val BaseTargetGamma = 64

  /**
    *
    * @param prevBlockTimestamp
    * @param prevBlockBaseTarget
    * @param timestamp
    * @param balance
    * @return
    */
  def calcTarget(prevBlockTimestamp: Long, prevBlockBaseTarget: Long, timestamp: Long, balance: Long): BigInt = {
    val eta = (timestamp - prevBlockTimestamp) / 1000
    BigInt(prevBlockBaseTarget) * eta * balance
  }

  /**
    *
    * @param lastBlockData
    * @param generator
    * @return
    */
  def calcHit(lastBlockData: NxtLikeConsensusBlockData, generator: PublicKeyAccount): BigInt =
    BigInt(1, calcGeneratorSignature(lastBlockData, generator).take(8).reverse)

  /**
    *
    * @param lastBlockData
    * @param generator
    * @return
    */
  def calcGeneratorSignature(lastBlockData: NxtLikeConsensusBlockData, generator: PublicKeyAccount): Array[Byte] =
    crypto.fastHash(lastBlockData.generationSignature.arr ++ generator.publicKey)

  /**
    *
    * @param avgBlockDelay
    * @param parentHeight
    * @param parentBaseTarget
    * @param parentTimestamp
    * @param maybeGreatGrandParentTimestamp
    * @param timestamp
    * @return
    */
  def calcBaseTarget(avgBlockDelay: FiniteDuration, parentHeight: Int, parentBaseTarget: Long,
                     parentTimestamp: Long, maybeGreatGrandParentTimestamp: Option[Long], timestamp: Long): Long = {
    val avgDelayInSeconds = avgBlockDelay.toSeconds

    val prevBaseTarget = parentBaseTarget
    if (parentHeight % 2 == 0) {
      val blocktimeAverage = maybeGreatGrandParentTimestamp.fold(timestamp - parentTimestamp)(ggpts => (timestamp - ggpts) / AvgBlockTimeDepth) / 1000
      val minBlocktimeLimit = normalize(MinBlockDelaySeconds, avgDelayInSeconds)
      val maxBlocktimeLimit = normalize(MaxBlockDelaySeconds, avgDelayInSeconds)
      val baseTargetGamma = normalize(BaseTargetGamma, avgDelayInSeconds)

      val baseTarget = (if (blocktimeAverage > avgDelayInSeconds) {
        prevBaseTarget * Math.min(blocktimeAverage, maxBlocktimeLimit) / avgDelayInSeconds
      } else {
        prevBaseTarget - prevBaseTarget * baseTargetGamma *
          (avgDelayInSeconds - Math.max(blocktimeAverage, minBlocktimeLimit)) / (avgDelayInSeconds * 100)
      }).toLong

      normalizeBaseTarget(baseTarget, avgDelayInSeconds)
    } else {
      prevBaseTarget
    }
  }

  /**
    *
    * @param state
    * @param fs
    * @param account
    * @param atHeight
    * @return
    */
  def generatingBalance(state: SnapshotStateReader, fs: FunctionalitySettings, account: Address, atHeight: Int): Try[Long] = {
    val generatingBalanceDepth = if (atHeight >= fs.generationBalanceDepthFrom50To1000AfterHeight) 1000 else 50
    state.effectiveBalanceAtHeightWithConfirmations(account, atHeight, generatingBalanceDepth)
  }

  /**
    *
    * @param height
    * @param state
    * @param fs
    * @param block
    * @param account
    * @param featureProvider
    * @return
    */
  def nextBlockGenerationTime(height: Int, state: StateReader, fs: FunctionalitySettings, block: Block,
                              account: PublicKeyAccount, featureProvider: FeatureProvider): Either[String, (Long, Long)] = {
    generatingBalance(state(), fs, account, height) match {
      case Success(balance) => for {
        _ <- Either.cond(balance >= MinimalEffectiveBalanceForGenerator, (),
          s"Balance $balance of ${account.address} is lower than required for generation")
        cData = block.consensusData
        hit = calcHit(cData, account)
        t = cData.baseTarget
        calculatedTs = (hit * 1000) / (BigInt(t) * balance) + block.timestamp
        _ <- Either.cond(0 < calculatedTs && calculatedTs < Long.MaxValue, (), s"Invalid next block generation time: $calculatedTs")
      } yield (balance, calculatedTs.toLong)
      case Failure(exc) =>
        log.error("Critical error calculating nextBlockGenerationTime", exc)
        Left(Throwables.getStackTraceAsString(exc))
    }
  }

  private def normalizeBaseTarget(bt: Long, averageBlockDelaySeconds: Long): Long = {
    val maxBaseTarget = Long.MaxValue / averageBlockDelaySeconds
    if (bt < MinBaseTarget) MinBaseTarget else if (bt > maxBaseTarget) maxBaseTarget else bt
  }

  private def normalize(value: Long, averageBlockDelaySeconds: Long): Double = value * averageBlockDelaySeconds / (60: Double)

}
println(PoSCalc.MinimalEffectiveBalanceForGenerator)

