/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0
 */

package com.progmasoft.xide.psi

import com.progmasoft.xide.syntax.Token
import com.progmasoft.xide.syntax.TokenKind
import com.progmasoft.xide.syntax.TalaTokenizer
import javax.swing.Icon
import org.jetbrains.kotlin.com.intellij.extapi.psi.ASTWrapperPsiElement
import org.jetbrains.kotlin.com.intellij.extapi.psi.PsiFileBase
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.Language
import org.jetbrains.kotlin.com.intellij.lang.ParserDefinition
import org.jetbrains.kotlin.com.intellij.lang.PsiParser
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.lexer.LexerBase
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.FileType
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.FileViewProvider
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.psi.tree.IFileElementType
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet

/** The Tala language, as the PSI platform identifies it. */
internal object TalaLanguage : Language("Tala")

/** The file type that ties `.tala` text to [TalaLanguage]. */
internal object TalaFileType : LanguageFileType(TalaLanguage) {
  override fun getName(): String = "Tala"

  override fun getDescription(): String = "Tala"

  override fun getDefaultExtension(): String = "tala"

  override fun getIcon(): Icon? = null
}

/** A parsed Tala file. */
internal class TalaFile(provider: FileViewProvider) : PsiFileBase(provider, TalaLanguage) {
  override fun getFileType(): FileType = TalaFileType
}

/**
 * The element types of the Tala tree.
 *
 * The token types follow the lexical kinds of the colouring tokenizer, because that tokenizer is the one place
 * where the lexical rules of the language are written down in Xide. The composite types are the declarations
 * the structural parser recognizes; see [TalaParser].
 */
internal object TalaTypes {
  val FILE = IFileElementType("TALA_FILE", TalaLanguage)

  val KEYWORD = element("KEYWORD")
  val IDENTIFIER = element("IDENTIFIER")
  val NUMBER = element("NUMBER")
  val STRING = element("STRING")
  val STRING_ESCAPE = element("STRING_ESCAPE")
  val INTERPOLATION = element("INTERPOLATION")
  val COMMENT = element("COMMENT")
  val DOC_COMMENT = element("DOC_COMMENT")
  val ANNOTATION = element("ANNOTATION")
  val OPERATOR = element("OPERATOR")
  val PUNCTUATION = element("PUNCTUATION")

  /** A preprocessor directive: `#` directly followed by a word, up to the end of its line. */
  val DIRECTIVE = element("DIRECTIVE")

  val NAMESPACE_DECLARATION = element("NAMESPACE_DECLARATION")
  val USING_DECLARATION = element("USING_DECLARATION")
  val TYPE_DECLARATION = element("TYPE_DECLARATION")
  val TYPE_BODY = element("TYPE_BODY")
  val CALLABLE_DECLARATION = element("CALLABLE_DECLARATION")
  val PROPERTY_DECLARATION = element("PROPERTY_DECLARATION")
  val FIELD_DECLARATION = element("FIELD_DECLARATION")
  val INITIALIZER = element("INITIALIZER")
  val BLOCK = element("BLOCK")

  /** The name a declaration introduces. */
  val NAME = element("NAME")

  val COMMENTS: TokenSet = TokenSet.create(COMMENT, DOC_COMMENT)
  val STRINGS: TokenSet = TokenSet.create(STRING, STRING_ESCAPE)

  /** The element type of a colour kind. */
  fun of(kind: TokenKind): IElementType =
    when (kind) {
      TokenKind.KEYWORD -> KEYWORD
      TokenKind.IDENTIFIER -> IDENTIFIER
      TokenKind.NUMBER -> NUMBER
      TokenKind.STRING -> STRING
      TokenKind.STRING_ESCAPE -> STRING_ESCAPE
      TokenKind.INTERPOLATION -> INTERPOLATION
      TokenKind.COMMENT -> COMMENT
      TokenKind.DOC_COMMENT -> DOC_COMMENT
      TokenKind.ANNOTATION -> ANNOTATION
      TokenKind.OPERATOR -> OPERATOR
      TokenKind.PUNCTUATION -> PUNCTUATION
      TokenKind.INVALID -> TokenType.BAD_CHARACTER
    }

  private fun element(name: String): IElementType = IElementType(name, TalaLanguage)
}

/**
 * The Tala lexer of the PSI platform.
 *
 * It does not define the lexical rules a second time: it presents the tokens of [TalaTokenizer] in the
 * form the platform expects, in which whitespace is a token too and every character belongs to exactly one
 * token. The one addition is the preprocessor directive, which the grammar ends at the end of its line; the
 * parser cannot see line ends, so the lexer reports the whole directive as one token.
 */
internal class TalaLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var bufferEnd = 0
  private var tokens: List<Token> = emptyList()

  /** The offset of the region [tokens] were computed for; token offsets are relative to it. */
  private var base = 0
  private var index = 0
  private var position = 0
  private var currentType: IElementType? = null
  private var currentEnd = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    bufferEnd = endOffset
    base = startOffset
    tokens = TalaTokenizer.tokenize(buffer.subSequence(startOffset, endOffset))
    index = 0
    position = startOffset
    locate()
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? = currentType

  override fun getTokenStart(): Int = position

  override fun getTokenEnd(): Int = currentEnd

  override fun advance() {
    position = currentEnd
    locate()
  }

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = bufferEnd

  /** Determines the token that starts at [position]. */
  private fun locate() {
    if (position >= bufferEnd) {
      currentType = null
      currentEnd = bufferEnd
      return
    }
    while (index < tokens.size && base + tokens[index].end <= position) index++
    val next = tokens.getOrNull(index)
    val nextStart = if (next == null) bufferEnd else base + next.start
    if (nextStart > position) {
      // The stretch before the next token is whitespace.
      currentType = TokenType.WHITE_SPACE
      currentEnd = nextStart
      return
    }
    val token = next!!
    if (buffer[position] == '#' && buffer.at(position + 1).isIdentifierStart()) {
      currentType = TalaTypes.DIRECTIVE
      var end = position
      while (end < bufferEnd && !isLineBreak(buffer[end])) end++
      currentEnd = end
      return
    }
    currentType = TalaTypes.of(token.kind)
    currentEnd = base + token.end
  }
}

/** Connects the Tala lexer and parser to the PSI platform. */
internal class TalaParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = TalaLexer()

  override fun createParser(project: Project?): PsiParser = TalaParser()

  override fun getFileNodeType(): IFileElementType = TalaTypes.FILE

  override fun getCommentTokens(): TokenSet = TalaTypes.COMMENTS

  override fun getStringLiteralElements(): TokenSet = TalaTypes.STRINGS

  override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = TalaFile(viewProvider)
}
