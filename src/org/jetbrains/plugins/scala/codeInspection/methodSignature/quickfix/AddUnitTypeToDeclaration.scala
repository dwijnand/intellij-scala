package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
 * Nikolay.Tropin
 * 6/24/13
 */
class AddUnitTypeToDeclaration(functionDecl: ScFunctionDeclaration)
        extends AbstractFixOnPsiElement(InspectionBundle.message("add.unit.type.to.declaration"), functionDecl) {

  def doApplyFix(project: Project) {
    val funDef = getElement
    implicit val manager = funDef.getManager
    funDef.getNode.addChild(createColon.getNode)
    funDef.getNode.addChild(createWhitespace.getNode)
    funDef.getNode.addChild(createTypeElementFromText("Unit").getNode)
  }
}
