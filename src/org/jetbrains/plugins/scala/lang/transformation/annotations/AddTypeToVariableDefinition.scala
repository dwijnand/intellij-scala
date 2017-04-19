package org.jetbrains.plugins.scala.lang.transformation.annotations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.transformation._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class AddTypeToVariableDefinition extends AbstractTransformer {
  def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (_: ScReferencePattern) && Parent(l@Parent(_: ScVariableDefinition)) && Typeable(t)
      if !l.nextSibling.exists(_.getText == ":") =>

      appendTypeAnnotation(l, t)
  }
}
