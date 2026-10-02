/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/**
 * Tokenizes Java source for colouring.
 *
 * Reserved words and the literals `true`, `false` and `null` are always keywords. The contextual words `var`,
 * `record`, `sealed`, `permits`, `yield` and `non-sealed` are keywords only where the neighbouring tokens show
 * the keyword use, so a variable called `record` or a method called `yield` stays a name.
 *
 * A text block is one string that spans lines. Three slashes start a Markdown documentation comment; a
 * block comment that opens with two asterisks is a classic one. `@interface` is a keyword; every other `@Name` is an annotation.
 */
object JavaTokenizer : Tokenizer {
  /** Reserved words and literal words. */
  val keywords: Set<String> =
    setOf(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
      "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "goto", "if",
      "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package", "private",
      "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
      "throw", "throws", "transient", "true", "try", "void", "volatile", "while",
    )

  /** Words that are keywords only in one grammatical position each. */
  val contextualKeywords: Set<String> = setOf("var", "record", "sealed", "permits", "yield", "non-sealed")

  internal val operators: List<String> =
    listOf(
      ">>>=",
      "<<=", ">>=", ">>>", "...",
      "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
      "<<", ">>",
      "=", "+", "-", "*", "/", "%", "<", ">", "!", "~", "?", "&", "|", "^", "@",
    )

  internal const val PUNCTUATION = "{}()[],:;."

  private const val TEXT_BLOCK = "\"\"\""

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
      current == '/' && peek(1) == '/' -> lineComment(documentationMarker = "///")
      current == '/' && peek(1) == '*' -> blockComment(nested = false)
      current == '"' ->
        if (lookingAt(TEXT_BLOCK)) {
          position += 3
          scanQuoted(start, TEXT_BLOCK, multiLine = true, escapes = true)
        } else {
          position++
          scanQuoted(start, "\"", multiLine = false, escapes = true)
        }
      current == '\'' -> {
        position++
        scanQuoted(start, "'", multiLine = false, escapes = true)
      }
      current == '@' && lookingAt("@interface") && !peek(10).isIdentifierPart() -> {
        position += 10
        emit(TokenKind.KEYWORD, start, position)
      }
      current == '@' && annotation() -> Unit
      lookingAt("non-sealed") && !peek(10).isIdentifierPart() -> {
        position += 10
        emit(TokenKind.KEYWORD, start, position)
      }
      current.isIdentifierStart() || current == '$' -> {
        val word = readDollarIdentifier()
        emit(if (isKeyword(word, start)) TokenKind.KEYWORD else TokenKind.IDENTIFIER, start, position)
      }
      current.isAsciiDigit() || (current == '.' && peek(1).isAsciiDigit() && lastToken?.end != position) -> {
        scanNumber("_")
        emit(TokenKind.NUMBER, start, position)
      }
      else -> emitSign(operators, PUNCTUATION)
    }
  }

  private fun Scanner.isKeyword(word: String, start: Int): Boolean {
    if (word in keywords) return true
    if (word !in contextualKeywords) return false
    val previous = lastToken
    if (previous != null && previous.kind == TokenKind.PUNCTUATION && text[previous.end - 1] == '.') return false
    val next = nextSignificant(position)
    val nextCharacter = if (next < length) text[next] else Scanner.NUL
    val namesFollow = nextCharacter.isIdentifierStart() || nextCharacter == '$'
    return when (word) {
      // `var name`, `record Name(`, `sealed class`: a declaration follows.
      "var", "record", "sealed" -> namesFollow && wordAt(next) != "instanceof"
      "permits" -> namesFollow && previous != null && previous.kind != TokenKind.KEYWORD
      // `yield value;` is a statement; `yield = 1`, `yield.x` and `yield(1)` use the name.
      "yield" -> startsLine(start) && nextCharacter !in "=.([,;)" && nextCharacter != Scanner.NUL
      else -> false
    }
  }
}
