package io.lunes.transaction.smart.script.v1

import io.lunes.crypto
import io.lunes.lang.ScriptVersion.Versions.V1
import io.lunes.lang.v1.compiler.Terms._
import io.lunes.lang.v1.evaluator.ctx.EvaluationContext
import io.lunes.lang.v1.{FunctionHeader, ScriptEstimator, Serde}
import io.lunes.state.ByteStr
import monix.eval.Coeval
import io.lunes.transaction.smart.script.Script
import io.lunes.lang.v1.evaluator.FunctionIds._

object ScriptV1 {
  private val functionCosts: Map[FunctionHeader, Long] =
    EvaluationContext.functionCosts(
      io.lunes.utils.dummyContext.functions.values)

  private val checksumLength = 4
  private val maxComplexity = 20 * functionCosts(FunctionHeader(SIGVERIFY))
  private val maxSizeInBytes = 8 * 1024

  def validateBytes(bs: Array[Byte]): Either[String, Unit] =
    Either.cond(
      bs.length <= maxSizeInBytes,
      (),
      s"Script is too large: ${bs.length} bytes > $maxSizeInBytes bytes")

  def apply(x: EXPR, checkSize: Boolean = true): Either[String, Script] =
    for {
      //_                <- Either.cond(x.tpe == BOOLEAN, (), "Script should return BOOLEAN")
      scriptComplexity <- ScriptEstimator(functionCosts, x)
      _ <- Either.cond(
        scriptComplexity <= maxComplexity,
        (),
        s"Script is too complex: $scriptComplexity > $maxComplexity")
      s = new ScriptV1(x)
      _ <- if (checkSize) validateBytes(s.bytes().arr) else Right(())
    } yield s

  private class ScriptV1(override val expr: EXPR) extends Script {
    override type V = V1.type
    override val version: V = V1
    override val text: String = expr.toString
    override val bytes: Coeval[ByteStr] =
      Coeval.evalOnce {
        val s = Array(version.value.toByte) ++ Serde.codec
          .encode(expr)
          .require
          .toByteArray
        ByteStr(s ++ crypto.secureHash(s).take(ScriptV1.checksumLength))
      }
  }
}
