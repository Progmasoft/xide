/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progmasoft.xide.compiler.DiagnosticSeverity
import com.progmasoft.xide.compiler.CompilerRequest
import com.progmasoft.xide.compiler.VisualXSharpCompilerClient
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val XideBackground = Color(0xFF151321)
private val XidePanel = Color(0xFF201D2E)
private val XideSelected = Color(0xFF343047)
private val XideBorder = Color(0xFF49435E)
private val XideText = Color(0xFFF4F0FF)
private val XideMutedText = Color(0xFFAAA2C0)
private val XideAccent = Color(0xFF8064FF)
private val XideError = Color(0xFFFF7B86)
private val XideWarning = Color(0xFFFFC66D)
private val XideInformation = Color(0xFF79C8FF)

/** Compose desktop surface backed by versioned documents and the structured compiler client. */
@Composable
fun XideApplication(
  owner: Frame,
  session: WorkspaceSession = remember { WorkspaceSession() },
  compilerClient: VisualXSharpCompilerClient = remember { VisualXSharpCompilerClient() },
) {
  var workspace by remember(session) { mutableStateOf(session.snapshot()) }
  var activity by remember { mutableStateOf("Ready") }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  fun openFile() {
    val path = chooseVisualXSharpFile(owner, FileDialog.LOAD, null) ?: return
    scope.launch {
      busy = true
      activity = "Opening ${path.fileName}…"
      runCatching { withContext(Dispatchers.IO) { session.openFile(path) } }
        .onSuccess {
          workspace = it
          activity = "Opened ${path.fileName}"
        }
        .onFailure { activity = it.message ?: "Could not open the file" }
      busy = false
    }
  }

  fun saveFile() {
    val active = workspace.activeDocument ?: return
    val destination = active.filePath ?: chooseVisualXSharpFile(owner, FileDialog.SAVE, active.title) ?: return
    scope.launch {
      busy = true
      activity = "Saving ${destination.fileName}…"
      runCatching { withContext(Dispatchers.IO) { session.saveActive(destination) } }
        .onSuccess {
          workspace = it
          activity = "Saved ${destination.fileName}"
        }
        .onFailure { activity = it.message ?: "Could not save the file" }
      busy = false
    }
  }

  fun checkFile() {
    val selected = workspace.activeDocument ?: return
    if (selected.filePath == null) {
      activity = "Save the document before checking it"
      return
    }
    scope.launch {
      busy = true
      activity = "Checking ${selected.title}…"
      runCatching {
          val prepared = withContext(Dispatchers.IO) {
            if (selected.isDirty) session.saveActive() else session.snapshot()
          }
          workspace = prepared
          val document = checkNotNull(prepared.activeDocument)
          val path = checkNotNull(document.filePath)
          val result = withContext(Dispatchers.IO) { compilerClient.check(CompilerRequest(path)) }
          Triple(document, result, session.publishDiagnostics(document.snapshot.uri, document.snapshot.version, result.diagnostics))
        }
        .onSuccess { (document, result, accepted) ->
          workspace = session.snapshot()
          activity =
            when {
              !accepted -> "Discarded stale results for ${document.title}"
              result.timedOut -> "Check timed out"
              result.succeeded -> "No problems found"
              else -> "Check finished with ${result.diagnostics.diagnostics.size} problem(s)"
            }
        }
        .onFailure { activity = it.message ?: "Compiler check failed" }
      busy = false
    }
  }

  MaterialTheme {
    Column(Modifier.fillMaxSize().background(XideBackground)) {
      ApplicationBar(
        canActOnDocument = workspace.activeDocument != null,
        busy = busy,
        onNewFile = {
          workspace = session.newScratch()
          activity = "Created ${workspace.activeDocument?.title}"
        },
        onOpenFile = ::openFile,
        onSaveFile = ::saveFile,
        onCheckFile = ::checkFile,
      )
      Row(Modifier.weight(1f).fillMaxWidth()) {
        Explorer(workspace, enabled = !busy, onSelect = { workspace = session.select(it) })
        Column(Modifier.weight(1f).fillMaxHeight()) {
          DocumentTabs(workspace, enabled = !busy, onSelect = { workspace = session.select(it) })
          Editor(
            document = workspace.activeDocument,
            readOnly = busy,
            onTextChange = { text -> workspace = session.replaceActiveText(text) },
          )
          DiagnosticsPanel(workspace.activeDocument)
        }
      }
      StatusBar(workspace, activity, busy)
    }
  }
}

