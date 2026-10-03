/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import com.progmasoft.xide.syntax.Tokenizer
import com.progmasoft.xide.syntax.VisualXSharpTokenizer
import org.jetbrains.kotlin.com.intellij.lang.Language
import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.groovy.GroovyLanguage

/**
 * The languages that have a syntax tree in Xide.
 *
 * Kotlin and Java are parsed by the parsers of the Kotlin compiler and of IntelliJ, Groovy by the parser of the
 * IntelliJ Groovy plugin, and Visual X# by Xide's own structural parser on the same platform.
 *
 * @property defaultFileName the file name a text is parsed under when the caller gives none. The extension
 *   matters for Kotlin, where a `.kts` name selects the script grammar.
 */
enum class PsiLanguage(val defaultFileName: String) {
  /** Kotlin sources and, under a `.kts` name, Kotlin scripts. */
  KOTLIN("Source.kt"),

  /** Java sources. */
  JAVA("Source.java"),

  /** Groovy sources and Groovy-based Gradle build scripts. */
  GROOVY("Source.groovy"),

  /** Visual X# sources. */
  VISUAL_XSHARP("Source.vxs"),
}

/**
 * A place where the parser could not read the text as the language's grammar requires.
 *
 * @property start the UTF-16 offset where the problem is reported.
 * @property end the offset just after the reported stretch. It equals [start] when the parser reports a missing
 *   token, because nothing stands where the token should be.
 * @property message the parser's description, in English.
 */
data class SyntaxProblem(val start: Int, val end: Int, val message: String) {
  init {
    require(start >= 0) { "problem start must not be negative" }
    require(end >= start) { "problem must not end before it starts" }
  }
}

/** What a declaration in an outline is. */
enum class OutlineKind {
  /** The package or namespace a file declares. */
  NAMESPACE,

  /** A class, interface, object, enumeration, record, trait, type alias or other type-like declaration. */
  TYPE,

  /** A function, method, constructor, destructor or operator. */
  CALLABLE,

  /** A property, field or top-level variable. */
  PROPERTY,

  /** An initializer block. */
  INITIALIZER,
}

/**
 * One declaration of a file, with the declarations nested inside it.
 *
 * @property kind what the declaration is.
 * @property name the declared name, or a word that stands for it when the declaration has none.
 * @property start the UTF-16 offset of the declaration's first character.
 * @property end the offset just after its last character.
 * @property children the declarations directly inside it, in source order.
 */
data class OutlineEntry(
  val kind: OutlineKind,
  val name: String,
  val start: Int,
  val end: Int,
  val children: List<OutlineEntry> = emptyList(),
)

/**
 * What the syntax tree of one text says about it.
 *
 * @property problems the syntax errors, in source order. The list is always empty for Visual X#, whose errors
 *   are reported by the compiler.
 * @property outline the declarations of the file, in source order.
 */
data class SourceAnalysis(val problems: List<SyntaxProblem>, val outline: List<OutlineEntry>)

/**
 * Parses source text into IntelliJ PSI and reports what the tree says.
 *
 * Analysis is thread-safe and may be called from any thread; calls run one at a time. A call parses the whole
 * text, which takes on the order of a tenth of a second for a few hundred lines, so an editor runs it in the
 * background and colours text with [tokenizer], which is fast enough for every key press.
 */
object SourceAnalyzer {
  /**
   * Starts the PSI platform when it is not running yet.
   *
   * The first analysis otherwise pays for the start, most of a second. Calling this from a background thread
   * while the application opens moves that cost out of the user's way.
   */
  fun warmUp() {
    PsiPlatform.warmUp()
  }

  /**
   * The tokenizer that colours [language].
   *
   * Kotlin, Java and Groovy are tokenized by the IntelliJ lexers of those languages; Visual X# by Xide's own
   * tokenizer. A tokenizer needs no platform and no parse.
   */
  fun tokenizer(language: PsiLanguage): Tokenizer =
    when (language) {
      PsiLanguage.KOTLIN -> KotlinLexerTokenizer
      PsiLanguage.JAVA -> JavaLexerTokenizer
      PsiLanguage.GROOVY -> GroovyLexerTokenizer
      PsiLanguage.VISUAL_XSHARP -> VisualXSharpTokenizer
    }

  /**
   * Parses [text] and returns its syntax problems and outline.
   *
   * Any text is accepted. A text the parser cannot make sense of yields problems, never an exception.
   *
   * @param fileName the name to parse under; see [PsiLanguage.defaultFileName].
   */
  fun analyze(language: PsiLanguage, text: String, fileName: String = language.defaultFileName): SourceAnalysis =
    parse(language, text, fileName) { file ->
      val problems = ArrayList<SyntaxProblem>()
      if (language != PsiLanguage.VISUAL_XSHARP) collectProblems(file, problems)
      SourceAnalysis(problems, Outlines.of(language, file))
    }

  /**
   * The syntax tree of [text] as indented text, one composite node per line.
   *
   * The form is for tests and for inspecting what a parser produced; it is not a stable format.
   */
  fun describeTree(language: PsiLanguage, text: String, fileName: String = language.defaultFileName): String =
    parse(language, text, fileName) { file ->
      val out = StringBuilder()
      describe(file, 0, out)
      out.toString()
    }

  private fun <T> parse(language: PsiLanguage, text: String, fileName: String, use: (PsiFile) -> T): T =
    PsiPlatform.withProject { project ->
      use(PsiFileFactory.getInstance(project).createFileFromText(fileName, platformLanguage(language), text))
    }

  private fun platformLanguage(language: PsiLanguage): Language =
    when (language) {
      PsiLanguage.KOTLIN -> KotlinLanguage.INSTANCE
      PsiLanguage.JAVA -> JavaLanguage.INSTANCE
      PsiLanguage.GROOVY -> GroovyLanguage.INSTANCE
      PsiLanguage.VISUAL_XSHARP -> VisualXSharpLanguage
    }

  private fun collectProblems(element: PsiElement, into: MutableList<SyntaxProblem>) {
    if (element is PsiErrorElement) {
      val range = element.textRange
      into += SyntaxProblem(range.startOffset, range.endOffset, element.errorDescription)
    }
    var child = element.firstChild
    while (child != null) {
      collectProblems(child, into)
      child = child.nextSibling
    }
  }

  private fun describe(element: PsiElement, depth: Int, out: StringBuilder) {
    // Leaves are the tokens of the text; the structure is in the nodes above them.
    if (element.firstChild == null && element !is PsiErrorElement) return
    repeat(depth) { out.append("  ") }
    out.append(element.node.elementType)
    if (element is PsiErrorElement) out.append(": ").append(element.errorDescription)
    out.append('\n')
    var child = element.firstChild
    while (child != null) {
      describe(child, depth + 1, out)
      child = child.nextSibling
    }
  }
}
