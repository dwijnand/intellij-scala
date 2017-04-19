package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignStmt, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.DynamicTypeReferenceResolver.getAllResolveResult

object DynamicResolveProcessor {

  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC = "applyDynamic"
  val SELECT_DYNAMIC = "selectDynamic"
  val UPDATE_DYNAMIC = "updateDynamic"
  val NAMED = "Named"

  def getDynamicReturn: ScType => ScType = {
    case methodType: ScMethodType => methodType.returnType
    case ScTypePolymorphicType(methodType: ScMethodType, parameters) =>
      ScTypePolymorphicType(getDynamicReturn(methodType), parameters)(methodType.typeSystem)
    case scType => scType
  }

  def getDynamicNameForMethodInvocation(call: MethodInvocation): String = {
    val arguments = call.argumentExpressions.collect {
      case statement: ScAssignStmt => statement.getLExpression
    }

    if (arguments.exists {
      case reference: ScReferenceExpression => reference.qualifier.isEmpty
      case _ => false
    }) APPLY_DYNAMIC_NAMED else APPLY_DYNAMIC
  }

  def isDynamicReference(reference: ScReferenceExpression): Boolean = {
    implicit val typeSystem: TypeSystem = reference.projectContext

    def qualifierType() = reference.qualifier
      .flatMap(_.getNonValueType().toOption)

    def cachedClassType() =
      ScalaPsiManager.instance(reference.getProject)
        .getCachedClass(reference.getResolveScope, "scala.Dynamic")
        .map(ScDesignatorType(_))

    qualifierType().zip(cachedClassType()).exists {
      case (qualifierType, classType) => qualifierType.conforms(classType)
    }
  }

  def resolveDynamic(reference: ScReferenceExpression): Seq[ResolveResult] = {
    import scala.collection.JavaConversions._
    getAllResolveResult(reference)
  }
}

