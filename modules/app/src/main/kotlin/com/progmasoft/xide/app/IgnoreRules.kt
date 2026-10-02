/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * One line of a `.gitignore`-style file, compiled for matching.
 *
 * [expression] matches a path relative to the directory that holds the file, written with `/` separators.
 */
internal class IgnoreRule(
  val negated: Boolean,
  val directoryOnly: Boolean,
  private val expression: Regex,
) {
  fun matches(relativePath: String, isDirectory: Boolean): Boolean =
    (isDirectory || !directoryOnly) && expression.matches(relativePath)

  companion object {
    /**
     * Compiles one pattern line, or returns null for a blank line or a comment.
     *
     * The syntax is the one `gitignore(5)` documents: `#` starts a comment, a leading `!` re-includes, a trailing
     * `/` restricts the pattern to directories, a pattern with a separator at its start or in its middle is anchored
     * to the file's directory while any other pattern matches at every depth, `*` and `?` do not cross a separator,
     * `[...]` is a character class, and `**` spans directories. A backslash makes the next character literal.
     */
    fun parse(line: String, ignoreCase: Boolean): IgnoreRule? {
      var pattern = stripTrailingSpaces(line)
      if (pattern.isEmpty() || pattern.startsWith("#")) return null
      val negated = pattern.startsWith("!")
      if (negated) pattern = pattern.substring(1)
      val directoryOnly = pattern.endsWith("/") && !pattern.endsWith("\\/")
      if (directoryOnly) pattern = pattern.dropLast(1)
      if (pattern.isEmpty()) return null
      val anchored = pattern.contains('/')
      if (pattern.startsWith("/")) pattern = pattern.substring(1)
      if (pattern.isEmpty()) return null
      val body = translate(pattern) ?: return null
      val source = if (anchored) body else "(?:.*/)?$body"
      val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
      return IgnoreRule(negated, directoryOnly, Regex(source, options))
    }

    /** Trailing spaces are not part of a pattern unless the last one is escaped with a backslash. */
    private fun stripTrailingSpaces(line: String): String {
      var end = line.length
      while (end > 0 && (line[end - 1] == ' ' || line[end - 1] == '\r')) {
        if (end >= 2 && line[end - 2] == '\\' && line[end - 1] == ' ') break
        end--
      }
      return line.substring(0, end)
    }

    /** Translates one glob to a regular expression, or returns null when a character class is malformed. */
    private fun translate(pattern: String): String? {
      val regex = StringBuilder()
      var index = 0
      while (index < pattern.length) {
        val current = pattern[index]
        when {
          current == '\\' && index + 1 < pattern.length -> {
            regex.append(Regex.escape(pattern[index + 1].toString()))
            index += 2
          }
          current == '*' && pattern.startsWith("**", index) -> {
            val atStart = index == 0 || pattern[index - 1] == '/'
            val end = index + 2
            val atEnd = end == pattern.length
            when {
              // `**/` matches zero or more whole directories.
              atStart && end < pattern.length && pattern[end] == '/' -> {
                regex.append("(?:.*/)?")
                index = end + 1
              }
              // A trailing `/**` matches everything below the directory.
              atStart && atEnd -> {
                regex.append(".*")
                index = end
              }
              // Anywhere else consecutive asterisks behave like one.
              else -> {
                regex.append("[^/]*")
                index = end
              }
            }
          }
          current == '*' -> {
            regex.append("[^/]*")
            index++
          }
          current == '?' -> {
            regex.append("[^/]")
            index++
          }
          current == '[' -> {
            val close = classEnd(pattern, index) ?: return null
            regex.append(translateClass(pattern.substring(index + 1, close)))
            index = close + 1
          }
          else -> {
            regex.append(Regex.escape(current.toString()))
            index++
          }
        }
      }
      return regex.toString()
    }

    /** The index of the `]` that closes the class opened at [open]; a `]` right after the opening is literal. */
    private fun classEnd(pattern: String, open: Int): Int? {
      var index = open + 1
      if (index < pattern.length && (pattern[index] == '!' || pattern[index] == '^')) index++
      if (index < pattern.length && pattern[index] == ']') index++
      while (index < pattern.length) {
        if (pattern[index] == ']') return index
        index++
      }
      return null
    }

    private fun translateClass(content: String): String {
      val negated = content.startsWith("!") || content.startsWith("^")
      val members = if (negated) content.substring(1) else content
      val escaped = StringBuilder()
      for (character in members) {
        // A hyphen keeps its range meaning; every other regex-significant character is literal in a glob class.
        if (character == '\\' || character == '[' || character == ']' || character == '^' || character == '&') {
          escaped.append('\\')
        }
        escaped.append(character)
      }
      // A class never matches a separator, exactly like `*` and `?`.
      return if (negated) "[^/$escaped]" else "(?!/)[$escaped]"
    }
  }
}

