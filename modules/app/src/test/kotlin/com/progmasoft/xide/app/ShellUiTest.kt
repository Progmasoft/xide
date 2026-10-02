/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.progmasoft.xide.compiler.CompilerDiagnostic
import com.progmasoft.xide.compiler.DiagnosticDocument
import com.progmasoft.xide.compiler.DiagnosticSeverity
import com.progmasoft.xide.compiler.DiagnosticStage
import com.progmasoft.xide.compiler.SourceLocation
import com.progmasoft.xide.compiler.SourcePosition
import com.progmasoft.xide.compiler.SourceRange
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the tool windows, the tab strip, the gutter and the status bar of the real Compose shell.
 *
 * The model tests prove each rule in isolation; these prove that the surfaces are wired to those models: a click
 * in the Project tool window opens a document, a stripe button hides a tool window, a modified tab asks before it
 * closes, and the gutter and status bar follow the text.
 */
@OptIn(ExperimentalTestApi::class)
class ShellUiTest {
  private fun project(vararg files: Pair<String, String>): Path {
    val root = createTempDirectory("xide-shell-ui")
    for ((relative, content) in files) {
      val file = root.resolve(relative)
      Files.createDirectories(file.parent)
      Files.writeString(file, content)
    }
    return root
  }

  private fun ComposeUiTest.awaitText(text: String) {
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun theProjectToolWindowBrowsesAndOpensSources() = runComposeUiTest {
    val root = project("src/Main.vxs" to "namespace Demo;\n", "README.md" to "notes\n", "build/out.txt" to "")
    Files.writeString(root.resolve(".gitignore"), "build/\n")
    val session = WorkspaceSession()
    setContent { XideApplication(owner = null, session = session, initialProject = ProjectTree(root)) }

    // The opened folder names the tool window; the excluded directory is not listed.
    onAllNodesWithText(root.fileName.toString()).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    onNodeWithText("src").assertExists()
    onNodeWithText("README.md").assertExists()
    onNodeWithText("build").assertDoesNotExist()
    onNodeWithText("Main.vxs").assertDoesNotExist()

    onNodeWithText("src").performClick()
    awaitText("Main.vxs")

    onNodeWithText("Main.vxs").performClick()
    awaitText("Opened Main.vxs")
    assertEquals("namespace Demo;\n", session.snapshot().activeDocument?.snapshot?.text)
    onNode(hasSetTextAction()).assertTextEquals("namespace Demo;\n")

    // A file of no recognized language is declined with a reason instead of being opened as something it is not.
    onNodeWithText("README.md").performClick()
    awaitText("README.md is not a source file Xide can open")
    assertEquals(1, session.snapshot().documents.size)

    // The name is now shown by the tree row, the tab and the Problems header. Collapsing the directory removes
    // only the tree row; the document stays open.
    assertEquals(3, onAllNodesWithText("Main.vxs").fetchSemanticsNodes().size)
    onNodeWithText("src").performClick()
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Main.vxs").fetchSemanticsNodes().size == 2 }
    assertEquals(1, session.snapshot().documents.size)
  }

  @Test
  fun reloadShowsFilesCreatedOutsideTheEditor() = runComposeUiTest {
    val root = project("A.vxs" to "")
    setContent { XideApplication(owner = null, initialProject = ProjectTree(root)) }
    onNodeWithText("A.vxs").assertExists()

    Files.writeString(root.resolve("B.vxs"), "")
    onNodeWithText("B.vxs").assertDoesNotExist()
    onNodeWithContentDescription("Reload project files").performClick()
    awaitText("B.vxs")
  }

  @Test
  fun stripeButtonsAndHeadersShowAndHideToolWindows() = runComposeUiTest {
    setContent { XideApplication(owner = null) }
    onNodeWithText("No folder is open.").assertExists()
    onNodeWithText("No document is open.").assertExists()

    onNodeWithContentDescription("Project tool window").performClick()
    onNodeWithText("No folder is open.").assertDoesNotExist()
    onNodeWithText("No document is open.").assertExists()

    onNodeWithContentDescription("Problems tool window").performClick()
    onNodeWithText("No document is open.").assertDoesNotExist()

    onNodeWithContentDescription("Project tool window").performClick()
    onNodeWithContentDescription("Problems tool window").performClick()
    onNodeWithText("No folder is open.").assertExists()
    onNodeWithText("No document is open.").assertExists()

    // Each tool window can also hide itself from its own header.
    onNodeWithContentDescription("Hide Project").performClick()
    onNodeWithText("No folder is open.").assertDoesNotExist()
    onNodeWithContentDescription("Hide Problems").performClick()
    onNodeWithText("No document is open.").assertDoesNotExist()
  }

  @Test
  fun keyboardShortcutsToggleToolWindows() = runComposeUiTest {
    val session = WorkspaceSession()
    session.newScratch()
    setContent { XideApplication(owner = null, session = session) }
    // Key events are delivered to the focused node, so give the editor focus first.
    onNode(hasSetTextAction()).performClick()
    onNodeWithText("No folder is open.").assertExists()

    onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.One) } }
    onNodeWithText("No folder is open.").assertDoesNotExist()
    onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.One) } }
    onNodeWithText("No folder is open.").assertExists()

    onNodeWithContentDescription("Hide Problems").assertExists()
    onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.Six) } }
    onNodeWithContentDescription("Hide Problems").assertDoesNotExist()
  }

  @Test
  fun anUnmodifiedTabClosesImmediately() = runComposeUiTest {
    val root = project("A.vxs" to "a\n", "B.vxs" to "b\n")
    val session = WorkspaceSession()
    session.openFile(root.resolve("A.vxs"))
    session.openFile(root.resolve("B.vxs"))
    setContent { XideApplication(owner = null, session = session) }

    onNodeWithContentDescription("Close A.vxs").performClick()
    waitForIdle()
    assertEquals(listOf("B.vxs"), session.snapshot().documents.map { it.title })
    onNodeWithContentDescription("Close A.vxs").assertDoesNotExist()
    onNodeWithText("Closed A.vxs").assertExists()

    onNodeWithContentDescription("Close B.vxs").performClick()
    waitForIdle()
    assertTrue(session.snapshot().documents.isEmpty())
    onNodeWithText("Create or open a file to begin editing.").assertExists()
  }

  @Test
  fun aModifiedTabAsksBeforeItCloses() = runComposeUiTest {
    val root = project("A.vxs" to "a\n")
    val session = WorkspaceSession()
    session.openFile(root.resolve("A.vxs"))
    setContent { XideApplication(owner = null, session = session) }

    onNode(hasSetTextAction()).performTextInput("edited ")
    waitForIdle()
    onNodeWithContentDescription("Close A.vxs").performClick()
    waitForIdle()

    // Nothing is closed and nothing is lost while the question is open.
    onNodeWithText("A.vxs has unsaved changes.").assertExists()
    assertEquals(1, session.snapshot().documents.size)

    onNodeWithText("Cancel").performClick()
    waitForIdle()
    onNodeWithText("A.vxs has unsaved changes.").assertDoesNotExist()
    assertEquals("edited a\n", session.snapshot().activeDocument?.snapshot?.text)

    onNodeWithContentDescription("Close A.vxs").performClick()
    waitForIdle()
    onNodeWithText("Discard Changes").performClick()
    waitForIdle()
    assertTrue(session.snapshot().documents.isEmpty())
    // The file on disk still has what was last saved.
    assertEquals("a\n", Files.readString(root.resolve("A.vxs")))
  }

  @Test
  fun saveAndCloseWritesTheTextBeforeTheTabGoesAway() = runComposeUiTest {
    val root = project("A.vxs" to "a\n")
    val session = WorkspaceSession()
    session.openFile(root.resolve("A.vxs"))
    setContent { XideApplication(owner = null, session = session) }

    onNode(hasSetTextAction()).performTextInput("kept ")
    waitForIdle()
    onNodeWithContentDescription("Close A.vxs").performClick()
    waitForIdle()
    onNodeWithText("Save and Close").performClick()
    waitUntil(timeoutMillis = 10_000) { session.snapshot().documents.isEmpty() }

    assertEquals("kept a\n", Files.readString(root.resolve("A.vxs")))
  }

  @Test
  fun theGutterAndStatusBarFollowTheText() = runComposeUiTest {
    val root = project("A.vxs" to "one\ntwo\n")
    val session = WorkspaceSession()
    session.openFile(root.resolve("A.vxs"))
    setContent { XideApplication(owner = null, session = session) }

    onNodeWithContentDescription("Line numbers").assertTextEquals("1\n2\n3")
    onNodeWithContentDescription("Caret position").assertTextEquals("1:1")
    onNodeWithContentDescription("Line separator").assertTextEquals("LF")
    onNodeWithContentDescription("File encoding").assertTextEquals("UTF-8")
    onNodeWithContentDescription("Language").assertTextEquals("Visual X#")

    // Typing at the start moves the caret and adds a line to the gutter.
    onNode(hasSetTextAction()).performTextInput("x\ny")
    waitForIdle()
    onNodeWithContentDescription("Line numbers").assertTextEquals("1\n2\n3\n4")
    onNodeWithContentDescription("Caret position").assertTextEquals("2:2")
  }

  @Test
  fun problemsMarkTheirLinesAndAreCounted() = runComposeUiTest {
    val root = project("A.vxs" to "one\ntwo\nthree\n")
    val file = root.resolve("A.vxs")
    val session = WorkspaceSession()
    val opened = session.openFile(file).activeDocument!!
    fun diagnostic(severity: DiagnosticSeverity, line: UInt, message: String) =
      CompilerDiagnostic(
        stage = DiagnosticStage.TYPE_CHECKER,
        severity = severity,
        code = "VXS-TEST",
        message = message,
        arguments = emptyList(),
        primaryLocation = SourceLocation(file.toString(), SourceRange(SourcePosition(line, 0u), SourcePosition(line, 1u))),
        relatedLocations = emptyList(),
        fixes = emptyList(),
      )
    check(
      session.publishDiagnostics(
        opened.snapshot.uri,
        opened.snapshot.version,
        DiagnosticDocument(
          listOf(
            diagnostic(DiagnosticSeverity.ERROR, 1u, "broken line"),
            diagnostic(DiagnosticSeverity.WARNING, 2u, "doubtful line"),
          )
        ),
      )
    )
    setContent { XideApplication(owner = null, session = session) }

    onNodeWithContentDescription("Problem markers").assertTextEquals(" \n●\n●\n ")
    onNodeWithContentDescription("Problems in the active document").assertTextEquals("1 error, 1 warning")
    onNodeWithText("broken line").assertExists()
    onNodeWithText("A.vxs:2:1").assertExists()
    onNodeWithText("A.vxs  1 error, 1 warning").assertExists()

    // An edit makes the check stale: markers, rows and counts all go away together.
    onNode(hasSetTextAction()).performTextInput("x")
    waitForIdle()
    onNodeWithContentDescription("Problem markers").assertTextEquals(" \n \n \n ")
    onNodeWithContentDescription("Problems in the active document").assertDoesNotExist()
    onNodeWithText("broken line").assertDoesNotExist()
    onNodeWithText("Run Check to see the problems of A.vxs.").assertExists()
  }

  @Test
  fun newCreatesAScratchTabFromTheToolbar() = runComposeUiTest {
    val session = WorkspaceSession()
    setContent { XideApplication(owner = null, session = session) }

    onNodeWithText("New").performClick()
    waitForIdle()
    assertEquals(listOf("Xide-1.vxs"), session.snapshot().documents.map { it.title })
    onNodeWithText("Created Xide-1.vxs").assertExists()
    onNodeWithContentDescription("Close Xide-1.vxs").assertExists()
  }

  @Test
  fun kotlinSourcesOpenAndEditButAreNotChecked() = runComposeUiTest {
    val root = project("src/App.kt" to "fun main() {}\n", "build.gradle.kts" to "plugins {}\n")
    val session = WorkspaceSession()
    setContent { XideApplication(owner = null, session = session, initialProject = ProjectTree(root)) }

    onNodeWithText("build.gradle.kts").performClick()
    awaitText("Opened build.gradle.kts")
    onNodeWithContentDescription("Language").assertTextEquals("Kotlin")
    onNode(hasSetTextAction()).assertTextEquals("plugins {}\n")

    onNode(hasSetTextAction()).performTextInput("// edited\n")
    waitForIdle()
    assertEquals("// edited\nplugins {}\n", session.snapshot().activeDocument?.snapshot?.text)

    // The compiler check belongs to Visual X#; for Kotlin the action is unavailable rather than misleading.
    onNodeWithText("Check").performClick()
    waitForIdle()
    assertEquals(null, session.snapshot().activeDocument?.diagnostics)
    onNodeWithText("Run Check to see the problems of build.gradle.kts.").assertExists()
  }
}