@Composable
private fun DiagnosticsPanel(document: OpenDocument?) {
  val diagnostics = document?.diagnostics?.document?.diagnostics.orEmpty()
  if (diagnostics.isEmpty()) return

  Column(
    Modifier.fillMaxWidth().heightIn(max = 200.dp).background(XidePanel).padding(10.dp).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("PROBLEMS (${diagnostics.size})", color = XideMutedText, fontSize = 11.sp)
    diagnostics.forEach { diagnostic ->
      val (marker, color) =
        when (diagnostic.severity) {
          DiagnosticSeverity.ERROR -> "Error" to XideError
          DiagnosticSeverity.WARNING -> "Warning" to XideWarning
          DiagnosticSeverity.INFORMATION -> "Information" to XideInformation
          DiagnosticSeverity.HINT -> "Hint" to XideMutedText
        }
      val location = diagnostic.primaryLocation?.range?.start
      val suffix = location?.let { "  ${it.line + 1u}:${it.column + 1u}" }.orEmpty()
      Text("$marker ${diagnostic.code}$suffix  ${diagnostic.message}", color = color, fontSize = 12.sp)
    }
  }
}

@Composable
private fun ApplicationBar(
  canActOnDocument: Boolean,
  busy: Boolean,
  onNewFile: () -> Unit,
  onOpenFile: () -> Unit,
  onSaveFile: () -> Unit,
  onCheckFile: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().background(XidePanel).padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Xide", color = XideText, fontSize = 18.sp)
    Spacer(Modifier.width(16.dp))
    Button(onClick = onNewFile, enabled = !busy) { Text("New") }
    Spacer(Modifier.width(8.dp))
    Button(onClick = onOpenFile, enabled = !busy) { Text("Open") }
    Spacer(Modifier.width(8.dp))
    Button(onClick = onSaveFile, enabled = canActOnDocument && !busy) { Text("Save") }
    Spacer(Modifier.width(8.dp))
    Button(onClick = onCheckFile, enabled = canActOnDocument && !busy) { Text("Check") }
  }
}

@Composable
private fun Explorer(workspace: WorkspaceSnapshot, enabled: Boolean, onSelect: (Int) -> Unit) {
  Column(Modifier.width(220.dp).fillMaxHeight().background(XidePanel).padding(8.dp)) {
    Text("OPEN EDITORS", color = XideMutedText, fontSize = 11.sp)
    Spacer(Modifier.padding(top = 4.dp))
    workspace.documents.forEachIndexed { index, document ->
      val background = if (index == workspace.activeIndex) XideSelected else Color.Transparent
      Text(
        text = document.title + if (document.isDirty) " ●" else "",
        color = XideText,
        modifier = Modifier.fillMaxWidth().background(background).clickable(enabled = enabled) { onSelect(index) }.padding(8.dp),
      )
    }
  }
}

@Composable
private fun DocumentTabs(workspace: WorkspaceSnapshot, enabled: Boolean, onSelect: (Int) -> Unit) {
  Row(Modifier.fillMaxWidth().background(XidePanel).horizontalScroll(rememberScrollState())) {
    workspace.documents.forEachIndexed { index, document ->
      val background = if (index == workspace.activeIndex) XideSelected else XidePanel
      Text(
        text = document.title + if (document.isDirty) " ●" else "",
        color = if (index == workspace.activeIndex) XideText else XideMutedText,
        modifier = Modifier.background(background).clickable(enabled = enabled) { onSelect(index) }.padding(12.dp, 8.dp),
      )
    }
  }
}

@Composable
private fun Editor(document: OpenDocument?, readOnly: Boolean, onTextChange: (String) -> Unit) {
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
      readOnly = readOnly,
      modifier = Modifier.fillMaxSize().background(XideBackground).padding(16.dp),
      textStyle = TextStyle(color = XideText, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(XideAccent),
    )
  }
}

@Composable
private fun StatusBar(workspace: WorkspaceSnapshot, activity: String, busy: Boolean) {
  val snapshot = workspace.activeDocument?.snapshot
  val status =
    if (snapshot == null) {
      "No document"
    } else {
      "${snapshot.lineMap().lineCount} lines  •  v${snapshot.version}"
    }
  Row(
    Modifier.fillMaxWidth().background(XideBorder).padding(horizontal = 10.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(if (busy) "Working…" else activity, color = XideText, fontSize = 11.sp)
    Text(status, color = XideText, fontSize = 11.sp)
  }
}

/** Native dialogs keep file ownership outside Compose state and return normalized absolute paths. */
private fun chooseVisualXSharpFile(owner: Frame, mode: Int, suggestedName: String?): Path? {
  val dialog = FileDialog(owner, if (mode == FileDialog.LOAD) "Open Visual X# File" else "Save Visual X# File", mode)
  dialog.file = suggestedName
  dialog.filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".vxs") }
  dialog.isVisible = true
  val directory = dialog.directory ?: return null
  val file = dialog.file ?: return null
  val selected = Path.of(directory, file)
  return (if (selected.fileName.toString().endsWith(".vxs")) selected else selected.resolveSibling("$file.vxs"))
    .toAbsolutePath()
    .normalize()
}
