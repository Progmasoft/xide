/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * Tokenizes Groovy source, including Gradle build scripts, for colouring.
 *
 * Groovy has more string forms than its relatives, and they differ in what they embed:
 *
 * - `'...'` and `'''...'''` are plain strings with backslash escapes;
 * - `"..."` and `"""..."""` also embed `$name`, `$name.property` and `${expression}`;
 * - `/.../` is a slashy string, which embeds the same way and escapes only the slash;
 * - `$/.../$` is a dollar-slashy string, coloured as plain text.
 *
 * A slash is ambiguous between division and a slashy string. It starts a string only where a value cannot be
 * divided — at the start of the text, or after an operator, a keyword or an opening sign — and only when a
 * closing slash follows on the same line. Everything else is division.
 */
object GroovyTokenizer : Tokenizer {
  /** Reserved words and literal words, as Groovy adds them to Java's. */
  val keywords: Set<String> = JavaTokenizer.keywords + setOf("as", "def", "in", "trait", "var")

  private val operators: List<String> =
    listOf(
      ">>>=", "<=>", "==~", "===", "!==", "**=", "..<", "<<=", ">>=", ">>>", "...", "?.", "?:", "?=", "=~", "*.",
      "..", "**",
    ) + JavaTokenizer.operators

  private const val TRIPLE_SINGLE = "'''"
  private const val TRIPLE_DOUBLE = "\"\"\""

  override fun tokenize(text: CharSequence): List<Token> {
    val scanner = Scanner(text)
    if (scanner.lookingAt("#!")) scanner.lineComment()
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
      current == '/' && peek(1) == '/' -> lineComment()
      current == '/' && peek(1) == '*' -> blockComment(nested = false)
      current == '/' && startsSlashyString() -> {
        position++
        scanSlashy(start, depth)
      }
      current == '$' && peek(1) == '/' -> {
        position += 2
        scanQuoted(start, "/$", multiLine = true, escapes = false)
      }
      current == '\'' ->
        if (lookingAt(TRIPLE_SINGLE)) {
          position += 3
          scanQuoted(start, TRIPLE_SINGLE, multiLine = true, escapes = true)
        } else {
          position++
          scanQuoted(start, "'", multiLine = false, escapes = true)
        }
      current == '"' ->
        if (lookingAt(TRIPLE_DOUBLE)) {
          position += 3
          scanQuoted(start, TRIPLE_DOUBLE, multiLine = true, escapes = true) { pending ->
            dollarInterpolation(pending, depth, dotted = true) { step(it) }
          }
        } else {
          position++
          scanQuoted(start, "\"", multiLine = false, escapes = true) { pending ->
            dollarInterpolation(pending, depth, dotted = true) { step(it) }
          }
        }
      current == '@' && lookingAt("@interface") && !peek(10).isIdentifierPart() -> {
        position += 10
        emit(TokenKind.KEYWORD, start, position)
      }
      current == '@' && annotation() -> Unit
      current.isIdentifierStart() || current == '$' -> {
        val word = readDollarIdentifier()
        emit(if (isKeyword(word)) TokenKind.KEYWORD else TokenKind.IDENTIFIER, start, position)
      }
      current.isAsciiDigit() -> {
        scanNumber("_")
        emit(TokenKind.NUMBER, start, position)
      }
      else -> emitSign(operators, JavaTokenizer.PUNCTUATION)
    }
  }

  /** A keyword after a member selector is a property or map key, as in `task.default`. */
  private fun Scanner.isKeyword(word: String): Boolean {
    if (word !in keywords) return false
    val previous = lastToken ?: return true
    val sign = lastText().toString()
    return !((previous.kind == TokenKind.PUNCTUATION && sign == ".") || sign == "?." || sign == "*.")
  }

  private fun Scanner.startsSlashyString(): Boolean {
    val previous = lastToken
    val valueBefore =
      previous != null &&
        when (previous.kind) {
          TokenKind.IDENTIFIER, TokenKind.NUMBER, TokenKind.STRING, TokenKind.INTERPOLATION -> true
          TokenKind.KEYWORD -> lastText().toString() in setOf("this", "super", "true", "false", "null")
          TokenKind.PUNCTUATION -> text[previous.end - 1] == ')' || text[previous.end - 1] == ']' ||
            text[previous.end - 1] == '}'
          TokenKind.OPERATOR -> lastText().toString() == "++" || lastText().toString() == "--"
          else -> false
        }
    if (valueBefore) return false
    // `/=` after nothing dividable cannot be an assignment, but an empty `//` never reaches here.
    var index = position + 1
    while (index < length && !Scanner.isLineBreak(text[index])) {
      if (text[index] == '\\' && index + 1 < length) {
        index += 2
        continue
      }
      if (text[index] == '/') return true
      index++
    }
    return false
  }

  /** Scans the body of a slashy string; only `\/` is an escape in it. */
  private fun Scanner.scanSlashy(start: Int, depth: Int) {
    scanQuoted(start, "/", multiLine = true, escapes = false) { pending ->
      if (peek() == '\\' && peek(1) == '/') {
        emit(TokenKind.STRING, pending, position)
        emit(TokenKind.STRING_ESCAPE, position, position + 2)
        position += 2
        true
      } else {
        dollarInterpolation(pending, depth, dotted = true) { step(it) }
      }
    }
  }
}
