/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

class PythonTokenizerTest {
  private val tokenizer = PythonTokenizer

  @Test
  fun aFunctionIsSplitIntoKeywordsNamesAndSigns() {
    tokenizer.assertPieces(
      "def add(a, b):\n    return a + b  # sum",
      K to "def", I to "add", P to "(", I to "a", P to ",", I to "b", P to ")", P to ":", K to "return", I to "a",
      O to "+", I to "b", C to "# sum",
    )
  }

  @Test
  fun reservedWordsAreKeywordsEverywhere() {
    for (keyword in PythonTokenizer.keywords) {
      tokenizer.assertPieces(keyword, K to keyword)
    }
    tokenizer.assertPieces("none true", I to "none", I to "true")
  }

  @Test
  fun softKeywordsNeedTheirStatementShape() {
    assertEquals(K, tokenizer.kindOf("match command:\n    case 1:\n        pass", "match"))
    assertEquals(K, tokenizer.kindOf("match command:\n    case 1:\n        pass", "case"))
    assertEquals(K, tokenizer.kindOf("match (a, b):  # header", "match"))
    assertEquals(I, tokenizer.kindOf("match = 1", "match"))
    assertEquals(I, tokenizer.kindOf("match.group(1)", "match"))
    assertEquals(I, tokenizer.kindOf("x = match", "match"))
    assertEquals(I, tokenizer.kindOf("match(x)", "match"))
    assertEquals(K, tokenizer.kindOf("type Alias = int", "type"))
    assertEquals(I, tokenizer.kindOf("type(x)", "type"))
    assertEquals(I, tokenizer.kindOf("x = type", "type"))
  }

  @Test
  fun stringsHaveEscapesAndPrefixes() {
    tokenizer.assertPieces("'a\\n' \"b\"", S to "'a", E to "\\n", S to "'", S to "\"b\"")
    tokenizer.assertPieces("b'raw' u\"x\" Rb'y'", S to "b'raw'", S to "u\"x\"", S to "Rb'y'")
    // A raw string has no escapes, but a backslash still shields the quote after it.
    tokenizer.assertPieces("r'a\\'b' x", S to "r'a\\'b'", I to "x")
    tokenizer.assertPieces("r'\\d+\\n'", S to "r'\\d+\\n'")
    tokenizer.assertPieces("'open\nx", S to "'open", I to "x")
    // A name that merely ends with prefix letters is a name.
    tokenizer.assertPieces("bar 'x'", I to "bar", S to "'x'")
  }

  @Test
  fun tripleQuotedStringsSpanLines() {
    tokenizer.assertPieces("\"\"\"doc\n\n  more\"\"\"\nx", S to "\"\"\"doc", S to "more\"\"\"", I to "x")
    tokenizer.assertPieces("'''a ' b'''", S to "'''a ' b'''")
    tokenizer.assertPieces("'''open\nstill", S to "'''open", S to "still")
  }

  @Test
  fun formattedStringsEmbedExpressions() {
    tokenizer.assertPieces("f'a {x + 1} b'", S to "f'a", X to "{", I to "x", O to "+", N to "1", X to "}", S to "b'")
    tokenizer.assertPieces("f'{{literal}}'", S to "f'", E to "{{", S to "literal", E to "}}", S to "'")
    tokenizer.assertPieces("f'{d[\"k\"]!r:>10}'", S to "f'", X to "{", I to "d", P to "[", S to "\"k\"", P to "]",
      O to "!", I to "r", P to ":", O to ">", N to "10", X to "}", S to "'")
    tokenizer.assertPieces("f'{ {1: 2}[1] }'", S to "f'", X to "{", P to "{", N to "1", P to ":", N to "2", P to "}",
      P to "[", N to "1", P to "]", X to "}", S to "'")
    // A plain string does not embed.
    tokenizer.assertPieces("'{x}'", S to "'{x}'")
    // A field that is never closed ends with its line in a single-line string.
    tokenizer.assertPieces("f'{a +\nb", S to "f'", X to "{", I to "a", O to "+", I to "b")
    tokenizer.assertPieces("rf'{x}\\d'", S to "rf'", X to "{", I to "x", X to "}", S to "\\d'")
  }

  @Test
  fun deeplyNestedFieldsStopBeingScanned() {
    val text = "f'{".repeat(300) + "x"
    assertEquals(null, checkTokens(text, tokenizer.tokenize(text)))
  }

  @Test
  fun decoratorsStartALine() {
    tokenizer.assertPieces("@staticmethod\ndef f(): pass", A to "@staticmethod", K to "def", I to "f", P to "(",
      P to ")", P to ":", K to "pass")
    tokenizer.assertPieces("  @app.route('/')", A to "@app.route", P to "(", S to "'/'", P to ")")
    tokenizer.assertPieces("a @ b", I to "a", O to "@", I to "b")
    tokenizer.assertPieces("a @= b", I to "a", O to "@=", I to "b")
  }

  @Test
  fun numbersAndOperators() {
    for (number in listOf("0", "1_000", "0xFF", "0o17", "0b11", "1.5", "1e-9", "3j", "1.5J", ".5")) {
      tokenizer.assertPieces(number, N to number)
    }
    tokenizer.assertPieces("a ** b // c", I to "a", O to "**", I to "b", O to "//", I to "c")
    tokenizer.assertPieces("(n := 1)", P to "(", I to "n", O to ":=", N to "1", P to ")")
    tokenizer.assertPieces("def f() -> int: ...", K to "def", I to "f", P to "(", P to ")", O to "->", I to "int",
      P to ":", O to "...")
    tokenizer.assertPieces("x = 1 + \\\n  2", I to "x", O to "=", N to "1", O to "+", P to "\\", N to "2")
    tokenizer.assertPieces("a ? b", I to "a", BAD to "?", I to "b")
  }
}
