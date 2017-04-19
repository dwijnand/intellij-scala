package org.jetbrains.plugins.scala.debugger

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.TestFixtureProvider
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author Nikolay.Tropin
 */
sealed trait ScalaVersion {
  val major: String
  val minor: String
}

case object Scala_2_10 extends ScalaVersion {
  override final val major: String = "2.10"
  override final val minor: String = "2.10.6"
}

case object Scala_2_11 extends ScalaVersion {
  override final val major: String = "2.11"
  override final val minor: String = "2.11.7"
}

case object Scala_2_11_8 extends ScalaVersion {
  override final val major: String = "2.11"
  override final val minor: String = "2.11.8"
}

case object Scala_2_12 extends ScalaVersion {
  override final val major: String = "2.12"
  override final val minor: String = "2.12.0"
}

trait ScalaSdkOwner {
  implicit val version: ScalaVersion

  implicit protected def project: Project

  implicit protected def module: Module

  protected def librariesLoaders: Seq[LibraryLoader]

  protected def setUpLibraries(): Unit =
    librariesLoaders.foreach(_.init)

  protected def tearDownLibraries(): Unit =
    librariesLoaders.foreach(_.clean())
}

// Java compatibility
trait DefaultScalaSdkOwner extends ScalaSdkOwner with TestFixtureProvider {
  override implicit val version: ScalaVersion = Scala_2_10
}
