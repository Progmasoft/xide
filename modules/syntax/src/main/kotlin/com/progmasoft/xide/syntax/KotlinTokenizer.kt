/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * Tokenizes Kotlin source for colouring.
 *
 * Hard keywords are always keywords. Kotlin's soft and modifier keywords are ordinary names except in the
 * positions where the grammar reads them as keywords; without a parser that position is recognized from the
 * neighbouring tokens. The rules are deliberately narrow: a word such as `value`, `data` or `open` used as a
 * name stays a name, at the price of an unusual modifier position occasionally staying uncoloured. Colouring a
 * variable as a keyword is the worse mistake of the two.
 *
 * Labels (`loop@`, `return@loop`) share [TokenKind.ANNOTATION] with annotations, including use-site targets such
 * as `@file:JvmName`. Multi-dollar string prefixes are part of the string; such a string is not scanned for
 * embedded expressions.
 */
object KotlinTokenizer : Tokenizer {
  /** Words that are keywords in every position. */
  val hardKeywords: Set<String> =
    setOf(
      "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
      "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
      "var", "when", "while",
    )

  /** Words that are keywords only in front of a declaration or another modifier. */
  val modifierKeywords: Set<String> =
    setOf(
      "abstract", "actual", "annotation", "companion", "const", "crossinline", "data", "enum", "expect", "external",
      "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override",
      "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "value", "vararg",
    )

  /** Words that are keywords only in one grammatical position each. */
  val softKeywords: Set<String> =
    setOf("by", "catch", "constructor", "finally", "get", "import", "init", "set", "where")

  /** Hard keywords that may follow a name as an infix word, so the name before them is not a modifier. */
  private val infixWords = setOf("as", "in", "is", "else")

  private val operators: List<String> =
    listOf(
      "===", "!==", "..<",
      "?.", "?:", "::", "..", "->", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "!!",
      "=", "+", "-", "*", "/", "%", "<", ">", "!", "?", "&", "|", "^", "~", "@",
    )

  private const val PUNCTUATION = "{}()[],:;."

