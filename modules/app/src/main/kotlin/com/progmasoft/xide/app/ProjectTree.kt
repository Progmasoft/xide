/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * One file or directory shown in the Project tool window.
 *
 * @property path the entry's absolute path.
 * @property name the entry's file name, as shown in the tree.
 * @property isDirectory whether the entry expands; a link to a directory is not a directory here.
 */
data class ProjectEntry(
  val path: Path,
  val name: String,
  val isDirectory: Boolean,
)

/**
 * The children of one directory as the tree shows them.
 *
 * [truncated] is true when the directory holds more entries than the listing limit, and [failure] carries the reason
 * a directory could not be read. Both are shown in the tree, so an incomplete listing never looks complete.
 *
 * @property entries the listed entries in display order.
 * @property truncated whether entries beyond the listing limit were left out.
 * @property failure why the directory could not be read, or null when it was read.
 */
data class ProjectListing(
  val entries: List<ProjectEntry>,
  val truncated: Boolean = false,
  val failure: String? = null,
)

/**
 * One visible line of the flattened project tree.
 *
 * @property entry the file or directory on this line.
 * @property depth how many directories lie between the opened folder and the entry.
 * @property expanded whether the entry is a directory whose children follow it.
 * @property note a notice shown in place of children: a read failure or a truncation notice.
 */
data class ProjectRow(
  val entry: ProjectEntry,
  val depth: Int,
  val expanded: Boolean,
  val note: String? = null,
)

/** Reads one directory level; the tree never walks the filesystem recursively on its own. */
fun interface ProjectDirectoryReader {
  /** Lists the children of one directory. */
  fun list(directory: Path): ProjectListing

  /** Drops whatever the reader cached, so the next listing reflects the filesystem again. */
  fun invalidate() {}
}

/**
 * Lists a directory the way the Project tool window presents it: directories before files, each group ordered by
 * name without regard to case.
 *
 * Nothing is left out automatically. An entry is omitted only when [ignore] excludes it, that is when a
 * `.gitignore` or the folder's `.xide/exclude.list` says so.
 *
 * Symbolic links are listed but never followed: a link to a directory appears as a file, so a link cycle cannot
 * make the tree infinite and a link cannot lead the tree outside the opened folder.
 */
class FileSystemDirectoryReader(
  private val ignore: ProjectIgnore,
  private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) : ProjectDirectoryReader {
  init {
    require(maximumEntries > 0) { "maximumEntries must be positive" }
  }

  /** Lists one directory level, leaving out what the exclusion list excludes. */
  override fun list(directory: Path): ProjectListing {
    val collected = ArrayList<ProjectEntry>()
    var truncated = false
    ignore.prepare()
    try {
      Files.newDirectoryStream(directory).use { stream ->
        for (child in stream) {
          val name = child.fileName?.toString() ?: continue
          val isDirectory = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
          if (ignore.isIgnored(child, isDirectory)) continue
          if (collected.size == maximumEntries) {
            truncated = true
            break
          }
          collected += ProjectEntry(child, name, isDirectory)
        }
      }
    } catch (failure: IOException) {
      return ProjectListing(emptyList(), failure = failure.message ?: failure.javaClass.simpleName)
    } catch (failure: SecurityException) {
      return ProjectListing(emptyList(), failure = failure.message ?: "access denied")
    }
    collected.sortWith(PROJECT_ORDER)
    return ProjectListing(collected, truncated)
  }

  override fun invalidate() = ignore.invalidate()

  /** Listing limits. */
  companion object {
    /** How many entries of one directory are listed before the listing is marked truncated. */
    const val DEFAULT_MAXIMUM_ENTRIES: Int = 2000

    private val PROJECT_ORDER: Comparator<ProjectEntry> =
      compareByDescending<ProjectEntry> { it.isDirectory }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.name }
  }
}

/**
 * The expansion state of one opened folder.
 *
 * A directory is read when it is first expanded and its listing is kept until [refresh]. The class is independent of
 * Compose so expansion, ordering and the flattened rows can be tested without a graphics environment.
 */
class ProjectTree(root: Path, reader: ProjectDirectoryReader? = null) {
  /** The opened folder as an absolute, normalized path. */
  val root: Path = root.toAbsolutePath().normalize()
  private val reader: ProjectDirectoryReader = reader ?: FileSystemDirectoryReader(ProjectIgnore(this.root))

  private val expanded = LinkedHashSet<Path>()
  private val listings = HashMap<Path, ProjectListing>()

  init {
    expanded.add(this.root)
  }

  /** The folder name shown as the tool window's root. */
  val rootName: String
    get() = root.fileName?.toString() ?: root.toString()

  /** Whether a directory currently shows its children. */
  @Synchronized fun isExpanded(directory: Path): Boolean = directory in expanded

  /** Expands a collapsed directory or collapses an expanded one. Collapsing keeps the nested expansion state. */
  @Synchronized
  fun toggle(directory: Path) {
    if (!expanded.remove(directory)) expanded.add(directory)
  }

  /** Forgets every cached listing and rule file so the next [rows] call reads the filesystem again. */
  @Synchronized
  fun refresh() {
    listings.clear()
    reader.invalidate()
  }

  /**
   * The visible rows in display order: the children of the root, with the children of each expanded directory
   * directly below it. The root itself is not a row; the tool window shows it as its title.
   */
  @Synchronized
  fun rows(): List<ProjectRow> {
    val rows = ArrayList<ProjectRow>()
    appendChildren(root, 0, rows)
    return rows
  }

  private fun appendChildren(directory: Path, depth: Int, rows: MutableList<ProjectRow>) {
    val listing = listings.getOrPut(directory) { reader.list(directory) }
    for (entry in listing.entries) {
      val open = entry.isDirectory && entry.path in expanded
      rows += ProjectRow(entry, depth, open)
      if (open) appendChildren(entry.path, depth + 1, rows)
    }
    val note =
      when {
        listing.failure != null -> "Cannot read this folder: ${listing.failure}"
        listing.truncated -> "More entries are not shown"
        else -> null
      }
    if (note != null) {
      rows += ProjectRow(ProjectEntry(directory, note, isDirectory = false), depth, expanded = false, note = note)
    }
  }
}
