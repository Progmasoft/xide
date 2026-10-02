/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinTokenizerTest {
  private val tokenizer = KotlinTokenizer
  private val dollar = "$"
  private val raw = "\"\"\""

  @Test
  fun aFunctionIsSplitIntoKeywordsNamesAndSigns() {
    tokenizer.assertPieces(
      "fun main(args: Array<String>) { println(1) }",
      K to "fun", I to "main", P to "(", I to "args", P to ":", I to "Array", O to "<", I to "String", O to ">",
      P to ")", P to "{", I to "println", P to "(", N to "1", P to ")", P to "}",
    )
  }

  @Test
  fun hardKeywordsAreKeywordsEverywhere() {
    for (keyword in KotlinTokenizer.hardKeywords) {
      tokenizer.assertPieces(keyword, K to keyword)
      tokenizer.assertPieces("x = $keyword", I to "x", O to "=", K to keyword)
    }
  }

  @Test
  fun modifiersAreKeywordsOnlyInFrontOfADeclaration() {
    assertEquals(K, tokenizer.kindOf("private val x = 1", "private"))
    assertEquals(K, tokenizer.kindOf("y\nprivate val x = 1", "private"))
    assertEquals(K, tokenizer.kindOf("data class P(val x: Int)", "data"))
    assertEquals(K, tokenizer.kindOf("@JvmInline value class V(val x: Int)", "value"))
    assertEquals(K, tokenizer.kindOf("override fun f() {}", "override"))
    assertEquals(K, tokenizer.kindOf("companion object {}", "companion"))
    assertEquals(K, tokenizer.kindOf("fun <reified T> f() {}", "reified"))
    assertEquals(K, tokenizer.kindOf("class Box<out T>", "out"))
    assertEquals(K, tokenizer.kindOf("fun f(vararg items: Int) {}", "vararg"))
    assertEquals(K, tokenizer.kindOf("val f: suspend () -> Unit", "suspend"))
    assertEquals(listOf(K, K), tokenizer.kindsOf("private inline fun f() {}", "private") +
      tokenizer.kindsOf("private inline fun f() {}", "inline"))
    assertEquals(K, tokenizer.kindOf("open @Deprecated(\"\") class A", "open"))
  }

  @Test
  fun modifierWordsUsedAsNamesStayNames() {
    assertEquals(I, tokenizer.kindOf("val value = 1", "value"))
    assertEquals(I, tokenizer.kindOf("value = data + 1", "value"))
    assertEquals(I, tokenizer.kindOf("value = data + 1", "data"))
    assertEquals(I, tokenizer.kindOf("print(value)", "value"))
    assertEquals(I, tokenizer.kindOf("if (ok) value else other", "value"))
    assertEquals(I, tokenizer.kindOf("data in items", "data"))
    assertEquals(I, tokenizer.kindOf("value as Int", "value"))
    assertEquals(I, tokenizer.kindOf("file.open()", "open"))
    assertEquals(I, tokenizer.kindOf("stream?.open", "open"))
    assertEquals(I, tokenizer.kindOf("File::open", "open"))
    assertEquals(I, tokenizer.kindOf("x.value class", "value"))
  }

  @Test
  fun softKeywordsDependOnTheirPosition() {
    assertEquals(K, tokenizer.kindOf("val x by lazy { 1 }", "by"))
    assertEquals(K, tokenizer.kindOf("class A(b: B) : B by b", "by"))
    assertEquals(I, tokenizer.kindOf("val by = 1", "by"))
    assertEquals(K, tokenizer.kindOf("try { } catch (e: Exception) { } finally { }", "catch"))
    assertEquals(K, tokenizer.kindOf("try { } catch (e: Exception) { } finally { }", "finally"))
    assertEquals(I, tokenizer.kindOf("val catch = 1", "catch"))
    assertEquals(K, tokenizer.kindOf("class A private constructor(x: Int)", "constructor"))
    assertEquals(K, tokenizer.kindOf("class A { init { } }", "init"))
    assertEquals(I, tokenizer.kindOf("init()", "init"))
    assertEquals(K, tokenizer.kindOf("import a.b.C", "import"))
    assertEquals(I, tokenizer.kindOf("x.import", "import"))
    assertEquals(K, tokenizer.kindOf("fun <T> f(x: T) where T : Any", "where"))
    assertEquals(K, tokenizer.kindOf("val x: Int\n  get() = 1", "get"))
    assertEquals(K, tokenizer.kindOf("var x = 1\n  private set", "set"))
    assertEquals(K, tokenizer.kindOf("var x = 1\n  set(v) { field = v }", "set"))
    assertEquals(I, tokenizer.kindOf("map.get(1)", "get"))
    assertEquals(I, tokenizer.kindOf("x = get(1)", "get"))
  }

  @Test
  fun commentsNestAndDocumentationIsDistinguished() {
    tokenizer.assertPieces("a // rest\nb", I to "a", C to "// rest", I to "b")
    tokenizer.assertPieces("/* a /* b */ c */ x", C to "/* a /* b */ c */", I to "x")
    tokenizer.assertPieces("/** doc */ x", D to "/** doc */", I to "x")
    tokenizer.assertPieces("/**/ x", C to "/**/", I to "x")
    tokenizer.assertPieces("/* open\n  more", C to "/* open", C to "more")
    tokenizer.assertPieces("#!/usr/bin/env kotlin\nval x", C to "#!/usr/bin/env kotlin", K to "val", I to "x")
  }

  @Test
  fun stringsColourEscapesAndTemplates() {
    tokenizer.assertPieces("\"a\\nb\"", S to "\"a", E to "\\n", S to "b\"")
    tokenizer.assertPieces("\"hi ${dollar}name!\"", S to "\"hi", X to "${dollar}name", S to "!\"")
    tokenizer.assertPieces(
      "\"sum ${dollar}{a + 1} end\"",
      S to "\"sum", X to "${dollar}{", I to "a", O to "+", N to "1", X to "}", S to "end\"",
    )
    // A lone dollar sign is text.
    tokenizer.assertPieces("\"cost: 5${dollar}\"", S to "\"cost: 5${dollar}\"")
    tokenizer.assertPieces("\"open", S to "\"open")
    tokenizer.assertPieces("\"open\nval", S to "\"open", K to "val")
    tokenizer.assertPieces("'a' '\\n' '\\u0041'", S to "'a'", S to "'", E to "\\n", S to "'", S to "'",
      E to "\\u0041", S to "'")
  }

  @Test
  fun templatesNestStringsLambdasAndTemplates() {
    tokenizer.assertPieces(
      "\"${dollar}{ items.map { it } }\"",
      S to "\"", X to "${dollar}{", I to "items", P to ".", I to "map", P to "{", I to "it", P to "}", X to "}",
      S to "\"",
    )
    tokenizer.assertPieces(
      "\"${dollar}{ \"in ${dollar}{x}\" }\"",
      S to "\"", X to "${dollar}{", S to "\"in", X to "${dollar}{", I to "x", X to "}", S to "\"", X to "}", S to "\"",
    )
    // An expression that is never closed takes the rest of the text, and nothing is lost.
    tokenizer.pieces("\"${dollar}{ a + ")
  }

  @Test
  fun deeplyNestedTemplatesStopBeingScannedInsteadOfOverflowing() {
    val depth = 400
    val text = "\"${dollar}{".repeat(depth) + "x" + "}\"".repeat(depth)
    val tokens = tokenizer.tokenize(text)
    assertEquals(null, checkTokens(text, tokens))
  }

  @Test
  fun rawStringsSpanLinesAndHaveNoEscapes() {
    tokenizer.assertPieces("$raw a\\n $raw x", S to "$raw a\\n $raw", I to "x")
    tokenizer.assertPieces("${raw}one\n  two$raw", S to "${raw}one", S to "two$raw")
    tokenizer.assertPieces("$raw${dollar}name$raw", S to raw, X to "${dollar}name", S to raw)
    // Quotes directly before the closing delimiter belong to the content.
    tokenizer.assertPieces("${raw}a\"\"$raw x", S to "${raw}a\"\"$raw", I to "x")
    tokenizer.assertPieces("$dollar$dollar\"a ${dollar}b\" x", S to "$dollar$dollar\"a ${dollar}b\"", I to "x")
  }

  @Test
  fun annotationsAndLabelsShareAKind() {
    tokenizer.assertPieces("@Composable fun f() {}", A to "@Composable", K to "fun", I to "f", P to "(", P to ")",
      P to "{", P to "}")
    tokenizer.assertPieces("@file:JvmName(\"A\")", A to "@file:JvmName", P to "(", S to "\"A\"", P to ")")
    tokenizer.assertPieces("@kotlin.jvm.JvmStatic", A to "@kotlin.jvm.JvmStatic")
    tokenizer.assertPieces("return@forEach", K to "return", A to "@forEach")
    tokenizer.assertPieces("loop@ for (i in a) break@loop", A to "loop@", K to "for", P to "(", I to "i", K to "in",
      I to "a", P to ")", K to "break", A to "@loop")
  }

  @Test
  fun numbersAndOperators() {
    for (number in listOf("0", "1_000", "0xFF_FF", "0b1010", "1.5", "1e10", "1.5e-3f", "100L", "42uL", ".5")) {
      tokenizer.assertPieces(number, N to number)
    }
    tokenizer.assertPieces("1..2", N to "1", O to "..", N to "2")
    tokenizer.assertPieces("0..<n", N to "0", O to "..<", I to "n")
    tokenizer.assertPieces("1.toString()", N to "1", P to ".", I to "toString", P to "(", P to ")")
    tokenizer.assertPieces("a?.b ?: c!!", I to "a", O to "?.", I to "b", O to "?:", I to "c", O to "!!")
    tokenizer.assertPieces("a === b !== c", I to "a", O to "===", I to "b", O to "!==", I to "c")
    tokenizer.assertPieces("x !in y", I to "x", O to "!", K to "in", I to "y")
    tokenizer.assertPieces("String::length", I to "String", O to "::", I to "length")
  }

  @Test
  fun backtickNamesAreOneIdentifier() {
    tokenizer.assertPieces("fun `a test name`() {}", K to "fun", I to "`a test name`", P to "(", P to ")", P to "{",
      P to "}")
    tokenizer.assertPieces("`class`", I to "`class`")
    tokenizer.assertPieces("`open\nx", I to "`open", I to "x")
  }

  @Test
  fun strayCharactersAreInvalid() {
    tokenizer.assertPieces("a \\ b", I to "a", BAD to "\\", I to "b")
    tokenizer.assertPieces("a # b", I to "a", BAD to "#", I to "b")
  }
}
