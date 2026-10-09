/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * Tokenizes Visual X# source for colouring.
 *
 * The lexical rules mirror the compiler's lexer where colouring depends on them:
 *
 * - `--` starts a comment that runs to the end of the line wherever it stands outside a string; the language has
 *   no decrement operator, so `value--` is a name followed by a comment;
 * - `--[[` and `--[=[` start a long comment that ends at the matching `]]` or `]=]`, at any nesting level of `=`;
 * - a comment whose text starts with `|` or `!` is a documentation comment;
 * - `[[` and `[=[` start a raw string with the same long-bracket rule, in which nothing is an escape;
 * - a quoted string may continue over line breaks and has backslash escapes;
 * - `'` separates digit groups inside a number and otherwise starts a character literal;
 * - `::` is one sign, the method reference of `Type::Method`, and not two colons.
 */
object VisualXSharpTokenizer : Tokenizer {
  /** The reserved words of the language, as the compiler's lexer lists them. */
  val keywords: Set<String> =
    setOf(
      "and", "auto", "bool", "break", "byte", "char", "class", "continue", "do", "double", "else", "enum", "false",
      "final", "float", "for", "guard", "if", "int", "internal", "is", "lfloat", "long", "longint", "match",
      "namespace", "not", "null", "or", "private", "protected", "public", "return", "sfloat", "static", "template",
      "true", "typename", "ubyte", "uint", "ulong", "ulongint", "unit", "ushort", "void", "while",
    )

  /** Longer signs come before their prefixes so the longest one wins. */
  private val operators: List<String> =
    listOf(
      "...", "<<=", ">>=", "**=", "//=", "??=",
      "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "??", "?:", "::", "==", "\\=", "<=", ">=", "&&", "||", "//", "->",
      "**", "++",
      "=", "+", "-", "*", "/", "%", "&", "|", "^", "<", ">", "!", "?", "\\",
    )

  private const val PUNCTUATION = "{}(),:;.[]"

  override fun tokenize(text: CharSequence): List<Token> {
    val scanner = Scanner(text)
    while (true) {
      scanner.skipWhitespace()
      if (scanner.atEnd) break
      scanner.step()
    }
    return scanner.result()
  }

  private fun Scanner.step() {
    val start = position
    val current = peek()
    when {
      current == '-' && peek(1) == '-' -> comment()
      current == '[' && longBracketLevel(position) >= 0 -> {
        val level = longBracketLevel(position)
        position += level + 2
        skipLongBracketBody(level)
        emit(TokenKind.STRING, start, position)
      }
      current.isIdentifierStart() -> {
        val word = readIdentifier()
        emit(if (word in keywords) TokenKind.KEYWORD else TokenKind.IDENTIFIER, start, position)
      }
      current.isAsciiDigit() -> {
        scanNumber("'_")
        emit(TokenKind.NUMBER, start, position)
      }
      current == '"' -> {
        position++
        scanQuoted(start, "\"", multiLine = true, escapes = true)
      }
      current == '\'' -> {
        position++
        scanQuoted(start, "'", multiLine = false, escapes = true)
      }
      else -> emitSign(operators, PUNCTUATION)
    }
  }

  private fun Scanner.comment() {
    val start = position
    position += 2
    val level = longBracketLevel(position)
    if (level >= 0) {
      position += level + 2
      val documentation = peek() == '|' || peek() == '!'
      skipLongBracketBody(level)
      emit(if (documentation) TokenKind.DOC_COMMENT else TokenKind.COMMENT, start, position)
      return
    }
    val documentation = peek() == '|' || peek() == '!'
    skipToLineEnd()
    emit(if (documentation) TokenKind.DOC_COMMENT else TokenKind.COMMENT, start, position)
  }

  /** The number of `=` signs of a long-bracket opener at [offset], or -1 when there is no opener. */
  private fun Scanner.longBracketLevel(offset: Int): Int {
    if (offset >= length || text[offset] != '[') return -1
    var index = offset + 1
    while (index < length && text[index] == '=') index++
    return if (index < length && text[index] == '[') index - offset - 1 else -1
  }

  /** Advances past the body and the closer of a long bracket of [level], or to the end when it is unterminated. */
  private fun Scanner.skipLongBracketBody(level: Int) {
    while (position < length) {
      if (text[position] == ']') {
        var index = position + 1
        var equals = 0
        while (index < length && text[index] == '=' && equals < level) {
          index++
          equals++
        }
        if (equals == level && index < length && text[index] == ']') {
          position = index + 1
          return
        }
      }
      position++
    }
  }
}
