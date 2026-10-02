/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

class VisualXSharpTokenizerTest {
  private val tokenizer = VisualXSharpTokenizer

  @Test
  fun aMethodIsSplitIntoKeywordsNamesAndSigns() {
    tokenizer.assertPieces(
      "public static void Main() { return; }",
      K to "public", K to "static", K to "void", I to "Main", P to "(", P to ")", P to "{", K to "return", P to ";",
      P to "}",
    )
  }

  @Test
  fun aDoubleDashStartsACommentToTheEndOfTheLine() {
    tokenizer.assertPieces("int a = 1; -- the count\nb", K to "int", I to "a", O to "=", N to "1", P to ";",
      C to "-- the count", I to "b")
    tokenizer.assertPieces("-- only a comment", C to "-- only a comment")
    tokenizer.assertPieces("--", C to "--")
  }

  @Test
  fun aDoubleDashDirectlyAfterAValueIsTheDecrementOperator() {
    tokenizer.assertPieces("count--;", I to "count", O to "--", P to ";")
    tokenizer.assertPieces("items[i]--;", I to "items", P to "[", I to "i", P to "]", O to "--", P to ";")
    tokenizer.assertPieces("(a)--;", P to "(", I to "a", P to ")", O to "--", P to ";")
    // With a space before it the same sign is a comment, as in the compiler's lexer.
    tokenizer.assertPieces("count --;", I to "count", C to "--;")
    tokenizer.assertPieces("a = --b", I to "a", O to "=", C to "--b")
  }

  @Test
  fun documentationCommentsAreMarkedByABarOrAnExclamationMark() {
    assertEquals(D, tokenizer.pieces("--| Adds two numbers.").single().first)
    assertEquals(D, tokenizer.pieces("--! Module notes.").single().first)
    assertEquals(C, tokenizer.pieces("-- |not a marker").single().first)
    assertEquals(D, tokenizer.pieces("--[[| long\ndocumentation ]]").first().first)
  }

  @Test
  fun aLongCommentEndsAtTheMatchingBracketLevel() {
    tokenizer.assertPieces("--[[ one ]] x", C to "--[[ one ]]", I to "x")
    tokenizer.assertPieces("--[==[ a ]] b ]==] x", C to "--[==[ a ]] b ]==]", I to "x")
    // Each line of a comment that spans lines is its own stretch, so blank lines stay uncoloured.
    tokenizer.assertPieces("--[[ a\n\n  b ]] x", C to "--[[ a", C to "b ]]", I to "x")
    tokenizer.assertPieces("--[[ never closed\nint", C to "--[[ never closed", C to "int")
  }

  @Test
  fun aLongBracketStringHasNoEscapesAndNoComments() {
    tokenizer.assertPieces("String t = [[a -- b\\n]];", I to "String", I to "t", O to "=", S to "[[a -- b\\n]]",
      P to ";")
    tokenizer.assertPieces("[=[ ]] ]=]", S to "[=[ ]] ]=]")
    // A single bracket is indexing.
    tokenizer.assertPieces("a[0]", I to "a", P to "[", N to "0", P to "]")
  }

  @Test
  fun aQuotedStringColoursItsEscapesAndIgnoresCommentSigns() {
    tokenizer.assertPieces("\"a -- b\"", S to "\"a -- b\"")
    tokenizer.assertPieces("\"a\\n\\u0041b\"", S to "\"a", E to "\\n", E to "\\u0041", S to "b\"")
    tokenizer.assertPieces("\"open", S to "\"open")
    tokenizer.assertPieces("'x' '\\''", S to "'x'", S to "'", E to "\\'", S to "'")
  }

  @Test
  fun numbersKeepTheirSeparatorsPrefixesAndSuffixes() {
    for (number in listOf("0", "42", "1'000'000", "1_000", "0xFF", "0b1010", "3.25", "1e9", "1.5e-3", "10ul")) {
      tokenizer.assertPieces(number, N to number)
    }
    // A quote that does not sit between digits starts a character literal.
    tokenizer.assertPieces("1 'a'", N to "1", S to "'a'")
    tokenizer.assertPieces("1...5", N to "1", O to "...", N to "5")
  }

  @Test
  fun operatorsUseTheLongestMatch() {
    tokenizer.assertPieces("a \\= b", I to "a", O to "\\=", I to "b")
    tokenizer.assertPieces("a ?: b ?? c", I to "a", O to "?:", I to "b", O to "??", I to "c")
    tokenizer.assertPieces("a //= 2 ** 3", I to "a", O to "//=", N to "2", O to "**", N to "3")
    tokenizer.assertPieces("x is not null", I to "x", K to "is", K to "not", K to "null")
    tokenizer.assertPieces("!mask", O to "!", I to "mask")
  }

  @Test
  fun everyCompilerKeywordIsAKeyword() {
    for (keyword in VisualXSharpTokenizer.keywords) {
      tokenizer.assertPieces(keyword, K to keyword)
      tokenizer.assertPieces("${keyword}x", I to "${keyword}x")
    }
  }

  @Test
  fun charactersTheLanguageDoesNotUseAreInvalid() {
    tokenizer.assertPieces("a # b", I to "a", BAD to "#", I to "b")
    tokenizer.assertPieces("😀", BAD to "😀")
  }
}
