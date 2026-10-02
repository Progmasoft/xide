/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * What a piece of source text is, as far as colouring it is concerned.
 *
 * The kinds are lexical. A tokenizer never resolves names or types, so an identifier is an identifier whether it
 * names a class or a local variable; anything that needs meaning belongs to a language service, not to this module.
 */
enum class TokenKind {
  /** A reserved word, or a contextual word used in the position where it acts as one. */
  KEYWORD,

  /** A name. */
  IDENTIFIER,

  /** An integer or floating-point literal, including its sign-free prefix, separators and suffix. */
  NUMBER,

  /** The literal text of a string or character literal, including its delimiters. */
  STRING,

  /** An escape sequence inside a string or character literal. */
  STRING_ESCAPE,

  /** The markers of an expression embedded in a string, and a directly embedded name. */
  INTERPOLATION,

  /** A comment that documents nothing in particular. */
  COMMENT,

  /** A comment the language's documentation tool reads. */
  DOC_COMMENT,

  /** An annotation, decorator or attribute, including its introducing sign. */
  ANNOTATION,

  /** An operator sign. */
  OPERATOR,

  /** A bracket, separator or terminator. */
  PUNCTUATION,

  /** Text the language does not allow where it stands, such as an unterminated literal or a stray character. */
  INVALID,
}

/**
 * One coloured stretch of a source text.
 *
 * @property kind what the stretch is.
 * @property start the UTF-16 offset of its first code unit.
 * @property end the UTF-16 offset just after it; always greater than [start].
 */
data class Token(val kind: TokenKind, val start: Int, val end: Int) {
  init {
    require(start >= 0) { "token start must not be negative" }
    require(end > start) { "token must not be empty" }
  }

  /** The number of UTF-16 code units the token covers. */
  val length: Int
    get() = end - start
}

/**
 * Splits a source text of one language into tokens for colouring.
 *
 * Every implementation is total: any text, including malformed, truncated or binary-looking text, yields a result
 * and never an exception, because the editor colours whatever is being typed. The result obeys one contract, which
 * [checkTokens] verifies:
 *
 * - tokens are in ascending order and do not overlap;
 * - every token lies inside the text and is not empty;
 * - every character that is not whitespace belongs to exactly one token, so nothing is left uncoloured by accident;
 * - no token consists only of whitespace.
 */
fun interface Tokenizer {
  /** Tokenizes a complete text. */
  fun tokenize(text: CharSequence): List<Token>
}

/**
 * Checks that [tokens] obey the [Tokenizer] contract for [text] and returns the first violation, or null.
 *
 * The check is part of the public surface so that tests of this module and of its consumers state the contract in
 * one place.
 */
fun checkTokens(text: CharSequence, tokens: List<Token>): String? {
  var position = 0
  for ((index, token) in tokens.withIndex()) {
    if (token.start < position) return "token $index starts at ${token.start}, before offset $position"
    if (token.end > text.length) return "token $index ends at ${token.end}, after the text"
    for (offset in position until token.start) {
      if (!text[offset].isWhitespace()) return "offset $offset is not whitespace but belongs to no token"
    }
    var blank = true
    for (offset in token.start until token.end) {
      if (!text[offset].isWhitespace()) {
        blank = false
        break
      }
    }
    if (blank) return "token $index covers only whitespace"
    position = token.end
  }
  for (offset in position until text.length) {
    if (!text[offset].isWhitespace()) return "offset $offset is not whitespace but belongs to no token"
  }
  return null
}