/**
 * Builds the text of `.xide/exclude.list` from the `.gitignore` files of a folder.
 *
 * The opened folder's own `.gitignore` is copied line for line. A `.gitignore` in a subdirectory is relative to
 * that subdirectory, so each of its patterns is rewritten to mean the same thing from the opened folder: an anchored
 * pattern gets the subdirectory as a prefix, and a pattern that matches at any depth gets the prefix followed by
 * a double-asterisk directory wildcard. Directories that the rules collected so far already exclude are not searched, exactly as Git does not read
 * a `.gitignore` inside an ignored directory.
 */
internal object ExcludeListGenerator {
  /** The search stops after this many directories, so opening a very large folder stays fast. */
  const val MAXIMUM_DIRECTORIES: Int = 5_000

  const val HEADER: String =
    "# Entries the Project tool window leaves out, in .gitignore syntax.\n" +
      "# Xide generated this file from the folder's .gitignore files. It is yours to edit;\n" +
      "# Xide reads it and does not regenerate it while it exists.\n"

  /** Returns the generated text, or null when the folder has no `.gitignore` to generate from. */
  fun generate(root: Path, ignoreCase: Boolean): String? {
    val text = StringBuilder(HEADER)
    val rules = ArrayList<IgnoreRule>()
    var found = false
    val pending = ArrayDeque<Path>()
    pending.add(root)
    var visited = 0
    while (pending.isNotEmpty() && visited < MAXIMUM_DIRECTORIES) {
      val directory = pending.removeFirst()
      visited++
      val prefix = if (directory == root) "" else slashed(root.relativize(directory))
      val lines = readLines(directory.resolve(".gitignore"))
      if (lines != null) {
        found = true
        text.append('\n').append("# From ").append(if (prefix.isEmpty()) ".gitignore" else "$prefix/.gitignore").append('\n')
        for (line in lines) {
          val rewritten = if (prefix.isEmpty()) line.trimEnd('\r') else rebase(line.trimEnd('\r'), prefix)
          text.append(rewritten).append('\n')
          IgnoreRule.parse(rewritten, ignoreCase)?.let(rules::add)
        }
      }
      for (child in subdirectories(directory)) {
        if (!isIgnored(rules, slashed(root.relativize(child)), isDirectory = true)) pending.add(child)
      }
    }
    return if (found) text.toString() else null
  }

  /** Rewrites one pattern of a nested `.gitignore` so it has the same meaning from the opened folder. */
  internal fun rebase(line: String, prefix: String): String {
    val trimmed = line.trimEnd(' ')
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return line
    val negated = trimmed.startsWith("!")
    val body = if (negated) trimmed.substring(1) else trimmed
    val withoutTrailing = body.removeSuffix("/")
    val anchored = withoutTrailing.contains('/')
    val rebased = if (anchored) "/$prefix/${body.removePrefix("/")}" else "/$prefix/**/$body"
    return (if (negated) "!" else "") + rebased
  }

  internal fun isIgnored(rules: List<IgnoreRule>, relativePath: String, isDirectory: Boolean): Boolean {
    var ignored = false
    for (rule in rules) {
      if (rule.matches(relativePath, isDirectory)) ignored = !rule.negated
    }
    return ignored
  }

  internal fun slashed(relative: Path): String = relative.joinToString("/") { it.toString() }

