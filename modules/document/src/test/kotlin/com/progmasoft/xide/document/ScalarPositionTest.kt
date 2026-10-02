/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.document

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScalarPositionTest {
  private fun snapshot(text: String) = DocumentSnapshot(URI.create("untitled:Scalars.vxs"), 0, text)

  @Test
  fun scalarColumnsEqualUtf16ColumnsForBasicMultilingualText() {
    val document = snapshot("alpha\nβeta")

    assertEquals(0, document.offsetAt(ScalarPosition(0, 0)))
    assertEquals(3, document.offsetAt(ScalarPosition(0, 3)))
    assertEquals(6, document.offsetAt(ScalarPosition(1, 0)))
    assertEquals(8, document.offsetAt(ScalarPosition(1, 2)))
  }

  @Test
  fun supplementaryCharactersAdvanceOneScalarButTwoCodeUnits() {
    // Each emoji is one scalar and two UTF-16 code units.
    val document = snapshot("a😀b😀c")

    assertEquals(1, document.offsetAt(ScalarPosition(0, 1)))
    assertEquals(3, document.offsetAt(ScalarPosition(0, 2)))
    assertEquals(4, document.offsetAt(ScalarPosition(0, 3)))
    assertEquals(6, document.offsetAt(ScalarPosition(0, 4)))
    assertEquals(7, document.offsetAt(ScalarPosition(0, 5)))
    assertEquals(TextRange(3, 6), document.rangeAt(ScalarPosition(0, 2), ScalarPosition(0, 4)))
  }

  @Test
  fun everyLineTerminatorStartsANewScalarLine() {
    val document = snapshot("😀\r\nx😀\ry\nz")

    assertEquals(2, document.offsetAt(ScalarPosition(0, 1)))
    assertEquals(4, document.offsetAt(ScalarPosition(1, 0)))
    assertEquals(7, document.offsetAt(ScalarPosition(1, 2)))
    assertEquals(8, document.offsetAt(ScalarPosition(2, 0)))
    assertEquals(10, document.offsetAt(ScalarPosition(3, 0)))
    assertEquals(11, document.offsetAt(ScalarPosition(3, 1)))
  }

  @Test
  fun endOfLineIsAddressableButNothingBeyondIt() {
    val document = snapshot("ab\ncd")

    assertEquals(2, document.offsetAt(ScalarPosition(0, 2)))
    assertEquals(5, document.offsetAt(ScalarPosition(1, 2)))
    assertFailsWith<IndexOutOfBoundsException> { document.offsetAt(ScalarPosition(0, 3)) }
    assertFailsWith<IndexOutOfBoundsException> { document.offsetAt(ScalarPosition(2, 0)) }
    assertFailsWith<IndexOutOfBoundsException> { document.offsetAt(ScalarPosition(1, Int.MAX_VALUE)) }
  }

  @Test
  fun emptyDocumentHasOneAddressableLine() {
    val document = snapshot("")

    assertEquals(0, document.offsetAt(ScalarPosition(0, 0)))
    assertEquals(TextRange(0, 0), document.rangeAt(ScalarPosition(0, 0), ScalarPosition(0, 0)))
    assertFailsWith<IndexOutOfBoundsException> { document.offsetAt(ScalarPosition(0, 1)) }
  }

  @Test
  fun unpairedSurrogatesCountOnceAndNeverPairAcrossALineEnd() {
    val high = '\uD83D'
    val low = '\uDE00'
    // A low surrogate first, then a high surrogate that ends its line: neither forms a pair.
    val document = snapshot("$low$high\n${low}x")

    assertEquals(1, document.offsetAt(ScalarPosition(0, 1)))
    assertEquals(2, document.offsetAt(ScalarPosition(0, 2)))
    assertFailsWith<IndexOutOfBoundsException> { document.offsetAt(ScalarPosition(0, 3)) }
    assertEquals(4, document.offsetAt(ScalarPosition(1, 1)))
  }

  @Test
  fun rangesMaySpanLinesButMustNotRunBackwards() {
    val document = snapshot("a😀\nb")

    assertEquals(TextRange(1, 5), document.rangeAt(ScalarPosition(0, 1), ScalarPosition(1, 1)))
    assertFailsWith<IllegalArgumentException> { document.rangeAt(ScalarPosition(1, 0), ScalarPosition(0, 0)) }
    assertFailsWith<IllegalArgumentException> { ScalarPosition(-1, 0) }
    assertFailsWith<IllegalArgumentException> { ScalarPosition(0, -1) }
  }
}
