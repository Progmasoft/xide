/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.com.intellij.psi.TokenType

/** Tests the Visual X# lexer and structural parser on the PSI platform. */
class VisualXSharpPsiTest {
  private fun List<OutlineEntry>.render(depth: Int = 0): String =
    joinToString("") { "  ".repeat(depth) + it.kind + " " + it.name + "\n" + it.children.render(depth + 1) }

  private fun outline(text: String): String = SourceAnalyzer.analyze(PsiLanguage.VISUAL_XSHARP, text).outline.render()

  private fun tree(text: String): String = SourceAnalyzer.describeTree(PsiLanguage.VISUAL_XSHARP, text)

  /** The tokens of the PSI lexer as `TYPE:text`. */
  private fun lexed(text: String): List<String> {
    val lexer = VisualXSharpLexer()
    lexer.start(text)
    val result = ArrayList<String>()
    var position = 0
    while (true) {
      val type = lexer.tokenType ?: break
      // The platform requires the tokens to follow each other without gap or overlap.
      assertEquals(position, lexer.tokenStart, "token start in: $text")
      assertEquals(true, lexer.tokenEnd > lexer.tokenStart, "empty token in: $text")
      position = lexer.tokenEnd
      if (type != TokenType.WHITE_SPACE) result += "$type:" + text.substring(lexer.tokenStart, lexer.tokenEnd)
      lexer.advance()
    }
    assertEquals(text.length, position, "tokens do not reach the end of: $text")
    return result
  }

  @Test
  fun theLexerPresentsTheColouringTokensWithWhitespaceBetweenThem() {
    assertEquals(
      listOf(
        "KEYWORD:class", "IDENTIFIER:A", "PUNCTUATION:{", "KEYWORD:int", "IDENTIFIER:x", "OPERATOR:=", "NUMBER:1",
        "PUNCTUATION:;", "COMMENT:-- note", "PUNCTUATION:}",
      ),
      lexed("class A {\n  int x = 1; -- note\n}\n"),
    )
  }

  @Test
  fun aPreprocessorDirectiveIsOneTokenUpToTheEndOfItsLine() {
    assertEquals(
      listOf("DIRECTIVE:#if os(windows) && not FAST", "KEYWORD:int", "IDENTIFIER:x", "PUNCTUATION:;", "DIRECTIVE:#endif"),
      lexed("#if os(windows) && not FAST\nint x;\n#endif"),
    )
    // `#[` opens an attribute; it is not a directive.
    assertEquals(
      listOf("BAD_CHARACTER:#", "PUNCTUATION:[", "IDENTIFIER:file", "PUNCTUATION:]"),
      lexed("#[file]"),
    )
  }

  @Test
  fun theLexerCoversAnyTextWithoutGaps() {
    val fragments =
      listOf(
        "class", " ", "\n", "\r\n", "{", "}", "\"", "'", "--", "--[[", "]]", "[[", "[=[", "]=]", "#", "#if", "#[", "x",
        "1'0", "\\", "é", "😀", "\u0000", ";", "(", ")",
      )
    val random = Random(41)
    repeat(500) {
      lexed(buildString { repeat(random.nextInt(0, 20)) { append(fragments[random.nextInt(fragments.size)]) } })
    }
  }

