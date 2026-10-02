/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.progmasoft.xide.compiler.CompilerDiagnostic
import com.progmasoft.xide.compiler.SourceLocation
import java.nio.file.Path as FilePath

/** A text button of the toolbar and of inline bars. */
@Composable
internal fun ShellAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
  BasicText(
    label,
    style = if (enabled) XideType.ui else XideType.uiMuted,
    modifier =
      Modifier.semantics { role = Role.Button }
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp),
  )
}

/** The main toolbar: the opened folder's name, then the file and compiler actions. */
@Composable
internal fun MainToolbar(
  projectName: String?,
  canActOnDocument: Boolean,
  canCheck: Boolean,
  busy: Boolean,
  onNewFile: () -> Unit,
  onOpenFile: () -> Unit,
  onOpenFolder: () -> Unit,
  onSaveFile: () -> Unit,
  onCheckFile: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().height(XideMetrics.toolbarHeight).background(XideColors.panel).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicText(
      projectName ?: "Xide",
      style = XideType.ui,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 8.dp),
    )
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(18.dp).background(XideColors.border))
    ShellAction("New", enabled = !busy, onClick = onNewFile)
    ShellAction("Open File", enabled = !busy, onClick = onOpenFile)
    ShellAction("Open Folder", enabled = !busy, onClick = onOpenFolder)
    ShellAction("Save", enabled = canActOnDocument && !busy, onClick = onSaveFile)
    Spacer(Modifier.weight(1f))
    ShellAction("Check", enabled = canCheck && !busy, onClick = onCheckFile)
  }
}

/**
 * The narrow bar on the window's left edge that opens and closes tool windows.
 *
 * Left-docked windows have their buttons at the top and bottom-docked windows at the bottom of the same bar, so
 * every tool window is reachable from one place whether or not it is open.
 */
@Composable
internal fun ToolWindowStripe(layout: ToolWindowLayout, onToggle: (ToolWindowId) -> Unit) {
  Column(
    Modifier.width(XideMetrics.stripeWidth).fillMaxHeight().background(XideColors.panel).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ToolWindowId.entries.filter { it.anchor == ToolWindowAnchor.LEFT }.forEach { StripeButton(it, layout, onToggle) }
    Spacer(Modifier.weight(1f))
    ToolWindowId.entries.filter { it.anchor == ToolWindowAnchor.BOTTOM }.forEach { StripeButton(it, layout, onToggle) }
  }
}

@Composable
private fun StripeButton(id: ToolWindowId, layout: ToolWindowLayout, onToggle: (ToolWindowId) -> Unit) {
  val open = layout.isVisible(id)
  Box(
    Modifier.padding(vertical = 3.dp)
      .size(30.dp)
      .background(if (open) XideColors.selection else Color.Transparent)
      .semantics {
        contentDescription = "${id.title} tool window"
        role = Role.Button
        selected = open
      }
      .clickable { onToggle(id) },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(16.dp)) {
      when (id) {
        ToolWindowId.PROJECT -> drawFolderGlyph(XideColors.text)
        ToolWindowId.PROBLEMS -> drawProblemGlyph(XideColors.text)
      }
    }
  }
}

/** A folder outline: a body with a raised tab on its upper left. */
private fun DrawScope.drawFolderGlyph(color: Color) {
  val stroke = Stroke(width = size.minDimension / 10f)
  val tab =
    Path().apply {
      moveTo(size.width * 0.06f, size.height * 0.30f)
      lineTo(size.width * 0.06f, size.height * 0.18f)
      lineTo(size.width * 0.42f, size.height * 0.18f)
      lineTo(size.width * 0.54f, size.height * 0.30f)
    }
  drawPath(tab, color, style = stroke)
  drawRect(
    color,
    topLeft = Offset(size.width * 0.06f, size.height * 0.30f),
    size = Size(size.width * 0.88f, size.height * 0.54f),
    style = stroke,
  )
}

