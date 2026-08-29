/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
 */

package com.progmasoft.xide.document

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextDocumentTest {
  @Test
  fun editsCreateNewImmutableVersions() {
    val document = TextDocument(URI.create("file:///workspace/Main.vxs"), "class Main {}")
    val original = document.snapshot()
    val updated = document.apply(0, TextEdit(TextRange(6, 10), "Program"))
    assertEquals(0, original.version)
    assertEquals("class Main {}", original.text)
    assertEquals(1, updated.version)
    assertEquals("class Program {}", updated.text)
    assertEquals(updated, document.snapshot())
  }

  @Test
  fun rejectsStaleAndOutOfBoundsEdits() {
    val document = TextDocument(URI.create("untitled:visual-xsharp"), "value")
    document.apply(0, TextEdit(TextRange(0, 5), "next"))
    assertFailsWith<StaleDocumentVersionException> {
      document.apply(0, TextEdit(TextRange(0, 0), "stale"))
    }
    assertFailsWith<IndexOutOfBoundsException> {
      document.apply(1, TextEdit(TextRange(0, 20), "outside"))
    }
  }
}
