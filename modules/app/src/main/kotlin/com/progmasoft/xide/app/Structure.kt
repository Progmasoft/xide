/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.progmasoft.xide.document.DocumentSnapshot
import com.progmasoft.xide.psi.OutlineEntry
import com.progmasoft.xide.psi.OutlineKind
import com.progmasoft.xide.psi.PsiLanguage
import com.progmasoft.xide.psi.SourceAnalyzer
import java.net.URI

/**
 * The longest text, in UTF-16 code units, whose structure is computed.
 *
 * The structure is parsed again after every edit. The bound is the one for colouring, for the same reason: beyond
 * it the work is no longer unnoticeable.
 */
internal const val MAXIMUM_STRUCTURED_LENGTH: Int = MAXIMUM_HIGHLIGHTED_LENGTH

/**
 * The PSI language that parses this language, or null when no parser for it is connected.
 *
 * Python has a tokenizer for colouring and no parser, so a Python source has no structure.
 */
internal val SourceLanguage.psiLanguage: PsiLanguage?
  get() =
    when (this) {
      SourceLanguage.VISUAL_XSHARP -> PsiLanguage.VISUAL_XSHARP
      SourceLanguage.KOTLIN -> PsiLanguage.KOTLIN
      SourceLanguage.JAVA -> PsiLanguage.JAVA
      SourceLanguage.GROOVY -> PsiLanguage.GROOVY
      SourceLanguage.PYTHON -> null
    }

/**
 * One line of the Structure tool window: a declaration of the document.
 *
 * @property depth how many declarations enclose this one.
 * @property kind what the declaration is.
 * @property name the declared name.
 * @property start the UTF-16 offset where the declaration starts.
 * @property end the UTF-16 offset just after the declaration.
 */
data class StructureRow(val depth: Int, val kind: OutlineKind, val name: String, val start: Int, val end: Int)

/**
 * The declarations of one version of one document.
 *
 * The offsets of the rows address exactly that version. A consumer that uses them must confirm that the document
 * still has it; see [isCurrentFor].
 *
 * @property uri the identity of the document.
 * @property version the snapshot version the rows were computed for.
 * @property rows the declarations in source order, each after the declaration that encloses it.
 */
data class DocumentStructure(val uri: URI, val version: Long, val rows: List<StructureRow>) {
  /** Whether the rows describe the text of [snapshot]. */
  fun isCurrentFor(snapshot: DocumentSnapshot): Boolean = uri == snapshot.uri && version == snapshot.version
}

/** The rows of an outline: every entry in source order, followed by the entries it encloses. */
fun structureRows(outline: List<OutlineEntry>): List<StructureRow> {
  val rows = ArrayList<StructureRow>()

  fun add(entries: List<OutlineEntry>, depth: Int) {
    for (entry in entries) {
      rows += StructureRow(depth, entry.kind, entry.name, entry.start, entry.end)
      add(entry.children, depth + 1)
    }
  }

  add(outline, 0)
  return rows
}

/**
 * The index of the innermost row whose declaration contains [offset], or null when none does.
 *
 * A declaration contains the offsets from its start up to and including its end, so a caret directly after a
 * declaration still belongs to it. Of two declarations that both contain the offset, the one that starts later is
 * the inner one, because the rows are in source order.
 */
fun enclosingRow(rows: List<StructureRow>, offset: Int): Int? {
  var found: Int? = null
  for ((index, row) in rows.withIndex()) {
    if (row.start > offset) break
    if (offset <= row.end) found = index
  }
  return found
}

/**
 * Parses the text of [snapshot] and returns its declarations, or null when [language] has no parser or the text is
 * longer than [MAXIMUM_STRUCTURED_LENGTH].
 *
 * The call parses the whole text and waits for any other parse to finish, so it belongs on a background thread.
 */
