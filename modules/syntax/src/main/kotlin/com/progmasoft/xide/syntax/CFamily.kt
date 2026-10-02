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
 * Scans a block comment at the scanner's position and emits it.
 *
 * A comment that opens with two asterisks and is not the empty comment is a documentation comment. With [nested]
 * an inner opener must be closed before the outer comment ends, as in Kotlin; without it the first closer ends the
 * comment, as in Java. An unterminated comment runs to the end of the text.
 */
internal fun Scanner.blockComment(nested: Boolean) {
  val start = position
  val documentation = lookingAt("/**") && peek(3) != '/'
  position += 2
  var depth = 1
  while (position < length && depth > 0) {
    if (lookingAt("*/")) {
      position += 2
      depth--
    } else if (nested && lookingAt("/*")) {
      position += 2
      depth++
    } else {
      position++
    }
  }
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
 * Handles `$name` and `${expression}` at the scanner's position inside a string.
 *
 * @param pendingStart where the string text that precedes the interpolation starts; it is emitted first.
 * @param depth the current nesting depth of embedded expressions.
 * @param dotted whether a directly embedded name may continue as a property path.
 * @param step tokenizes one token of the host language; it receives the depth to use inside the expression.
 * @return true when an interpolation was emitted and the scanner advanced past it.
 */
internal inline fun Scanner.dollarInterpolation(
  pendingStart: Int,
  depth: Int,
  dotted: Boolean = false,
  step: (Int) -> Unit,
): Boolean {
  if (peek() != '$') return false
  if (peek(1) == '{') {
    if (depth >= MAXIMUM_INTERPOLATION_DEPTH) return false
    emit(TokenKind.STRING, pendingStart, position)
    emit(TokenKind.INTERPOLATION, position, position + 2)
    position += 2
    embeddedExpression { step(depth + 1) }
    return true
  }
  if (peek(1).isIdentifierStart()) {
    emit(TokenKind.STRING, pendingStart, position)
    val start = position
    position++
    while (position < length && text[position].isIdentifierPart()) position++
    // Groovy also embeds a property path: `$user.name`.
    while (dotted && peek() == '.' && peek(1).isIdentifierStart()) {
      position++
      while (position < length && text[position].isIdentifierPart()) position++
    }
    emit(TokenKind.INTERPOLATION, start, position)
    return true
  }
  return false
}

/** Reads a name that may also contain `$`, as Java and Groovy allow, and advances past it. */
internal fun Scanner.readDollarIdentifier(): String {
  val start = position
  while (position < length && (text[position].isIdentifierPart() || text[position] == '$')) position++
  return text.subSequence(start, position).toString()
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

/** The word that starts at [from], or an empty string when no identifier starts there. */
internal fun Scanner.wordAt(from: Int): String {
  var index = from
  while (index < length && text[index].isIdentifierPart()) index++
  return text.subSequence(from, index).toString()
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
