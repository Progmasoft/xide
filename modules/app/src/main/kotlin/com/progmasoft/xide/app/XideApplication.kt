/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.progmasoft.xide.compiler.CompilerRequest
import com.progmasoft.xide.compiler.SourceLocation
import com.progmasoft.xide.compiler.VisualXSharpCompilerClient
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Xide desktop shell.
 *
 * The window follows the familiar IDE arrangement: a toolbar on top, a stripe of tool-window buttons on the left
 * edge, the Project tool window beside it, the tabbed editor in the centre with the Problems tool window below it,
 * and a status bar at the bottom. All document state lives in [session]; the shell only renders immutable
 * snapshots and forwards user intent.
 */
@Composable
fun XideApplication(
  owner: Frame?,
  session: WorkspaceSession = remember { WorkspaceSession() },
  compilerClient: VisualXSharpCompilerClient = remember { VisualXSharpCompilerClient() },
  initialProject: ProjectTree? = null,
) {
  var workspace by remember(session) { mutableStateOf(session.snapshot()) }
  var activity by remember { mutableStateOf("Ready") }
  var busy by remember { mutableStateOf(false) }
  // A navigation request is applied once by the editor that shows its document version. The serial makes a repeated
  // click on the same problem a new request after the caret has moved away.
  var navigation by remember { mutableStateOf<NavigationRequest?>(null) }
  var layout by remember { mutableStateOf(ToolWindowLayout()) }
  var project by remember { mutableStateOf(initialProject) }
  // The tree is mutable and not observable; bumping this revision is what makes Compose read its rows again.
  var projectRevision by remember { mutableIntStateOf(0) }
  var caretOffset by remember { mutableIntStateOf(0) }
  // The tab whose close was requested while it had unsaved changes, identified by its document URI.
  var pendingClose by remember { mutableStateOf<java.net.URI?>(null) }
  val scope = rememberCoroutineScope()

  val activeDocument = workspace.activeDocument
  val projectRows = remember(project, projectRevision) { project?.rows().orEmpty() }
  val markers = remember(workspace) { workspace.activeIndex?.let(session::lineMarkers).orEmpty() }

  fun showLocation(location: SourceLocation) {
    val owningIndex = workspace.activeIndex ?: return
    val target = session.locate(owningIndex, location)
    if (target == null) {
      activity = "This location is not in an open, unmodified document"
      return
    }
    workspace = session.select(target.documentIndex)
    navigation = NavigationRequest((navigation?.serial ?: 0L) + 1L, target)
    activity = "Showing ${workspace.activeDocument?.title}"
  }

  fun openPath(path: Path) {
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

  fun openFile() {
    openPath(chooseSourceFile(owner, FileDialog.LOAD, null) ?: return)
  }

  fun openProjectEntry(path: Path) {
    if (SourceLanguage.of(path) != null) {
      openPath(path)
    } else {
      activity = "${path.fileName} is not a source file Xide can open"
    }
  }

  fun openFolder() {
    val directory = chooseDirectory(owner) ?: return
    scope.launch {
      busy = true
      activity = "Opening ${directory.fileName ?: directory}…"
      val ignore = ProjectIgnore(directory)
      val tree = ProjectTree(directory, FileSystemDirectoryReader(ignore))
      // Reading the first level also loads, and when needed generates, the folder's exclusion list.
      withContext(Dispatchers.IO) { tree.rows() }
      project = tree
      projectRevision++
      layout = layout.show(ToolWindowId.PROJECT)
      activity =
        ignore.saveFailure?.let { "Opened ${tree.rootName}; ${ProjectIgnore.EXCLUDE_LIST} could not be saved: $it" }
          ?: "Opened ${tree.rootName}"
      busy = false
    }
  }

  fun refreshProject() {
    val tree = project ?: return
    scope.launch {
      busy = true
      tree.refresh()
      withContext(Dispatchers.IO) { tree.rows() }
      projectRevision++
      activity = "Reloaded ${tree.rootName}"
      busy = false
    }
  }

  fun toggleDirectory(directory: Path) {
    val tree = project ?: return
    scope.launch {
      tree.toggle(directory)
      // Expanding reads the directory once; keep that off the UI thread.
      withContext(Dispatchers.IO) { tree.rows() }
      projectRevision++
    }
  }

  fun saveFile(then: () -> Unit = {}) {
    val active = workspace.activeDocument ?: return
    val destination = active.filePath ?: chooseSourceFile(owner, FileDialog.SAVE, active.title) ?: return
    scope.launch {
      busy = true
      activity = "Saving ${destination.fileName}…"
      runCatching { withContext(Dispatchers.IO) { session.saveActive(destination) } }
        .onSuccess {
          workspace = it
          activity = "Saved ${destination.fileName}"
          if (project != null) {
            project?.refresh()
            projectRevision++
          }
          then()
        }
        .onFailure { activity = it.message ?: "Could not save the file" }
      busy = false
    }
  }

  fun closeDocument(index: Int, discardChanges: Boolean = false) {
    val document = workspace.documents.getOrNull(index) ?: return
    val closed = session.close(index, discardChanges)
    if (closed == null) {
      // Nothing is closed yet; the bar above the editor asks what to do with the unsaved text.
      workspace = session.select(index)
      pendingClose = document.snapshot.uri
      return
    }
    pendingClose = null
    workspace = closed
    activity = "Closed ${document.title}"
  }

  fun checkFile() {
    val selected = workspace.activeDocument ?: return
    if (selected.language != SourceLanguage.VISUAL_XSHARP) {
      // Opening a language and analyzing it are separate capabilities; only the Visual X# compiler is connected.
      activity = "Check is available for Visual X# sources; ${selected.language?.displayName ?: "this file"} analysis is not connected yet"
      return
    }
    if (selected.filePath == null) {
      activity = "Save the document before checking it"
      return
    }
    scope.launch {
      busy = true
      activity = "Checking ${selected.title}…"
      runCatching {
          val prepared = withContext(Dispatchers.IO) { if (selected.isDirty) session.saveActive() else session.snapshot() }
          workspace = prepared
          val document = checkNotNull(prepared.activeDocument)
          val path = checkNotNull(document.filePath)
          val result = withContext(Dispatchers.IO) { compilerClient.check(CompilerRequest(path)) }
          Triple(document, result, session.publishDiagnostics(document.snapshot.uri, document.snapshot.version, result.diagnostics))
        }
        .onSuccess { (document, result, accepted) ->
          workspace = session.snapshot()
          if (accepted && !result.succeeded) layout = layout.show(ToolWindowId.PROBLEMS)
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

  fun newFile() {
    workspace = session.newScratch()
    activity = "Created ${workspace.activeDocument?.title}"
  }

  /** Shell-wide shortcuts. They are handled before the focused editor sees the key. */
  fun handleKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isAltPressed && !event.isCtrlPressed) {
      when (event.key) {
        Key.One -> layout = layout.toggle(ToolWindowId.PROJECT)
        Key.Six -> layout = layout.toggle(ToolWindowId.PROBLEMS)
        else -> return false
      }
      return true
    }
    if (!event.isCtrlPressed || event.isAltPressed || busy) return false
    when (event.key) {
      Key.N -> newFile()
      Key.O -> openFile()
      Key.S -> if (workspace.activeDocument != null) saveFile() else return false
      Key.W,
      Key.F4 -> workspace.activeIndex?.let(::closeDocument) ?: return false
      else -> return false
    }
    return true
  }

  Column(Modifier.fillMaxSize().background(XideColors.editor).onPreviewKeyEvent(::handleKey)) {
    MainToolbar(
      projectName = project?.rootName,
      canActOnDocument = activeDocument != null,
      canCheck = activeDocument?.language == SourceLanguage.VISUAL_XSHARP,
      busy = busy,
      onNewFile = ::newFile,
      onOpenFile = ::openFile,
      onOpenFolder = ::openFolder,
      onSaveFile = { saveFile() },
      onCheckFile = ::checkFile,
    )
    HorizontalBorder()
    Row(Modifier.weight(1f).fillMaxWidth()) {
      ToolWindowStripe(layout, onToggle = { layout = layout.toggle(it) })
      VerticalBorder()
      if (layout.isVisible(ToolWindowId.PROJECT)) {
        ProjectToolWindow(
          tree = project,
          rows = projectRows,
          activeFile = activeDocument?.filePath,
          enabled = !busy,
          onToggleDirectory = ::toggleDirectory,
          onOpenFile = ::openProjectEntry,
          onOpenFolder = ::openFolder,
          onRefresh = ::refreshProject,
          onHide = { layout = layout.toggle(ToolWindowId.PROJECT) },
        )
        VerticalBorder()
      }
      Column(Modifier.weight(1f).fillMaxHeight()) {
        EditorTabs(
          workspace,
          enabled = !busy,
          onSelect = {
            workspace = session.select(it)
            pendingClose = null
          },
          onClose = ::closeDocument,
        )
        HorizontalBorder()
        val closing = workspace.documents.indexOfFirst { it.snapshot.uri == pendingClose }
        if (closing >= 0) {
          val document = workspace.documents[closing]
          UnsavedChangesBar(
            title = document.title,
            canSave = !busy,
            onSaveAndClose = {
              workspace = session.select(closing)
              // Saving a scratch document gives it a new identity, so the tab is found again as the active one.
              saveFile(then = { session.snapshot().activeIndex?.let { closeDocument(it) } })
            },
            onDiscard = { closeDocument(closing, discardChanges = true) },
            onCancel = { pendingClose = null },
          )
          HorizontalBorder()
        }
        // The editor takes the height that remains after the tabs and the Problems tool window. Filling the whole
        // column instead would leave the tool window below it with no height.
        EditorArea(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          document = activeDocument,
          markers = markers,
          readOnly = busy,
          navigation = navigation,
          onTextChange = { text -> workspace = session.replaceActiveText(text) },
          onCaretChange = { caretOffset = it },
        )
        if (layout.isVisible(ToolWindowId.PROBLEMS)) {
          HorizontalBorder()
          ProblemsToolWindow(
            document = activeDocument,
            enabled = !busy,
            onShowLocation = ::showLocation,
            onHide = { layout = layout.toggle(ToolWindowId.PROBLEMS) },
          )
        }
      }
    }
    HorizontalBorder()
    StatusBar(
      document = activeDocument,
      caret = activeDocument?.let { caretPosition(it.snapshot, caretOffset) },
      activity = activity,
      busy = busy,
    )
  }
}

@Composable
private fun HorizontalBorder() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(XideColors.border))
}