fun documentStructure(language: SourceLanguage?, snapshot: DocumentSnapshot): DocumentStructure? {
  val psiLanguage = language?.psiLanguage ?: return null
  if (snapshot.text.length > MAXIMUM_STRUCTURED_LENGTH) return null
  val analysis = SourceAnalyzer.analyze(psiLanguage, snapshot.text)
  return DocumentStructure(snapshot.uri, snapshot.version, structureRows(analysis.outline))
}

/** The word that names a kind of declaration to a screen reader and in tests. */
internal fun OutlineKind.label(): String =
  when (this) {
    OutlineKind.NAMESPACE -> "namespace"
    OutlineKind.TYPE -> "type"
    OutlineKind.CALLABLE -> "function"
    OutlineKind.PROPERTY -> "property"
    OutlineKind.INITIALIZER -> "initializer"
  }

/** The one-letter mark in front of a declaration's name. */
private fun OutlineKind.mark(): String =
  when (this) {
    OutlineKind.NAMESPACE -> "N"
    OutlineKind.TYPE -> "T"
    OutlineKind.CALLABLE -> "f"
    OutlineKind.PROPERTY -> "p"
    OutlineKind.INITIALIZER -> "i"
  }

private fun OutlineKind.color(): Color =
  when (this) {
    OutlineKind.NAMESPACE -> XideColors.mutedText
    OutlineKind.TYPE -> SyntaxColors.keyword
    OutlineKind.CALLABLE -> SyntaxColors.interpolation
    OutlineKind.PROPERTY -> SyntaxColors.number
    OutlineKind.INITIALIZER -> SyntaxColors.annotation
  }

/**
 * The Structure tool window: the declarations of the active document as an indented list.
 *
 * A row asks the shell to show its declaration. The row that contains the caret is marked. While the document is
 * being edited the list shows the declarations of the last parsed version until the next parse replaces them; the
 * shell declines to navigate from rows that are not current.
 *
 * @param structure the declarations to list, or null when none were computed for the active document.
 * @param caretOffset the caret of the active document, used to mark the row that contains it.
 */
@Composable
internal fun StructureToolWindow(
  document: OpenDocument?,
  structure: DocumentStructure?,
  caretOffset: Int,
  enabled: Boolean,
  onShowRow: (StructureRow) -> Unit,
  onHide: () -> Unit,
) {
  Column(Modifier.width(XideMetrics.projectWidth).fillMaxSize().background(XideColors.panel)) {
    ToolWindowHeader(ToolWindowId.STRUCTURE.title, document?.title, onHide)
    val language = document?.language
    val message =
      when {
        document == null -> "No document is open."
        language?.psiLanguage == null ->
          "Structure is not available for ${language?.displayName ?: "this file"}."
        document.snapshot.text.length > MAXIMUM_STRUCTURED_LENGTH -> "${document.title} is too long to show its structure."
        structure == null || structure.uri != document.snapshot.uri -> "Reading ${document.title}…"
        structure.rows.isEmpty() -> "${document.title} declares nothing."
        else -> null
      }
    if (message != null || structure == null) {
      BasicText(message.orEmpty(), style = XideType.uiMuted, modifier = Modifier.padding(12.dp))
      return@Column
    }
    val current = if (structure.isCurrentFor(document!!.snapshot)) enclosingRow(structure.rows, caretOffset) else null
    LazyColumn(Modifier.fillMaxSize()) {
      items(structure.rows.size) { index ->
        val row = structure.rows[index]
        Row(
          Modifier.fillMaxWidth()
            .background(if (index == current) XideColors.selection else Color.Transparent)
            .semantics {
              contentDescription = "${row.kind.label()} ${row.name}"
              selected = index == current
            }
            .clickable(enabled = enabled) { onShowRow(row) }
            .padding(start = 12.dp + XideMetrics.treeIndent * row.depth, end = 8.dp, top = 3.dp, bottom = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BasicText(
            row.kind.mark(),
            style = XideType.small.copy(color = row.kind.color()),
            modifier = Modifier.width(XideMetrics.treeIndent),
          )
          BasicText(row.name, style = XideType.ui, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
