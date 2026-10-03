/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import com.progmasoft.xide.syntax.Token
import com.progmasoft.xide.syntax.TokenKind
import com.progmasoft.xide.syntax.Tokenizer
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Colours Kotlin source with the lexer of the Kotlin compiler.
 *
 * The lexer decides where every token starts and ends: comments, the pieces of a string template, numbers,
 * hard keywords and signs. Two things a lexer cannot know are added here, from the neighbouring tokens.
 *
 * - **Soft and modifier keywords.** To the lexer `open`, `data` or `by` are names; the parser makes them
 *   keywords by position. Running the parser on every key press is too slow, so the position is recognized from
 *   the tokens around the word. The rules are deliberately narrow: a word such as `value`, `data` or `open` used
 *   as a name stays a name, at the price of an unusual modifier position occasionally staying uncoloured.
 *   Colouring a variable as a keyword is the worse mistake of the two.
 * - **Annotations and labels.** `@Name`, `@file:Name`, `name@` and `@name` are each one coloured stretch of
 *   kind [TokenKind.ANNOTATION].
 */
object KotlinLexerTokenizer : Tokenizer {
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

  private val punctuation =
    TokenSet.create(
      KtTokens.LBRACE, KtTokens.RBRACE, KtTokens.LPAR, KtTokens.RPAR, KtTokens.LBRACKET, KtTokens.RBRACKET,
      KtTokens.COMMA, KtTokens.COLON, KtTokens.SEMICOLON, KtTokens.DOT,
    )

  /** Signs the lexer reports in pieces. */
  private val compoundSigns = setOf("?.", "?:", "!!")

  private val stringPieces =
    TokenSet.create(
      KtTokens.OPEN_QUOTE, KtTokens.CLOSING_QUOTE, KtTokens.REGULAR_STRING_PART, KtTokens.INTERPOLATION_PREFIX,
    )

  /** Tokenizes a complete text; any text yields a result that obeys the [Tokenizer] contract. */
  override fun tokenize(text: CharSequence): List<Token> =
    tokenizeSafely(text) { sink ->
      val lexemes = lexemes(KotlinLexer(), text)
      // The name right after `$` in a string is part of the interpolation, not an ordinary name.
      var embeddedName = false
      for (lexeme in lexemes) {
        val type = lexeme.type
        val start = lexeme.start
        val end = lexeme.end
        // A lexeme inside an annotation or label that was already emitted as one stretch.
        if (start < sink.coveredUntil) continue
        val embedded = embeddedName
        embeddedName = false
        when {
          type == TokenType.WHITE_SPACE || type == KtTokens.DANGLING_NEWLINE -> Unit
          type == KtTokens.DOC_COMMENT -> sink.add(TokenKind.DOC_COMMENT, start, end)
          type == KtTokens.EOL_COMMENT || type == KtTokens.BLOCK_COMMENT || type == KtTokens.SHEBANG_COMMENT ->
            sink.add(TokenKind.COMMENT, start, end)
          type == KtTokens.INTEGER_LITERAL || type == KtTokens.FLOAT_LITERAL -> sink.add(TokenKind.NUMBER, start, end)
          type == KtTokens.CHARACTER_LITERAL -> sink.addQuoted(start, end)
          stringPieces.contains(type) -> sink.add(TokenKind.STRING, start, end)
          type == KtTokens.ESCAPE_SEQUENCE -> sink.add(TokenKind.STRING_ESCAPE, start, end)
          type == KtTokens.SHORT_TEMPLATE_ENTRY_START -> {
            sink.add(TokenKind.INTERPOLATION, start, end)
            embeddedName = true
          }
          type == KtTokens.LONG_TEMPLATE_ENTRY_START || type == KtTokens.LONG_TEMPLATE_ENTRY_END ->
            sink.add(TokenKind.INTERPOLATION, start, end)
          embedded -> sink.add(TokenKind.INTERPOLATION, start, end)
          type == KtTokens.AT -> {
            val annotationEnd = text.annotationEnd(start, useSiteTarget = true)
            if (annotationEnd > start) {
              sink.add(TokenKind.ANNOTATION, start, annotationEnd)
            } else {
              sink.add(TokenKind.OPERATOR, start, end)
            }
          }
          type == KtTokens.IDENTIFIER -> identifier(text, sink, start, end)
          type == KtTokens.FIELD_IDENTIFIER -> sink.add(TokenKind.IDENTIFIER, start, end)
          KtTokens.KEYWORDS.contains(type) -> sink.add(TokenKind.KEYWORD, start, end)
          // `#` is a token of the lexer but of no Kotlin construct.
          type == TokenType.BAD_CHARACTER || type == KtTokens.HASH -> sink.add(TokenKind.INVALID, start, end)
          // The lexer reports `?.`, `?:` and `!!` in two pieces each.
          sink.joinSign(start, end, compoundSigns) -> Unit
          punctuation.contains(type) -> sink.add(TokenKind.PUNCTUATION, start, end)
          else -> sink.add(TokenKind.OPERATOR, start, end)
        }
      }
    }

  private fun identifier(text: CharSequence, sink: TokenSink, start: Int, end: Int) {
    val word = text.subSequence(start, end).toString()
    if (isKeyword(text, sink, word, start, end)) {
      sink.add(TokenKind.KEYWORD, start, end)
    } else if (text.at(end) == '@' && !text.at(end + 1).isIdentifierStart()) {
      // `name@` declares a label.
      sink.add(TokenKind.ANNOTATION, start, end + 1)
    } else {
      sink.add(TokenKind.IDENTIFIER, start, end)
    }
  }

  /** Whether [word], which covers [start] until [end], acts as a keyword there. */
  private fun isKeyword(text: CharSequence, sink: TokenSink, word: String, start: Int, end: Int): Boolean {
    val modifier = word in modifierKeywords
    if (!modifier && word !in softKeywords) return false
    val previous = sink.last
    val previousEnd = if (previous == null) '\u0000' else text[previous.end - 1]
    // A member name after `.`, `?.` or `::` is never a keyword.
    if (previous != null) {
      val sign = sink.lastText().toString()
      if ((previous.kind == TokenKind.PUNCTUATION && sign == ".") || sign == "?." || sign == "::") return false
    }
    val next = text.nextSignificant(end)
    val nextCharacter = text.at(next)
    val nextWord = text.wordAt(next)
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
      "import" -> text.startsLine(start)
      "get" -> accessorPosition(text, start, previous) && nextCharacter == '(' && text.at(text.nextSignificant(next + 1)) == ')'
      "set" -> accessorPosition(text, start, previous) && (nextCharacter == '(' || lineEndsBefore(text, end, next))
      else -> modifier && (nextCharacter == '@' || (nextWord.isNotEmpty() && nextWord !in infixWords))
    }
  }

  /** An accessor starts its line or follows its own modifiers. */
  private fun accessorPosition(text: CharSequence, start: Int, previous: Token?): Boolean =
    text.startsLine(start) || (previous != null && previous.kind == TokenKind.KEYWORD && previous.end < start)

  /** Whether the line ends between [from] and [next]. */
  private fun lineEndsBefore(text: CharSequence, from: Int, next: Int): Boolean {
    if (next >= text.length) return true
    for (index in from until next) {
      if (isLineBreak(text[index])) return true
    }
    return false
  }
}
