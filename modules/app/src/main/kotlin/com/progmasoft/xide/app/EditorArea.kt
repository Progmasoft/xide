/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange as SelectionRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.progmasoft.xide.compiler.DiagnosticSeverity

/** One user request to reveal a resolved diagnostic location. */
internal data class NavigationRequest(val serial: Long, val target: NavigationTarget)

/** The tab strip above the editor: one tab per open document, each with its own close control. */
@Composable
internal fun EditorTabs(
  workspace: WorkspaceSnapshot,
  enabled: Boolean,
  onSelect: (Int) -> Unit,
  onClose: (Int) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth()
      .height(XideMetrics.headerHeight)
      .background(XideColors.panel)
      .horizontalScroll(rememberScrollState())
  ) {
    workspace.documents.forEachIndexed { index, document ->
      val active = index == workspace.activeIndex
      Column(Modifier.fillMaxHeight().background(if (active) XideColors.editor else XideColors.panel)) {
        Row(
          Modifier.weight(1f).clickable(enabled = enabled) { onSelect(index) }.padding(start = 12.dp, end = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          BasicText(document.title, style = if (active) XideType.ui else XideType.uiMuted)
          // The dot replaces nothing: a modified tab shows both its marker and its close control.
          if (document.isDirty) BasicText("●", style = XideType.small.copy(color = XideColors.accent))
          BasicText(
            "×",
            style = XideType.uiMuted,
            modifier =
              Modifier.semantics { contentDescription = "Close ${document.title}" }
                .clickable(enabled = enabled) { onClose(index) }
                .padding(horizontal = 4.dp),
          )
        }
        // The active tab carries the accent underline.
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) XideColors.accent else XideColors.panel))
      }
    }
  }
}

/** Shown instead of closing a modified tab, so unsaved text is never dropped without a decision. */
@Composable
internal fun UnsavedChangesBar(
  title: String,
  canSave: Boolean,
  onSaveAndClose: () -> Unit,
  onDiscard: () -> Unit,
  onCancel: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().background(XideColors.hover).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BasicText("$title has unsaved changes.", style = XideType.ui, modifier = Modifier.weight(1f))
    if (canSave) ShellAction("Save and Close", onClick = onSaveAndClose)
    ShellAction("Discard Changes", onClick = onDiscard)
    ShellAction("Cancel", onClick = onCancel)
  }
}

/**
 * The editor of the active document: a gutter with problem markers and line numbers beside one text field.
 *
 * The gutter and the field scroll vertically as one unit and share a text style, so line N of the gutter is always
 * beside line N of the text. Lines are not wrapped; long lines scroll horizontally, because a wrapped line would
 * occupy several rows while the gutter has one number for it.
 */
@Composable
internal fun EditorArea(
  modifier: Modifier,
  document: OpenDocument?,
  markers: Map<Int, DiagnosticSeverity>,
  readOnly: Boolean,
  navigation: NavigationRequest?,
  onTextChange: (String) -> Unit,
  onCaretChange: (Int) -> Unit,
) {
  if (document == null) {
    Box(modifier.background(XideColors.editor), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Visual X#", style = XideType.ui.copy(fontSize = XideType.ui.fontSize * 2))
        BasicText("Create or open a file to begin editing.", style = XideType.uiMuted)
      }
    }
    return
  }

  // Keying the editor by URI changes its backing value when a tab is selected while preserving edits within one tab.
  key(document.snapshot.uri) {
    val snapshot = document.snapshot
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var selection by remember { mutableStateOf(SelectionRange.Zero) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // The versioned document owns the text; this field only adds the caret. A selection left over from longer text
    // is clamped so it can never address offsets outside the current snapshot.
    val length = snapshot.text.length
    val visibleSelection = SelectionRange(selection.start.coerceIn(0, length), selection.end.coerceIn(0, length))
    LaunchedEffect(visibleSelection.end, snapshot.version) { onCaretChange(visibleSelection.end) }

    BoxWithConstraints(modifier.background(XideColors.editor)) {
      val viewportHeight = maxHeight
      val viewportHeightPx = with(density) { viewportHeight.toPx() }

      // The target names the exact version its offsets were computed for. Applying it to any other text would
      // select unrelated source, so a request for another document or an older version is ignored.
      LaunchedEffect(navigation) {
        val target = navigation?.target
        if (target != null && target.uri == snapshot.uri && target.version == snapshot.version) {
          // A focused text field owns its caret and immediately collapses a selection set from outside, so the
          // editor gives up focus first and shows the range one frame later. Clicking into the editor resumes
          // typing.
          focusManager.clearFocus()
          withFrameNanos {}
          selection = SelectionRange(target.range.start, target.range.end)
          // The field no longer scrolls itself once it is unfocused, so the shared scroll position is moved to
          // put the target line about a third of the way down the viewport.
          layout?.let { measured ->
            if (target.range.start <= measured.layoutInput.text.length) {
              val top = measured.getLineTop(measured.getLineForOffset(target.range.start))
              vertical.scrollTo((top - viewportHeightPx / 3f).toInt().coerceAtLeast(0))
            }
          }
        }
      }

      val lineCount = remember(snapshot.version, snapshot.uri) { snapshot.lineMap().lineCount }
      val numbers = remember(lineCount) { gutterNumbers(lineCount) }
      val markerText = remember(lineCount, markers) { gutterMarkers(lineCount, markers) }

      Row(Modifier.verticalScroll(vertical).heightIn(min = viewportHeight)) {
        BasicText(
          markerText,
          style = XideType.code,
          modifier =
            Modifier.semantics { contentDescription = "Problem markers" }
              .padding(start = 6.dp, top = XideMetrics.editorPadding, bottom = XideMetrics.editorPadding),
        )
        BasicText(
          numbers,
          style = XideType.code.copy(color = XideColors.gutterText),
          modifier =
            Modifier.semantics { contentDescription = "Line numbers" }
              .padding(horizontal = 8.dp, vertical = XideMetrics.editorPadding),
        )
        Box(Modifier.width(1.dp).heightIn(min = viewportHeight).background(XideColors.border))
        BoxWithConstraints(Modifier.weight(1f)) {
          val textWidth = maxWidth
          Box(Modifier.horizontalScroll(horizontal)) {
            BasicTextField(
              value = TextFieldValue(snapshot.text, visibleSelection),
              onValueChange = { value ->
                selection = value.selection
                if (value.text != snapshot.text) onTextChange(value.text)
              },
              readOnly = readOnly,
              // The minimum size makes the whole editor surface, not only the typed text, accept a click.
              modifier =
                Modifier.widthIn(min = textWidth)
                  .heightIn(min = viewportHeight)
                  .padding(horizontal = 10.dp, vertical = XideMetrics.editorPadding),
              textStyle = XideType.code,
              cursorBrush = SolidColor(XideColors.text),
              onTextLayout = { layout = it },
            )
          }
        }
      }
    }
  }
}

/**
 * The marker column of the gutter: one line per document line, a coloured dot on lines that have a problem.
 *
 * It is a single text with the editor's style, which keeps every dot on the same baseline as its line without
 * measuring each line separately.
 */
internal fun gutterMarkers(lineCount: Int, markers: Map<Int, DiagnosticSeverity>): AnnotatedString =
  buildAnnotatedString {
    for (line in 0 until lineCount) {
      if (line > 0) append('\n')
      val severity = markers[line]
      if (severity == null) {
        append(' ')
      } else {
        withStyle(SpanStyle(color = XideColors.severity(severity))) { append('●') }
      }
    }
  }
