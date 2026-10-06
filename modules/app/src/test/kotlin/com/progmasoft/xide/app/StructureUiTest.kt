/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the Structure tool window of the real Compose shell.
 *
 * The model tests prove how an outline becomes rows; these prove that the tool window is wired to the document:
 * it lists the declarations of the active document, follows an edit, moves the caret to a clicked declaration and
 * marks the declaration around the caret.
 */
@OptIn(ExperimentalTestApi::class)
class StructureUiTest {
  private val source = "namespace Demo;\npublic class Program {\n    int count;\n    public static void Main() { }\n}\n"

  private fun session(name: String, text: String): WorkspaceSession {
    val file = createTempDirectory("xide-structure").resolve(name)
    Files.writeString(file, text)
    return WorkspaceSession().also { it.openFile(file) }
  }

  private fun ComposeUiTest.awaitDescription(description: String) {
    waitUntil(timeoutMillis = 20_000) { onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.awaitNoDescription(description: String) {
    waitUntil(timeoutMillis = 20_000) { onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty() }
  }

  @Test
  fun theStructureToolWindowListsTheDeclarationsOfTheActiveDocument() = runComposeUiTest {
    setContent { XideApplication(owner = null, session = session("Main.vxs", source)) }

    // The tool window is closed until it is asked for.
    onNodeWithContentDescription("type Program").assertDoesNotExist()
    onNodeWithContentDescription("Structure tool window").performClick()

    awaitDescription("type Program")
    onNodeWithContentDescription("namespace Demo").assertExists()
    onNodeWithContentDescription("property count").assertExists()
    onNodeWithContentDescription("function Main").assertExists()

    onNodeWithContentDescription("Hide Structure").performClick()
    waitForIdle()
    onNodeWithContentDescription("type Program").assertDoesNotExist()
  }

  @Test
  fun clickingADeclarationMovesTheCaretToItAndMarksIt() = runComposeUiTest {
    setContent { XideApplication(owner = null, session = session("Main.vxs", source)) }
    val editor = onNode(hasSetTextAction())
    fun selection(): TextRange? = editor.fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)

    onNodeWithContentDescription("Structure tool window").performClick()
    awaitDescription("function Main")
    // The caret starts in the namespace declaration.
    onNodeWithContentDescription("namespace Demo").assertIsSelected()
    onNodeWithContentDescription("function Main").assertIsNotSelected()

    onNodeWithContentDescription("function Main").performClick()
    waitForIdle()
    val main = source.indexOf("public static void Main")
    assertEquals(TextRange(main, main), selection())
    onNodeWithText("Showing Main").assertExists()
    onNodeWithContentDescription("function Main").assertIsSelected()
    onNodeWithContentDescription("namespace Demo").assertIsNotSelected()

    onNodeWithContentDescription("property count").performClick()
    waitForIdle()
    val count = source.indexOf("int count")
    assertEquals(TextRange(count, count), selection())
    onNodeWithContentDescription("property count").assertIsSelected()
  }

  @Test
  fun theStructureFollowsAnEdit() = runComposeUiTest {
    setContent { XideApplication(owner = null, session = session("Main.vxs", source)) }
    onNodeWithContentDescription("Structure tool window").performClick()
    awaitDescription("function Main")

    onNode(hasSetTextAction()).performTextReplacement("namespace Other;\nclass Renamed { void Run() { } }\n")
    awaitDescription("type Renamed")
    awaitNoDescription("type Program")
    onNodeWithContentDescription("namespace Other").assertExists()
    onNodeWithContentDescription("function Run").assertExists()
    onNodeWithContentDescription("function Main").assertDoesNotExist()
  }

  @Test
  fun kotlinSourcesHaveAStructure() = runComposeUiTest {
    val session = session("App.kt", "class App { fun start() {} }\n")
    setContent { XideApplication(owner = null, session = session) }
    onNodeWithContentDescription("Structure tool window").performClick()
    awaitDescription("type App")
    onNodeWithContentDescription("function start").assertExists()
  }

  @Test
  fun aLanguageWithoutAParserSaysThatItHasNoStructure() = runComposeUiTest {
    setContent { XideApplication(owner = null, session = session("tool.py", "def run():\n    pass\n")) }
    onNodeWithContentDescription("Structure tool window").performClick()
    waitForIdle()
    onNodeWithText("Structure is not available for Python.").assertExists()
  }

  @Test
  fun altSevenOpensAndClosesTheToolWindow() = runComposeUiTest {
    setContent { XideApplication(owner = null, session = session("Main.vxs", source)) }
    // Key events are delivered to the focused node, so give the editor focus first.
    onNode(hasSetTextAction()).performClick()
    onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.Seven) } }
    awaitDescription("function Main")
    onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.Seven) } }
    waitForIdle()
    onNodeWithContentDescription("function Main").assertDoesNotExist()
  }

  @Test
  fun withoutADocumentTheToolWindowSaysSo() = runComposeUiTest {
    setContent { XideApplication(owner = null, session = WorkspaceSession()) }
    onNodeWithContentDescription("Structure tool window").performClick()
    waitForIdle()
    // The Problems tool window shows the same sentence.
    assertEquals(2, onAllNodesWithText("No document is open.").fetchSemanticsNodes().size)
  }
}
