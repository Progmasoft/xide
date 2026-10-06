/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import com.progmasoft.xide.document.DocumentSnapshot
import com.progmasoft.xide.psi.OutlineEntry
import com.progmasoft.xide.psi.OutlineKind
import com.progmasoft.xide.psi.PsiLanguage
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureTest {
  private val uri = URI("file:///work/Source")

  private fun snapshot(text: String, version: Long = 0) = DocumentSnapshot(uri, version, text)

  @Test
  fun everyLanguageWithAParserHasAStructure() {
    assertEquals(PsiLanguage.VISUAL_XSHARP, SourceLanguage.VISUAL_XSHARP.psiLanguage)
    assertEquals(PsiLanguage.KOTLIN, SourceLanguage.KOTLIN.psiLanguage)
    assertEquals(PsiLanguage.JAVA, SourceLanguage.JAVA.psiLanguage)
    assertEquals(PsiLanguage.GROOVY, SourceLanguage.GROOVY.psiLanguage)
    // Python is coloured by a tokenizer and has no parser.
    assertNull(SourceLanguage.PYTHON.psiLanguage)
  }

  @Test
  fun rowsFollowSourceOrderWithEnclosedDeclarationsAfterTheirOwner() {
    val outline =
      listOf(
        OutlineEntry(OutlineKind.NAMESPACE, "Demo", 0, 10),
        OutlineEntry(
          OutlineKind.TYPE,
          "Outer",
          12,
          90,
          listOf(
            OutlineEntry(OutlineKind.PROPERTY, "count", 20, 30),
            OutlineEntry(OutlineKind.TYPE, "Inner", 32, 70, listOf(OutlineEntry(OutlineKind.CALLABLE, "Run", 40, 60))),
            OutlineEntry(OutlineKind.INITIALIZER, "init", 72, 88),
          ),
        ),
      )
    assertEquals(
      listOf(
        StructureRow(0, OutlineKind.NAMESPACE, "Demo", 0, 10),
        StructureRow(0, OutlineKind.TYPE, "Outer", 12, 90),
        StructureRow(1, OutlineKind.PROPERTY, "count", 20, 30),
        StructureRow(1, OutlineKind.TYPE, "Inner", 32, 70),
        StructureRow(2, OutlineKind.CALLABLE, "Run", 40, 60),
        StructureRow(1, OutlineKind.INITIALIZER, "init", 72, 88),
      ),
      structureRows(outline),
    )
    assertEquals(emptyList(), structureRows(emptyList()))
  }

  @Test
  fun theEnclosingRowIsTheInnermostDeclarationAroundAnOffset() {
    val rows =
      listOf(
        StructureRow(0, OutlineKind.TYPE, "Outer", 12, 90),
        StructureRow(1, OutlineKind.PROPERTY, "count", 20, 30),
        StructureRow(1, OutlineKind.TYPE, "Inner", 32, 70),
        StructureRow(2, OutlineKind.CALLABLE, "Run", 40, 60),
        StructureRow(0, OutlineKind.TYPE, "Later", 100, 120),
      )
    // Before the first declaration, between two, and after the last one nothing encloses the offset.
    assertNull(enclosingRow(rows, 0))
    assertNull(enclosingRow(rows, 95))
    assertNull(enclosingRow(rows, 121))
    assertNull(enclosingRow(emptyList(), 5))

    assertEquals(0, enclosingRow(rows, 12))
    assertEquals(0, enclosingRow(rows, 15))
    assertEquals(1, enclosingRow(rows, 20))
    assertEquals(1, enclosingRow(rows, 30))
    // Between two members the owner encloses the offset.
    assertEquals(0, enclosingRow(rows, 31))
    assertEquals(2, enclosingRow(rows, 35))
    assertEquals(3, enclosingRow(rows, 50))
    assertEquals(2, enclosingRow(rows, 65))
    assertEquals(0, enclosingRow(rows, 80))
    assertEquals(0, enclosingRow(rows, 90))
    assertEquals(4, enclosingRow(rows, 100))
    assertEquals(4, enclosingRow(rows, 120))
  }

  @Test
  fun aStructureIsCurrentOnlyForTheVersionItWasReadFrom() {
    val structure = DocumentStructure(uri, 3, emptyList())
    assertTrue(structure.isCurrentFor(snapshot("", 3)))
    assertFalse(structure.isCurrentFor(snapshot("", 4)))
    assertFalse(structure.isCurrentFor(DocumentSnapshot(URI("file:///work/Other"), 3, "")))
  }

  @Test
  fun theStructureOfAVisualXSharpSourceListsItsDeclarations() {
    val text = "namespace Demo;\npublic class Program {\n    int count;\n    public static void Main() { }\n}\n"
    val structure = documentStructure(SourceLanguage.VISUAL_XSHARP, snapshot(text, 7))!!
    assertEquals(uri, structure.uri)
    assertEquals(7, structure.version)
    assertEquals(
      listOf(
        Triple(0, OutlineKind.NAMESPACE, "Demo"),
        Triple(0, OutlineKind.TYPE, "Program"),
        Triple(1, OutlineKind.PROPERTY, "count"),
        Triple(1, OutlineKind.CALLABLE, "Main"),
      ),
      structure.rows.map { Triple(it.depth, it.kind, it.name) },
    )
    // Every row addresses the text it was read from, and a member lies inside its owner.
    val program = structure.rows[1]
    assertEquals("public class Program", text.substring(program.start, program.start + 20))
    for (row in structure.rows) assertTrue(row.start in 0..row.end && row.end <= text.length)
    for (member in structure.rows.drop(2)) assertTrue(member.start >= program.start && member.end <= program.end)
  }

  @Test
  fun kotlinJavaAndGroovySourcesHaveAStructure() {
    val kotlin = documentStructure(SourceLanguage.KOTLIN, snapshot("class A { fun run() {} }\nval top = 1\n"))!!
    assertEquals(listOf("A", "run", "top"), kotlin.rows.map { it.name })
    val java = documentStructure(SourceLanguage.JAVA, snapshot("class A { int n; void run() {} }\n"))!!
    assertEquals(listOf("A", "n", "run"), java.rows.map { it.name })
    val groovy = documentStructure(SourceLanguage.GROOVY, snapshot("class A { def run() {} }\n"))!!
    assertEquals(listOf("A", "run"), groovy.rows.map { it.name })
  }

  @Test
  fun aTextThatDoesNotParseStillHasTheStructureThatWasRecognized() {
    val structure = documentStructure(SourceLanguage.KOTLIN, snapshot("class A { fun run( }\nclass B\n"))!!
    assertTrue("A" in structure.rows.map { it.name })
  }

  @Test
  fun noStructureIsReadWithoutAParserOrForAVeryLongText() {
    assertNull(documentStructure(SourceLanguage.PYTHON, snapshot("def run():\n    pass\n")))
    assertNull(documentStructure(null, snapshot("anything")))
    val long = "-- filler\n".repeat(MAXIMUM_STRUCTURED_LENGTH / 10 + 1)
    assertNull(documentStructure(SourceLanguage.VISUAL_XSHARP, snapshot(long)))
    assertEquals(emptyList(), documentStructure(SourceLanguage.VISUAL_XSHARP, snapshot(""))!!.rows)
  }

  @Test
  fun everyKindHasAWordOfItsOwn() {
    assertEquals(OutlineKind.entries.size, OutlineKind.entries.map { it.label() }.distinct().size)
  }
}
