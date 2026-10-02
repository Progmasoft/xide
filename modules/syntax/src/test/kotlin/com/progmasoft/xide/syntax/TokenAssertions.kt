/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.test.assertEquals
import kotlin.test.assertNull

internal typealias Piece = Pair<TokenKind, String>

internal val K = TokenKind.KEYWORD
internal val I = TokenKind.IDENTIFIER
internal val N = TokenKind.NUMBER
internal val S = TokenKind.STRING
internal val E = TokenKind.STRING_ESCAPE
internal val X = TokenKind.INTERPOLATION
internal val C = TokenKind.COMMENT
internal val D = TokenKind.DOC_COMMENT
internal val A = TokenKind.ANNOTATION
internal val O = TokenKind.OPERATOR
internal val P = TokenKind.PUNCTUATION
internal val BAD = TokenKind.INVALID

/** Tokenizes [text], checks the contract, and returns each token's kind with the text it covers. */
internal fun Tokenizer.pieces(text: String): List<Piece> {
  val tokens = tokenize(text)
  assertNull(checkTokens(text, tokens), "contract violated for: $text")
  return tokens.map { it.kind to text.substring(it.start, it.end) }
}

/** Asserts the exact token sequence of [text]. */
internal fun Tokenizer.assertPieces(text: String, vararg expected: Piece) {
  assertEquals(expected.toList(), pieces(text), "tokens of: $text")
}

/** The kind of the first token whose text is exactly [fragment]. */
internal fun Tokenizer.kindOf(text: String, fragment: String): TokenKind? =
  pieces(text).firstOrNull { it.second == fragment }?.first

/** The kinds of every token whose text is exactly [fragment], in order. */
internal fun Tokenizer.kindsOf(text: String, fragment: String): List<TokenKind> =
  pieces(text).filter { it.second == fragment }.map { it.first }