  private fun readLines(file: Path): List<String>? =
    try {
      if (Files.isRegularFile(file)) Files.readAllLines(file, StandardCharsets.UTF_8).take(ProjectIgnore.MAXIMUM_RULES) else null
    } catch (_: IOException) {
      null
    }

  private fun subdirectories(directory: Path): List<Path> =
    try {
      Files.newDirectoryStream(directory).use { stream ->
        stream.filter { Files.isDirectory(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }.sortedBy { it.fileName.toString() }
      }
    } catch (_: IOException) {
      emptyList()
    } catch (_: SecurityException) {
      emptyList()
    }
}

/**
 * Decides which entries of an opened folder the Project tool window leaves out.
 *
 * Xide excludes nothing on its own. The single source of exclusions is `.xide/exclude.list` in the opened folder,
 * written in `.gitignore` syntax. When that file does not exist, Xide generates it from the folder's `.gitignore`
 * files and from then on only reads it, so the list can be edited without touching `.gitignore`. A folder with
 * neither file has no exclusions. The rules are kept until [invalidate].
 */
class ProjectIgnore(root: Path, private val ignoreCase: Boolean = WINDOWS_HOST) {
  private val root: Path = root.toAbsolutePath().normalize()
  private var rules: List<IgnoreRule>? = null

  /** Why the generated list could not be saved, when that happened; the generated rules still apply in memory. */
  @get:Synchronized
  var saveFailure: String? = null
    private set

  @Synchronized
  /** Forgets the loaded rules so the next query reads the exclusion list again. */
  fun invalidate() {
    rules = null
  }

  /**
   * Whether [path], an entry below the opened folder, is excluded. The last matching rule decides, so a later `!`
   * pattern re-includes what an earlier pattern excluded. An entry outside the opened folder is never excluded.
   */
  @Synchronized
  fun isIgnored(path: Path, isDirectory: Boolean): Boolean {
    val absolute = path.toAbsolutePath().normalize()
    if (!absolute.startsWith(root) || absolute == root) return false
    val relative = ExcludeListGenerator.slashed(root.relativize(absolute))
    return ExcludeListGenerator.isIgnored(loaded(), relative, isDirectory)
  }

  /**
   * Reads the exclusion list now, generating it first when it is missing.
   *
   * A directory listing calls this before it opens the directory, so a list generated on first use is already on
   * disk when the opened folder itself is listed.
   */
  @Synchronized
  fun prepare() {
    loaded()
  }

  private fun loaded(): List<IgnoreRule> = rules ?: load().also { rules = it }

  private fun load(): List<IgnoreRule> {
    val file = root.resolve(EXCLUDE_LIST)
    val text =
      if (Files.isRegularFile(file)) {
        // An unreadable list contributes no rules; it never hides entries by accident.
        try {
          Files.readString(file, StandardCharsets.UTF_8)
        } catch (_: IOException) {
          ""
        }
      } else {
        val generated = ExcludeListGenerator.generate(root, ignoreCase) ?: return emptyList()
        save(file, generated)
        generated
      }
    return text.removePrefix(BYTE_ORDER_MARK).lineSequence().take(MAXIMUM_RULES).mapNotNull { IgnoreRule.parse(it, ignoreCase) }.toList()
  }

  private fun save(file: Path, text: String) {
    saveFailure =
      try {
        Files.createDirectories(file.parent)
        Files.writeString(file, text, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
        null
      } catch (failure: IOException) {
        failure.message ?: failure.javaClass.simpleName
      } catch (failure: SecurityException) {
        failure.message ?: "access denied"
      }
  }

  /** Names and limits of the exclusion list. */
  companion object {
    /** Project-local exclusions in `.gitignore` syntax, relative to the opened folder. */
    const val EXCLUDE_LIST: String = ".xide/exclude.list"

    /** A rule file longer than this is read only up to the limit; the tree stays responsive on any input. */
    const val MAXIMUM_RULES: Int = 10_000

    /** A byte order mark some editors write at the start of a UTF-8 file. */
    private const val BYTE_ORDER_MARK: String = "\uFEFF"

    internal val WINDOWS_HOST: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
  }
}
