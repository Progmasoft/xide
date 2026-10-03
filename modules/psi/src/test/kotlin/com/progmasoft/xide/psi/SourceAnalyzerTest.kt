/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.groovy.GroovyLanguage

/** Tests the PSI parse of each language: the problems it reports and the outline it yields. */
class SourceAnalyzerTest {
  /** An outline as indented lines of `KIND name`, which is easier to compare and to read than nested objects. */
  private fun List<OutlineEntry>.render(depth: Int = 0): String =
    joinToString("") { "  ".repeat(depth) + it.kind + " " + it.name + "\n" + it.children.render(depth + 1) }

  private fun outline(language: PsiLanguage, text: String, fileName: String = language.defaultFileName): String =
    SourceAnalyzer.analyze(language, text, fileName).outline.render()

  private fun problems(language: PsiLanguage, text: String): List<SyntaxProblem> =
    SourceAnalyzer.analyze(language, text).problems

  @Test
  fun aKotlinFileListsItsPackageTypesAndMembers() {
    val text =
      """
      package a.b
      import x.Y
      class A(val p: Int) : B() {
        val q = 1
        fun f(x: Int): Int = x
        companion object { const val C = 1 }
        init { }
        constructor() : this(1)
        class Inner
      }
      fun top() {}
      val prop = 2
      typealias T = Int
      object O
      """.trimIndent()
    assertEquals(emptyList(), problems(PsiLanguage.KOTLIN, text))
    assertEquals(
      """
      NAMESPACE a.b
      TYPE A
        PROPERTY q
        CALLABLE f
        TYPE Companion
          PROPERTY C
        INITIALIZER init
        CALLABLE constructor
        TYPE Inner
      CALLABLE top
      PROPERTY prop
      TYPE T
      TYPE O

      """.trimIndent(),
      outline(PsiLanguage.KOTLIN, text),
    )
  }

  @Test
  fun aKotlinScriptIsParsedWithTheScriptGrammar() {
    val script = "plugins { kotlin(\"jvm\") }\nval x = 1\nfun helper() = 2\n"
    // Under a `.kts` name top-level statements are allowed and the declarations among them are listed.
    val analysis = SourceAnalyzer.analyze(PsiLanguage.KOTLIN, script, "build.gradle.kts")
    assertEquals(emptyList(), analysis.problems)
    assertEquals("PROPERTY x\nCALLABLE helper\n", analysis.outline.render())
    // Under a `.kt` name the same text has a statement where only declarations may stand.
    assertTrue(SourceAnalyzer.analyze(PsiLanguage.KOTLIN, script, "Build.kt").problems.isNotEmpty())
  }

  @Test
  fun aKotlinSyntaxErrorIsReportedWhereTheParserMissesAToken() {
    val text = "class A { fun f( { } }"
    assertEquals(listOf(SyntaxProblem(16, 16, "Expecting ')'")), problems(PsiLanguage.KOTLIN, text))
    // The declarations around the error are still recognized.
    assertEquals("TYPE A\n  CALLABLE f\n", outline(PsiLanguage.KOTLIN, text))
  }

  @Test
  fun aJavaFileListsItsPackageTypesAndMembers() {
    val text =
      """
      package a.b;
      import x.Y;
      public class A {
        int field = 1;
        A() {}
        void m(int x) {}
        static { }
        class Inner { }
        enum E { X, Y }
      }
      record R(int x) {}
      interface I { void f(); }
      """.trimIndent()
    assertEquals(emptyList(), problems(PsiLanguage.JAVA, text))
    assertEquals(
      """
      NAMESPACE a.b
      TYPE A
        PROPERTY field
        CALLABLE A
        CALLABLE m
        INITIALIZER initializer
        TYPE Inner
        TYPE E
          PROPERTY X
          PROPERTY Y
      TYPE R
      TYPE I
        CALLABLE f

      """.trimIndent(),
      outline(PsiLanguage.JAVA, text),
    )
  }

  @Test
  fun javaSyntaxErrorsAreReportedInSourceOrder() {
    val text = "class A { void f( { } int x }"
    assertEquals(
      listOf(SyntaxProblem(17, 17, "')' expected"), SyntaxProblem(27, 27, "';' expected")),
      problems(PsiLanguage.JAVA, text),
    )
    assertEquals("TYPE A\n  CALLABLE f\n  PROPERTY x\n", outline(PsiLanguage.JAVA, text))
  }

  @Test
  fun aGroovyFileListsItsPackageTypesAndMembers() {
    val text =
      """
      package a.b
      import x.Y
      class A {
        int field = 1
        def prop
        A() {}
        def m(int x) { println x }
        static { }
        class Inner { }
      }
      def top() { 1 }
      def v = 2
      enum E { X, Y }
      trait T { }
      """.trimIndent()
    assertEquals(emptyList(), problems(PsiLanguage.GROOVY, text))
    assertEquals(
      """
      NAMESPACE a.b
      TYPE A
        PROPERTY field
        PROPERTY prop
        CALLABLE A
        CALLABLE m
        INITIALIZER initializer
        TYPE Inner
      CALLABLE top
      PROPERTY v
      TYPE E
      TYPE T

      """.trimIndent(),
      outline(PsiLanguage.GROOVY, text),
    )
  }

  @Test
  fun aGradleBuildScriptParsesWithoutProblems() {
    val script =
      """
      plugins { id 'java' }
      repositories { mavenCentral() }
      dependencies {
        implementation 'a:b:1.0'
        testImplementation "junit:junit:${'$'}{junitVersion}"
      }
      tasks.register('hello') { doLast { println 'hi' } }
      ext.pattern = ~/a\/b/
      """.trimIndent()
    val analysis = SourceAnalyzer.analyze(PsiLanguage.GROOVY, script, "build.gradle")
    assertEquals(emptyList(), analysis.problems)
    // A build script consists of statements; it declares nothing an outline would list.
    assertEquals(emptyList(), analysis.outline)
  }

