/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import com.progmasoft.xide.syntax.Token
import com.progmasoft.xide.syntax.TokenKind
import com.progmasoft.xide.syntax.Tokenizer
import org.jetbrains.kotlin.com.intellij.lang.java.lexer.JavaLexer
import org.jetbrains.kotlin.com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.com.intellij.psi.JavaTokenType
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.ElementType
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.JavaDocElementType
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet

/**
 * Colours Java source with the Java lexer of IntelliJ.
 *
 * The lexer decides where every token starts and ends. Reserved words and the literals `true`, `false` and
 * `null` are always keywords. The contextual words `var`, `record`, `sealed`, `permits` and `yield` are names
 * to the lexer and keywords only where the neighbouring tokens show the keyword use, so a variable called
 * `record` or a method called `yield` stays a name; `non-sealed` is a keyword as a whole.
 *
 * A text block is one string that spans lines. Three slashes start a Markdown documentation comment; a block
 * comment that opens with two asterisks is a classic one. `@interface` is a keyword; every other `@Name` is an
 * annotation.
 */
object JavaLexerTokenizer : Tokenizer {
  /** Words that are keywords only in one grammatical position each. */
  val contextualKeywords: Set<String> = setOf("var", "record", "sealed", "permits", "yield", "non-sealed")

  private const val NON_SEALED = "non-sealed"

  /** Signs the lexer reports as a run of `>` and `=` tokens. */
  private val shiftSigns = setOf(">>", ">>>", ">>=", ">>>=")

  private val punctuation =
    TokenSet.create(
      JavaTokenType.LPARENTH, JavaTokenType.RPARENTH, JavaTokenType.LBRACE, JavaTokenType.RBRACE,
      JavaTokenType.LBRACKET, JavaTokenType.RBRACKET, JavaTokenType.SEMICOLON, JavaTokenType.COMMA,
      JavaTokenType.DOT, JavaTokenType.COLON,
    )

  private val numbers =
    TokenSet.create(
      JavaTokenType.INTEGER_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.FLOAT_LITERAL,
      JavaTokenType.DOUBLE_LITERAL,
    )

  private val quoted =
    TokenSet.create(JavaTokenType.STRING_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL, JavaTokenType.CHARACTER_LITERAL)

  private val literalWords =
    TokenSet.create(JavaTokenType.TRUE_KEYWORD, JavaTokenType.FALSE_KEYWORD, JavaTokenType.NULL_KEYWORD)

  /** Tokenizes a complete text; any text yields a result that obeys the [Tokenizer] contract. */
  override fun tokenize(text: CharSequence): List<Token> =
    tokenizeSafely(text) { sink ->
      for (lexeme in lexemes(JavaLexer(LanguageLevel.HIGHEST), text)) {
        val type = lexeme.type
        val start = lexeme.start
        val end = lexeme.end
        // A lexeme inside `@interface`, an annotation or `non-sealed` that was already emitted as one stretch.
        if (start < sink.coveredUntil) continue
        when {
          type == TokenType.WHITE_SPACE -> Unit
          type == JavaDocElementType.DOC_COMMENT -> sink.add(TokenKind.DOC_COMMENT, start, end)
          type == JavaTokenType.END_OF_LINE_COMMENT ->
            sink.add(if (text.startsWith("///", start)) TokenKind.DOC_COMMENT else TokenKind.COMMENT, start, end)
          type == JavaTokenType.C_STYLE_COMMENT -> sink.add(TokenKind.COMMENT, start, end)
          quoted.contains(type) -> sink.addQuoted(start, end)
          numbers.contains(type) -> sink.add(TokenKind.NUMBER, start, end)
          type == JavaTokenType.AT -> at(text, sink, start, end)
          type == JavaTokenType.IDENTIFIER -> identifier(text, sink, start, end)
          ElementType.KEYWORD_BIT_SET.contains(type) || literalWords.contains(type) ->
            sink.add(TokenKind.KEYWORD, start, end)
          type == TokenType.BAD_CHARACTER -> sink.add(TokenKind.INVALID, start, end)
          // The lexer reports a right shift as single `>` tokens, because `>>` also closes two type arguments.
          sink.joinSign(start, end, shiftSigns) -> Unit
          punctuation.contains(type) -> sink.add(TokenKind.PUNCTUATION, start, end)
          else -> sink.add(TokenKind.OPERATOR, start, end)
        }
      }
    }

  private fun at(text: CharSequence, sink: TokenSink, start: Int, end: Int) {
    if (text.startsWith("@interface", start) && !text.at(start + 10).isIdentifierPart()) {
      sink.add(TokenKind.KEYWORD, start, start + 10)
      return
    }
    val annotationEnd = text.annotationEnd(start, useSiteTarget = false)
    if (annotationEnd > start) {
      sink.add(TokenKind.ANNOTATION, start, annotationEnd)
    } else {
      sink.add(TokenKind.OPERATOR, start, end)
    }
  }

  private fun identifier(text: CharSequence, sink: TokenSink, start: Int, end: Int) {
    if (text.startsWith(NON_SEALED, start) && !text.at(start + NON_SEALED.length).isIdentifierPart() && end == start + 3) {
      sink.add(TokenKind.KEYWORD, start, start + NON_SEALED.length)
      return
    }
    val word = text.subSequence(start, end).toString()
    sink.add(if (isKeyword(text, sink, word, start, end)) TokenKind.KEYWORD else TokenKind.IDENTIFIER, start, end)
  }

  /** Whether the contextual word [word], which covers [start] until [end], acts as a keyword there. */
  private fun isKeyword(text: CharSequence, sink: TokenSink, word: String, start: Int, end: Int): Boolean {
    if (word !in contextualKeywords) return false
    val previous = sink.last
    if (previous != null && previous.kind == TokenKind.PUNCTUATION && text[previous.end - 1] == '.') return false
    val next = text.nextSignificant(end)
    val nextCharacter = text.at(next)
    val namesFollow = nextCharacter.isIdentifierStart() || nextCharacter == '$'
    return when (word) {
      // `var name`, `record Name(`, `sealed class`: a declaration follows.
      "var", "record", "sealed" -> namesFollow && text.wordAt(next) != "instanceof"
      "permits" -> namesFollow && previous != null && previous.kind != TokenKind.KEYWORD
      // `yield value;` is a statement; `yield = 1`, `yield.x` and `yield(1)` use the name.
      "yield" -> text.startsLine(start) && nextCharacter !in "=.([,;)" && nextCharacter != '\u0000'
      else -> false
    }
  }
}
