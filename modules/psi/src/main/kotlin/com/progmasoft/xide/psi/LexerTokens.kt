/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import com.progmasoft.xide.syntax.Token
import com.progmasoft.xide.syntax.TokenKind
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType

/**
 * One token as an IntelliJ lexer reports it.
 *
 * @property type the lexer's element type.
 * @property start the offset of the first character.
 * @property end the offset just after the last character; always greater than [start].
 */
internal class Lexeme(val type: IElementType, val start: Int, val end: Int)

/** Runs [lexer] over [text] and returns every token that is not empty, whitespace included. */
internal fun lexemes(lexer: Lexer, text: CharSequence): List<Lexeme> {
  val result = ArrayList<Lexeme>()
  lexer.start(text)
  while (true) {
    val type = lexer.tokenType ?: break
    val start = lexer.tokenStart
    val end = lexer.tokenEnd
    if (end > start) result += Lexeme(type, start, end)
    lexer.advance()
  }
  return result
}

/**
 * Collects the coloured tokens of one text and keeps them within the tokenizer contract.
 *
 * A lexer's tokens do not map one to one onto colours. A string arrives as an opening quote, its content and a
 * closing quote; a comment or a raw string spans lines; and a lexer reports whitespace, which a colour token
 * must not consist of. The sink records text-like kinds the way the other tokenizers of Xide do: line by line,
 * without the whitespace at either edge of a line, joining a piece to the token it directly continues. At the
 * end it makes sure that no visible character was left without a token.
 */
internal class TokenSink(private val text: CharSequence) {
  private val tokens = ArrayList<Token>()

  /**
   * A text-like stretch that is still growing: the lexer pieces of one literal or comment, which are recorded
   * together once the next piece does not continue them.
   */
  private var pendingKind: TokenKind? = null
  private var pendingStart = 0
  private var pendingEnd = 0

  /** The offset up to which the text has been recorded so far. */
  val coveredUntil: Int
    get() = if (pendingKind != null) pendingEnd else tokens.lastOrNull()?.end ?: 0

  /** The most recent token, or null before the first one. */
  val last: Token?
    get() {
      flush()
      return tokens.lastOrNull()
    }

  /** The text of the most recent token, or an empty text before the first one. */
  fun lastText(): CharSequence = last?.let { text.subSequence(it.start, it.end) } ?: ""

  /**
   * Records [start] until [end] as [kind].
   *
   * A range that is empty or lies before the last token is ignored. Text-like kinds, which may contain
   * whitespace and span lines, are recorded line by line without the whitespace at either edge of a line, so no
   * token is ever whitespace only and a blank line inside a comment or a raw string belongs to no token. A piece
   * that directly continues the previous token of the same kind extends it instead of starting a new one.
   */
  fun add(kind: TokenKind, start: Int, end: Int) {
    if (kind in TEXT_KINDS) {
      // The pieces of one literal, such as its delimiters and its content, are recorded as one stretch, so
      // that whitespace between them is inside the token and not at an edge that would be trimmed away.
      if (pendingKind == kind && pendingEnd == start && end > start) {
        pendingEnd = minOf(end, text.length)
        return
      }
      flush()
      val from = maxOf(start, tokens.lastOrNull()?.end ?: 0)
      val to = minOf(end, text.length)
      if (to <= from) return
      pendingKind = kind
      pendingStart = from
      pendingEnd = to
      return
    }
    flush()
    val from = maxOf(start, tokens.lastOrNull()?.end ?: 0)
    val to = minOf(end, text.length)
    if (to <= from) return
    run {
      val previous = tokens.lastOrNull()
      // A run of embedded names, as in `$a.b`, is a single coloured stretch.
      if (kind == TokenKind.INTERPOLATION && previous != null && previous.kind == kind && previous.end == from) {
        tokens[tokens.size - 1] = Token(kind, previous.start, to)
      } else if (!isBlank(from, to)) {
        tokens += Token(kind, from, to)
      }
    }
  }

  /** Records the pending text-like stretch line by line. */
  private fun flush() {
    val kind = pendingKind ?: return
    pendingKind = null
    val to = pendingEnd
    var lineStart = pendingStart
    while (lineStart < to) {
      var lineEnd = lineStart
      while (lineEnd < to && !isLineBreak(text[lineEnd])) lineEnd++
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
      while (lineStart < to && isLineBreak(text[lineStart])) lineStart++
    }
  }

  /**
   * Extends the most recent token to [end] when [start] directly continues it and the joined text is one of
   * [signs]; returns whether it did.
   *
   * Some lexers report a compound sign in pieces and leave joining them to the parser: `>>>=` as four tokens,
   * `?.` as two. For colouring, and for the rules that look at the previous sign, the compound sign is one token.
   */
  fun joinSign(start: Int, end: Int, signs: Set<String>): Boolean {
    val previous = last ?: return false
    if (previous.kind != TokenKind.OPERATOR || previous.end != start) return false
    if (text.subSequence(previous.start, end).toString() !in signs) return false
    tokens[tokens.size - 1] = Token(TokenKind.OPERATOR, previous.start, end)
    return true
  }

