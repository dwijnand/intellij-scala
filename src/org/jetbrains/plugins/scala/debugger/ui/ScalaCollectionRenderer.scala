package org.jetbrains.plugins.scala.debugger.ui

import java.util

import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, TypeEvaluator}
import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluateException, EvaluationContext, TextWithImportsImpl}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render._
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptor, NodeManager, ValueDescriptor}
import com.intellij.debugger.{DebuggerBundle, DebuggerContext}
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.psi.PsiElement
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaDuplexEvaluator, ScalaFieldEvaluator, ScalaMethodEvaluator, ScalaThisEvaluator}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.ScalaCollectionRenderer._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.collection.mutable
import scala.reflect.NameTransformer

/**
 * @author Nikolay.Tropin
 */

class ScalaCollectionRenderer extends CompoundReferenceRenderer(NodeRendererSettings.getInstance(), "Scala collection", sizeLabelRenderer, ScalaToArrayRenderer) {

  setClassName(collectionClassName)


  override def isApplicable(tp: Type): Boolean = {
    super.isApplicable(tp) && notStream(tp) && notView(tp)
  }

  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().FRIENDLY_COLLECTION_DISPLAY_ENABLED
}

object ScalaCollectionRenderer {
  implicit class ToExpressionEvaluator(val e: Evaluator) extends AnyVal {
    def exprEval = new ExpressionEvaluatorImpl(e)
  }

  private val evaluators: mutable.HashMap[DebugProcess, CachedEvaluators] = new mutable.HashMap()

  private def cachedEvaluators(context: EvaluationContext): CachedEvaluators = {
    val debugProcess = context.getDebugProcess
    evaluators.get(debugProcess).orElse {
      if (debugProcess.isDetached || debugProcess.isDetaching) None
      else {
        val value = new CachedEvaluators
        evaluators.put(debugProcess, value)
        debugProcess.addDebugProcessListener(new DebugProcessAdapterImpl {
          override def processDetached(process: DebugProcessImpl, closedByUser: Boolean): Unit = evaluators.remove(debugProcess)
        })
        Some(value)
      }
    }.getOrElse(new CachedEvaluators)
  }

  private def hasDefiniteSizeEval(context: EvaluationContext) = cachedEvaluators(context).hasDefiniteSizeEval
  private def nonEmptyEval(context: EvaluationContext) = cachedEvaluators(context).nonEmptyEval
  private def sizeEval(context: EvaluationContext) = cachedEvaluators(context).sizeEval

  val collectionClassName = "scala.collection.GenIterable"
  val streamClassName = "scala.collection.immutable.Stream"
  val streamViewClassName = "scala.collection.immutable.StreamView"
  val viewClassName = "scala.collection.IterableView"
  val iteratorClassName = "scala.collection.Iterator"

  val sizeLabelRenderer = createSizeLabelRenderer()

  def hasDefiniteSize(value: Value, evaluationContext: EvaluationContext): Boolean = {
    value.`type`() match {
      case ct: ClassType if ct.name.startsWith("scala.collection") && notStream(ct) && notIterator(ct) => true
      case _ => evaluateBoolean(value, evaluationContext, hasDefiniteSizeEval(evaluationContext))
    }
  }

  def nonEmpty(value: Value, evaluationContext: EvaluationContext): Boolean = {
    value.`type`() match {
      case ct: ClassType if ct.name.toLowerCase.contains("empty") || ct.name.contains("Nil") => false
      case _ => evaluateBoolean(value, evaluationContext, nonEmptyEval(evaluationContext))
    }
  }

  def size(value: Value, evaluationContext: EvaluationContext): Int = evaluateInt(value, evaluationContext, sizeEval(evaluationContext))

  private def checkNotCollectionOfKind(tp: Type, shortName: String, baseClassName: String) = !tp.name.contains(shortName) && !DebuggerUtils.instanceOf(tp, baseClassName)

  private def notView(tp: Type): Boolean = checkNotCollectionOfKind(tp, "View", viewClassName)

  private def notStream(tp: Type): Boolean = checkNotCollectionOfKind(tp, "Stream", streamClassName)

  private def notIterator(tp: Type): Boolean = checkNotCollectionOfKind(tp, "Iterator", iteratorClassName)
  /**
   * util method for collection displaying in debugger
    *
    * @param name name encoded for jvm (for example, scala.collection.immutable.$colon$colon)
   * @return decoded nonqualified part (:: in example)
   */
  def transformName(name: String): String = getNonQualifiedName(NameTransformer decode name)

  private def getNonQualifiedName(fullName: String): String = {
    val index =
      if (fullName endsWith "`") fullName.substring(0, fullName.length - 1).lastIndexOf('`')
      else fullName.lastIndexOf('.')
    "\"" + fullName.substring(index + 1) + "\""
  }

