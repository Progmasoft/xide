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

class IgnoreRulesTest {
  private fun matches(pattern: String, path: String, isDirectory: Boolean = false, ignoreCase: Boolean = false): Boolean =
    checkNotNull(IgnoreRule.parse(pattern, ignoreCase)) { "pattern $pattern did not compile" }.matches(path, isDirectory)

  @Test
  fun blankLinesAndCommentsAreNotRules() {
    assertNull(IgnoreRule.parse("", false))
    assertNull(IgnoreRule.parse("   ", false))
    assertNull(IgnoreRule.parse("# build output", false))
    assertNull(IgnoreRule.parse("/", false))
    assertNull(IgnoreRule.parse("!", false))
    // An escaped hash is a file name, not a comment.
    assertTrue(matches("\\#notes", "#notes"))
  }

  @Test
  fun aPatternWithoutSeparatorMatchesAtEveryDepth() {
    assertTrue(matches("build", "build", isDirectory = true))
    assertTrue(matches("build", "modules/app/build", isDirectory = true))
    assertTrue(matches("*.class", "Main.class"))
    assertTrue(matches("*.class", "out/production/Main.class"))
    assertFalse(matches("*.class", "Main.classes"))
    assertFalse(matches("build", "rebuild"))
    assertFalse(matches("build", "build2/x"))
  }

  @Test
  fun aPatternWithASeparatorIsAnchoredToItsDirectory() {
    assertTrue(matches("/build", "build", isDirectory = true))
    assertFalse(matches("/build", "modules/build", isDirectory = true))
    assertTrue(matches("docs/api", "docs/api", isDirectory = true))
    assertFalse(matches("docs/api", "site/docs/api", isDirectory = true))
    // A trailing separator alone does not anchor the pattern.
    assertTrue(matches("cache/", "deep/cache", isDirectory = true))
  }

  @Test
  fun aTrailingSeparatorRestrictsThePatternToDirectories() {
    assertTrue(matches("out/", "out", isDirectory = true))
    assertFalse(matches("out/", "out", isDirectory = false))
    assertTrue(matches("out", "out", isDirectory = false))
  }

  @Test
  fun wildcardsDoNotCrossASeparator() {
    assertTrue(matches("src/*.vxs", "src/Main.vxs"))
    assertFalse(matches("src/*.vxs", "src/nested/Main.vxs"))
    assertTrue(matches("file?.txt", "file1.txt"))
    assertFalse(matches("file?.txt", "file/.txt"))
    assertFalse(matches("file?.txt", "file12.txt"))
  }

  @Test
  fun doubleAsteriskSpansDirectories() {
    assertTrue(matches("**/generated", "generated", isDirectory = true))
    assertTrue(matches("**/generated", "a/b/generated", isDirectory = true))
    assertTrue(matches("docs/**", "docs/a/b.md"))
    assertFalse(matches("docs/**", "docs"))
    assertTrue(matches("a/**/z", "a/z"))
    assertTrue(matches("a/**/z", "a/b/c/z"))
    assertFalse(matches("a/**/z", "b/a/z"))
  }

  @Test
  fun characterClassesMatchOneNonSeparatorCharacter() {
    assertTrue(matches("log[0-9].txt", "log7.txt"))
    assertFalse(matches("log[0-9].txt", "logx.txt"))
    assertTrue(matches("log[!0-9].txt", "logx.txt"))
    assertFalse(matches("log[!0-9].txt", "log7.txt"))
    assertFalse(matches("a[!x]b", "a/b"))
    // An unterminated class is not a rule rather than a rule that matches something unintended.
    assertNull(IgnoreRule.parse("log[0-9", false))
  }

  @Test
  fun regularExpressionCharactersAreLiteral() {
    assertTrue(matches("a+b(1).txt", "a+b(1).txt"))
    assertFalse(matches("a+b(1).txt", "aab1.txt"))
    assertTrue(matches("name.vxs", "name.vxs"))
    assertFalse(matches("name.vxs", "nameXvxs"))
  }

