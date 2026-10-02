/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

class GroovyTokenizerTest {
  private val tokenizer = GroovyTokenizer
  private val dollar = "$"

  @Test
  fun aGradleScriptIsSplitIntoNamesStringsAndSigns() {
    tokenizer.assertPieces(
      "plugins {\n  id 'java'\n}\ndef v = \"1.0\"",
      I to "plugins", P to "{", I to "id", S to "'java'", P to "}", K to "def", I to "v", O to "=", S to "\"1.0\"",
    )
  }

  @Test
  fun groovyAddsItsOwnKeywordsToJavas() {
    for (keyword in listOf("def", "in", "as", "trait", "var", "class", "return", "instanceof")) {
      tokenizer.assertPieces(keyword, K to keyword)
    }
    // After a member selector a reserved word is a property name.
    assertEquals(I, tokenizer.kindOf("config.default", "default"))
    assertEquals(I, tokenizer.kindOf("config?.class", "class"))
    assertEquals(I, tokenizer.kindOf("items*.new", "new"))
  }

  @Test
  fun singleQuotedStringsDoNotInterpolate() {
    tokenizer.assertPieces("'a ${dollar}b \\n'", S to "'a ${dollar}b", E to "\\n", S to "'")
    tokenizer.assertPieces("'''a\n${dollar}{b}'''", S to "'''a", S to "${dollar}{b}'''")
  }

  @Test
  fun doubleQuotedStringsInterpolateNamesPathsAndExpressions() {
    tokenizer.assertPieces("\"v ${dollar}version!\"", S to "\"v", X to "${dollar}version", S to "!\"")
    tokenizer.assertPieces("\"${dollar}project.name.\"", S to "\"", X to "${dollar}project.name", S to ".\"")
    tokenizer.assertPieces(
      "\"${dollar}{a + 1}\"",
      S to "\"", X to "${dollar}{", I to "a", O to "+", N to "1", X to "}", S to "\"",
    )
    tokenizer.assertPieces(
      "\"\"\"a\n${dollar}{b}\"\"\"",
      S to "\"\"\"a", X to "${dollar}{", I to "b", X to "}", S to "\"\"\"",
    )
  }

  @Test
  fun aSlashStartsAStringOnlyWhereNothingCanBeDivided() {
    tokenizer.assertPieces("def p = /a\\/b ${dollar}x/", K to "def", I to "p", O to "=", S to "/a", E to "\\/",
      S to "b", X to "${dollar}x", S to "/")
    tokenizer.assertPieces("x =~ /\\d+/", I to "x", O to "=~", S to "/\\d+/")
    tokenizer.assertPieces("f(/a/)", I to "f", P to "(", S to "/a/", P to ")")
    // After a value the slash divides.
    tokenizer.assertPieces("a / b / c", I to "a", O to "/", I to "b", O to "/", I to "c")
    tokenizer.assertPieces("(a) / 2 / x", P to "(", I to "a", P to ")", O to "/", N to "2", O to "/", I to "x")
    tokenizer.assertPieces("a /= 2", I to "a", O to "/=", N to "2")
    // With no closing slash on the line it is division even where a string could start.
    tokenizer.assertPieces("x = / 2", I to "x", O to "=", O to "/", N to "2")
  }

  @Test
  fun dollarSlashyStringsArePlainText() {
    tokenizer.assertPieces("${dollar}/ a / b /${dollar} x", S to "${dollar}/ a / b /${dollar}", I to "x")
  }

  @Test
  fun commentsAnnotationsAndTheInterpreterLine() {
    tokenizer.assertPieces("#!/usr/bin/env groovy\nx", C to "#!/usr/bin/env groovy", I to "x")
    tokenizer.assertPieces("a // b\n/** d */ /* c */", I to "a", C to "// b", D to "/** d */", C to "/* c */")
    tokenizer.assertPieces("@CompileStatic class A {}", A to "@CompileStatic", K to "class", I to "A", P to "{",
      P to "}")
    tokenizer.assertPieces("@interface M {}", K to "@interface", I to "M", P to "{", P to "}")
  }

  @Test
  fun groovyOperators() {
    tokenizer.assertPieces("a <=> b", I to "a", O to "<=>", I to "b")
    tokenizer.assertPieces("a ==~ b", I to "a", O to "==~", I to "b")
    tokenizer.assertPieces("a ?: b", I to "a", O to "?:", I to "b")
    tokenizer.assertPieces("0..<n", N to "0", O to "..<", I to "n")
    tokenizer.assertPieces("a ** 2", I to "a", O to "**", N to "2")
    tokenizer.assertPieces("{ x -> x }", P to "{", I to "x", O to "->", I to "x", P to "}")
  }
}