  /**
   * Adds a quoted literal, splitting off each backslash escape as [TokenKind.STRING_ESCAPE].
   *
   * An escape is the backslash and the character after it; a Unicode escape also takes up to four hexadecimal
   * digits. A backslash before a line break, or at the very end, is ordinary string text.
   */
  fun addQuoted(start: Int, end: Int) {
    var pending = start
    var position = start
    while (position < end) {
      if (text[position] == '\\' && position + 1 < end && !isLineBreak(text[position + 1])) {
        add(TokenKind.STRING, pending, position)
        val escapeStart = position
        position += 2
        if (text[escapeStart + 1] == 'u') {
          var digits = 0
          while (digits < 4 && position < end && text[position].isHexDigit()) {
            position++
            digits++
          }
        }
        add(TokenKind.STRING_ESCAPE, escapeStart, position)
        pending = position
      } else {
        position++
      }
    }
    add(TokenKind.STRING, pending, end)
  }

  /**
   * The tokens in order, with every visible character covered.
   *
   * A stretch that no token covers is added as a plain name. That does not happen for the lexers in use; the
   * fallback keeps the contract even if a lexer one day reports a token this module does not expect.
   */
  fun result(): List<Token> {
    flush()
    val complete = ArrayList<Token>(tokens.size)
    var position = 0
    for (token in tokens) {
      fill(complete, position, token.start)
      complete += token
      position = token.end
    }
    fill(complete, position, text.length)
    return complete
  }

  private fun fill(into: MutableList<Token>, from: Int, to: Int) {
    var index = from
    while (index < to) {
      if (text[index].isWhitespace()) {
        index++
        continue
      }
      val start = index
      while (index < to && !text[index].isWhitespace()) index++
      into += Token(TokenKind.IDENTIFIER, start, index)
    }
  }

  private fun isBlank(from: Int, to: Int): Boolean {
    for (index in from until to) {
      if (!text[index].isWhitespace()) return false
    }
    return true
  }

  private companion object {
    /** The kinds that may contain whitespace and span lines. */
    val TEXT_KINDS = setOf(TokenKind.STRING, TokenKind.COMMENT, TokenKind.DOC_COMMENT, TokenKind.INVALID)
  }
}

/**
 * Tokenizes [text] with [body] and never fails.
 *
 * The editor colours whatever is being typed, so a tokenizer has to return a result for any text. The IntelliJ
 * lexers are total as well; should one of them throw on some input, the text is returned uncoloured rather than
 * taking the editor down with it.
 */
internal inline fun tokenizeSafely(text: CharSequence, body: (TokenSink) -> Unit): List<Token> {
  val sink = TokenSink(text)
  return try {
    body(sink)
    sink.result()
  } catch (_: RuntimeException) {
    TokenSink(text).result()
  }
}

/** Whether a character may start a name. */
internal fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter()

/** Whether a character may continue a name. */
internal fun Char.isIdentifierPart(): Boolean = this == '_' || isLetterOrDigit()

/** Whether a character is a hexadecimal digit. */
internal fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/** Whether a character ends a line. */
internal fun isLineBreak(character: Char): Boolean = character == '\n' || character == '\r'

/** The character at [index], or NUL beyond the end. */
internal fun CharSequence.at(index: Int): Char = if (index in 0 until length) this[index] else '\u0000'

/** The index of the next character at or after [from] that is not whitespace, or the length. */
internal fun CharSequence.nextSignificant(from: Int): Int {
  var index = from
  while (index < length && this[index].isWhitespace()) index++
  return index
}

/** The word that starts at [from], or an empty string when no name starts there. */
internal fun CharSequence.wordAt(from: Int): String {
  if (!at(from).isIdentifierStart()) return ""
  var index = from
  while (index < length && this[index].isIdentifierPart()) index++
  return subSequence(from, index).toString()
}

/** Whether only blanks stand between the start of the line and [start]. */
internal fun CharSequence.startsLine(start: Int): Boolean {
  var index = start - 1
  while (index >= 0 && (this[index] == ' ' || this[index] == '\t')) index--
  return index < 0 || isLineBreak(this[index])
}

/**
 * The end of `@Name`, `@qualified.Name` and, with [useSiteTarget], `@target:Name` that starts at [start].
 *
 * Returns [start] when no name follows the sign.
 */
internal fun CharSequence.annotationEnd(start: Int, useSiteTarget: Boolean): Int {
  if (at(start) != '@' || !at(start + 1).isIdentifierStart()) return start
  var index = start + 1
  while (at(index).isIdentifierPart()) index++
  if (useSiteTarget && at(index) == ':' && at(index + 1).isIdentifierStart()) {
    index++
    while (at(index).isIdentifierPart()) index++
  }
  while (at(index) == '.' && at(index + 1).isIdentifierStart()) {
    index++
    while (at(index).isIdentifierPart()) index++
  }
  return index
}
