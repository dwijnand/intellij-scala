package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

import scala.collection.Set

trait ResolvableStableCodeReferenceElement

object ResolvableStableCodeReferenceElement {

  implicit class Ext(val stableRef: ScStableCodeReferenceElement) extends AnyVal {

    private def getProject = stableRef.getProject

    private def getContext = stableRef.getContext

    private def getText = stableRef.getText

    private implicit def typeSystem: TypeSystem = stableRef.projectContext

    @CachedWithRecursionGuard(stableRef, Array.empty, ModCount.getBlockModificationCount)
    def resolveTypesOnly(incomplete: Boolean): Array[ResolveResult] = {
      val importResolverNoMethods = new StableCodeReferenceElementResolver(stableRef, false, false, false) {
        override protected def getKindsFor(ref: ScStableCodeReferenceElement): Set[ResolveTargets.Value] = {
          ref.getKinds(incomplete = false) -- StdKinds.methodRef
        }
      }
      importResolverNoMethods.resolve(stableRef, incomplete)
    }

    @CachedWithRecursionGuard(stableRef, Array.empty, ModCount.getBlockModificationCount)
    def resolveMethodsOnly(incomplete: Boolean): Array[ResolveResult] = {
      val importResolverNoTypes = new StableCodeReferenceElementResolver(stableRef, false, false, false) {
        override protected def getKindsFor(ref: ScStableCodeReferenceElement): Set[ResolveTargets.Value] = {
          ref.getKinds(incomplete = false) -- StdKinds.stableClass
        }
      }

      importResolverNoTypes.resolve(stableRef, incomplete)
    }

    @CachedWithRecursionGuard(stableRef, EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def resolveNoConstructor: Array[ResolveResult] = {
      ProgressManager.checkCanceled()
      val noConstructorResolver = new StableCodeReferenceElementResolver(stableRef, false, false, true)
      noConstructorResolver.resolve(stableRef, incomplete = false)
    }

    @CachedWithRecursionGuard(stableRef, EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def resolveAllConstructors: Array[ResolveResult] = {
      ProgressManager.checkCanceled()
      val resolverAllConstructors = new StableCodeReferenceElementResolver(stableRef, false, true, false)
      resolverAllConstructors.resolve(stableRef, incomplete = false)
    }

    @CachedWithRecursionGuard(stableRef, EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def shapeResolve: Array[ResolveResult] = {
      ProgressManager.checkCanceled()
      val shapesResolver = new StableCodeReferenceElementResolver(stableRef, true, false, false)
      shapesResolver.resolve(stableRef, incomplete = false)
    }

    @CachedWithRecursionGuard(stableRef, EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def shapeResolveConstr: Array[ResolveResult] = {
      ProgressManager.checkCanceled()
      val shapesResolverAllConstructors = new StableCodeReferenceElementResolver(stableRef, true, true, false)
      shapesResolverAllConstructors.resolve(stableRef, incomplete = false)
    }
  }

  val EMPTY_ARRAY: Array[ResolveResult] = Array.empty[ResolveResult]
}
