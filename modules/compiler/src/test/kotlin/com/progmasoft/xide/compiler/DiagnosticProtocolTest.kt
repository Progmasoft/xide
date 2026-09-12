/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.compiler

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticProtocolTest {
  @Test
  fun decodesTheStableEmptyDocument() {
    val document = DiagnosticProtocol.decode(byteArrayOf(0x56, 0x58, 0x44, 0x47, 1, 0, 0, 0, 0, 0, 0, 0))

    assertTrue(document.diagnostics.isEmpty())
  }

  @Test
  fun decodesEveryFieldOfARichDiagnostic() {
    val bytes =
      WireWriter()
        .header(1)
        .byte(DiagnosticStage.TYPE_CHECKER.ordinal)
        .byte(DiagnosticSeverity.ERROR.ordinal)
        .text("VXT204")
        .text("argument {actual} cannot be converted to {expected}")
        .count(2)
        .text("actual")
        .text("String")
        .text("expected")
        .text("int")
        .boolean(true)
        .location("C:/work/Main.vxs", 8, 11, 8, 16)
        .count(1)
        .location("C:/work/Library.vxs", 3, 4, 3, 10)
        .text("parameter is declared here")
        .count(1)
        .text("Convert the argument")
        .count(1)
        .location("C:/work/Main.vxs", 8, 11, 8, 16)
        .text("value.ToInt()")
        .toByteArray()

    val diagnostic = DiagnosticProtocol.decode(bytes).diagnostics.single()

    assertEquals(DiagnosticStage.TYPE_CHECKER, diagnostic.stage)
    assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
    assertEquals("VXT204", diagnostic.code)
    assertEquals(2, diagnostic.arguments.size)
    assertEquals("String", diagnostic.arguments.first().value)
    assertEquals(SourcePosition(8u, 11u), diagnostic.primaryLocation?.range?.start)
    assertEquals("parameter is declared here", diagnostic.relatedLocations.single().message)
    assertEquals("Convert the argument", diagnostic.fixes.single().title)
    assertEquals("value.ToInt()", diagnostic.fixes.single().edits.single().replacement)
  }

  @Test
  fun preservesSupplementaryUnicodeScalarsWithoutTreatingTextAsUtf8() {
    val bytes =
      WireWriter()
        .header(1)
        .byte(DiagnosticStage.LEXER.ordinal)
        .byte(DiagnosticSeverity.WARNING.ordinal)
        .text("VXL1")
        .text("bad 🧪 scalar")
        .count(0)
        .boolean(false)
        .count(0)
        .count(0)
        .toByteArray()

    val diagnostic = DiagnosticProtocol.decode(bytes).diagnostics.single()

    assertEquals("bad 🧪 scalar", diagnostic.message)
    assertNull(diagnostic.primaryLocation)
  }

  @Test
  fun mapsAllStableStageAndSeverityTags() {
    DiagnosticStage.entries.forEach { stage ->
      DiagnosticSeverity.entries.forEach { severity ->
        val bytes =
          WireWriter()
            .header(1)
            .byte(stage.ordinal)
            .byte(severity.ordinal)
            .text("VX1")
            .text("message")
            .count(0)
            .boolean(false)
            .count(0)
            .count(0)
            .toByteArray()

        val decoded = DiagnosticProtocol.decode(bytes).diagnostics.single()
        assertEquals(stage, decoded.stage)
        assertEquals(severity, decoded.severity)
      }
    }
  }

  @Test
  fun rejectsWrongMagic() {
    val bytes = WireWriter().header(0).toByteArray().also { it[0] = 'B'.code.toByte() }

    val problem = assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes) }

    assertEquals("magic", problem.context)
  }

  @Test
  fun rejectsUnsupportedVersionsAndReservedFlags() {
    val versionProblem = assertFailsWith<DiagnosticProtocolException> {
      DiagnosticProtocol.decode(WireWriter().rawHeader(version = 2, flags = 0, count = 0).toByteArray())
    }
    val flagProblem = assertFailsWith<DiagnosticProtocolException> {
      DiagnosticProtocol.decode(WireWriter().rawHeader(version = 1, flags = 1, count = 0).toByteArray())
    }

    assertEquals("version", versionProblem.context)
    assertEquals("flags", flagProblem.context)
  }

  @Test
  fun rejectsUnknownEnumAndBooleanTags() {
    val unknownStage =
      WireWriter().header(1).byte(255).byte(0).text("VX1").text("message").count(0).boolean(false).count(0).count(0)
    val badBoolean =
      WireWriter().header(1).byte(0).byte(0).text("VX1").text("message").count(0).byte(2).count(0).count(0)

    assertEquals(
      "diagnostic stage",
      assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(unknownStage.toByteArray()) }.context,
    )
    assertEquals(
      "primary location presence",
      assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(badBoolean.toByteArray()) }.context,
    )
  }

  @Test
  fun rejectsTruncationAtEveryByteBoundary() {
    val valid =
      WireWriter()
        .header(1)
        .byte(0)
        .byte(0)
        .text("VX1")
        .text("message")
        .count(0)
        .boolean(false)
        .count(0)
        .count(0)
        .toByteArray()

    for (length in 0 until valid.size) {
      assertFailsWith<DiagnosticProtocolException>("truncation at $length bytes") {
        DiagnosticProtocol.decode(valid.copyOf(length))
      }
    }
    assertEquals(1, DiagnosticProtocol.decode(valid).diagnostics.size)
  }

  @Test
  fun rejectsTrailingInput() {
    val bytes = WireWriter().header(0).byte(42).toByteArray()

    val problem = assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes) }

    assertEquals("document", problem.context)
  }

  @Test
  fun rejectsCountsBeforeAllocatingCollections() {
    val limits = DiagnosticProtocolLimits(maximumDiagnostics = 1u)
    val bytes = WireWriter().header(2).toByteArray()

    val problem = assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes, limits) }

    assertEquals("diagnostic count", problem.context)
  }

  @Test
  fun rejectsInvalidScalarValues() {
    val bytes =
      WireWriter()
        .header(1)
        .byte(0)
        .byte(0)
        .count(1)
        .u32(0xD800)
        .text("message")
        .count(0)
        .boolean(false)
        .count(0)
        .count(0)
        .toByteArray()

    val problem = assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes) }

    assertEquals("diagnostic code", problem.context)
  }

  @Test
  fun rejectsReversedLocationsAsAnInvalidModel() {
    val bytes =
      WireWriter()
        .header(1)
        .byte(0)
        .byte(0)
        .text("VX1")
        .text("message")
        .count(0)
        .boolean(true)
        .location("Main.vxs", 2, 0, 1, 0)
        .count(0)
        .count(0)
        .toByteArray()

    val problem = assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes) }

    assertEquals("diagnostic location", problem.context)
  }

  @Test
  fun rejectsDuplicateArgumentNames() {
    val bytes =
      WireWriter()
        .header(1)
        .byte(0)
        .byte(0)
        .text("VX1")
        .text("message")
        .count(2)
        .text("name")
        .text("first")
        .text("name")
        .text("second")
        .boolean(false)
        .count(0)
        .count(0)
        .toByteArray()

    assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes) }
  }

  @Test
  fun rejectsEmptyFixes() {
    val bytes =
      WireWriter()
        .header(1)
        .byte(0)
        .byte(0)
        .text("VX1")
        .text("message")
        .count(0)
        .boolean(false)
        .count(0)
        .count(1)
        .text("Fix it")
        .count(0)
        .toByteArray()

    assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes) }
  }

  @Test
  fun rejectsWireDocumentsLargerThanTheConfiguredLimit() {
    val bytes = WireWriter().header(0).toByteArray()
    val limits = DiagnosticProtocolLimits(maximumWireBytes = bytes.size - 1)

    val problem = assertFailsWith<DiagnosticProtocolException> { DiagnosticProtocol.decode(bytes, limits) }

    assertEquals("wire byte length", problem.context)
  }
}

