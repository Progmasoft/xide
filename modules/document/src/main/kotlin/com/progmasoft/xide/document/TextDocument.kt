/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.document

import java.net.URI

/**
 * An immutable document version safe to share with background language services.
 *
 * @property uri the absolute identity of the document.
 * @property version the number of edits applied since the document was opened.
 * @property text the complete text of this version.
 */
data class DocumentSnapshot(val uri: URI, val version: Long, val text: String) {
  init {
    require(uri.isAbsolute) { "uri must be absolute" }
    require(version >= 0) { "version must not be negative" }
  }

  /** Indexes this snapshot's lines. The map is computed on each call and is valid for this snapshot only. */
  fun lineMap(): LineMap = LineMap.of(text)

  /**
   * Converts a scalar position to a UTF-16 offset in this exact snapshot.
   *
   * The column may equal the number of scalars on the line, which addresses the position just before the line
   * terminator. A surrogate pair counts once; an unpaired surrogate counts as one scalar so malformed editor text
   * still has a total, monotonic mapping. Positions outside the document are rejected rather than clamped, because
   * a clamped location would silently point at different source than the compiler reported.
   */
  fun offsetAt(position: ScalarPosition): Int {
    val line = lineMap().lineRange(position.line)
    var offset = line.start
    repeat(position.column) {
      if (offset >= line.end) {
        throw IndexOutOfBoundsException("scalar column is outside the line")
      }
      val first = text[offset]
      val paired = Character.isHighSurrogate(first) && offset + 1 < line.end && Character.isLowSurrogate(text[offset + 1])
      offset += if (paired) 2 else 1
    }
    return offset
  }

  /** Converts a half-open scalar range to UTF-16 offsets; [end] must not precede [start]. */
  fun rangeAt(start: ScalarPosition, end: ScalarPosition): TextRange {
    val startOffset = offsetAt(start)
    val endOffset = offsetAt(end)
    if (endOffset < startOffset) {
      throw IllegalArgumentException("range end must not precede its start")
    }
    return TextRange(startOffset, endOffset)
  }

  /**
   * Returns the snapshot that results from one edit; this snapshot is unchanged.
   *
   * The result has the next version number.
   *
   * @throws IndexOutOfBoundsException when the edit's range lies outside the text.
   */
  fun apply(edit: TextEdit): DocumentSnapshot {
    if (edit.range.end > text.length) {
      throw IndexOutOfBoundsException("edit range is outside the document")
    }
    val updated =
      buildString(text.length - edit.range.length + edit.replacement.length) {
        append(text, 0, edit.range.start)
        append(edit.replacement)
        append(text, edit.range.end, text.length)
      }
    return DocumentSnapshot(uri, Math.addExact(version, 1), updated)
  }
}

/** Owns the current snapshot of one open editor document. */
class TextDocument(uri: URI, text: String) {
  private var current = DocumentSnapshot(uri, 0, text)

  /** The current immutable snapshot. */
  @Synchronized fun snapshot(): DocumentSnapshot = current

  /**
   * Applies an edit that was prepared against [expectedVersion] and returns the new snapshot.
   *
   * @throws StaleDocumentVersionException when the document has changed since that version, so an edit computed
   *   from older text can never be applied to newer text.
   */
  @Synchronized
  fun apply(expectedVersion: Long, edit: TextEdit): DocumentSnapshot {
    if (current.version != expectedVersion) {
      throw StaleDocumentVersionException(expectedVersion, current.version)
    }
    return current.apply(edit).also { current = it }
  }
}
