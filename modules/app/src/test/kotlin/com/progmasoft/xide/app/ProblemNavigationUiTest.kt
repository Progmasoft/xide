/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.progmasoft.xide.compiler.CompilerDiagnostic
import com.progmasoft.xide.compiler.DiagnosticDocument
import com.progmasoft.xide.compiler.DiagnosticSeverity
import com.progmasoft.xide.compiler.DiagnosticStage
import com.progmasoft.xide.compiler.SourceLocation
import com.progmasoft.xide.compiler.SourcePosition
import com.progmasoft.xide.compiler.SourceRange
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the real Compose shell: a click on a rendered problem must move the editor selection.
 *
 * The session tests prove that a location resolves to the right offsets; this proves that the Problems surface, the
 * session and the editor are actually wired together.
 */
@OptIn(ExperimentalTestApi::class)
class ProblemNavigationUiTest {
  private fun diagnostic(message: String, location: SourceLocation) =
    CompilerDiagnostic(
      stage = DiagnosticStage.TYPE_CHECKER,
      severity = DiagnosticSeverity.ERROR,
      code = "VXS-TEST",
      message = message,
      arguments = emptyList(),
      primaryLocation = location,
      relatedLocations = emptyList(),
      fixes = emptyList(),
    )

  private fun location(source: String, line: UInt, start: UInt, end: UInt) =
    SourceLocation(source, SourceRange(SourcePosition(line, start), SourcePosition(line, end)))

  @Test
  fun clickingAProblemSelectsItsRangeInTheEditor() = runComposeUiTest {
    val file = createTempDirectory("xide-ui").resolve("Main.vxs")
    // `value` starts at scalar column 5 but UTF-16 column 6 because of the emoji before it.
    Files.writeString(file, "line one\n-- 😀 value here\n")
    val session = WorkspaceSession()
    val opened = session.openFile(file).activeDocument!!
    val published =
      session.publishDiagnostics(
        opened.snapshot.uri,
        opened.snapshot.version,
        DiagnosticDocument(
          listOf(
            diagnostic("first problem", location(file.toString(), 1u, 5u, 10u)),
            diagnostic("second problem", location(file.toString(), 0u, 0u, 4u)),
            diagnostic("outside problem", location(file.toString(), 9u, 0u, 1u)),
          )
        ),
      )
    check(published)

    setContent { XideApplication(owner = null, session = session) }
    val editor = onNode(hasSetTextAction())
    fun selection(): TextRange? = editor.fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)

    assertEquals(TextRange(0, 0), selection())

    onNodeWithText("first problem", substring = true).performClick()
    waitForIdle()
    onNodeWithText("Showing Main.vxs").assertExists()
    assertEquals(TextRange(15, 20), selection())

    onNodeWithText("second problem", substring = true).performClick()
    waitForIdle()
    assertEquals(TextRange(0, 4), selection())

    // Clicking the same problem again after the caret moved must select it again.
    onNodeWithText("first problem", substring = true).performClick()
    waitForIdle()
    assertEquals(TextRange(15, 20), selection())

    // The same must hold while the editor has keyboard focus, where a text field otherwise keeps its own caret.
    editor.performClick()
    waitForIdle()
    onNodeWithText("second problem", substring = true).performClick()
    waitForIdle()
    assertEquals(TextRange(0, 4), selection())
    onNodeWithText("first problem", substring = true).performClick()
    waitForIdle()
    assertEquals(TextRange(15, 20), selection())

    // A location outside the document is declined and leaves the selection alone.
    onNodeWithText("outside problem", substring = true).performClick()
    waitForIdle()
    assertEquals(TextRange(15, 20), selection())
    onNodeWithText("This location is not in an open, unmodified document").assertExists()
  }

  @Test
  fun editingTheDocumentRemovesItsProblems() = runComposeUiTest {
    val file = createTempDirectory("xide-ui").resolve("Main.vxs")
    Files.writeString(file, "abc\n")
    val session = WorkspaceSession()
    val opened = session.openFile(file).activeDocument!!
    check(
      session.publishDiagnostics(
        opened.snapshot.uri,
        opened.snapshot.version,
        DiagnosticDocument(listOf(diagnostic("stale after edit", location(file.toString(), 0u, 0u, 1u)))),
      )
    )

    setContent { XideApplication(owner = null, session = session) }
    onNodeWithText("stale after edit", substring = true).assertExists()

    // Typing changes the document version, so its diagnostics no longer describe the text on screen.
    onNode(hasSetTextAction()).performTextInput("x")
    waitForIdle()
    onNodeWithText("stale after edit", substring = true).assertDoesNotExist()
    assertEquals(1L, session.snapshot().activeDocument?.snapshot?.version)
  }
}
