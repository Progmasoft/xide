/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import com.progmasoft.xide.document.StaleDocumentVersionException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceSessionTest {
  @Test
  fun createsDistinctVisualXSharpScratchDocuments() {
    val session = WorkspaceSession()

    session.newScratch()
    val workspace = session.newScratch()

    assertEquals(listOf("Xide-1.vxs", "Xide-2.vxs"), workspace.documents.map { it.title })
    assertEquals(1, workspace.activeIndex)
    assertEquals("untitled:Xide-2.vxs", workspace.activeDocument?.snapshot?.uri.toString())
    assertTrue(workspace.activeDocument?.snapshot?.text?.contains("public static void Main()") == true)
  }

  @Test
  fun repeatedOpenSelectsTheExistingDocumentWithoutReplacingIt() {
    val session = WorkspaceSession()
    val uri = URI.create("file:///workspace/Main.vxs")

    session.open(uri, "original")
    session.newScratch()
    val workspace = session.open(uri, "unexpected replacement")

    assertEquals(2, workspace.documents.size)
    assertEquals(0, workspace.activeIndex)
    assertEquals("original", workspace.activeDocument?.snapshot?.text)
  }

  @Test
  fun editorChangesUseTheVersionedDocumentBoundary() {
    val session = WorkspaceSession()
    val opened = session.open(URI.create("file:///workspace/Main.vxs"), "old")
    val version = opened.activeDocument!!.snapshot.version

    val updated = session.replaceActiveText(version, "new")

    assertEquals(1, updated.activeDocument?.snapshot?.version)
    assertEquals("new", updated.activeDocument?.snapshot?.text)
    assertFailsWith<StaleDocumentVersionException> {
      session.replaceActiveText(version, "stale")
    }
  }

  @Test
  fun equalEditorEchoDoesNotCreateANewVersion() {
    val session = WorkspaceSession()
    val opened = session.open(URI.create("untitled:Stable.vxs"), "same")

    val unchanged = session.replaceActiveText(opened.activeDocument!!.snapshot.version, "same")

    assertEquals(0, unchanged.activeDocument?.snapshot?.version)
  }

  @Test
  fun serializedUiChangesAlwaysAdvanceFromTheCurrentVersion() {
    val session = WorkspaceSession()
    session.open(URI.create("untitled:Typing.vxs"), "")

    session.replaceActiveText("a")
    val updated = session.replaceActiveText("ab")

    assertEquals(2, updated.activeDocument?.snapshot?.version)
    assertEquals("ab", updated.activeDocument?.snapshot?.text)
  }

  @Test
  fun snapshotsRejectInvalidActiveIndexesAndDuplicateUris() {
    val session = WorkspaceSession()
    val first = session.open(URI.create("untitled:One.vxs"), "").activeDocument!!

    assertFailsWith<IllegalArgumentException> { WorkspaceSnapshot(listOf(first), 1) }
    assertFailsWith<IllegalArgumentException> { WorkspaceSnapshot(listOf(first, first), 0) }
  }
}
