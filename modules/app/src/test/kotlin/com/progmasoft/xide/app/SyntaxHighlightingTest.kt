/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.progmasoft.xide.syntax.Token
import com.progmasoft.xide.syntax.TokenKind
import com.progmasoft.xide.syntax.checkTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyntaxHighlightingTest {
  private fun colourOf(language: SourceLanguage, text: String, fragment: String) =
    syntaxSpans(language, text).firstOrNull { text.substring(it.start, it.end) == fragment }?.item?.color

  @Test
  fun everyLanguageHasATokenizerThatObeysTheContract() {
    val text = "class A { x = \"s\" } -- c\n# d\n// e"
    for (language in SourceLanguage.entries) {
      assertNull(checkTokens(text, language.tokenizer.tokenize(text)), language.displayName)
    }
    assertEquals(SourceLanguage.entries.size, SourceLanguage.entries.map { it.tokenizer }.distinct().size)
  }

  @Test
  fun eachLanguageIsColouredByItsOwnRules() {
    // The same text is a comment in one language and code in another.
    assertEquals(SyntaxColors.comment, colourOf(SourceLanguage.VISUAL_XSHARP, "-- note", "-- note"))
    assertNull(colourOf(SourceLanguage.KOTLIN, "-- note", "-- note"))
    assertEquals(SyntaxColors.comment, colourOf(SourceLanguage.PYTHON, "# note", "# note"))
    assertEquals(SyntaxColors.comment, colourOf(SourceLanguage.JAVA, "// note", "// note"))
    assertEquals(SyntaxColors.keyword, colourOf(SourceLanguage.KOTLIN, "fun main() {}", "fun"))
    assertEquals(SyntaxColors.keyword, colourOf(SourceLanguage.GROOVY, "def x = 1", "def"))
    assertEquals(SyntaxColors.number, colourOf(SourceLanguage.GROOVY, "def x = 1", "1"))
    assertEquals(SyntaxColors.string, colourOf(SourceLanguage.JAVA, "String s = \"a\";", "\"a\""))
    assertEquals(SyntaxColors.annotation, colourOf(SourceLanguage.JAVA, "@Override void f() {}", "@Override"))
    assertEquals(SyntaxColors.documentation, colourOf(SourceLanguage.KOTLIN, "/** d */ val x = 1", "/** d */"))
    assertEquals(SyntaxColors.escape, colourOf(SourceLanguage.KOTLIN, "\"a\\n\"", "\\n"))
    assertEquals(XideColors.error, colourOf(SourceLanguage.VISUAL_XSHARP, "a # b", "#"))
  }

  @Test
  fun namesOperatorsAndPunctuationKeepTheEditorColour() {
    val text = "total = count + offset;"
    assertEquals(emptyList(), syntaxSpans(SourceLanguage.VISUAL_XSHARP, text))
    for (kind in listOf(TokenKind.IDENTIFIER, TokenKind.OPERATOR, TokenKind.PUNCTUATION)) {
      assertNull(SyntaxColors.style(kind), kind.name)
    }
  }

  @Test
  fun everyOtherKindHasAStyleAndDocumentationIsItalic() {
    val plain = setOf(TokenKind.IDENTIFIER, TokenKind.OPERATOR, TokenKind.PUNCTUATION)
    for (kind in TokenKind.entries - plain) {
      assertNotNull(SyntaxColors.style(kind), kind.name)
    }
    assertEquals(FontStyle.Italic, SyntaxColors.style(TokenKind.DOC_COMMENT)?.fontStyle)
    assertNotEquals(SyntaxColors.style(TokenKind.COMMENT), SyntaxColors.style(TokenKind.DOC_COMMENT))
  }

  @Test
  fun aDocumentWithoutALanguageOrWithoutTextIsNotColoured() {
    assertEquals(emptyList(), syntaxSpans(null, "fun main() {}"))
    assertEquals(emptyList(), syntaxSpans(SourceLanguage.KOTLIN, ""))
  }

  @Test
  fun aTextBeyondTheBoundIsShownPlainRatherThanPartlyColoured() {
    val line = "val x = 1\n"
    val within = line.repeat(MAXIMUM_HIGHLIGHTED_LENGTH / line.length)
    assertTrue(within.length <= MAXIMUM_HIGHLIGHTED_LENGTH)
    assertTrue(syntaxSpans(SourceLanguage.KOTLIN, within).isNotEmpty())
    val beyond = within + line
    assertTrue(beyond.length > MAXIMUM_HIGHLIGHTED_LENGTH)
    assertEquals(emptyList(), syntaxSpans(SourceLanguage.KOTLIN, beyond))
  }

  @Test
  fun spansStayInsideTheTextAndInOrder() {
    val text = "fun f() = \"a \${b} c\" // done\n@A class B"
    val spans = syntaxSpans(SourceLanguage.KOTLIN, text)
    var position = 0
    for (span in spans) {
      assertTrue(span.start >= position && span.end > span.start && span.end <= text.length)
      position = span.end
    }
    // A token that points outside the text it is applied to is dropped instead of crashing the text layout.
    val stray = listOf(Token(TokenKind.KEYWORD, 0, 3), Token(TokenKind.KEYWORD, 4, 99))
    assertEquals(1, spansOf(stray, 10).size)
  }

  @Test
  fun theTransformationAddsColoursWithoutChangingTextOrOffsets() {
    val text = "val x = 1 // one"
    val spans = syntaxSpans(SourceLanguage.KOTLIN, text)
    val transformed = SyntaxHighlightTransformation(text.length, spans).filter(AnnotatedString(text))

    assertEquals(text, transformed.text.text)
    assertEquals(spans, transformed.text.spanStyles)
    for (offset in 0..text.length) {
      assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
      assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
    }
  }

  @Test
  fun spansOfAnotherTextAreNotApplied() {
    val spans = syntaxSpans(SourceLanguage.KOTLIN, "val x = 1 // one")
    val transformation = SyntaxHighlightTransformation("val x = 1 // one".length, spans)
    val shorter = transformation.filter(AnnotatedString("val"))
    assertEquals("val", shorter.text.text)
    assertEquals(emptyList(), shorter.text.spanStyles)
  }

  @Test
  fun transformationsOfTheSameColoursAreEqual() {
    val text = "val x = 1"
    val first = SyntaxHighlightTransformation(text.length, syntaxSpans(SourceLanguage.KOTLIN, text))
    val second = SyntaxHighlightTransformation(text.length, syntaxSpans(SourceLanguage.KOTLIN, text))
    assertEquals(first, second)
    assertEquals(first.hashCode(), second.hashCode())
    assertNotEquals(first, SyntaxHighlightTransformation(text.length, emptyList()))
    assertNotEquals<Any>(first, text)
  }
}
