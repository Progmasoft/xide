/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import com.progmasoft.xide.compiler.DiagnosticSeverity
import com.progmasoft.xide.document.DocumentSnapshot

/** The window edge a tool window is docked to. */
enum class ToolWindowAnchor {
  LEFT,
  BOTTOM,
}

/**
 * The tool windows of the shell, in the order their stripe buttons appear.
 *
 * @property title the name shown in the tool window's header.
 * @property anchor the edge the tool window is docked to.
 */
enum class ToolWindowId(val title: String, val anchor: ToolWindowAnchor) {
  PROJECT("Project", ToolWindowAnchor.LEFT),
  PROBLEMS("Problems", ToolWindowAnchor.BOTTOM),
}

/**
 * Which tool windows are open.
 *
 * A tool window is toggled from its stripe button and keeps its place on its edge; closing one gives its space to
 * the editor area. The value is immutable so Compose can observe it as ordinary state.
 *
 * @property visible the tool windows that are currently open.
 */
data class ToolWindowLayout(val visible: Set<ToolWindowId> = setOf(ToolWindowId.PROJECT, ToolWindowId.PROBLEMS)) {
  /** Whether a tool window is open. */
  fun isVisible(id: ToolWindowId): Boolean = id in visible

  /** The layout with one tool window opened if it was closed and closed if it was open. */
  fun toggle(id: ToolWindowId): ToolWindowLayout = copy(visible = if (id in visible) visible - id else visible + id)

  /** The layout with one tool window open; an already open window leaves the layout unchanged. */
  fun show(id: ToolWindowId): ToolWindowLayout = if (id in visible) this else copy(visible = visible + id)
}

/**
 * A caret location as the status bar shows it.
 *
 * @property line the one-based line number.
 * @property column the one-based column, counted in UTF-16 code units.
 */
data class CaretPosition(val line: Int, val column: Int) {
  /** The status-bar spelling, `line:column`. */
  override fun toString(): String = "$line:$column"
}

/**
 * The status-bar position of a UTF-16 [offset] in [snapshot].
 *
 * The column counts UTF-16 code units, which is the unit the editor's caret moves in. An offset left over from
 * longer text is clamped to the document, because the status bar must show a position for whatever caret the
 * editor currently draws.
 */
fun caretPosition(snapshot: DocumentSnapshot, offset: Int): CaretPosition {
  val position = snapshot.lineMap().positionAt(offset.coerceIn(0, snapshot.text.length))
  return CaretPosition(position.line + 1, position.column + 1)
}

/**
 * How many problems of each severity a document currently shows.
 *
 * @property errors the number of error diagnostics.
 * @property warnings the number of warning diagnostics.
 * @property others the number of information and hint diagnostics.
 */
data class ProblemCounts(val errors: Int, val warnings: Int, val others: Int) {
  /** The number of problems of every severity. */
  val total: Int
    get() = errors + warnings + others

  /** Counting helpers. */
  companion object {
    /** The counts of a document without problems. */
    val NONE = ProblemCounts(0, 0, 0)

    /** Counts the diagnostics a document currently carries; a missing document or missing diagnostics count as none. */
    fun of(document: OpenDocument?): ProblemCounts {
      val diagnostics = document?.diagnostics?.document?.diagnostics ?: return NONE
      var errors = 0
      var warnings = 0
      var others = 0
      for (diagnostic in diagnostics) {
        when (diagnostic.severity) {
          DiagnosticSeverity.ERROR -> errors++
          DiagnosticSeverity.WARNING -> warnings++
          DiagnosticSeverity.INFORMATION,
          DiagnosticSeverity.HINT -> others++
        }
      }
      return ProblemCounts(errors, warnings, others)
    }
  }
}

/**
 * The gutter text for a document with [lineCount] lines: one right-aligned line number per line.
 *
 * Every number is padded to the width of the largest one, so the gutter keeps a constant width while the caret
 * moves and its lines stay aligned with the editor's lines when both use the same monospace style.
 */
fun gutterNumbers(lineCount: Int): String {
  require(lineCount >= 1) { "a document has at least one line" }
  val width = lineCount.toString().length
  return buildString(lineCount * (width + 1)) {
    for (line in 1..lineCount) {
      if (line > 1) append('\n')
      val text = line.toString()
      repeat(width - text.length) { append(' ') }
      append(text)
    }
  }
}

/**
 * The status-bar name of the line terminator a text uses, or "Mixed" when it uses more than one kind.
 *
 * A text without any terminator reports LF, the terminator the editor inserts.
 */
fun lineSeparatorLabel(text: String): String {
  var lf = false
  var crlf = false
  var cr = false
  var index = 0
  while (index < text.length) {
    val current = text[index]
    if (current == '\n') {
      lf = true
    } else if (current == '\r') {
      if (index + 1 < text.length && text[index + 1] == '\n') {
        crlf = true
        index++
      } else {
        cr = true
      }
    }
    index++
  }
  return when {
    listOf(lf, crlf, cr).count { it } > 1 -> "Mixed"
    crlf -> "CRLF"
    cr -> "CR"
    else -> "LF"
  }
}
