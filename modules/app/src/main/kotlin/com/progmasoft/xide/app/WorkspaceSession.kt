/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import com.progmasoft.xide.document.DocumentSnapshot
import com.progmasoft.xide.document.TextDocument
import com.progmasoft.xide.document.TextEdit
import com.progmasoft.xide.document.TextRange
import java.net.URI

/** Immutable view of the documents that the desktop shell may render. */
data class WorkspaceSnapshot(
  val documents: List<OpenDocument>,
  val activeIndex: Int?,
) {
  init {
    require(activeIndex == null || activeIndex in documents.indices) {
      "activeIndex must identify an open document"
    }
    require(documents.map { it.snapshot.uri }.distinct().size == documents.size) {
      "document URIs must be unique within a workspace"
    }
  }

  val activeDocument: OpenDocument?
    get() = activeIndex?.let(documents::get)
}

/** Display metadata paired with the immutable editor snapshot. */
data class OpenDocument(
  val title: String,
  val snapshot: DocumentSnapshot,
)

/**
 * Owns the small amount of mutable state needed by the first desktop shell.
 *
 * Compose observes immutable [WorkspaceSnapshot] values. The session itself does not depend on Compose, which keeps
 * document identity, version checks, and duplicate-open behavior testable without starting a graphics environment.
 */
class WorkspaceSession {
  private data class Entry(val title: String, val document: TextDocument)

  private val entries = mutableListOf<Entry>()
  private var activeIndex: Int? = null
  private var nextScratchNumber = 1

  @Synchronized
  fun snapshot(): WorkspaceSnapshot =
    WorkspaceSnapshot(
      documents = entries.map { OpenDocument(it.title, it.document.snapshot()) },
      activeIndex = activeIndex,
    )

  /** Opens a document once and selects the already-open instance on repeated requests. */
  @Synchronized
  fun open(uri: URI, text: String): WorkspaceSnapshot {
    require(uri.isAbsolute) { "uri must be absolute" }
    val existingIndex = entries.indexOfFirst { it.document.snapshot().uri == uri }
    if (existingIndex >= 0) {
      activeIndex = existingIndex
      return snapshot()
    }

    entries += Entry(titleFor(uri), TextDocument(uri, text))
    activeIndex = entries.lastIndex
    return snapshot()
  }

  /** Creates an unsaved Visual X# document with a process-local, stable identity. */
  @Synchronized
  fun newScratch(): WorkspaceSnapshot {
    val number = nextScratchNumber++
    return open(URI.create("untitled:Xide-$number.vxs"), defaultScratchText(number))
  }

  @Synchronized
  fun select(index: Int): WorkspaceSnapshot {
    require(index in entries.indices) { "index must identify an open document" }
    activeIndex = index
    return snapshot()
  }

  /**
   * Replaces editor text through the versioned document model instead of letting UI state bypass stale-edit checks.
   * Equal text is a rendering echo, so it does not create an artificial document version.
   */
  @Synchronized
  fun replaceActiveText(text: String): WorkspaceSnapshot {
    val current = activeEntry().document.snapshot()
    return replaceActiveText(current.version, text)
  }

  /** Applies a version-bound replacement prepared by an asynchronous editor or language-service consumer. */
  @Synchronized
  fun replaceActiveText(expectedVersion: Long, text: String): WorkspaceSnapshot {
    val entry = activeEntry()
    val current = entry.document.snapshot()
    if (current.text == text) {
      return snapshot()
    }
    entry.document.apply(expectedVersion, TextEdit(TextRange(0, current.text.length), text))
    return snapshot()
  }

  private fun activeEntry(): Entry {
    val index = activeIndex ?: throw IllegalStateException("no document is active")
    return entries[index]
  }

  private fun titleFor(uri: URI): String {
    val candidate = uri.path?.substringAfterLast('/')?.takeIf(String::isNotBlank)
    return candidate ?: uri.schemeSpecificPart.substringAfterLast('/').ifBlank { "Untitled.vxs" }
  }

  private fun defaultScratchText(number: Int): String =
    """
    namespace Scratch$number;

    public class Program {
        public static void Main() {
        }
    }
    """.trimIndent() + "\n"
}
