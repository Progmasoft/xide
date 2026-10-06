/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import com.progmasoft.xide.compiler.CompilerDiagnostic
import com.progmasoft.xide.compiler.DiagnosticDocument
import com.progmasoft.xide.compiler.DiagnosticSeverity
import com.progmasoft.xide.compiler.DiagnosticStage
import com.progmasoft.xide.compiler.SourceLocation
import com.progmasoft.xide.compiler.SourcePosition
import com.progmasoft.xide.compiler.SourceRange
import com.progmasoft.xide.document.DocumentSnapshot
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellModelTest {
  private fun snapshot(text: String) = DocumentSnapshot(URI.create("untitled:Test.vxs"), 0, text)

  private fun diagnostic(severity: DiagnosticSeverity, source: String?, line: UInt = 0u) =
    CompilerDiagnostic(
      stage = DiagnosticStage.TYPE_CHECKER,
      severity = severity,
      code = "VXS-TEST",
      message = "problem",
      arguments = emptyList(),
      primaryLocation =
        source?.let { SourceLocation(it, SourceRange(SourcePosition(line, 0u), SourcePosition(line, 1u))) },
      relatedLocations = emptyList(),
      fixes = emptyList(),
    )

  @Test
  fun toolWindowsToggleIndependently() {
    val initial = ToolWindowLayout()
    assertTrue(initial.isVisible(ToolWindowId.PROJECT))
    assertTrue(initial.isVisible(ToolWindowId.PROBLEMS))

    val hidden = initial.toggle(ToolWindowId.PROJECT)
    assertFalse(hidden.isVisible(ToolWindowId.PROJECT))
    assertTrue(hidden.isVisible(ToolWindowId.PROBLEMS))
    assertEquals(initial, hidden.toggle(ToolWindowId.PROJECT))
    // Showing an open window changes nothing; showing a closed one opens it.
    assertEquals(initial, initial.show(ToolWindowId.PROJECT))
    assertEquals(initial, hidden.show(ToolWindowId.PROJECT))
  }

  @Test
  fun everyToolWindowHasAnEdgeAndATitle() {
    assertEquals(ToolWindowAnchor.LEFT, ToolWindowId.PROJECT.anchor)
    assertEquals(ToolWindowAnchor.BOTTOM, ToolWindowId.PROBLEMS.anchor)
    assertEquals(ToolWindowAnchor.LEFT, ToolWindowId.STRUCTURE.anchor)
    assertEquals(listOf("Project", "Structure", "Problems"), ToolWindowId.entries.map { it.title })
    // Structure is opened on demand; the shell starts with the two windows it always had.
    assertEquals(setOf(ToolWindowId.PROJECT, ToolWindowId.PROBLEMS), ToolWindowLayout().visible)
  }

  @Test
  fun caretPositionIsOneBasedAndCountsUtf16Units() {
    val text = snapshot("ab\n😀c\r\nlast")
    assertEquals(CaretPosition(1, 1), caretPosition(text, 0))
    assertEquals(CaretPosition(1, 3), caretPosition(text, 2))
    assertEquals(CaretPosition(2, 1), caretPosition(text, 3))
    // The emoji is one character on screen but two units for the caret.
    assertEquals(CaretPosition(2, 3), caretPosition(text, 5))
    assertEquals(CaretPosition(3, 5), caretPosition(text, text.text.length))
    assertEquals("3:5", caretPosition(text, text.text.length).toString())
  }

  @Test
  fun aCaretLeftOverFromLongerTextIsClamped() {
    val text = snapshot("ab")
    assertEquals(CaretPosition(1, 3), caretPosition(text, 99))
    assertEquals(CaretPosition(1, 1), caretPosition(text, -5))
  }

  @Test
  fun gutterNumbersAreRightAlignedToTheWidestNumber() {
    assertEquals("1", gutterNumbers(1))
    assertEquals("1\n2\n3", gutterNumbers(3))
    val ten = gutterNumbers(10).split('\n')
    assertEquals(10, ten.size)
    assertEquals(" 1", ten.first())
    assertEquals("10", ten.last())
    assertTrue(ten.all { it.length == 2 })
    assertFailsWith<IllegalArgumentException> { gutterNumbers(0) }
  }

  @Test
  fun gutterMarkersHaveOneLinePerDocumentLine() {
    val markers = gutterMarkers(4, mapOf(1 to DiagnosticSeverity.ERROR, 3 to DiagnosticSeverity.WARNING))
    assertEquals(" \n●\n \n●", markers.text)
    assertEquals(2, markers.spanStyles.size)
    assertEquals(XideColors.error, markers.spanStyles[0].item.color)
    assertEquals(XideColors.warning, markers.spanStyles[1].item.color)
    assertEquals(" \n \n ", gutterMarkers(3, emptyMap()).text)
  }

  @Test
  fun lineSeparatorIsNamedFromTheText() {
    assertEquals("LF", lineSeparatorLabel(""))
    assertEquals("LF", lineSeparatorLabel("one line"))
    assertEquals("LF", lineSeparatorLabel("a\nb\n"))
    assertEquals("CRLF", lineSeparatorLabel("a\r\nb\r\n"))
    assertEquals("CR", lineSeparatorLabel("a\rb"))
    assertEquals("Mixed", lineSeparatorLabel("a\r\nb\n"))
    assertEquals("Mixed", lineSeparatorLabel("a\rb\n"))
  }

  @Test
  fun problemCountsGroupSeverities() {
    assertEquals(ProblemCounts.NONE, ProblemCounts.of(null))
    val document =
      OpenDocument(
        "Main.vxs",
        snapshot("x"),
        VersionedDiagnostics(
          0,
          DiagnosticDocument(
            listOf(
              diagnostic(DiagnosticSeverity.ERROR, null),
              diagnostic(DiagnosticSeverity.ERROR, null),
              diagnostic(DiagnosticSeverity.WARNING, null),
              diagnostic(DiagnosticSeverity.HINT, null),
              diagnostic(DiagnosticSeverity.INFORMATION, null),
            )
          ),
        ),
      )
    val counts = ProblemCounts.of(document)
    assertEquals(ProblemCounts(2, 1, 2), counts)
    assertEquals(5, counts.total)
    assertEquals("2 errors, 1 warning, 2 notes", problemSummary(counts))
    assertEquals("1 error", problemSummary(ProblemCounts(1, 0, 0)))
    assertEquals("", problemSummary(ProblemCounts.NONE))
  }

  private fun sessionWith(vararg files: Pair<String, String>): Pair<WorkspaceSession, Path> {
    val directory = createTempDirectory("xide-shell")
    val session = WorkspaceSession()
    for ((name, text) in files) {
      val file = directory.resolve(name)
      Files.writeString(file, text)
      session.openFile(file)
    }
    return session to directory
  }

  @Test
  fun closingAnUnmodifiedTabKeepsTheOthersInOrder() {
    val (session, _) = sessionWith("A.vxs" to "a", "B.vxs" to "b", "C.vxs" to "c")
    session.select(1)

    val closed = checkNotNull(session.close(1))
    assertEquals(listOf("A.vxs", "C.vxs"), closed.documents.map { it.title })
    // The tab that took the closed tab's place becomes active.
    assertEquals("C.vxs", closed.activeDocument?.title)
  }

  @Test
  fun closingTheLastTabSelectsTheNewLastTab() {
    val (session, _) = sessionWith("A.vxs" to "a", "B.vxs" to "b")
    val closed = checkNotNull(session.close(1))
    assertEquals("A.vxs", closed.activeDocument?.title)

    val empty = checkNotNull(session.close(0))
    assertTrue(empty.documents.isEmpty())
    assertNull(empty.activeIndex)
  }

  @Test
  fun closingAnInactiveTabKeepsTheActiveDocument() {
    val (session, _) = sessionWith("A.vxs" to "a", "B.vxs" to "b", "C.vxs" to "c")
    // C is active after the last open.
    assertEquals("C.vxs", checkNotNull(session.close(0)).activeDocument?.title)
    session.select(0)
    assertEquals("B.vxs", checkNotNull(session.close(1)).activeDocument?.title)
  }

  @Test
  fun aModifiedTabIsNotClosedWithoutADecision() {
    val (session, _) = sessionWith("A.vxs" to "a")
    session.replaceActiveText("changed")

    assertNull(session.close(0))
    assertEquals("changed", session.snapshot().activeDocument?.snapshot?.text)

    val discarded = checkNotNull(session.close(0, discardChanges = true))
    assertTrue(discarded.documents.isEmpty())
  }

  @Test
  fun anUnsavedScratchDocumentCountsAsModified() {
    val session = WorkspaceSession()
    session.newScratch()
    assertNull(session.close(0))
    assertFailsWith<IllegalArgumentException> { session.close(5) }
  }

  @Test
  fun lineMarkersKeepTheMostSevereProblemOfEachLine() {
    val (session, directory) = sessionWith("Main.vxs" to "one\ntwo\nthree\n")
    val file = directory.resolve("Main.vxs").toString()
    val opened = session.snapshot().activeDocument!!
    session.publishDiagnostics(
      opened.snapshot.uri,
      opened.snapshot.version,
      DiagnosticDocument(
        listOf(
          diagnostic(DiagnosticSeverity.WARNING, file, 1u),
          diagnostic(DiagnosticSeverity.ERROR, file, 1u),
          diagnostic(DiagnosticSeverity.HINT, file, 1u),
          diagnostic(DiagnosticSeverity.INFORMATION, "Main.vxs", 2u),
          // Not this file, no location, and a line outside the document: none of these mark a line.
          diagnostic(DiagnosticSeverity.ERROR, directory.resolve("Other.vxs").toString(), 0u),
          diagnostic(DiagnosticSeverity.ERROR, null),
          diagnostic(DiagnosticSeverity.ERROR, file, 40u),
        )
      ),
    )

    assertEquals(mapOf(1 to DiagnosticSeverity.ERROR, 2 to DiagnosticSeverity.INFORMATION), session.lineMarkers(0))
  }

  @Test
  fun lineMarkersDisappearWhenTheTextNoLongerMatchesTheCheck() {
    val (session, directory) = sessionWith("Main.vxs" to "one\n")
    val opened = session.snapshot().activeDocument!!
    session.publishDiagnostics(
      opened.snapshot.uri,
      opened.snapshot.version,
      DiagnosticDocument(listOf(diagnostic(DiagnosticSeverity.ERROR, directory.resolve("Main.vxs").toString()))),
    )
    assertEquals(1, session.lineMarkers(0).size)

    session.replaceActiveText("edited\n")
    assertTrue(session.lineMarkers(0).isEmpty())
    assertTrue(session.lineMarkers(7).isEmpty())
  }
}
