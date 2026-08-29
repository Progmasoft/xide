/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
 */

package com.progmasoft.xide.document

/** A zero-based line and UTF-16 code-unit column. */
data class TextPosition(val line: Int, val column: Int) {
  init {
    require(line >= 0) { "line must not be negative" }
    require(column >= 0) { "column must not be negative" }
  }
}

/** A half-open range of UTF-16 code-unit offsets. */
data class TextRange(val start: Int, val end: Int) {
  init {
    require(start >= 0) { "start must not be negative" }
    require(end >= start) { "end must not precede start" }
  }

  val length: Int
    get() = end - start
}

/** Replaces [range] with [replacement] in one immutable document version. */
data class TextEdit(val range: TextRange, val replacement: String)

/** Raised when an edit was prepared against a snapshot that is no longer current. */
class StaleDocumentVersionException(expected: Long, actual: Long) :
  IllegalStateException("expected document version $expected, but current version is $actual")
