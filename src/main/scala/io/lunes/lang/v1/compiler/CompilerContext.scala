package io.lunes.lang.v1.compiler

import io.lunes.lang.v1.compiler.CompilerContext._
import io.lunes.lang.v1.compiler.Terms.TYPE
import io.lunes.lang.v1.evaluator.ctx.PredefFunction.FunctionTypeSignature
import io.lunes.lang.v1.evaluator.ctx.{EvaluationContext, PredefBase}
import shapeless._

case class CompilerContext(predefTypes: Map[String, PredefBase],
                           varDefs: TypeDefs,
                           functionDefs: FunctionSigs,
                           tmpArgsIdx: Int = 0) {
  def functionTypeSignaturesByName(name: String): Seq[FunctionTypeSignature] =
    functionDefs.getOrElse(name, Seq.empty)
}

object CompilerContext {

  type TypeDefs = Map[String, TYPE]
  type FunctionSigs = Map[String, Seq[FunctionTypeSignature]]

  val empty = CompilerContext(Map.empty, Map.empty, Map.empty, 0)

  def fromEvaluationContext(ctx: EvaluationContext,
                            types: Map[String, PredefBase],
                            varDefs: TypeDefs): CompilerContext = {
    val map =
      ctx.functions.values.groupBy(_.name).mapValues(_.map(_.signature).toSeq)
    CompilerContext(predefTypes = types, varDefs = varDefs, functionDefs = map)
  }

  val types: Lens[CompilerContext, Map[String, PredefBase]] = lens[
    CompilerContext] >> 'predefTypes
  val vars: Lens[CompilerContext, Map[String, TYPE]] = lens[CompilerContext] >> 'varDefs
  val functions
    : Lens[CompilerContext, Map[String, Seq[FunctionTypeSignature]]] = lens[
    CompilerContext] >> 'functionDefs
}