  override fun tokenize(text: CharSequence): List<Token> {
    val scanner = Scanner(text)
    // A script may start with an interpreter line.
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
      current == '/' && peek(1) == '*' -> blockComment(nested = true)
      current == '"' -> string(depth)
      current == '$' && dollarPrefixedString() -> Unit
      current == '\'' -> {
        position++
        scanQuoted(start, "'", multiLine = false, escapes = true)
      }
      current == '`' -> {
        position++
        while (position < length && text[position] != '`' && !Scanner.isLineBreak(text[position])) position++
        if (peek() == '`') position++
        emit(TokenKind.IDENTIFIER, start, position)
      }
      current == '@' && annotationOrLabel() -> Unit
      current.isIdentifierStart() -> word(start)
      current.isAsciiDigit() || (current == '.' && peek(1).isAsciiDigit() && lastToken?.end != position) -> {
        scanNumber("_")
        emit(TokenKind.NUMBER, start, position)
      }
      else -> emitSign(operators, PUNCTUATION)
    }
  }

  private fun Scanner.word(start: Int) {
    val word = readIdentifier()
    if (isKeyword(word, start)) {
      emit(TokenKind.KEYWORD, start, position)
    } else if (peek() == '@' && !peek(1).isIdentifierStart()) {
      // `name@` declares a label.
      position++
      emit(TokenKind.ANNOTATION, start, position)
    } else {
      emit(TokenKind.IDENTIFIER, start, position)
    }
  }

  private fun Scanner.string(depth: Int) {
    val start = position
    if (lookingAt(RAW_QUOTE)) {
      position += 3
      val closed =
        scanQuoted(start, RAW_QUOTE, multiLine = true, escapes = false) { pending ->
          dollarInterpolation(pending, depth) { step(it) }
        }
      if (closed) {
        // Quotes directly before the closing delimiter are content, so the delimiter is the last three.
        val extra = position
        while (peek() == '"') position++
        emit(TokenKind.STRING, extra, position)
      }
    } else {
      position++
      scanQuoted(start, "\"", multiLine = false, escapes = true) { pending ->
        dollarInterpolation(pending, depth) { step(it) }
      }
    }
  }

  /** Scans `$$"..."` and `$$"""..."""`; returns false when the dollars do not prefix a string. */
  private fun Scanner.dollarPrefixedString(): Boolean {
    val start = position
    var index = position
    while (index < length && text[index] == '$') index++
    if (index - start < 2 || index >= length || text[index] != '"') return false
    position = index
    if (lookingAt(RAW_QUOTE)) {
      position += 3
      if (scanQuoted(start, RAW_QUOTE, multiLine = true, escapes = false)) {
        val extra = position
        while (peek() == '"') position++
        emit(TokenKind.STRING, extra, position)
      }
    } else {
      position++
      scanQuoted(start, "\"", multiLine = false, escapes = true)
    }
    return true
  }

  /** Scans `@Name`, `@target:Name`, `@qualified.Name` or `@label`; returns false for a bare `@`. */
  private fun Scanner.annotationOrLabel(): Boolean {
    if (!peek(1).isIdentifierStart()) return false
    val start = position
    position++
    readIdentifier()
    if (peek() == ':' && peek(1).isIdentifierStart()) {
      position++
      readIdentifier()
    }
    while (peek() == '.' && peek(1).isIdentifierStart()) {
      position++
      readIdentifier()
    }
    emit(TokenKind.ANNOTATION, start, position)
    return true
  }

  /** Whether [word], which starts at [start] and ends at the scanner's position, acts as a keyword there. */
  private fun Scanner.isKeyword(word: String, start: Int): Boolean {
    if (word in hardKeywords) return true
    val modifier = word in modifierKeywords
    if (!modifier && word !in softKeywords) return false
    val previous = lastToken
    val previousEnd = if (previous == null) Scanner.NUL else text[previous.end - 1]
    // A member name after `.`, `?.` or `::` is never a keyword.
    if (previous != null) {
      val sign = lastText().toString()
      if ((previous.kind == TokenKind.PUNCTUATION && sign == ".") || sign == "?." || sign == "::") return false
    }
    val next = nextSignificant(position)
    val nextCharacter = if (next < length) text[next] else Scanner.NUL
    val nextWord = if (nextCharacter.isIdentifierStart()) wordAt(next) else ""
    val followsValue =
      previous != null &&
        (previous.kind == TokenKind.IDENTIFIER || previousEnd == ')' || previousEnd == '>' || previousEnd == '?')
    val followsBlock = previous != null && previous.kind == TokenKind.PUNCTUATION && previousEnd == '}'
    return when (word) {
      "value" -> nextWord == "class"
      "data" -> nextWord == "class" || nextWord == "object"
      "suspend" -> nextCharacter == '(' || (nextWord.isNotEmpty() && nextWord !in infixWords)
      "by" -> followsValue && (nextWord.isNotEmpty() || nextCharacter == '{' || nextCharacter == '(')
      "where" -> followsValue && nextWord.isNotEmpty()
      "catch" -> followsBlock && nextCharacter == '('
      "finally" -> followsBlock && nextCharacter == '{'
      "constructor" -> nextCharacter == '('
      "init" -> nextCharacter == '{'
      "import" -> startsLine(start)
      "get" -> accessorPosition(start, previous) && nextCharacter == '(' && closesAt(next + 1)
      "set" -> accessorPosition(start, previous) && (nextCharacter == '(' || lineEndsBefore(next))
      else -> modifier && (nextCharacter == '@' || (nextWord.isNotEmpty() && nextWord !in infixWords))
    }
  }

  /** An accessor starts its line or follows its own modifiers. */
  private fun Scanner.accessorPosition(start: Int, previous: Token?): Boolean =
    startsLine(start) || (previous != null && previous.kind == TokenKind.KEYWORD && previous.end < start)

  private fun Scanner.closesAt(from: Int): Boolean {
    val index = nextSignificant(from)
    return index < length && text[index] == ')'
  }

  /** Whether nothing but blanks stands between the scanner's position and [next] on the current line. */
  private fun Scanner.lineEndsBefore(next: Int): Boolean {
    if (next >= length) return true
    for (index in position until next) {
      if (Scanner.isLineBreak(text[index])) return true
    }
    return false
  }

  private const val RAW_QUOTE = "\"\"\""
}
