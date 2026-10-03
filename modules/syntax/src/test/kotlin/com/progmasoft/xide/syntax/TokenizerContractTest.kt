/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks the contract every tokenizer promises, on inputs nobody wrote on purpose.
 *
 * The generated texts are seeded, so a failure names a seed that reproduces it.
 */
class TokenizerContractTest {
  private val tokenizers: Map<String, Tokenizer> =
    mapOf(
      "Visual X#" to VisualXSharpTokenizer,
      "Python" to PythonTokenizer,
    )

  /** Pieces that open, close and confuse the lexical constructs of the languages Xide colours. */
  private val fragments: List<String> =
    listOf(
      "\"", "'", "\"\"\"", "'''", "`", "\\", "\\\"", "\\n", "\\u00", "\\u0041", "$", "\${", "\$name", "}", "{", "{{",
      "}}", "/", "//", "///", "/*", "/**", "*/", "--", "--[[", "--[==[", "]]", "]==]", "[[", "[=[", "#", "#!", "@",
      "@interface", "@file:Name", "non-sealed", "\n", "\r\n", "\r", " ", "\t", " ", " ", "0", "1_0", "0x",
      "0xG", "1e", "1e+", "1.", ".5", "1'0", "f'", "rb\"", "r'", "$/", "/$", "a", "value", "class", "match", "case",
      "yield", "by", "get", "set", "=", "==", "=~", "?:", "??", "...", "..<", "<", ">", "(", ")", "[", "]", ":", ";",
      ",", ".", "é", "名", "😀", "\uD83D", "\uDE00", "\u0000", "\uFEFF", "\u0007",
    )

  @Test
  fun emptyAndBlankTextsHaveNoTokens() {
    for ((name, tokenizer) in tokenizers) {
      assertEquals(emptyList(), tokenizer.tokenize(""), name)
      assertEquals(emptyList(), tokenizer.tokenize(" \t\r\n \n"), name)
    }
  }

  @Test
  fun everySingleFragmentObeysTheContract() {
    for ((name, tokenizer) in tokenizers) {
      for (fragment in fragments) {
        assertNull(checkTokens(fragment, tokenizer.tokenize(fragment)), "$name on '$fragment'")
      }
    }
  }

  @Test
  fun everyFragmentPairObeysTheContract() {
    for ((name, tokenizer) in tokenizers) {
      for (first in fragments) {
        for (second in fragments) {
          val text = first + second
          assertNull(checkTokens(text, tokenizer.tokenize(text)), "$name on '$text'")
        }
      }
    }
  }

  @Test
  fun randomFragmentSequencesObeyTheContract() {
    for ((name, tokenizer) in tokenizers) {
      for (seed in 0 until 1500) {
        val random = Random(seed)
        val text = buildString { repeat(random.nextInt(1, 40)) { append(fragments[random.nextInt(fragments.size)]) } }
        assertNull(checkTokens(text, tokenizer.tokenize(text)), "$name, seed $seed")
      }
    }
  }

  @Test
  fun randomCharactersObeyTheContract() {
    for ((name, tokenizer) in tokenizers) {
      for (seed in 0 until 300) {
        val random = Random(seed)
        val text = buildString { repeat(random.nextInt(0, 200)) { append(random.nextInt(0, 0x3000).toChar()) } }
        assertNull(checkTokens(text, tokenizer.tokenize(text)), "$name, seed $seed")
      }
    }
  }

  @Test
  fun tokenizingIsDeterministic() {
    val text = "class A { val s = \"a \${b} c\" } // done\n--[[ x ]] 'q' f'{z}'"
    for ((name, tokenizer) in tokenizers) {
      assertEquals(tokenizer.tokenize(text), tokenizer.tokenize(text), name)
    }
  }

  @Test
  fun aLargeTextIsTokenizedInLinearTime() {
    val line = "val name = call(\"text \${value}\", 12345) // comment\n"
    val text = line.repeat(20_000)
    for ((name, tokenizer) in tokenizers) {
      val tokens = tokenizer.tokenize(text)
      assertNull(checkTokens(text, tokens), name)
      assertTrue(tokens.size > 100_000, name)
    }
  }

  @Test
  fun pathologicalOpenersDoNotOverflowTheStack() {
    val openers = listOf("\"\${", "f'{", "/*", "--[[", "(", "{", "\"\"\"\${", "\"\$a")
    for ((name, tokenizer) in tokenizers) {
      for (opener in openers) {
        val text = opener.repeat(5_000)
        assertNull(checkTokens(text, tokenizer.tokenize(text)), "$name on repeated '$opener'")
      }
    }
  }

  @Test
  fun aSurrogatePairIsNeverSplitBetweenTokens() {
    val text = "a 😀 b"
    for ((name, tokenizer) in tokenizers) {
      val tokens = tokenizer.tokenize(text)
      assertTrue(tokens.none { it.start == 3 || it.end == 3 }, name)
    }
  }

  @Test
  fun theCheckerReportsEachKindOfViolation() {
    val text = "ab cd"
    assertNull(checkTokens(text, listOf(Token(TokenKind.IDENTIFIER, 0, 2), Token(TokenKind.IDENTIFIER, 3, 5))))
    assertNotNull(checkTokens(text, listOf(Token(TokenKind.IDENTIFIER, 0, 2))))
    assertNotNull(checkTokens(text, listOf(Token(TokenKind.IDENTIFIER, 3, 5))))
    assertNotNull(checkTokens(text, listOf(Token(TokenKind.IDENTIFIER, 0, 3), Token(TokenKind.IDENTIFIER, 2, 5))))
    assertNotNull(checkTokens(text, listOf(Token(TokenKind.IDENTIFIER, 0, 2), Token(TokenKind.IDENTIFIER, 3, 9))))
    assertNotNull(
      checkTokens(
        text,
        listOf(Token(TokenKind.IDENTIFIER, 0, 2), Token(TokenKind.COMMENT, 2, 3), Token(TokenKind.IDENTIFIER, 3, 5)),
      ),
    )
  }

  @Test
  fun aTokenCannotBeEmptyOrNegative() {
    assertFailsWith<IllegalArgumentException> { Token(TokenKind.NUMBER, 2, 2) }
    assertFailsWith<IllegalArgumentException> { Token(TokenKind.NUMBER, 3, 2) }
    assertFailsWith<IllegalArgumentException> { Token(TokenKind.NUMBER, -1, 2) }
    assertEquals(3, Token(TokenKind.NUMBER, 2, 5).length)
  }
}
