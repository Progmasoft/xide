/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.progmasoft.xide.syntax.GroovyTokenizer
import com.progmasoft.xide.syntax.JavaTokenizer
import com.progmasoft.xide.syntax.KotlinTokenizer
import com.progmasoft.xide.syntax.PythonTokenizer
import com.progmasoft.xide.syntax.Token
import com.progmasoft.xide.syntax.TokenKind
import com.progmasoft.xide.syntax.Tokenizer
import com.progmasoft.xide.syntax.VisualXSharpTokenizer

/**
 * The longest text, in UTF-16 code units, that is coloured.
 *
 * Colouring tokenizes the whole text on every edit. Beyond this length that work is no longer unnoticeable while
 * typing, so a larger text is shown in the plain editor colour instead of being coloured late or partly.
 */
internal const val MAXIMUM_HIGHLIGHTED_LENGTH: Int = 400_000

/** The tokenizer that colours this language. */
internal val SourceLanguage.tokenizer: Tokenizer
  get() =
    when (this) {
      SourceLanguage.VISUAL_XSHARP -> VisualXSharpTokenizer
      SourceLanguage.KOTLIN -> KotlinTokenizer
      SourceLanguage.JAVA -> JavaTokenizer
      SourceLanguage.GROOVY -> GroovyTokenizer
      SourceLanguage.PYTHON -> PythonTokenizer
    }

/**
 * The colours of source text in the dark theme.
 *
 * Names, operators and punctuation keep the editor's text colour: colouring everything leaves nothing to stand
 * out, and a name cannot be coloured by meaning without a language service.
 */
internal object SyntaxColors {
  val keyword = Color(0xFFCF8E6D)
  val number = Color(0xFF2AACB8)
  val string = Color(0xFF6AAB73)
  val escape = Color(0xFFCF8E6D)
  val interpolation = Color(0xFFC77DBB)
  val comment = Color(0xFF7A7E85)
  val documentation = Color(0xFF5F826B)
  val annotation = Color(0xFFB3AE60)

  /** The style of a token kind, or null when the kind is shown in the editor's own text style. */
  fun style(kind: TokenKind): SpanStyle? =
    when (kind) {
      TokenKind.KEYWORD -> SpanStyle(color = keyword)
      TokenKind.NUMBER -> SpanStyle(color = number)
      TokenKind.STRING -> SpanStyle(color = string)
      TokenKind.STRING_ESCAPE -> SpanStyle(color = escape)
      TokenKind.INTERPOLATION -> SpanStyle(color = interpolation)
      TokenKind.COMMENT -> SpanStyle(color = comment)
      TokenKind.DOC_COMMENT -> SpanStyle(color = documentation, fontStyle = FontStyle.Italic)
      TokenKind.ANNOTATION -> SpanStyle(color = annotation)
      TokenKind.INVALID -> SpanStyle(color = XideColors.error)
      TokenKind.IDENTIFIER, TokenKind.OPERATOR, TokenKind.PUNCTUATION -> null
    }
}

/**
 * The coloured stretches of [text] in [language].
 *
 * The result is empty when the document has no language, when the text is longer than
 * [MAXIMUM_HIGHLIGHTED_LENGTH], and for tokens whose kind has no style of its own. Every range lies inside the
 * text, so the spans can be applied to it without further checks.
 */
internal fun syntaxSpans(language: SourceLanguage?, text: String): List<AnnotatedString.Range<SpanStyle>> {
  if (language == null || text.isEmpty() || text.length > MAXIMUM_HIGHLIGHTED_LENGTH) return emptyList()
  return spansOf(language.tokenizer.tokenize(text), text.length)
}

/** Converts tokens into spans, dropping any token that does not lie inside a text of [length] code units. */
internal fun spansOf(tokens: List<Token>, length: Int): List<AnnotatedString.Range<SpanStyle>> {
  val spans = ArrayList<AnnotatedString.Range<SpanStyle>>()
  for (token in tokens) {
    if (token.end > length) continue
    val style = SyntaxColors.style(token.kind) ?: continue
    spans += AnnotatedString.Range(style, token.start, token.end)
  }
  return spans
}

/**
 * Shows the editor text with syntax colours without changing it.
 *
 * The transformation only adds styles: the transformed text has the same characters as the original, so offsets
 * map one to one and the caret, the selection and diagnostic ranges keep addressing the document's own offsets.
 * Spans computed for another text are ignored rather than applied to offsets they were not computed for.
 */
internal class SyntaxHighlightTransformation(
  private val sourceLength: Int,
  private val spans: List<AnnotatedString.Range<SpanStyle>>,
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val applicable = if (text.length == sourceLength) spans else emptyList()
    return TransformedText(AnnotatedString(text.text, applicable), OffsetMapping.Identity)
  }

  override fun equals(other: Any?): Boolean =
    other is SyntaxHighlightTransformation && other.sourceLength == sourceLength && other.spans == spans

  override fun hashCode(): Int = 31 * sourceLength + spans.hashCode()
}
