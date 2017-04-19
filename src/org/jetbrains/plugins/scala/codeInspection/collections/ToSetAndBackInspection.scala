package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * @author Nikolay.Tropin
 */
class ToSetAndBackInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ToSetAndBackToDistinct)
}

object ToSetAndBackToDistinct extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.toSet.and.back.with.distinct")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    import expr.projectContext
    expr match {
      case (qual@Typeable(qualType)) `.toSet` () `.toCollection` ()
        if sameCollectionType(qualType, expr.getType().getOrAny) && (isSeq(qual) || isArray(qual)) =>
        Some(replace(expr).withText(invocationText(qual, "distinct")).highlightFrom(qual))
      case _ => None
    }
  }

  def sameCollectionType(tp1: ScType, tp2: ScType)
                        (implicit typeSystem: TypeSystem): Boolean = {
    (tp1, tp2) match {
      case (ParameterizedType(des1, _), ParameterizedType(des2, _)) if des1.equiv(des2) => true
      case _ => false
    }
  }
}
