/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

/** How deep embedded expressions in strings are tokenized before the rest is treated as string text. */
internal const val MAXIMUM_INTERPOLATION_DEPTH: Int = 16

/**
 * Scans a `//` comment at the scanner's position and emits it.
 *
 * The comment is a documentation comment when it starts with [documentationMarker], for example `///`.
 */
internal fun Scanner.lineComment(documentationMarker: String? = null) {
  val start = position
  val documentation = documentationMarker != null && lookingAt(documentationMarker)
  skipToLineEnd()
  emit(if (documentation) TokenKind.DOC_COMMENT else TokenKind.COMMENT, start, position)
}

/**
 * Tokenizes the expression inside `${ ... }` and emits its closing brace as an interpolation marker.
 *
 * The scanner must stand just after the opening marker. Braces inside the expression are counted so that a
 * lambda or a nested block does not end the expression early. When the text ends first, what was scanned is
 * simply what there is.
 *
 * @param multiLine whether the expression may continue on a following line.
 * @param step tokenizes one token of the host language at the scanner's position.
 */
internal inline fun Scanner.embeddedExpression(multiLine: Boolean = true, step: () -> Unit) {
  var depth = 0
  while (true) {
    while (position < length && text[position].isWhitespace()) {
      // An expression inside a single-line literal cannot outlive its line.
      if (!multiLine && Scanner.isLineBreak(text[position])) return
      position++
    }
    if (atEnd) return
    val current = peek()
    if (current == '}' && depth == 0) {
      emit(TokenKind.INTERPOLATION, position, position + 1)
      position++
      return
    }
    if (current == '{') depth++ else if (current == '}') depth--
    step()
  }
}

/**
 * Scans `@Name` or `@qualified.Name` at the scanner's position and emits it as an annotation.
 *
 * @return false, without advancing, when no name follows the sign.
 */
internal fun Scanner.annotation(): Boolean {
  if (peek() != '@' || !peek(1).isIdentifierStart()) return false
  val start = position
  position++
  while (position < length && text[position].isIdentifierPart()) position++
  while (peek() == '.' && peek(1).isIdentifierStart()) {
    position++
    while (position < length && text[position].isIdentifierPart()) position++
  }
  emit(TokenKind.ANNOTATION, start, position)
  return true
}

/** The index of the next character at or after [from] that is not whitespace, or the text length. */
internal fun Scanner.nextSignificant(from: Int): Int {
  var index = from
  while (index < length && text[index].isWhitespace()) index++
  return index
}

/** Whether the most recent token is the first token on its line, counting from [start] backwards. */
internal fun Scanner.startsLine(start: Int): Boolean {
  var index = start - 1
  while (index >= 0 && (text[index] == ' ' || text[index] == '\t')) index--
  return index < 0 || Scanner.isLineBreak(text[index])
}
