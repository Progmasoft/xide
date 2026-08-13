/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <support@xsharp-lang.xyz>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.progmasoft.xide.document

import java.net.URI

/** An immutable document version safe to share with background language services. */
data class DocumentSnapshot(val uri: URI, val version: Long, val text: String) {
  init {
    require(version >= 0) { "version must not be negative" }
  }

  fun lineMap(): LineMap = LineMap.of(text)

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

  @Synchronized fun snapshot(): DocumentSnapshot = current

  @Synchronized
  fun apply(expectedVersion: Long, edit: TextEdit): DocumentSnapshot {
    if (current.version != expectedVersion) {
      throw StaleDocumentVersionException(expectedVersion, current.version)
    }
    return current.apply(edit).also { current = it }
  }
}
