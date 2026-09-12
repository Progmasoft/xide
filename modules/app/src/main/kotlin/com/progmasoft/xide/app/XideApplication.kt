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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progmasoft.xide.compiler.DiagnosticSeverity

private val XideBackground = Color(0xFF151321)
private val XidePanel = Color(0xFF201D2E)
private val XideSelected = Color(0xFF343047)
private val XideBorder = Color(0xFF49435E)
private val XideText = Color(0xFFF4F0FF)
private val XideMutedText = Color(0xFFAAA2C0)
private val XideAccent = Color(0xFF8064FF)

/** First real Compose desktop surface backed by the versioned document foundation. */
@Composable
fun XideApplication(session: WorkspaceSession = remember { WorkspaceSession() }) {
  var workspace by remember(session) { mutableStateOf(session.snapshot()) }

  MaterialTheme {
    Column(Modifier.fillMaxSize().background(XideBackground)) {
      ApplicationBar(onNewFile = { workspace = session.newScratch() })
      Row(Modifier.weight(1f).fillMaxWidth()) {
        Explorer(workspace, onSelect = { workspace = session.select(it) })
        Column(Modifier.weight(1f).fillMaxHeight()) {
          DocumentTabs(workspace, onSelect = { workspace = session.select(it) })
          Editor(
            document = workspace.activeDocument,
            onTextChange = { text -> workspace = session.replaceActiveText(text) },
          )
          DiagnosticsPanel(workspace.activeDocument)
        }
      }
      StatusBar(workspace)
    }
  }
}

@Composable
private fun DiagnosticsPanel(document: OpenDocument?) {
  val diagnostics = document?.diagnostics?.document?.diagnostics.orEmpty()
  if (diagnostics.isEmpty()) return

  Column(
    Modifier.fillMaxWidth().heightIn(max = 180.dp).background(XidePanel).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("PROBLEMS (${diagnostics.size})", color = XideMutedText, fontSize = 11.sp)
    diagnostics.forEach { diagnostic ->
      val marker =
        when (diagnostic.severity) {
          DiagnosticSeverity.ERROR -> "Error"
          DiagnosticSeverity.WARNING -> "Warning"
          DiagnosticSeverity.INFORMATION -> "Information"
          DiagnosticSeverity.HINT -> "Hint"
        }
      val location = diagnostic.primaryLocation?.range?.start
      val suffix = location?.let { "  ${it.line + 1u}:${it.column + 1u}" }.orEmpty()
      Text("$marker ${diagnostic.code}$suffix  ${diagnostic.message}", color = XideText, fontSize = 12.sp)
    }
  }
}

@Composable
private fun ApplicationBar(onNewFile: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().background(XidePanel).padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Xide", color = XideText, fontSize = 18.sp)
    Spacer(Modifier.width(16.dp))
    Button(onClick = onNewFile) { Text("New Visual X# File") }
  }
}

@Composable
private fun Explorer(workspace: WorkspaceSnapshot, onSelect: (Int) -> Unit) {
  Column(Modifier.width(220.dp).fillMaxHeight().background(XidePanel).padding(8.dp)) {
    Text("OPEN EDITORS", color = XideMutedText, fontSize = 11.sp)
    Spacer(Modifier.padding(top = 4.dp))
    workspace.documents.forEachIndexed { index, document ->
      val background = if (index == workspace.activeIndex) XideSelected else Color.Transparent
      Text(
        text = document.title,
        color = XideText,
        modifier = Modifier.fillMaxWidth().background(background).clickable { onSelect(index) }.padding(8.dp),
      )
    }
  }
}

@Composable
private fun DocumentTabs(workspace: WorkspaceSnapshot, onSelect: (Int) -> Unit) {
  Row(Modifier.fillMaxWidth().background(XidePanel).horizontalScroll(rememberScrollState())) {
    workspace.documents.forEachIndexed { index, document ->
      val background = if (index == workspace.activeIndex) XideSelected else XidePanel
      Text(
        text = document.title,
        color = if (index == workspace.activeIndex) XideText else XideMutedText,
        modifier = Modifier.background(background).clickable { onSelect(index) }.padding(12.dp, 8.dp),
      )
    }
  }
}

@Composable
private fun Editor(document: OpenDocument?, onTextChange: (String) -> Unit) {
  if (document == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Visual X#", color = XideText, fontSize = 28.sp)
        Text("Create a file to begin editing.", color = XideMutedText)
      }
    }
    return
  }

  // Keying the editor by URI changes its backing value when a tab is selected while preserving edits within one tab.
  androidx.compose.runtime.key(document.snapshot.uri) {
    BasicTextField(
      value = document.snapshot.text,
      onValueChange = onTextChange,
      modifier = Modifier.fillMaxSize().background(XideBackground).padding(16.dp),
      textStyle = TextStyle(color = XideText, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(XideAccent),
    )
  }
}

@Composable
private fun StatusBar(workspace: WorkspaceSnapshot) {
  val snapshot = workspace.activeDocument?.snapshot
  val status =
    if (snapshot == null) {
      "No document"
    } else {
      "${snapshot.lineMap().lineCount} lines  •  UTF-16  •  v${snapshot.version}"
    }
  Row(
    Modifier.fillMaxWidth().background(XideBorder).padding(horizontal = 10.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.End,
  ) {
    Text(status, color = XideText, fontSize = 11.sp)
  }
}
