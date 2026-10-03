/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import java.nio.file.Files
import org.jetbrains.kotlin.com.intellij.core.JavaCoreApplicationEnvironment
import org.jetbrains.kotlin.com.intellij.core.JavaCoreProjectEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

/**
 * The IntelliJ core that parses source text into PSI.
 *
 * IntelliJ's PSI needs an application and a project to exist before a file can be parsed. Inside the IDE the
 * platform provides them; here they are the small "core" environment that the platform ships for command-line
 * tools. It has no editor, no index and no plugin system: it can lex, parse and hand out a syntax tree, which is
 * all this module asks of it.
 *
 * The environment is created once, on first use, and lives as long as the process. Creating it takes most of a
 * second, so an application that is about to need it can call [warmUp] from a background thread.
 *
 * The core environment was designed for single-threaded tools. Every use goes through [withProject], which runs
 * one action at a time.
 */
internal object PsiPlatform {
  private val lock = Any()
  private var environment: JavaCoreProjectEnvironment? = null

  /** Creates the environment now when it does not exist yet. */
  fun warmUp() {
    withProject {}
  }

  /** Runs [action] with the project of the environment, creating the environment first when needed. */
  fun <T> withProject(action: (Project) -> T): T =
    synchronized(lock) {
      val current = environment ?: create().also { environment = it }
      action(current.project)
    }

  private fun create(): JavaCoreProjectEnvironment {
    configureStandaloneUse()
    // The environment is never disposed: its lifetime is that of the process.
    val lifetime: Disposable = Disposer.newDisposable("Xide PSI platform")
    val application = JavaCoreApplicationEnvironment(lifetime)
    // Java is registered by the Java core environment itself.
    application.registerFileType(KotlinFileType.INSTANCE, "kt")
    application.registerParserDefinition(KotlinParserDefinition())
    application.registerFileType(GroovyPsiFileType, "groovy")
    application.registerParserDefinition(GroovyPsiParserDefinition())
    application.registerFileType(VisualXSharpFileType, "vxs")
    application.registerParserDefinition(VisualXSharpParserDefinition())
    return JavaCoreProjectEnvironment(lifetime, application)
  }

  /**
   * Tells the platform that it is not running inside an installed IDE.
   *
   * The core reads a few system properties while it starts. Without a home path it looks for the files of an IDE
   * installation and fails; an empty directory satisfies it. A property the host already set is left alone.
   */
  private fun configureStandaloneUse() {
    setIfAbsent("idea.ignore.disabled.plugins", "true")
    if (System.getProperty("idea.home.path") == null) {
      val home = Files.createTempDirectory("xide-psi-platform")
      home.toFile().deleteOnExit()
      System.setProperty("idea.home.path", home.toString())
    }
  }

  private fun setIfAbsent(name: String, value: String) {
    if (System.getProperty(name) == null) System.setProperty(name, value)
  }
}
