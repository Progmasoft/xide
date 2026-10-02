/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.document

/** Maps offsets without converting the JVM's native UTF-16 string representation. */
class LineMap private constructor(
  private val lineStarts: IntArray,
  private val lineEnds: IntArray,
  private val textLength: Int,
) {
  /** The number of lines; a text always has at least one, and a trailing terminator starts an empty last line. */
  val lineCount: Int
    get() = lineStarts.size

  /**
   * The UTF-16 offset of a line and column.
   *
   * The column may equal the line's length, which addresses the position just before its terminator.
   *
   * @throws IndexOutOfBoundsException when the line does not exist or the column lies beyond the line.
   */
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

  /** The UTF-16 offsets of one line's content, excluding its line terminator. */
  fun lineRange(line: Int): TextRange {
    if (line !in lineStarts.indices) {
      throw IndexOutOfBoundsException("line is outside the document")
    }
    return TextRange(lineStarts[line], lineEnds[line])
  }

  /**
   * The line and column of a UTF-16 offset.
   *
   * An offset inside a line terminator reports the end of the line it terminates, so every offset in `0..length`
   * has a position.
   *
   * @throws IndexOutOfBoundsException when the offset is outside the text.
   */
  fun positionAt(offset: Int): TextPosition {
    if (offset !in 0..textLength) {
      throw IndexOutOfBoundsException("offset is outside the document")
    }
    val search = lineStarts.binarySearch(offset)
    val line = if (search >= 0) search else -search - 2
    val column = minOf(offset, lineEnds[line]) - lineStarts[line]
    return TextPosition(line, column)
  }

  /** Builds line maps. */
  companion object {
    /** Indexes the lines of a text. LF, CRLF and a lone CR each end a line, and CRLF counts as one terminator. */
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
