/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
 */

package com.progmasoft.xide.document

/** Maps offsets without converting the JVM's native UTF-16 string representation. */
class LineMap private constructor(
  private val lineStarts: IntArray,
  private val lineEnds: IntArray,
  private val textLength: Int,
) {
  val lineCount: Int
    get() = lineStarts.size

  fun offsetAt(position: TextPosition): Int {
    if (position.line !in lineStarts.indices) {
      throw IndexOutOfBoundsException("line is outside the document")
    }
    val lineLength = lineEnds[position.line] - lineStarts[position.line]
    if (position.column > lineLength) {
      throw IndexOutOfBoundsException("column is outside the line")
    }
    return lineStarts[position.line] + position.column
  }

  fun positionAt(offset: Int): TextPosition {
    if (offset !in 0..textLength) {
      throw IndexOutOfBoundsException("offset is outside the document")
    }
    val search = lineStarts.binarySearch(offset)
    val line = if (search >= 0) search else -search - 2
    val column = minOf(offset, lineEnds[line]) - lineStarts[line]
    return TextPosition(line, column)
  }

  companion object {
    fun of(text: String): LineMap {
      val starts = mutableListOf<Int>()
      val ends = mutableListOf<Int>()
      var lineStart = 0
      var offset = 0
      while (offset < text.length) {
        val current = text[offset]
        if (current != '\r' && current != '\n') {
          offset++
          continue
        }
        starts += lineStart
        ends += offset
        if (current == '\r' && offset + 1 < text.length && text[offset + 1] == '\n') {
          offset++
        }
        lineStart = ++offset
      }
      starts += lineStart
      ends += text.length
      return LineMap(starts.toIntArray(), ends.toIntArray(), text.length)
    }
  }
}