  @Test
  fun aFileListsItsNamespaceTypesAndMembers() {
    val text =
      """
      #[file: Something]
      namespace Examples.Shapes;
      using System.Math;
      using selected System.Collections { List, Map = Dictionary };
      #if os(windows)
      #define FAST
      #endif

      -- A shape.
      public abstract class Shape(int sides) : Base, Other {
          private int count = 0;
          public static final double Pi = 3.14;
          List<Map<String, int>> table;
          public int Sides { get; set; } = 3;
          int self[int index] { get { return 0; } }
          Shape(int sides) : super(sides) { count = sides; }
          ~Shape() { }
          static { count = 1; }
          public abstract double Area();
          public virtual String Name() = "shape";
          public static void Main() {
              int x = 1;
              if (x > 0) { x = 2; }
          }
          Operator<+>(Shape other) { return self; }
          template <typename T> T Convert(T value) { return value; }
          class Inner { int v; }
          enum Color { Red, Green = 2 }
          enum class Kind(int code) { case A(1), case B(2) int Code() { return code; } }
      }
      data Point(int x, int y);
      data class Pair(int a, int b) { int Sum() { return a + b; } }
      interface Drawable { void Draw(); }
      object Registry { int size; }
      extension Extra : Shape { int More() { return 1; } }
      typealias Id = int;
      concept Addable = requires(T a) { a + a; };
      attribute Marked(int level) targets(class, data class);
      int data = 5;
      """.trimIndent()
    assertEquals(
      """
      NAMESPACE Examples.Shapes
      TYPE Shape
        PROPERTY count
        PROPERTY Pi
        PROPERTY table
        PROPERTY Sides
        PROPERTY self
        CALLABLE Shape
        CALLABLE ~Shape
        INITIALIZER static
        CALLABLE Area
        CALLABLE Name
        CALLABLE Main
        CALLABLE Operator<+>
        CALLABLE Convert
        TYPE Inner
          PROPERTY v
        TYPE Color
        TYPE Kind
      TYPE Point
      TYPE Pair
        CALLABLE Sum
      TYPE Drawable
        CALLABLE Draw
      TYPE Registry
        PROPERTY size
      TYPE Extra
        CALLABLE More
      TYPE Id
      TYPE Addable
      TYPE Marked
      PROPERTY data

      """.trimIndent(),
      outline(text),
    )
  }

  @Test
  fun theTreeNamesEachDeclarationAndKeepsBodiesOpaque() {
    assertEquals(
      """
      VXS_FILE
        NAMESPACE_DECLARATION
          NAME
        TYPE_DECLARATION
          NAME
          TYPE_BODY
            CALLABLE_DECLARATION
              NAME
              BLOCK
            FIELD_DECLARATION
              NAME

      """.trimIndent(),
      tree("namespace A;\nclass B { int f(int x) { if (x > 0) { return x; } return 0; } int y = 1; }\n"),
    )
  }

  @Test
  fun aWordThatAlsoNamesAKindOfTypeCanBeAFieldName() {
    // `data`, `type` and `object` open a declaration only when a name follows them.
    assertEquals("PROPERTY data\nPROPERTY type\nPROPERTY object\n", outline("int data = 5; String type; Thing object;"))
    assertEquals("TYPE data\n", outline("class data { }"))
  }

  @Test
  fun anInitializerMayContainBraces() {
    // The braces of a lambda or a match in an initializer do not start a body of the declaration.
    assertEquals("PROPERTY handler\nPROPERTY next\n", outline("Action handler = { x -> x + 1 }; int next = 2;"))
    assertEquals("CALLABLE Pick\nCALLABLE After\n", outline("int Pick(int x) = match (x) { 1 -> 2, _ -> 3 }; void After() { }"))
  }

  @Test
  fun aDefaultArgumentWithBracesStaysInsideTheParameterList() {
    assertEquals("CALLABLE Run\nPROPERTY after\n", outline("void Run(Action a = { x -> x }) { } int after;"))
  }

  @Test
  fun visualXSharpReportsNoSyntaxProblems() {
    // Its errors come from the compiler; the structural parser accepts whatever is being typed.
    for (text in listOf("}}} class { ( [ \"unterminated", "class", "int x", "namespace", "using selected a {", "#")) {
      assertEquals(emptyList(), SourceAnalyzer.analyze(PsiLanguage.VISUAL_XSHARP, text).problems, text)
    }
  }

  @Test
  fun unbalancedTextStillYieldsTheDeclarationsBeforeIt() {
    assertEquals("NAMESPACE A\nTYPE B\n  CALLABLE f\n", outline("namespace A; class B { void f() { "))
    assertEquals("TYPE B\nTYPE C\n", outline("class B { } } } class C { }"))
  }
}
