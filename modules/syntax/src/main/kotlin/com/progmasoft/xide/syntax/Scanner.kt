/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * A cursor over a source text that collects tokens.
 *
 * Every token goes through [emit], which is where the shared half of the tokenizer contract is enforced: tokens
 * stay ordered, and no token is whitespace only.
 */
internal class Scanner(val text: CharSequence) {
  /** The offset of the next unread character. */
  var position: Int = 0

  private val tokens = ArrayList<Token>()

  val length: Int
    get() = text.length

  val atEnd: Boolean
    get() = position >= text.length

  /** The character at [position] plus [offset], or NUL beyond either end. */
  fun peek(offset: Int = 0): Char {
    val index = position + offset
    return if (index in 0 until text.length) text[index] else NUL
  }

  /** Whether the text continues with [expected] at the current position. */
  fun lookingAt(expected: String): Boolean {
    if (position + expected.length > text.length) return false
    for (index in expected.indices) {
      if (text[position + index] != expected[index]) return false
    }
    return true
  }

  /**
   * Records [start] until [end] as [kind].
   *
   * A range that is empty or lies before the last token is ignored. Text-like kinds, which may contain whitespace
   * and span lines, are recorded line by line without the whitespace at either edge of a line, so no token is
   * ever whitespace only and a blank line inside a comment or a raw string belongs to no token. A piece that
   * directly continues the previous token of the same kind extends it instead of starting a new one.
   */
  fun emit(kind: TokenKind, start: Int, end: Int) {
    val from = maxOf(start, tokens.lastOrNull()?.end ?: 0)
    if (end <= from) return
    if (kind !in TEXT_KINDS) {
      tokens += Token(kind, from, end)
      return
    }
    var lineStart = from
    while (lineStart < end) {
      var lineEnd = lineStart
      while (lineEnd < end && !isLineBreak(text[lineEnd])) lineEnd++
      var first = lineStart
      while (first < lineEnd && text[first].isWhitespace()) first++
      var last = lineEnd
      while (last > first && text[last - 1].isWhitespace()) last--
      if (last > first) {
        val previous = tokens.lastOrNull()
        if (previous != null && previous.kind == kind && previous.end == first) {
          tokens[tokens.size - 1] = Token(kind, previous.start, last)
        } else {
          tokens += Token(kind, first, last)
        }
      }
      lineStart = lineEnd
      while (lineStart < end && isLineBreak(text[lineStart])) lineStart++
    }
  }

  /** Skips whitespace and returns whether anything was skipped. */
  fun skipWhitespace(): Boolean {
    val start = position
    while (position < text.length && text[position].isWhitespace()) position++
    return position > start
  }

  /** Advances past the rest of the current line, stopping before its terminator. */
  fun skipToLineEnd() {
    while (position < text.length && !isLineBreak(text[position])) position++
  }

  /** The kind of the most recently recorded token, or null when there is none. */
  val lastKind: TokenKind?
    get() = tokens.lastOrNull()?.kind

  /** The most recently recorded token, or null when there is none. */
  val lastToken: Token?
    get() = tokens.lastOrNull()

  /** The text of the most recently recorded token, or an empty string when there is none. */
  fun lastText(): CharSequence = tokens.lastOrNull()?.let { text.subSequence(it.start, it.end) } ?: ""

  fun result(): List<Token> = tokens

  companion object {
    const val NUL: Char = '\u0000'

    /** Kinds that may contain whitespace and span lines. */
    private val TEXT_KINDS = setOf(TokenKind.STRING, TokenKind.COMMENT, TokenKind.DOC_COMMENT, TokenKind.INVALID)

    fun isLineBreak(character: Char): Boolean = character == '\n' || character == '\r'
  }
}

/** Whether a character may start an identifier in the languages this module covers. */
internal fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter()

/** Whether a character may continue an identifier in the languages this module covers. */
internal fun Char.isIdentifierPart(): Boolean = this == '_' || isLetterOrDigit()

/** Whether a character is an ASCII decimal digit. Unicode digits are not digits of a numeric literal. */
internal fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/** Whether a character is an ASCII hexadecimal digit. */
internal fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/** Reads the identifier at the scanner's position, advances past it and returns it. */
internal fun Scanner.readIdentifier(): String {
  val start = position
  while (position < length && text[position].isIdentifierPart()) position++
  return text.subSequence(start, position).toString()
}

/**
 * Scans a numeric literal starting at the scanner's position and advances past it.
 *
 * The scan is permissive on purpose: it takes the longest run that could belong to one literal, including a radix
 * prefix, digit separators, a fraction, an exponent and trailing suffix letters. Whether that run is a valid
 * literal is the compiler's judgement; for colouring, a malformed number is still a number.
 *
 * @param separators the digit separators the language allows.
 */