  @Test
  fun trailingSpacesAreIgnoredUnlessEscaped() {
    assertTrue(matches("notes.txt   ", "notes.txt"))
    assertTrue(matches("notes\\ ", "notes "))
    assertTrue(matches("notes.txt\r", "notes.txt"))
  }

  @Test
  fun caseSensitivityFollowsTheHostConvention() {
    assertFalse(matches("Build", "build", isDirectory = true, ignoreCase = false))
    assertTrue(matches("Build", "build", isDirectory = true, ignoreCase = true))
  }

  @Test
  fun aNegatedRuleIsMarkedAndStillMatchesItsPath() {
    val rule = checkNotNull(IgnoreRule.parse("!keep.log", false))
    assertTrue(rule.negated)
    assertTrue(rule.matches("logs/keep.log", false))
  }

  private fun project(vararg files: Pair<String, String>): Path {
    val root = createTempDirectory("xide-ignore")
    for ((relative, content) in files) {
      val file = root.resolve(relative)
      Files.createDirectories(file.parent)
      Files.writeString(file, content)
    }
    return root
  }

  @Test
  fun aFolderWithoutRuleFilesExcludesNothing() {
    val root = project("build/output.txt" to "", ".git/config" to "", "node_modules/x.js" to "")
    val ignore = ProjectIgnore(root, ignoreCase = false)

    assertFalse(ignore.isIgnored(root.resolve("build"), true))
    assertFalse(ignore.isIgnored(root.resolve(".git"), true))
    assertFalse(ignore.isIgnored(root.resolve("node_modules"), true))
    // Nothing to generate from, so no list is written either.
    assertFalse(Files.exists(root.resolve(ProjectIgnore.EXCLUDE_LIST)))
    assertNull(ignore.saveFailure)
  }

  @Test
  fun theExcludeListIsGeneratedFromGitignoreWhenItIsMissing() {
    val root = project(".gitignore" to "build/\n*.log\n!keep.log\n", "build/x.txt" to "", "a.log" to "", "keep.log" to "")
    val ignore = ProjectIgnore(root, ignoreCase = false)

    assertTrue(ignore.isIgnored(root.resolve("build"), true))
    assertTrue(ignore.isIgnored(root.resolve("a.log"), false))
    assertFalse(ignore.isIgnored(root.resolve("keep.log"), false))
    assertFalse(ignore.isIgnored(root.resolve(".gitignore"), false))

    val generated = Files.readString(root.resolve(ProjectIgnore.EXCLUDE_LIST))
    assertTrue(generated.startsWith(ExcludeListGenerator.HEADER))
    assertTrue(generated.contains("# From .gitignore\nbuild/\n*.log\n!keep.log\n"))
    assertNull(ignore.saveFailure)
  }

  @Test
  fun anExistingExcludeListIsReadAndNeverRegenerated() {
    val written = "# mine\nsecret/\n"
    val root = project(".gitignore" to "build/\n", ".xide/exclude.list" to written, "build/x" to "", "secret/x" to "")
    val ignore = ProjectIgnore(root, ignoreCase = false)

    // Only the user's list applies: .gitignore is the seed for a missing list, not a second source.
    assertTrue(ignore.isIgnored(root.resolve("secret"), true))
    assertFalse(ignore.isIgnored(root.resolve("build"), true))
    assertEquals(written, Files.readString(root.resolve(ProjectIgnore.EXCLUDE_LIST)))
  }

  @Test
  fun anEmptyExcludeListMeansNoExclusions() {
    val root = project(".gitignore" to "build/\n", ".xide/exclude.list" to "", "build/x" to "")
    val ignore = ProjectIgnore(root, ignoreCase = false)

    assertFalse(ignore.isIgnored(root.resolve("build"), true))
    assertEquals("", Files.readString(root.resolve(ProjectIgnore.EXCLUDE_LIST)))
  }

