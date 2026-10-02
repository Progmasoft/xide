/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceLanguageTest {
  @Test
  fun languagesAreRecognizedFromTheExactExtension() {
    assertEquals(SourceLanguage.VISUAL_XSHARP, SourceLanguage.ofFileName("Main.vxs"))
    assertEquals(SourceLanguage.KOTLIN, SourceLanguage.ofFileName("App.kt"))
    assertEquals(SourceLanguage.KOTLIN, SourceLanguage.ofFileName("build.gradle.kts"))
    assertEquals(SourceLanguage.JAVA, SourceLanguage.ofFileName("Main.java"))
    assertEquals(SourceLanguage.GROOVY, SourceLanguage.ofFileName("build.gradle"))
    assertEquals(SourceLanguage.GROOVY, SourceLanguage.ofFileName("Script.groovy"))
    assertEquals(SourceLanguage.PYTHON, SourceLanguage.ofFileName("tool.py"))
    assertEquals(SourceLanguage.KOTLIN, SourceLanguage.of(Path.of("src", "App.kt")))
  }

  @Test
  fun unknownAndMalformedNamesHaveNoLanguage() {
    assertNull(SourceLanguage.ofFileName("README.md"))
    assertNull(SourceLanguage.ofFileName("Main.VXS"))
    assertNull(SourceLanguage.ofFileName("Main.vxs.bak"))
    assertNull(SourceLanguage.ofFileName("Makefile"))
    assertNull(SourceLanguage.ofFileName("trailing."))
    // A leading dot names a hidden file, not an extension.
    assertNull(SourceLanguage.ofFileName(".kt"))
    assertNull(SourceLanguage.ofFileName(""))
  }

  @Test
  fun theLanguageOrderIsTheSupportPriority() {
    assertEquals(
      listOf("Visual X#", "Kotlin", "Java", "Groovy", "Python"),
      SourceLanguage.entries.map { it.displayName },
    )
    assertEquals(listOf("vxs", "kt", "kts", "java", "groovy", "gradle", "py"), SourceLanguage.allExtensions)
    assertEquals(SourceLanguage.allExtensions.size, SourceLanguage.allExtensions.distinct().size)
    assertTrue(SourceLanguage.supportedDescription().startsWith("Visual X# (.vxs), Kotlin (.kt, .kts)"))
  }

  @Test
  fun theSessionOpensEverySupportedLanguageAndNothingElse() {
    val directory = createTempDirectory("xide-language")
    val session = WorkspaceSession()
    for (name in listOf("Main.vxs", "App.kt", "build.gradle.kts", "Main.java", "build.gradle", "tool.py")) {
      Files.writeString(directory.resolve(name), "text of $name")
      val opened = session.openFile(directory.resolve(name))
      assertEquals("text of $name", opened.activeDocument?.snapshot?.text)
      assertEquals(SourceLanguage.ofFileName(name), opened.activeDocument?.language)
    }
    assertEquals(6, session.snapshot().documents.size)

    Files.writeString(directory.resolve("README.md"), "notes")
    val failure = assertFailsWith<IllegalArgumentException> { session.openFile(directory.resolve("README.md")) }
    assertTrue(failure.message!!.contains("Kotlin (.kt, .kts)"))
    assertEquals(6, session.snapshot().documents.size)
  }

  @Test
  fun aKotlinDocumentSavesInPlaceAndCannotBecomeAnUnknownFile() {
    val directory = createTempDirectory("xide-language")
    val file = directory.resolve("App.kt")
    Files.writeString(file, "fun main() {}\n")
    val session = WorkspaceSession()
    session.openFile(file)
    session.replaceActiveText("fun main() = Unit\n")

    val saved = session.saveActive()
    assertEquals("fun main() = Unit\n", Files.readString(file))
    assertEquals(false, saved.activeDocument?.isDirty)

    assertFailsWith<IllegalArgumentException> { session.saveActive(directory.resolve("App.txt")) }
    assertEquals(false, Files.exists(directory.resolve("App.txt")))
  }
}
