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
import com.progmasoft.xide.document.TextRange
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiagnosticNavigationTest {
  private fun location(source: String, startLine: UInt, startColumn: UInt, endLine: UInt, endColumn: UInt) =
    SourceLocation(source, SourceRange(SourcePosition(startLine, startColumn), SourcePosition(endLine, endColumn)))

  private fun diagnostics(vararg locations: SourceLocation) =
    DiagnosticDocument(
      locations.map {
        CompilerDiagnostic(
          stage = DiagnosticStage.TYPE_CHECKER,
          severity = DiagnosticSeverity.ERROR,
          code = "VXS-TEST",
          message = "test diagnostic",
          arguments = emptyList(),
          primaryLocation = it,
          relatedLocations = emptyList(),
          fixes = emptyList(),
        )
      }
    )

  private fun write(directory: Path, name: String, text: String): Path =
    directory.resolve(name).also { Files.writeString(it, text) }

  /** Opens [text] as a saved file and publishes [locations] for its current version. */
  private fun checked(session: WorkspaceSession, file: Path, vararg locations: SourceLocation): Int {
    val workspace = session.openFile(file)
    val document = workspace.activeDocument!!
    check(session.publishDiagnostics(document.snapshot.uri, document.snapshot.version, diagnostics(*locations)))
    return workspace.activeIndex!!
  }

  @Test
  fun scalarColumnsResolveToUtf16OffsetsOfTheCheckedDocument() {
    val directory = createTempDirectory("xide-navigation")
    // The emoji is one scalar column but two UTF-16 code units, so `value` starts at scalar column 5 but UTF-16 column 6.
    val file = write(directory, "Main.vxs", "line one\n-- 😀 value here\n")
    val session = WorkspaceSession()
    val place = location(file.toString(), 1u, 5u, 1u, 10u)
    val owner = checked(session, file, place)

    val target = assertNotNull(session.locate(owner, place))

    assertEquals(owner, target.documentIndex)
    assertEquals(TextRange(15, 20), target.range)
    val snapshot = session.snapshot().documents[owner].snapshot
    assertEquals("value", snapshot.text.substring(target.range.start, target.range.end))
    assertEquals(snapshot.version, target.version)
    assertEquals(snapshot.uri, target.uri)
  }

  @Test
  fun relativeSourceIdentityIsResolvedAgainstTheCheckedDocumentDirectory() {
    val directory = createTempDirectory("xide-navigation")
    val file = write(directory, "Main.vxs", "abc\n")
    val session = WorkspaceSession()
    val owner = checked(session, file)

    val target = session.locate(owner, location("./Main.vxs", 0u, 1u, 0u, 2u))

    assertEquals(TextRange(1, 2), target?.range)
  }

  @Test
  fun locationsInAnotherOpenAndUnmodifiedFileSelectThatDocument() {
    val directory = createTempDirectory("xide-navigation")
    val main = write(directory, "Main.vxs", "main\n")
    val other = write(directory, "Other.vxs", "first\nsecond\n")
    val session = WorkspaceSession()
    val otherIndex = session.openFile(other).activeIndex!!
    val owner = checked(session, main)

    val target = assertNotNull(session.locate(owner, location(other.toString(), 1u, 0u, 1u, 6u)))

    assertEquals(otherIndex, target.documentIndex)
    assertEquals(TextRange(6, 12), target.range)
  }

  @Test
  fun editedDocumentsAreNeverNavigationTargets() {
    val directory = createTempDirectory("xide-navigation")
    val main = write(directory, "Main.vxs", "main\n")
    val other = write(directory, "Other.vxs", "first\nsecond\n")
    val session = WorkspaceSession()
    session.openFile(other)
    val owner = checked(session, main)
    val inOther = location(other.toString(), 1u, 0u, 1u, 6u)
    val inMain = location(main.toString(), 0u, 0u, 0u, 4u)
    assertNotNull(session.locate(owner, inOther))
    assertNotNull(session.locate(owner, inMain))

    // The compiler read Other.vxs from disk; an unsaved edit makes every reported offset there unreliable.
    session.select(0)
    session.replaceActiveText("inserted\nfirst\nsecond\n")
    assertNull(session.locate(owner, inOther))
    assertNotNull(session.locate(owner, inMain))

    // Editing the checked document discards its diagnostics, so none of its locations may be used afterwards.
    session.select(owner)
    session.replaceActiveText("changed\n")
    assertNull(session.locate(owner, inMain))
  }

  @Test
  fun locationsOutsideAnyOpenDocumentAreDeclinedInsteadOfClamped() {
    val directory = createTempDirectory("xide-navigation")
    val file = write(directory, "Main.vxs", "ab\ncd")
    val session = WorkspaceSession()
    val owner = checked(session, file)
    val source = file.toString()

    assertNull(session.locate(owner, location(source, 0u, 3u, 0u, 3u)))
    assertNull(session.locate(owner, location(source, 2u, 0u, 2u, 0u)))
    assertNull(session.locate(owner, location(source, 0u, 0u, 9u, 0u)))
    assertNull(session.locate(owner, location(source, UInt.MAX_VALUE, 0u, UInt.MAX_VALUE, 0u)))
    assertNull(session.locate(owner, location(source, 0u, UInt.MAX_VALUE, 0u, UInt.MAX_VALUE)))
    assertNull(session.locate(owner, location(directory.resolve("NotOpen.vxs").toString(), 0u, 0u, 0u, 0u)))
    assertNull(session.locate(owner, location("\u0000invalid", 0u, 0u, 0u, 0u)))
    assertNull(session.locate(owner + 7, location(source, 0u, 0u, 0u, 0u)))
    // The end of the last line is a valid, empty location.
    assertEquals(TextRange(5, 5), session.locate(owner, location(source, 1u, 2u, 1u, 2u))?.range)
  }

  @Test
  fun documentsWithoutCurrentDiagnosticsOwnNoLocations() {
    val directory = createTempDirectory("xide-navigation")
    val file = write(directory, "Main.vxs", "abc\n")
    val session = WorkspaceSession()
    val workspace = session.openFile(file)
    val place = location(file.toString(), 0u, 0u, 0u, 1u)

    assertNull(session.locate(workspace.activeIndex!!, place))

    val scratch = session.newScratch()
    assertNull(session.locate(scratch.activeIndex!!, place))
  }
}
