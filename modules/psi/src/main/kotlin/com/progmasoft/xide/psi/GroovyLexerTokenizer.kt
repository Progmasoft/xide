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
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes

/**
 * Colours Groovy source, including Gradle build scripts, with the Groovy lexer of IntelliJ.
 *
 * The lexer decides where every token starts and ends, including the hard cases of the language: which slash
 * starts a slashy string and which one divides, and where an expression embedded in a double-quoted string ends.
 *
 * Three things are decided here, from the neighbouring tokens:
 *
 * - a keyword after a member selector is a property or map key, as in `task.default`, and stays a name;
 * - the contextual words `var`, `val`, `record`, `sealed`, `permits` and `yield` are keywords only where a
 *   declaration or statement follows, and `module` is always a name, because all of them are common names in
 *   build scripts;
 * - `@interface` is a keyword and every other `@Name` is one annotation.
 */
object GroovyLexerTokenizer : Tokenizer {
  private val punctuation =
    TokenSet.create(
      GroovyTokenTypes.mLPAREN, GroovyTokenTypes.mRPAREN, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mRBRACK,
      GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY, GroovyTokenTypes.mSEMI, GroovyTokenTypes.mCOMMA,
      GroovyTokenTypes.mDOT, GroovyTokenTypes.mCOLON,
    )

  private val numbers =
    TokenSet.create(
      GroovyTokenTypes.mNUM_INT, GroovyTokenTypes.mNUM_LONG, GroovyTokenTypes.mNUM_BIG_INT,
      GroovyTokenTypes.mNUM_BIG_DECIMAL, GroovyTokenTypes.mNUM_FLOAT, GroovyTokenTypes.mNUM_DOUBLE,
    )

  private val comments =
    TokenSet.create(GroovyTokenTypes.mSL_COMMENT, GroovyTokenTypes.mML_COMMENT, GroovyTokenTypes.mSH_COMMENT)

  /** Whole literals with backslash escapes. */
  private val escapedStrings =
    TokenSet.create(
      GroovyElementTypes.STRING_SQ, GroovyElementTypes.STRING_TSQ, GroovyElementTypes.STRING_DQ,
      GroovyElementTypes.STRING_TDQ, GroovyTokenTypes.mGSTRING_CONTENT,
    )

  /** Delimiters and content in which a backslash escapes at most the delimiter. */
  private val plainStrings =
    TokenSet.create(
      GroovyTokenTypes.mGSTRING_BEGIN, GroovyTokenTypes.mGSTRING_END, GroovyTokenTypes.mREGEX_BEGIN,
      GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
      GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END,
    )

  /** Tokenizes a complete text; any text yields a result that obeys the [Tokenizer] contract. */
  override fun tokenize(text: CharSequence): List<Token> =
    tokenizeSafely(text) { sink ->
      // One entry per `${` that is still open: the number of ordinary braces opened inside it.
      val embeddedBraces = ArrayList<Int>()
      // After `$name` in a string, `.property` continues the embedded name.
      var embeddedName = false
      var afterDollar = false
      for (lexeme in lexemes(GroovyLexer(), text)) {
        val type = lexeme.type
        val start = lexeme.start
        val end = lexeme.end
        // A lexeme inside `@interface` or an annotation that was already emitted as one stretch.
        if (start < sink.coveredUntil) continue
        val dollarBefore = afterDollar
        afterDollar = false
        val continuesName = embeddedName && (type == GroovyTokenTypes.mDOT || type == GroovyTokenTypes.mIDENT)
        embeddedName = false
        when {
          type == TokenType.WHITE_SPACE || type == GroovyTokenTypes.mNLS -> Unit
          type == GroovyTokenTypes.mDOLLAR -> {
            sink.add(TokenKind.INTERPOLATION, start, end)
            afterDollar = true
          }
          dollarBefore && type == GroovyTokenTypes.mLCURLY -> {
            sink.add(TokenKind.INTERPOLATION, start, end)
            embeddedBraces += 0
          }
          dollarBefore || continuesName -> {
            sink.add(TokenKind.INTERPOLATION, start, end)
            embeddedName = true
          }
          type == GroovyTokenTypes.mLCURLY && embeddedBraces.isNotEmpty() -> {
            embeddedBraces[embeddedBraces.size - 1] = embeddedBraces.last() + 1
            sink.add(TokenKind.PUNCTUATION, start, end)
          }
          type == GroovyTokenTypes.mRCURLY && embeddedBraces.isNotEmpty() ->
            if (embeddedBraces.last() == 0) {
              embeddedBraces.removeAt(embeddedBraces.size - 1)
              sink.add(TokenKind.INTERPOLATION, start, end)
            } else {
              embeddedBraces[embeddedBraces.size - 1] = embeddedBraces.last() - 1
              sink.add(TokenKind.PUNCTUATION, start, end)
            }
          type == GroovyDocElementTypes.GROOVY_DOC_COMMENT -> sink.add(TokenKind.DOC_COMMENT, start, end)
          comments.contains(type) -> sink.add(TokenKind.COMMENT, start, end)
          escapedStrings.contains(type) -> sink.addQuoted(start, end)
          type == GroovyTokenTypes.mREGEX_CONTENT -> slashyContent(text, sink, start, end)
          plainStrings.contains(type) -> sink.add(TokenKind.STRING, start, end)
          numbers.contains(type) -> sink.add(TokenKind.NUMBER, start, end)
          type == GroovyTokenTypes.mAT -> at(text, sink, start, end)
          type == GroovyTokenTypes.mIDENT -> sink.add(TokenKind.IDENTIFIER, start, end)
          TokenSets.KEYWORDS.contains(type) ->
            sink.add(if (isKeyword(text, sink, start, end)) TokenKind.KEYWORD else TokenKind.IDENTIFIER, start, end)
          type == GroovyTokenTypes.mWRONG || type == TokenType.BAD_CHARACTER -> sink.add(TokenKind.INVALID, start, end)
          punctuation.contains(type) -> sink.add(TokenKind.PUNCTUATION, start, end)
          else -> sink.add(TokenKind.OPERATOR, start, end)
        }
      }
    }

  /** In a slashy string only `\/` is an escape. */
  private fun slashyContent(text: CharSequence, sink: TokenSink, start: Int, end: Int) {
    var pending = start
    var position = start
    while (position < end) {
      if (text[position] == '\\' && position + 1 < end && text[position + 1] == '/') {
        sink.add(TokenKind.STRING, pending, position)
        sink.add(TokenKind.STRING_ESCAPE, position, position + 2)
        position += 2
        pending = position
      } else {
        position++
      }
    }
    sink.add(TokenKind.STRING, pending, end)
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

  /** Whether the keyword-shaped word that covers [start] until [end] acts as a keyword there. */
  private fun isKeyword(text: CharSequence, sink: TokenSink, start: Int, end: Int): Boolean {
    val previous = sink.last
    if (previous != null) {
      val sign = sink.lastText().toString()
      if ((previous.kind == TokenKind.PUNCTUATION && sign == ".") || sign == "?." || sign == "*.") return false
    }
    val next = text.nextSignificant(end)
    val nextCharacter = text.at(next)
    val namesFollow = nextCharacter.isIdentifierStart() || nextCharacter == '$'
    return when (text.subSequence(start, end).toString()) {
      "module" -> false
      "var", "val", "record", "sealed" -> namesFollow && text.wordAt(next) != "instanceof"
      "permits" -> namesFollow && previous != null && previous.kind != TokenKind.KEYWORD
      "yield" -> text.startsLine(start) && nextCharacter !in "=.([,;)" && nextCharacter != '\u0000'
      else -> true
    }
  }
}
