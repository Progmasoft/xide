/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * Tokenizes Python source for colouring.
 *
 * A string literal may carry a prefix of the letters `r`, `b`, `u`, `f` and `t` in either case. The prefix
 * decides how the body is read: a raw string has no escapes, although a backslash still keeps the following
 * quote from ending it, and a formatted or template string embeds `{expression}` fields, with `{{` and `}}`
 * standing for literal braces. A triple-quoted string spans lines.
 *
 * `match`, `case` and `type` are keywords only at the start of a statement of the matching shape. A decorator is
 * `@` at the start of a line; elsewhere `@` is the matrix-multiplication operator.
 */
object PythonTokenizer : Tokenizer {
  /** Reserved words and literal words. */
  val keywords: Set<String> =
    setOf(
      "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
      "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
      "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
    )

  /** Words that are keywords only at the start of one kind of statement. */
  val softKeywords: Set<String> = setOf("match", "case", "type")

  private val operators: List<String> =
    listOf(
      "**=", "//=", ">>=", "<<=", "...",
      "->", ":=", "**", "//", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "@=", "<<", ">>",
      "=", "+", "-", "*", "/", "%", "<", ">", "&", "|", "^", "~", "@", "!",
    )

  private const val PUNCTUATION = "{}()[],:;.\\"

  private const val PREFIX_LETTERS = "rRbBuUfFtT"

  override fun tokenize(text: CharSequence): List<Token> {
    val scanner = Scanner(text)
    while (true) {
      scanner.skipWhitespace()
      if (scanner.atEnd) break
      scanner.step(0)
    }
    return scanner.result()
  }

  private fun Scanner.step(depth: Int) {
    val start = position
    val current = peek()
    when {
      current == '#' -> lineComment()
      current == '"' || current == '\'' -> string(start, depth)
      current.isIdentifierStart() -> {
        val quote = prefixedQuoteOffset()
        if (quote > 0) {
          position += quote
          string(start, depth)
        } else {
          val word = readIdentifier()
          emit(if (isKeyword(word, start)) TokenKind.KEYWORD else TokenKind.IDENTIFIER, start, position)
        }
      }
      current.isAsciiDigit() || (current == '.' && peek(1).isAsciiDigit() && lastToken?.end != position) -> {
        scanNumber("_")
        emit(TokenKind.NUMBER, start, position)
      }
      current == '@' && startsLine(start) && peek(1).isIdentifierStart() -> annotation()
      else -> emitSign(operators, PUNCTUATION)
    }
  }

  /** The length of a string prefix at the scanner's position, or 0 when no prefixed string starts there. */
  private fun Scanner.prefixedQuoteOffset(): Int {
    var offset = 0
    while (offset < 3 && peek(offset) in PREFIX_LETTERS) offset++
    if (offset == 0) return 0
    val quote = peek(offset)
    return if (quote == '"' || quote == '\'') offset else 0
  }

  /**
   * Scans a string whose prefix, if any, lies between [start] and the scanner's position, which is at the quote.
   */
  private fun Scanner.string(start: Int, depth: Int) {
    var raw = false
    var formatted = false
    for (index in start until position) {
      when (text[index].lowercaseChar()) {
        'r' -> raw = true
        'f', 't' -> formatted = true
      }
    }
    val quote = peek()
    val triple = peek(1) == quote && peek(2) == quote
    val terminator = if (triple) "$quote$quote$quote" else quote.toString()
    position += terminator.length
    scanQuoted(start, terminator, multiLine = triple, escapes = !raw) { pending ->
      when {
        // In a raw string the backslash is text, but it still shields the character after it.
        raw && peek() == '\\' && position + 1 < length && !Scanner.isLineBreak(peek(1)) -> {
          emit(TokenKind.STRING, pending, position + 2)
          position += 2
          true
        }
        formatted && (lookingAt("{{") || lookingAt("}}")) -> {
          emit(TokenKind.STRING, pending, position)
          emit(TokenKind.STRING_ESCAPE, position, position + 2)
          position += 2
          true
        }
        formatted && peek() == '{' && depth < MAXIMUM_INTERPOLATION_DEPTH -> {
          emit(TokenKind.STRING, pending, position)
          emit(TokenKind.INTERPOLATION, position, position + 1)
          position++
          embeddedExpression(multiLine = triple) { step(depth + 1) }
          true
        }
        else -> false
      }
    }
  }

  private fun Scanner.isKeyword(word: String, start: Int): Boolean {
    if (word in keywords) return true
    if (word !in softKeywords || !startsLine(start)) return false
    val next = nextSignificant(position)
    val nextCharacter = if (next < length) text[next] else Scanner.NUL
    return when (word) {
      // `type Name = ...` declares an alias; `type(x)` and `type = 1` use the builtin name.
      "type" -> nextCharacter.isIdentifierStart()
      // `match subject:` and `case pattern:` are headers, so their line ends with a colon.
      else -> nextCharacter !in "=.,)]}:" && nextCharacter != Scanner.NUL && lineEndsWithColon()
    }
  }

  /** Whether the current line, without a trailing comment, ends with `:`. */
  private fun Scanner.lineEndsWithColon(): Boolean {
    var index = position
    var last = Scanner.NUL
    while (index < length && !Scanner.isLineBreak(text[index]) && text[index] != '#') {
      if (!text[index].isWhitespace()) last = text[index]
      index++
    }
    return last == ':'
  }
}
