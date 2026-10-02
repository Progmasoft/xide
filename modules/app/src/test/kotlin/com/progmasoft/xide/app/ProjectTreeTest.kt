/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectTreeTest {
  private fun project(vararg files: String): Path {
    val root = createTempDirectory("xide-tree")
    for (relative in files) {
      val file = root.resolve(relative)
      if (relative.endsWith("/")) {
        Files.createDirectories(file)
      } else {
        Files.createDirectories(file.parent)
        Files.writeString(file, "")
      }
    }
    return root
  }

  private fun names(tree: ProjectTree): List<String> = tree.rows().map { "  ".repeat(it.depth) + it.entry.name }

  @Test
  fun directoriesComeBeforeFilesAndNamesIgnoreCase() {
    val root = project("zeta.vxs", "Alpha.vxs", "beta.vxs", "src/", "Docs/", "assets/")
    val tree = ProjectTree(root)

    assertEquals(listOf("assets", "Docs", "src", "Alpha.vxs", "beta.vxs", "zeta.vxs"), names(tree))
  }

  @Test
  fun nothingIsHiddenWithoutARule() {
    val root = project("build/out.txt", ".git/config", "node_modules/a.js", ".idea/x.xml", "Main.vxs")
    val tree = ProjectTree(root)

    assertEquals(listOf(".git", ".idea", "build", "node_modules", "Main.vxs"), names(tree))
  }

  @Test
  fun entriesExcludedByTheListAreNotListed() {
    val root = project("build/out.txt", "src/Main.vxs", "notes.log", "keep.log")
    Files.writeString(root.resolve(".gitignore"), "build/\n*.log\n!keep.log\n")
    val tree = ProjectTree(root)

    // The generated list lives in the folder and is itself an ordinary entry.
    assertEquals(listOf(".xide", "src", ".gitignore", "keep.log"), names(tree))
  }

  @Test
  fun aDirectoryIsReadOnlyWhenItIsExpanded() {
    val root = project("src/app/Main.vxs", "src/Lib.vxs", "README.md")
    val listed = ArrayList<Path>()
    val real = FileSystemDirectoryReader(ProjectIgnore(root))
    val tree = ProjectTree(root) { directory -> listed.add(directory); real.list(directory) }

    assertEquals(listOf("src", "README.md"), names(tree))
    assertEquals(listOf(tree.root), listed)

    tree.toggle(root.resolve("src"))
    assertEquals(listOf("src", "  app", "  Lib.vxs", "README.md"), names(tree))
    assertTrue(tree.rows().first().expanded)
    assertEquals(listOf(tree.root, root.resolve("src")), listed)

    // A second read of the rows uses the cached listings.
    tree.rows()
    assertEquals(2, listed.size)
  }

  @Test
  fun collapsingKeepsTheNestedExpansionState() {
    val root = project("src/app/Main.vxs")
    val tree = ProjectTree(root)
    tree.toggle(root.resolve("src"))
    tree.toggle(root.resolve("src/app"))
    assertEquals(listOf("src", "  app", "    Main.vxs"), names(tree))

    tree.toggle(root.resolve("src"))
    assertEquals(listOf("src"), names(tree))
    assertFalse(tree.rows().single().expanded)

    tree.toggle(root.resolve("src"))
    assertEquals(listOf("src", "  app", "    Main.vxs"), names(tree))
  }

  @Test
  fun refreshShowsEntriesCreatedAfterTheFirstRead() {
    val root = project("a.vxs")
    val tree = ProjectTree(root)
    assertEquals(listOf("a.vxs"), names(tree))

    Files.writeString(root.resolve("b.vxs"), "")
    assertEquals(listOf("a.vxs"), names(tree))
    tree.refresh()
    assertEquals(listOf("a.vxs", "b.vxs"), names(tree))
  }

  @Test
  fun refreshAlsoRereadsTheExcludeList() {
    val root = project("a/x", "b/x", ".xide/exclude.list")
    Files.writeString(root.resolve(".xide/exclude.list"), "a/\n")
    val tree = ProjectTree(root)
    assertEquals(listOf(".xide", "b"), names(tree))

    Files.writeString(root.resolve(".xide/exclude.list"), "b/\n")
    tree.refresh()
    assertEquals(listOf(".xide", "a"), names(tree))
  }

  @Test
  fun aTruncatedListingSaysSo() {
    val root = project("a.vxs", "b.vxs", "c.vxs")
    val tree = ProjectTree(root, FileSystemDirectoryReader(ProjectIgnore(root), maximumEntries = 2))
    val rows = tree.rows()

    assertEquals(3, rows.size)
    assertNull(rows[0].note)
    assertEquals("More entries are not shown", rows.last().note)
  }

  @Test
  fun anUnreadableDirectoryShowsItsFailureInsteadOfLookingEmpty() {
    val root = project("src/")
    val tree =
      ProjectTree(root) { directory ->
        if (directory == root.resolve("src")) {
          ProjectListing(emptyList(), failure = "access denied")
        } else {
          ProjectListing(listOf(ProjectEntry(root.resolve("src"), "src", isDirectory = true)))
        }
      }
    tree.toggle(root.resolve("src"))
    val rows = tree.rows()

    assertEquals(2, rows.size)
    assertEquals("Cannot read this folder: access denied", rows[1].note)
    assertEquals(1, rows[1].depth)
  }

  @Test
  fun aMissingFolderIsReportedByTheRealReader() {
    val root = createTempDirectory("xide-tree").resolve("gone")
    val rows = ProjectTree(root).rows()

    assertEquals(1, rows.size)
    assertTrue(rows.single().note!!.startsWith("Cannot read this folder"))
  }

  @Test
  fun aDirectoryLinkIsListedAsAFileAndNotFollowed() {
    val root = project("real/inner.vxs")
    val link = root.resolve("link")
    try {
      Files.createSymbolicLink(link, root.resolve("real"))
    } catch (_: Exception) {
      // Creating a symbolic link needs a privilege on some Windows hosts; there is nothing to check without one.
      return
    }
    val tree = ProjectTree(root)
    val row = tree.rows().single { it.entry.name == "link" }

    assertFalse(row.entry.isDirectory)
  }

  @Test
  fun theRootNameIsTheFolderName() {
    val root = project("a.vxs")
    assertEquals(root.fileName.toString(), ProjectTree(root).rootName)
    assertTrue(ProjectTree(root).isExpanded(root.toAbsolutePath().normalize()))
  }
}