internal fun Scanner.scanNumber(separators: String) {
  val start = position
  fun digits(accept: (Char) -> Boolean) {
    // A separator belongs to the number only between digits, so a quote that starts a literal is left alone.
    while (position < length) {
      val current = text[position]
      val separates = current in separators && position + 1 < length && text[position + 1].isLetterOrDigit()
      if (!accept(current) && !separates) break
      position++
    }
  }
  fun exponent(first: Char, second: Char) {
    val marker = peek()
    if (marker != first && marker != second) return
    val signed = peek(1) == '+' || peek(1) == '-'
    if (peek(if (signed) 2 else 1).isAsciiDigit()) {
      position += if (signed) 2 else 1
      digits { it.isAsciiDigit() }
    }
  }
  if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
    position += 2
    digits { it.isHexDigit() }
    // A hexadecimal floating literal has a hexadecimal fraction and a binary exponent.
    if (peek() == '.' && peek(1).isHexDigit()) {
      position++
      digits { it.isHexDigit() }
    }
    exponent('p', 'P')
  } else if (peek() == '0' && peek(1) in "bBoO") {
    position += 2
    digits { it.isAsciiDigit() }
  } else {
    digits { it.isAsciiDigit() }
    // A dot belongs to the number only when a digit follows, so `1.toString()` and `1..2` keep their dots.
    if (peek() == '.' && peek(1).isAsciiDigit()) {
      position++
      digits { it.isAsciiDigit() }
    }
    exponent('e', 'E')
  }
  // Suffix letters, and any letters or digits glued to the number, belong to the same literal: a malformed
  // number is one token rather than a number followed by an identifier.
  while (position < length && text[position].isIdentifierPart()) position++
  if (position == start) position++
}

/**
 * Emits the operator or punctuation sign at the scanner's position and advances past it.
 *
 * [operators] must list longer signs before their prefixes. A character that is in neither list is emitted as
 * [TokenKind.INVALID] and consumed, so scanning always makes progress.
 */
internal fun Scanner.emitSign(operators: List<String>, punctuation: String) {
  val start = position
  for (operator in operators) {
    if (lookingAt(operator)) {
      position += operator.length
      emit(TokenKind.OPERATOR, start, position)
      return
    }
  }
  val current = text[position]
  // A surrogate pair is one character on screen and must not be split between two tokens.
  position += if (current.isHighSurrogate() && position + 1 < length && text[position + 1].isLowSurrogate()) 2 else 1
  emit(if (current in punctuation) TokenKind.PUNCTUATION else TokenKind.INVALID, start, position)
}

/**
 * Scans the body of a quoted literal that ends at [terminator] and emits it.
 *
 * The scanner must stand just after the opening delimiter, whose offset is [start]. The body is emitted as
 * [TokenKind.STRING] with each backslash escape as [TokenKind.STRING_ESCAPE]. When [multiLine] is false the literal
 * also ends, unterminated, at the end of the line; an unterminated literal is still emitted as a string, because
 * that is what is being typed.
 *
 * @param onInterpolation called at a character that might start an embedded expression; it returns true after
 *   it has emitted the embedded tokens and advanced the scanner, or false to treat the character as text. The
 *   argument is the offset where the pending string text starts, which the callback must emit first.
 * @return true when the terminator was found.
 */
internal inline fun Scanner.scanQuoted(
  start: Int,
  terminator: String,
  multiLine: Boolean,
  escapes: Boolean,
  onInterpolation: (pendingStart: Int) -> Boolean = { false },
): Boolean {
  var pending = start
  while (!atEnd) {
    val current = text[position]
    if (lookingAt(terminator)) {
      position += terminator.length
      emit(TokenKind.STRING, pending, position)
      return true
    }
    if (!multiLine && Scanner.isLineBreak(current)) break
    if (escapes && current == '\\' && position + 1 < length && !Scanner.isLineBreak(text[position + 1])) {
      emit(TokenKind.STRING, pending, position)
      val escapeStart = position
      position += 2
      // A Unicode escape colours its hexadecimal digits too.
      if (text[escapeStart + 1] == 'u') {
        var digits = 0
        while (digits < 4 && position < length && text[position].isHexDigit()) {
          position++
          digits++
        }
      }
      emit(TokenKind.STRING_ESCAPE, escapeStart, position)
      pending = position
      continue
    }
    val before = position
    if (onInterpolation(pending)) {
      pending = position
      continue
    }
    position = before + 1
  }
  emit(TokenKind.STRING, pending, position)
  return false
}