@Composable
private fun VerticalBorder() {
  Box(Modifier.fillMaxHeight().width(1.dp).background(XideColors.border))
}

/**
 * Native dialogs keep file ownership outside Compose state and return normalized absolute paths.
 *
 * Opening accepts any source Xide recognizes. Saving keeps a recognized extension the user typed and otherwise
 * adds `.vxs`, the extension of the scratch documents that are the only ones without a path.
 */
private fun chooseSourceFile(owner: Frame?, mode: Int, suggestedName: String?): Path? {
  val dialog = FileDialog(owner, if (mode == FileDialog.LOAD) "Open File" else "Save File", mode)
  dialog.file = suggestedName
  dialog.filenameFilter = java.io.FilenameFilter { _, name -> SourceLanguage.ofFileName(name) != null }
  dialog.isVisible = true
  val directory = dialog.directory ?: return null
  val file = dialog.file ?: return null
  val selected = Path.of(directory, file)
  val resolved =
    if (mode == FileDialog.LOAD || SourceLanguage.of(selected) != null) selected else selected.resolveSibling("$file.vxs")
  return resolved.toAbsolutePath().normalize()
}

/** The AWT file dialog cannot select a directory on every platform, so the folder chooser is the Swing one. */
private fun chooseDirectory(owner: Frame?): Path? {
  val chooser = JFileChooser()
  chooser.dialogTitle = "Open Folder"
  chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
  chooser.isAcceptAllFileFilterUsed = false
  if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return null
  return chooser.selectedFile?.toPath()?.toAbsolutePath()?.normalize()
}