  @Test
  fun nestedGitignorePatternsAreRebasedToTheOpenedFolder() {
    val root =
      project(
        ".gitignore" to "*.tmp\n",
        "modules/app/.gitignore" to "/build\ncache\n!cache/keep\n# local\n",
        "modules/app/build/x" to "",
        "modules/app/src/cache/x" to "",
        "modules/app/cache/keep/x" to "",
        "build/x" to "",
        "cache/x" to "",
      )
    val ignore = ProjectIgnore(root, ignoreCase = false)

    assertTrue(ignore.isIgnored(root.resolve("modules/app/build"), true))
    assertTrue(ignore.isIgnored(root.resolve("modules/app/src/cache"), true))
    // The nested file speaks only for its own directory.
    assertFalse(ignore.isIgnored(root.resolve("build"), true))
    assertFalse(ignore.isIgnored(root.resolve("cache"), true))
    assertTrue(ignore.isIgnored(root.resolve("modules/app/x.tmp"), false))

    val generated = Files.readString(root.resolve(ProjectIgnore.EXCLUDE_LIST))
    assertTrue(generated.contains("# From modules/app/.gitignore\n/modules/app/build\n/modules/app/**/cache\n!/modules/app/cache/keep\n# local\n"))
  }

  @Test
  fun rebaseKeepsTheMeaningOfEachPatternKind() {
    assertEquals("/sub/build", ExcludeListGenerator.rebase("/build", "sub"))
    assertEquals("/sub/docs/api", ExcludeListGenerator.rebase("docs/api", "sub"))
    assertEquals("/sub/**/*.log", ExcludeListGenerator.rebase("*.log", "sub"))
    assertEquals("/sub/**/out/", ExcludeListGenerator.rebase("out/", "sub"))
    assertEquals("!/sub/**/keep.log", ExcludeListGenerator.rebase("!keep.log", "sub"))
    assertEquals("# note", ExcludeListGenerator.rebase("# note", "sub"))
    assertEquals("", ExcludeListGenerator.rebase("", "sub"))
  }

  @Test
  fun aGitignoreInsideAnExcludedDirectoryIsNotRead() {
    val root =
      project(
        ".gitignore" to "vendor/\n",
        "vendor/.gitignore" to "*.vxs\n",
        "src/Main.vxs" to "",
      )
    val ignore = ProjectIgnore(root, ignoreCase = false)

    assertTrue(ignore.isIgnored(root.resolve("vendor"), true))
    assertFalse(ignore.isIgnored(root.resolve("src/Main.vxs"), false))
    assertFalse(Files.readString(root.resolve(ProjectIgnore.EXCLUDE_LIST)).contains("vendor/.gitignore"))
  }

  @Test
  fun invalidateRereadsAnEditedExcludeList() {
    val root = project(".xide/exclude.list" to "a/\n", "a/x" to "", "b/x" to "")
    val ignore = ProjectIgnore(root, ignoreCase = false)
    assertTrue(ignore.isIgnored(root.resolve("a"), true))

    Files.writeString(root.resolve(ProjectIgnore.EXCLUDE_LIST), "b/\n")
    // The cached rules stay in force until the tree is refreshed.
    assertTrue(ignore.isIgnored(root.resolve("a"), true))
    ignore.invalidate()
    assertFalse(ignore.isIgnored(root.resolve("a"), true))
    assertTrue(ignore.isIgnored(root.resolve("b"), true))
  }

  @Test
  fun theOpenedFolderAndPathsOutsideItAreNeverExcluded() {
    val root = project(".xide/exclude.list" to "*\n", "a" to "")
    val ignore = ProjectIgnore(root, ignoreCase = false)

    assertTrue(ignore.isIgnored(root.resolve("a"), false))
    assertFalse(ignore.isIgnored(root, true))
    assertFalse(ignore.isIgnored(root.resolveSibling("elsewhere"), true))
  }

  @Test
  fun aByteOrderMarkDoesNotHideTheFirstRule() {
    val root = project(".xide/exclude.list" to "\uFEFFa/\n", "a/x" to "")
    assertTrue(ProjectIgnore(root, ignoreCase = false).isIgnored(root.resolve("a"), true))
  }
}
