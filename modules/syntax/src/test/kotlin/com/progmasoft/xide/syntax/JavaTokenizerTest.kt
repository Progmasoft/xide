/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

class JavaTokenizerTest {
  private val tokenizer = JavaTokenizer
  private val block = "\"\"\""

  @Test
  fun aClassIsSplitIntoKeywordsNamesAndSigns() {
    tokenizer.assertPieces(
      "public final class A { int x = 1; }",
      K to "public", K to "final", K to "class", I to "A", P to "{", K to "int", I to "x", O to "=", N to "1", P to ";",
      P to "}",
    )
  }

  @Test
  fun reservedWordsAreKeywordsEverywhere() {
    for (keyword in JavaTokenizer.keywords) {
      tokenizer.assertPieces(keyword, K to keyword)
      tokenizer.assertPieces("${keyword}_", I to "${keyword}_")
    }
  }

  @Test
  fun contextualWordsAreKeywordsOnlyInTheirPosition() {
    assertEquals(K, tokenizer.kindOf("var x = 1;", "var"))
    assertEquals(I, tokenizer.kindOf("var = 1;", "var"))
    assertEquals(K, tokenizer.kindOf("record Point(int x) {}", "record"))
    assertEquals(I, tokenizer.kindOf("Record record = other;", "record"))
    assertEquals(I, tokenizer.kindOf("this.record = r;", "record"))
    assertEquals(K, tokenizer.kindOf("sealed interface S permits A, B {}", "sealed"))
    assertEquals(K, tokenizer.kindOf("sealed interface S permits A, B {}", "permits"))
    assertEquals(I, tokenizer.kindOf("int permits = 1;", "permits"))
    assertEquals(K, tokenizer.kindOf("non-sealed class A {}", "non-sealed"))
    assertEquals(K, tokenizer.kindOf("case 1 -> {\n  yield 5;\n}", "yield"))
    assertEquals(I, tokenizer.kindOf("  yield = 5;", "yield"))
    assertEquals(I, tokenizer.kindOf("  yield(5);", "yield"))
    assertEquals(I, tokenizer.kindOf("Thread.yield();", "yield"))
    // Subtraction between two names is not the hyphenated keyword.
    tokenizer.assertPieces("non-sealedx", I to "non", O to "-", I to "sealedx")
  }

  @Test
  fun commentsAndDocumentation() {
    tokenizer.assertPieces("a // rest\nb", I to "a", C to "// rest", I to "b")
    tokenizer.assertPieces("/// markdown doc\nb", D to "/// markdown doc", I to "b")
    tokenizer.assertPieces("/** doc */ x", D to "/** doc */", I to "x")
    // Block comments do not nest: the first closer ends the comment.
    tokenizer.assertPieces("/* a /* b */ c */", C to "/* a /* b */", I to "c", O to "*", O to "/")
    tokenizer.assertPieces("/**/ x", C to "/**/", I to "x")
    tokenizer.assertPieces("/* open", C to "/* open")
  }

  @Test
  fun stringsCharactersAndTextBlocks() {
    tokenizer.assertPieces("\"a\\tb\"", S to "\"a", E to "\\t", S to "b\"")
    tokenizer.assertPieces("\"open\nint", S to "\"open", K to "int")
    tokenizer.assertPieces("'a' '\\''", S to "'a'", S to "'", E to "\\'", S to "'")
    tokenizer.assertPieces("$block\n  line \"one\"\n  two\\n$block;", S to block, S to "line \"one\"", S to "two",
      E to "\\n", S to block, P to ";")
    // A dollar sign is an ordinary character of a Java string.
    tokenizer.assertPieces("\"\${x}\"", S to "\"\${x}\"")
  }

  @Test
  fun annotationsAndAnnotationDeclarations() {
    tokenizer.assertPieces("@Override public", A to "@Override", K to "public")
    tokenizer.assertPieces("@java.lang.Deprecated", A to "@java.lang.Deprecated")
    tokenizer.assertPieces("public @interface Marker {}", K to "public", K to "@interface", I to "Marker", P to "{",
      P to "}")
    tokenizer.assertPieces("@interfaces", A to "@interfaces")
    tokenizer.assertPieces("@ x", O to "@", I to "x")
  }

  @Test
  fun numbersAndOperators() {
    for (number in listOf("0", "1_000", "0x1F", "0b11", "017", "1.5f", "1e-9", "0x1.8p1", "10L", ".25")) {
      tokenizer.assertPieces(number, N to number)
    }
    tokenizer.assertPieces("a >>>= 2", I to "a", O to ">>>=", N to "2")
    tokenizer.assertPieces("x -> y", I to "x", O to "->", I to "y")
    tokenizer.assertPieces("String::valueOf", I to "String", O to "::", I to "valueOf")
    tokenizer.assertPieces("int... xs", K to "int", O to "...", I to "xs")
    tokenizer.assertPieces("a.b", I to "a", P to ".", I to "b")
  }

  @Test
  fun dollarSignsBelongToNames() {
    tokenizer.assertPieces("\$outer.this\$0", I to "\$outer", P to ".", I to "this\$0")
  }
}