/** Test-only encoder. Production Xide deliberately owns only the consumer side of this one-way protocol. */
internal class WireWriter {
  private val output = ByteArrayOutputStream()

  fun header(count: Int): WireWriter = rawHeader(1, 0, count)

  fun rawHeader(version: Int, flags: Int, count: Int): WireWriter =
    byte('V'.code).byte('X'.code).byte('D'.code).byte('G'.code).u16(version).u16(flags).count(count)

  fun location(source: String, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int): WireWriter =
    text(source).u32(startLine).u32(startColumn).u32(endLine).u32(endColumn)

  fun text(value: String): WireWriter {
    val scalars = value.codePoints().toArray()
    count(scalars.size)
    scalars.forEach(::u32)
    return this
  }

  fun boolean(value: Boolean): WireWriter = byte(if (value) 1 else 0)

  fun count(value: Int): WireWriter = u32(value)

  fun u32(value: Int): WireWriter {
    repeat(Int.SIZE_BYTES) { shift -> byte(value ushr (shift * Byte.SIZE_BITS)) }
    return this
  }

  private fun u16(value: Int): WireWriter {
    byte(value)
    byte(value ushr Byte.SIZE_BITS)
    return this
  }

  fun byte(value: Int): WireWriter {
    output.write(value and 0xFF)
    return this
  }

  fun toByteArray(): ByteArray = output.toByteArray()
}
