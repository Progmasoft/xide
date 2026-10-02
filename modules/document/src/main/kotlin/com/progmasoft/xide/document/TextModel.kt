/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.document

/**
 * A zero-based line and UTF-16 code-unit column.
 *
 * @property line the zero-based line index.
 * @property column the zero-based offset from the start of the line, in UTF-16 code units.
 */
data class TextPosition(val line: Int, val column: Int) {
  init {
    require(line >= 0) { "line must not be negative" }
    require(column >= 0) { "column must not be negative" }
  }
}

/**
 * A zero-based line and Unicode-scalar column, the coordinate system of compiler diagnostics.
 *
 * A supplementary character is one scalar but two UTF-16 code units, so this is deliberately a different type from
 * [TextPosition]: adding a scalar column to a JVM string index lands inside or past the intended character.
 *
 * @property line the zero-based line index.
 * @property column the zero-based offset from the start of the line, in Unicode scalar values.
 */
data class ScalarPosition(val line: Int, val column: Int) {
  init {
    require(line >= 0) { "line must not be negative" }
    require(column >= 0) { "column must not be negative" }
  }
}

/**
 * A half-open range of UTF-16 code-unit offsets.
 *
 * @property start the offset of the first code unit in the range.
 * @property end the offset just after the range; it never precedes [start].
 */
data class TextRange(val start: Int, val end: Int) {
  init {
    require(start >= 0) { "start must not be negative" }
    require(end >= start) { "end must not precede start" }
  }

  /** The number of UTF-16 code units in the range. */
  val length: Int
    get() = end - start
}

/**
 * Replaces [range] with [replacement] in one immutable document version.
 *
 * @property range the offsets to replace; an empty range inserts.
 * @property replacement the text that takes the range's place; an empty text deletes.
 */
data class TextEdit(val range: TextRange, val replacement: String)

/** Raised when an edit was prepared against a snapshot that is no longer current. */
class StaleDocumentVersionException(expected: Long, actual: Long) :
  IllegalStateException("expected document version $expected, but current version is $actual")