  private def createSizeLabelRenderer(): LabelRenderer = {
    val expressionText = "size()"
    val sizePrefix = " size = "
    val labelRenderer: LabelRenderer = new LabelRenderer() {
      override def calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, labelListener: DescriptorLabelListener): String = {
        descriptor.getValue match {
          case null => "null"
          case objRef: ObjectReference =>
            val typeName = if (objRef.referenceType() != null) ScalaCollectionRenderer.transformName(objRef.referenceType().name) else ""
            val sizeValue =
              if (!hasDefiniteSize(objRef, evaluationContext)) "?"
              else size(objRef, evaluationContext)
            typeName + sizePrefix + sizeValue
        }
      }
    }
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", StdFileTypes.JAVA))
    labelRenderer
  }

  private def evaluateBoolean(value: Value, context: EvaluationContext, evaluator: Evaluator): Boolean = {
    evaluate(value, context, evaluator) match {
      case b: BooleanValue => b.booleanValue()
      case x => throw EvaluationException(s"$x is not a boolean")
    }
  }

  private def evaluateInt(value: Value, context: EvaluationContext, evaluator: Evaluator): Int = {
    evaluate(value, context, evaluator) match {
      case i: IntegerValue => i.intValue()
      case x => throw EvaluationException(s"$x is not an integer")
    }
  }

  private def evaluate(value: Value, context: EvaluationContext, evaluator: Evaluator) = {
    if (value != null) {
      val newContext = context.createEvaluationContext(value)
      evaluator.exprEval.evaluate(newContext)
    }
    else if (value != null) {
      val newContext = context.createEvaluationContext(value)
      evaluator.exprEval.evaluate(newContext) match {
        case b: BooleanValue => b.booleanValue()
        case _ => throw EvaluationException("Cannot evaluate expression")
      }
    }
    else throw EvaluationException("Cannot evaluate expression")
  }

  object ScalaToArrayRenderer extends ReferenceRenderer(collectionClassName) with ChildrenRenderer {

    private def toArrayEvaluator(context: EvaluationContext) = cachedEvaluators(context).toArrayEvaluator

    override def getUniqueId: String = "ScalaToArrayRenderer"

    override def isExpandable(value: Value, context: EvaluationContext, parentDescriptor: NodeDescriptor): Boolean = {
      val evaluationContext: EvaluationContext = context.createEvaluationContext(value)
      try {
        return nonEmpty(value, context) && hasDefiniteSize(value, context)
      }
      catch {
        case _: EvaluateException =>
      }

      try {
        val children: Value = evaluateChildren(evaluationContext, parentDescriptor)
        val defaultChildrenRenderer: ChildrenRenderer = DebugProcessImpl.getDefaultRenderer(value.`type`)
        defaultChildrenRenderer.isExpandable(children, evaluationContext, parentDescriptor)
      }
      catch {
        case _: EvaluateException =>
          true
      }
    }

    override def getChildValueExpression(node: DebuggerTreeNode, context: DebuggerContext): PsiElement = {
      createExpressionFromText("this.toArray()")(context.getProject)
    }


    override def buildChildren(value: Value, builder: ChildrenBuilder, evaluationContext: EvaluationContext) {
      val nodeManager: NodeManager = builder.getNodeManager
      try {
        val parentDescriptor: ValueDescriptor = builder.getParentDescriptor
        val childrenValue: Value = evaluateChildren(evaluationContext.createEvaluationContext(value), parentDescriptor)
        val renderer: NodeRenderer = getChildrenRenderer(childrenValue, parentDescriptor)
        renderer.buildChildren(childrenValue, builder, evaluationContext)
      }
      catch {
        case e: EvaluateException =>
          val errorChildren: util.ArrayList[DebuggerTreeNode] = new util.ArrayList[DebuggerTreeNode]
          errorChildren.add(nodeManager.createMessageNode(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + e.getMessage))
          builder.setChildren(errorChildren)
      }
    }

    private def getChildrenRenderer(childrenValue: Value, parentDescriptor: ValueDescriptor): NodeRenderer = {
      var renderer: NodeRenderer = ExpressionChildrenRenderer.getLastChildrenRenderer(parentDescriptor)
      if (renderer == null || childrenValue == null || !renderer.isApplicable(childrenValue.`type`)) {
        renderer = DebugProcessImpl.getDefaultRenderer(if (childrenValue != null) childrenValue.`type` else null)
        ExpressionChildrenRenderer.setPreferableChildrenRenderer(parentDescriptor, renderer)
      }
      renderer
    }

    private def evaluateChildren(context: EvaluationContext, descriptor: NodeDescriptor): Value = {
      val evaluator: ExpressionEvaluator = toArrayEvaluator(context).exprEval
      val value: Value = evaluator.evaluate(context)
      DebuggerUtilsEx.keep(value, context)
      value
    }
  }

  private class CachedEvaluators {
    val hasDefiniteSizeEval = ScalaMethodEvaluator(new ScalaThisEvaluator(), "hasDefiniteSize", JVMNameUtil.getJVMRawText("()Z"), Nil)
    val nonEmptyEval = ScalaMethodEvaluator(new ScalaThisEvaluator(), "nonEmpty", JVMNameUtil.getJVMRawText("()Z"), Nil)
    val sizeEval = ScalaMethodEvaluator(new ScalaThisEvaluator(), "size", JVMNameUtil.getJVMRawText("()I"), Nil)

    val toArrayEvaluator = {
      val classTagObjectEval = {
        val classTagEval = stableObjectEval("scala.reflect.ClassTag$")
        ScalaMethodEvaluator(classTagEval, "Object", null, Nil)
      }

      val manifestObjectEval = {
        val predefEval = stableObjectEval("scala.Predef$")
        val manifestEval = ScalaMethodEvaluator(predefEval, "Manifest", null, Nil)
        ScalaMethodEvaluator(manifestEval, "Object", null, Nil)
      }
      val argEval = ScalaDuplexEvaluator(classTagObjectEval, manifestObjectEval)

      ScalaMethodEvaluator(new ScalaThisEvaluator(), "toArray", null, Seq(argEval))
    }

    private def stableObjectEval(name: String) = ScalaFieldEvaluator(new TypeEvaluator(JVMNameUtil.getJVMRawText(name)), "MODULE$", classPrivateThisField = false)
  }
}


