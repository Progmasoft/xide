/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <support@xsharp-lang.xyz>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.progmasoft.xide.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LineMapTest {
  @Test
  fun mapsLfCrlfAndCrLineEndings() {
    val map = LineMap.of("one\r\ntwo\nthree\rfour")
    assertEquals(4, map.lineCount)
    assertEquals(5, map.offsetAt(TextPosition(1, 0)))
    assertEquals(9, map.offsetAt(TextPosition(2, 0)))
    assertEquals(TextPosition(3, 4), map.positionAt(19))
  }

  @Test
  fun countsSupplementaryCharactersAsTwoUtf16CodeUnits() {
    val map = LineMap.of("A😀\nβ")
    assertEquals(TextPosition(0, 3), map.positionAt(3))
    assertEquals(4, map.offsetAt(TextPosition(1, 0)))
  }

  @Test
  fun rejectsPositionsOutsideTheDocument() {
    val map = LineMap.of("x")
    assertFailsWith<IndexOutOfBoundsException> { map.offsetAt(TextPosition(1, 0)) }
    assertFailsWith<IndexOutOfBoundsException> { map.offsetAt(TextPosition(0, 2)) }
    assertFailsWith<IndexOutOfBoundsException> { map.positionAt(2) }
  }
}