  @Test
  fun groovySyntaxErrorsAreReportedWithTheParsersMessages() {
    val text = "class A { def f( { } }"
    assertEquals(
      listOf(SyntaxProblem(17, 18, "identifier expected, got '{'"), SyntaxProblem(21, 22, "'}' unexpected")),
      problems(PsiLanguage.GROOVY, text),
    )
  }

  @Test
  fun theTreeOfAGroovyClassNamesItsConstructs() {
    assertEquals(
      """
      GROOVY_FILE
        CLASS_TYPE_DEFINITION
          CLASS_BODY
            METHOD
              MODIFIER_LIST
              PARAMETER_LIST
              OPEN_BLOCK

      """.trimIndent(),
      SourceAnalyzer.describeTree(PsiLanguage.GROOVY, "class A { def f() { } }"),
    )
  }

  @Test
  fun everyParserRebuildsExactlyTheTextItWasGiven() {
    val languages =
      listOf(KotlinLanguage.INSTANCE, JavaLanguage.INSTANCE, GroovyLanguage.INSTANCE, VisualXSharpLanguage)
    val random = Random(20261003)
    val fragments = FRAGMENTS
    PsiPlatform.withProject { project ->
      val factory = PsiFileFactory.getInstance(project)
      repeat(300) { round ->
        val text = buildString { repeat(random.nextInt(1, 30)) { append(fragments[random.nextInt(fragments.size)]) } }
        for (language in languages) {
          val file = factory.createFileFromText("Source", language, text)
          // The leaves of a PSI tree are the tokens of the text, so a tree that lost or invented a character
          // would spell a different text.
          assertEquals(text, file.text, "round $round, $language")
        }
      }
    }
  }

  @Test
  fun anyTextIsAnalyzedWithoutAnException() {
    val random = Random(7)
    repeat(200) { round ->
      val text = buildString { repeat(random.nextInt(0, 25)) { append(FRAGMENTS[random.nextInt(FRAGMENTS.size)]) } }
      for (language in PsiLanguage.entries) {
        val analysis = SourceAnalyzer.analyze(language, text)
        var previous = 0
        for (problem in analysis.problems) {
          assertTrue(problem.start >= previous, "round $round, $language: problems out of order")
          assertTrue(problem.end <= text.length, "round $round, $language: problem beyond the text")
          previous = problem.start
        }
        checkOutline(analysis.outline, 0, text.length, "round $round, $language")
      }
    }
  }

  private fun checkOutline(entries: List<OutlineEntry>, from: Int, to: Int, where: String) {
    var position = from
    for (entry in entries) {
      assertTrue(entry.start >= position && entry.end <= to && entry.end >= entry.start, "$where: $entry")
      checkOutline(entry.children, entry.start, entry.end, where)
      position = entry.end
    }
  }

  @Test
  fun analysisMayBeCalledFromSeveralThreadsAtOnce() {
    val kotlin = "class A { fun f() = 1 }"
    val java = "class B { int g() { return 1; } }"
    val pool = Executors.newFixedThreadPool(8)
    try {
      val tasks =
        (0 until 64).map { index ->
          Callable {
            if (index % 2 == 0) outline(PsiLanguage.KOTLIN, kotlin) else outline(PsiLanguage.JAVA, java)
          }
        }
      val results = pool.invokeAll(tasks, 60, TimeUnit.SECONDS).map { it.get() }
      for ((index, result) in results.withIndex()) {
        assertEquals(if (index % 2 == 0) "TYPE A\n  CALLABLE f\n" else "TYPE B\n  CALLABLE g\n", result)
      }
    } finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun eachLanguageHasATokenizer() {
    assertEquals(KotlinLexerTokenizer, SourceAnalyzer.tokenizer(PsiLanguage.KOTLIN))
    assertEquals(JavaLexerTokenizer, SourceAnalyzer.tokenizer(PsiLanguage.JAVA))
    assertEquals(GroovyLexerTokenizer, SourceAnalyzer.tokenizer(PsiLanguage.GROOVY))
    for (language in PsiLanguage.entries) {
      assertTrue(SourceAnalyzer.tokenizer(language).tokenize("a b").isNotEmpty(), language.name)
    }
  }

  @Test
  fun aProblemCannotEndBeforeItStarts() {
    kotlin.test.assertFailsWith<IllegalArgumentException> { SyntaxProblem(3, 2, "x") }
    kotlin.test.assertFailsWith<IllegalArgumentException> { SyntaxProblem(-1, 2, "x") }
    assertEquals(0, SyntaxProblem(4, 4, "missing").let { it.end - it.start })
  }

  private companion object {
    /** Pieces that open, close and confuse the constructs of the four languages. */
    val FRAGMENTS =
      listOf(
        "class ", "fun ", "def ", "namespace ", "using ", "interface ", "enum ", "data ", "static ", "public ",
        "void ", "int ", "A", "b", "x1", " ", "\n", "\r\n", "\t", "{", "}", "(", ")", "[", "]", "<", ">", ";", ",", ".",
        ":", "=", "==", "->", "=>", "@", "#", "#if x\n", "#[", "\"", "'", "\"\"\"", "'''", "`", "\\", "$", "\${",
        "/", "//", "/*", "*/", "/**", "--", "--[[", "]]", "[[", "0", "1.5", "0x", "é", "名", "😀", "\u0000",
        "template <typename T> ", "Operator<+>", "~", "self", "return ", "if ", "else ", "package a\n", "import a.b\n",
      )
  }
}