/** A circle with an exclamation mark, the usual sign for a list of problems. */
private fun DrawScope.drawProblemGlyph(color: Color) {
  val stroke = size.minDimension / 10f
  drawCircle(color, radius = size.minDimension * 0.44f, style = Stroke(width = stroke))
  drawLine(
    color,
    Offset(size.width / 2f, size.height * 0.26f),
    Offset(size.width / 2f, size.height * 0.56f),
    strokeWidth = stroke * 1.2f,
  )
  drawCircle(color, radius = stroke * 0.75f, center = Offset(size.width / 2f, size.height * 0.72f))
}

/** The title row every tool window starts with: its name, optional detail, its own actions and a hide control. */
@Composable
private fun ToolWindowHeader(title: String, detail: String?, onHide: () -> Unit, actions: @Composable () -> Unit = {}) {
  Row(
    Modifier.fillMaxWidth().height(XideMetrics.headerHeight).padding(start = 12.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicText(title, style = XideType.ui)
    if (detail != null) {
      BasicText(
        detail,
        style = XideType.smallMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 10.dp).weight(1f),
      )
    } else {
      Spacer(Modifier.weight(1f))
    }
    actions()
    BasicText(
      "—",
      style = XideType.uiMuted,
      modifier =
        Modifier.semantics {
            contentDescription = "Hide $title"
            role = Role.Button
          }
          .clickable(onClick = onHide)
          .padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

/**
 * The Project tool window: the opened folder as a tree.
 *
 * A directory row expands or collapses; a file row asks the shell to open the file. Which entries are listed is
 * decided by the tree, not here: Xide leaves out only what `.gitignore` or `.xide/exclude.list` excludes.
 */
@Composable
internal fun ProjectToolWindow(
  tree: ProjectTree?,
  rows: List<ProjectRow>,
  activeFile: FilePath?,
  enabled: Boolean,
  onToggleDirectory: (FilePath) -> Unit,
  onOpenFile: (FilePath) -> Unit,
  onOpenFolder: () -> Unit,
  onRefresh: () -> Unit,
  onHide: () -> Unit,
) {
  Column(Modifier.width(XideMetrics.projectWidth).fillMaxHeight().background(XideColors.panel)) {
    ToolWindowHeader(ToolWindowId.PROJECT.title, tree?.rootName, onHide) {
      if (tree != null) {
        BasicText(
          "⟳",
          style = XideType.uiMuted,
          modifier =
            Modifier.semantics {
                contentDescription = "Reload project files"
                role = Role.Button
              }
              .clickable(enabled = enabled, onClick = onRefresh)
              .padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
    if (tree == null) {
      Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("No folder is open.", style = XideType.uiMuted)
        ShellAction("Open Folder", enabled = enabled, onClick = onOpenFolder)
      }
      return@Column
    }
    if (rows.isEmpty()) {
      BasicText("This folder has no entries to show.", style = XideType.uiMuted, modifier = Modifier.padding(12.dp))
      return@Column
    }
    LazyColumn(Modifier.fillMaxSize()) {
      items(rows, key = { row -> (row.note ?: "") + "|" + row.entry.path.toString() }) { row ->
        ProjectRowView(row, activeFile, enabled, onToggleDirectory, onOpenFile)
      }
    }
  }
}

@Composable
private fun ProjectRowView(
  row: ProjectRow,
  activeFile: FilePath?,
  enabled: Boolean,
  onToggleDirectory: (FilePath) -> Unit,
  onOpenFile: (FilePath) -> Unit,
) {
  val indent = XideMetrics.treeIndent * row.depth
  if (row.note != null) {
    BasicText(
      row.note,
      style = XideType.smallMuted,
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp + indent + XideMetrics.treeIndent, top = 3.dp, bottom = 3.dp),
    )
    return
  }
  val entry = row.entry
  val active = !entry.isDirectory && activeFile != null && activeFile == entry.path
  Row(
    Modifier.fillMaxWidth()
      .background(if (active) XideColors.selection else Color.Transparent)
      .clickable(enabled = enabled) { if (entry.isDirectory) onToggleDirectory(entry.path) else onOpenFile(entry.path) }
      .padding(start = 12.dp + indent, end = 8.dp, top = 3.dp, bottom = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Files reserve the chevron's width so names at one depth share a left edge.
    BasicText(
      if (!entry.isDirectory) " " else if (row.expanded) "▾" else "▸",
      style = XideType.uiMuted,
      modifier = Modifier.width(XideMetrics.treeIndent),
    )
    val color =
      when {
        entry.isDirectory -> XideColors.folder
        SourceLanguage.ofFileName(entry.name) != null -> XideColors.source
        else -> XideColors.mutedText
      }
    Box(Modifier.padding(end = 6.dp).size(8.dp).background(color))
    BasicText(entry.name, style = XideType.ui, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** The Problems tool window: the compiler's findings for the active document, most recent check only. */
@Composable
internal fun ProblemsToolWindow(
  document: OpenDocument?,
  enabled: Boolean,
  onShowLocation: (SourceLocation) -> Unit,
  onHide: () -> Unit,
) {
  val diagnostics = document?.diagnostics?.document?.diagnostics.orEmpty()
  val counts = ProblemCounts.of(document)
  val detail =
    when {
      document == null -> null
      counts.total == 0 -> document.title
      else -> "${document.title}  ${problemSummary(counts)}"
    }
  Column(Modifier.fillMaxWidth().height(XideMetrics.problemsHeight).background(XideColors.panel)) {
    ToolWindowHeader(ToolWindowId.PROBLEMS.title, detail, onHide)
    if (diagnostics.isEmpty()) {
      val message =
        when {
          document == null -> "No document is open."
          document.diagnostics == null -> "Run Check to see the problems of ${document.title}."
          else -> "No problems in ${document.title}."
        }
      BasicText(message, style = XideType.uiMuted, modifier = Modifier.padding(12.dp))
      return@Column
    }
    LazyColumn(Modifier.fillMaxSize()) {
      items(diagnostics.size) { index -> ProblemRow(diagnostics[index], enabled, onShowLocation) }
    }
  }
}

@Composable
private fun ProblemRow(diagnostic: CompilerDiagnostic, enabled: Boolean, onShowLocation: (SourceLocation) -> Unit) {
  val primary = diagnostic.primaryLocation
  val position = primary?.range?.start?.let { ":${it.line + 1u}:${it.column + 1u}" }.orEmpty()
  val file = primary?.source?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
  Row(
    (if (primary == null) Modifier else Modifier.clickable(enabled = enabled) { onShowLocation(primary) })
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      Modifier.size(8.dp)
        .background(XideColors.severity(diagnostic.severity))
        .semantics { contentDescription = diagnostic.severity.label() }
    )
    BasicText(diagnostic.message, style = XideType.ui, maxLines = 1, overflow = TextOverflow.Ellipsis)
    BasicText(diagnostic.code, style = XideType.smallMuted, maxLines = 1)
    if (primary != null) BasicText("$file$position", style = XideType.smallMuted, maxLines = 1)
  }
}

internal fun problemSummary(counts: ProblemCounts): String {
  val parts = ArrayList<String>(3)
  if (counts.errors > 0) parts += plural(counts.errors, "error")
  if (counts.warnings > 0) parts += plural(counts.warnings, "warning")
  if (counts.others > 0) parts += plural(counts.others, "note")
  return parts.joinToString(", ")
}

private fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

/** The status bar: what the shell is doing on the left, facts about the active document on the right. */
@Composable
internal fun StatusBar(document: OpenDocument?, caret: CaretPosition?, activity: String, busy: Boolean) {
  Row(
    Modifier.fillMaxWidth().height(XideMetrics.statusHeight).background(XideColors.panel).padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicText(if (busy) "Working…" else activity, style = XideType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.weight(1f))
    if (document == null) {
      BasicText("No document", style = XideType.smallMuted)
      return@Row
    }
    val counts = ProblemCounts.of(document)
    if (counts.total > 0) StatusItem(problemSummary(counts), "Problems in the active document")
    if (caret != null) StatusItem(caret.toString(), "Caret position")
    StatusItem(lineSeparatorLabel(document.snapshot.text), "Line separator")
    StatusItem("UTF-8", "File encoding")
    StatusItem(document.language?.displayName ?: "Plain text", "Language")
  }
}

@Composable
private fun StatusItem(text: String, description: String) {
  BasicText(
    text,
    style = XideType.small,
    modifier = Modifier.padding(start = 16.dp).semantics { contentDescription = description },
  )
}
