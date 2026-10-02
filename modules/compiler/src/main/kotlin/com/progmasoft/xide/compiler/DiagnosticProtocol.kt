/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.compiler

/** Compiler stages have stable wire ordinals. Append stages; never reorder existing values. */
enum class DiagnosticStage {
  SOURCE_LOADER,
  LEXER,
  PARSER,
  RENAMER,
  NAME_RESOLUTION,
  TYPE_CHECKER,
  DESUGARER,
  CORE,
  CORE_OPTIMIZER,
  CORE_PREP,
  XPP_LOWERING,
  XPP_OPTIMIZER,
  XMM_LOWERING,
  XMM_OPTIMIZER,
  LLVM_BACKEND,
}

/** How serious a diagnostic is, declared from most to least severe. The wire ordinals are stable. */
enum class DiagnosticSeverity {
  ERROR,
  WARNING,
  INFORMATION,
  HINT,
}

/**
 * A position in compiler coordinates.
 *
 * @property line the zero-based line index.
 * @property column the zero-based column counted in Unicode scalar values, not UTF-16 code units.
 */
data class SourcePosition(val line: UInt, val column: UInt) : Comparable<SourcePosition> {
  /** Orders positions by line and then by column. */
  override fun compareTo(other: SourcePosition): Int =
    compareValuesBy(this, other, SourcePosition::line, SourcePosition::column)
}

/**
 * A half-open range between two positions of one source.
 *
 * @property start the first position of the range.
 * @property end the position just after the range; it never precedes [start].
 */
data class SourceRange(val start: SourcePosition, val end: SourcePosition) {
  init {
    require(start <= end) { "diagnostic range end must not precede its start" }
  }
}

/**
 * A range inside one source.
 *
 * @property source the compiler's identity for the source: an absolute path or one relative to its working directory.
 * @property range the range inside that source.
 */
data class SourceLocation(val source: String, val range: SourceRange) {
  init {
    require(source.isNotEmpty()) { "diagnostic source identity must not be empty" }
  }
}

/**
 * A named value substituted into a diagnostic message, kept separately so tooling can use it.
 *
 * @property name the argument's name, unique within its diagnostic.
 * @property value the argument's rendered value.
 */
data class DiagnosticArgument(val name: String, val value: String) {
  init {
    require(name.isNotEmpty()) { "diagnostic argument name must not be empty" }
  }
}

/**
 * A secondary location that explains a diagnostic.
 *
 * @property location where the related source is.
 * @property message what that location contributes.
 */
data class RelatedDiagnostic(val location: SourceLocation, val message: String) {
  init {
    require(message.isNotEmpty()) { "related diagnostic message must not be empty" }
  }
}

/**
 * One replacement a fix performs.
 *
 * @property location the range to replace.
 * @property replacement the text that replaces it.
 */
data class DiagnosticTextEdit(val location: SourceLocation, val replacement: String)

/**
 * A change the compiler offers to resolve a diagnostic.
 *
 * @property title the label shown for the fix.
 * @property edits the replacements the fix performs; never empty.
 */
data class DiagnosticFix(val title: String, val edits: List<DiagnosticTextEdit>) {
  init {
    require(title.isNotEmpty()) { "diagnostic fix title must not be empty" }
    require(edits.isNotEmpty()) { "diagnostic fix must contain at least one edit" }
  }
}

/**
 * One finding reported by the compiler.
 *
 * @property stage the compiler stage that reported it.
 * @property severity how serious it is.
 * @property code the stable diagnostic code: 1 to 64 ASCII uppercase letters, digits or hyphens.
 * @property message the rendered message.
 * @property arguments the named values that appear in the message.
 * @property primaryLocation where the finding is, or null when it is not tied to a source range.
 * @property relatedLocations secondary locations that explain it.
 * @property fixes the changes the compiler offers for it.
 */
data class CompilerDiagnostic(
  val stage: DiagnosticStage,
  val severity: DiagnosticSeverity,
  val code: String,
  val message: String,
  val arguments: List<DiagnosticArgument>,
  val primaryLocation: SourceLocation?,
  val relatedLocations: List<RelatedDiagnostic>,
  val fixes: List<DiagnosticFix>,
) {
  init {
    require(code.isNotEmpty() && code.length <= 64 && code.all(::isCodeCharacter)) {
      "diagnostic code must contain 1-64 ASCII uppercase letters, digits, or hyphens"
    }
    require(message.isNotEmpty()) { "diagnostic message must not be empty" }
    require(arguments.map(DiagnosticArgument::name).distinct().size == arguments.size) {
      "diagnostic argument names must be unique within one diagnostic"
    }
  }

  /** Validation helpers for diagnostic codes. */
  companion object {
    private fun isCodeCharacter(character: Char): Boolean =
      character in 'A'..'Z' || character in '0'..'9' || character == '-'
  }
}

/**
 * Everything one compiler run reported.
 *
 * @property diagnostics the findings in the order the compiler emitted them.
 */
data class DiagnosticDocument(val diagnostics: List<CompilerDiagnostic>)

/**
 * Upper bounds the decoder enforces before it allocates anything.
 *
 * @property maximumWireBytes the largest accepted document, in bytes.
 * @property maximumDiagnostics the largest accepted number of diagnostics.
 * @property maximumTextScalars the largest accepted length of one text, in Unicode scalar values.
 * @property maximumArguments the largest accepted number of arguments of one diagnostic.
 * @property maximumRelatedLocations the largest accepted number of related locations of one diagnostic.
 * @property maximumFixes the largest accepted number of fixes of one diagnostic.
 * @property maximumEditsPerFix the largest accepted number of edits of one fix.
 */
data class DiagnosticProtocolLimits(
  val maximumWireBytes: Int = 16 * 1024 * 1024,
  val maximumDiagnostics: UInt = 65_535u,
  val maximumTextScalars: UInt = 1024u * 1024u,
  val maximumArguments: UInt = 256u,
  val maximumRelatedLocations: UInt = 256u,
  val maximumFixes: UInt = 128u,
  val maximumEditsPerFix: UInt = 4096u,
) {
  init {
    require(maximumWireBytes >= 0) { "wire byte limit must not be negative" }
  }
}

/**
 * A diagnostic document that does not follow the protocol.
 *
 * @property offset the byte offset at which decoding failed.
 * @property context the field that was being decoded.
 */
class DiagnosticProtocolException(
  val offset: Int,
  val context: String,
  message: String,
) : IllegalArgumentException("$context at byte $offset: $message")

/**
 * Decodes the private VXDG v1 compiler-to-tooling protocol.
 *
 * Text is carried as counted Unicode scalar values, matching the compiler artifact protocols. It is deliberately not
 * decoded as UTF-8: source encoding and protocol encoding are separate concerns. Every count is checked before an
 * allocation, every scalar is validated, and trailing input is rejected so corrupted compiler output cannot be
 * mistaken for a partial success.
 */
object DiagnosticProtocol {
  /** The protocol version this decoder accepts. */
  const val VERSION: Int = 1
  private val magic = byteArrayOf('V'.code.toByte(), 'X'.code.toByte(), 'D'.code.toByte(), 'G'.code.toByte())

  /**
   * Decodes one complete diagnostic document.
   *
   * @throws DiagnosticProtocolException when the bytes are not a valid document within [limits].
   */
  fun decode(
    bytes: ByteArray,
    limits: DiagnosticProtocolLimits = DiagnosticProtocolLimits(),
  ): DiagnosticDocument {
    if (bytes.size > limits.maximumWireBytes) {
      throw DiagnosticProtocolException(0, "wire byte length", "diagnostic document exceeds configured limit")
    }

    val reader = Reader(bytes, limits)
    magic.forEach { expected ->
      if (reader.readByte("magic") != expected.toUByte()) {
        reader.fail("magic", "input is not a Visual X# diagnostic document")
      }
    }
    val version = reader.readU16("version")
    if (version != VERSION) {
      reader.fail("version", "unsupported diagnostic protocol version $version")
    }
    val flags = reader.readU16("flags")
    if (flags != 0) {
      reader.fail("flags", "reserved diagnostic flags must be zero")
    }

    val count = reader.readCount(limits.maximumDiagnostics, "diagnostic count")
    val diagnostics = List(count) { reader.readDiagnostic() }
    reader.requireEnd()
    return DiagnosticDocument(diagnostics)
  }

  private class Reader(
    private val bytes: ByteArray,
    private val limits: DiagnosticProtocolLimits,
  ) {
    private var offset = 0

    fun readDiagnostic(): CompilerDiagnostic {
      val stage = readEnum(DiagnosticStage.entries, "diagnostic stage")
      val severity = readEnum(DiagnosticSeverity.entries, "diagnostic severity")
      val code = readText("diagnostic code")
      val message = readText("diagnostic message")
      val arguments = readList(limits.maximumArguments, "diagnostic argument count") {
        val name = readText("diagnostic argument name")
        val value = readText("diagnostic argument value")
        model("diagnostic argument") { DiagnosticArgument(name, value) }
      }
      val primaryLocation = if (readBoolean("primary location presence")) readLocation() else null
      val related = readList(limits.maximumRelatedLocations, "related location count") {
        val location = readLocation()
        val message = readText("related location message")
        model("related location") { RelatedDiagnostic(location, message) }
      }
      val fixes = readList(limits.maximumFixes, "diagnostic fix count") {
        val title = readText("diagnostic fix title")
        val edits = readList(limits.maximumEditsPerFix, "diagnostic edit count") {
          DiagnosticTextEdit(readLocation(), readText("diagnostic edit replacement"))
        }
        model("diagnostic fix") { DiagnosticFix(title, edits) }
      }
      return model("diagnostic record") {
        CompilerDiagnostic(stage, severity, code, message, arguments, primaryLocation, related, fixes)
      }
    }

    fun readByte(context: String): UByte {
      requireAvailable(1, context)
      return bytes[offset++].toUByte()
    }

    fun readU16(context: String): Int {
      val low = readByte(context).toInt()
      val high = readByte(context).toInt()
      return low or (high shl 8)
    }

    fun fail(context: String, message: String): Nothing =
      throw DiagnosticProtocolException(offset, context, message)

    fun requireEnd() {
      if (offset != bytes.size) {
        fail("document", "${bytes.size - offset} trailing bytes remain")
      }
    }

    private fun readLocation(): SourceLocation {
      val source = readText("diagnostic source")
      val start = SourcePosition(readU32("diagnostic start line"), readU32("diagnostic start column"))
      val end = SourcePosition(readU32("diagnostic end line"), readU32("diagnostic end column"))
      return model("diagnostic location") { SourceLocation(source, SourceRange(start, end)) }
    }

    private fun readBoolean(context: String): Boolean =
      when (val tag = readByte(context).toInt()) {
        0 -> false
        1 -> true
        else -> fail(context, "invalid Boolean tag $tag")
      }

    private fun readU32(context: String): UInt {
      var result = 0u
      repeat(UInt.SIZE_BYTES) { shift ->
        result = result or (readByte(context).toUInt() shl (shift * Byte.SIZE_BITS))
      }
      return result
    }

    private fun readText(context: String): String {
      val scalarCount = readCount(limits.maximumTextScalars, "$context scalar count")
      val builder = StringBuilder(scalarCount)
      repeat(scalarCount) {
        val scalar = readU32("$context scalar").toLong()
        if (scalar > Character.MAX_CODE_POINT || scalar in 0xD800L..0xDFFFL) {
          fail(context, "invalid Unicode scalar U+${scalar.toString(16).uppercase()}")
        }
        builder.appendCodePoint(scalar.toInt())
      }
      return builder.toString()
    }

    fun readCount(limit: UInt, context: String): Int {
      val count = readU32(context)
      if (count > limit || count > Int.MAX_VALUE.toUInt()) {
        fail(context, "count $count exceeds configured limit $limit")
      }
      return count.toInt()
    }

    private fun <T> readList(limit: UInt, context: String, readElement: () -> T): List<T> =
      List(readCount(limit, context)) { readElement() }

    private fun <T : Enum<T>> readEnum(values: List<T>, context: String): T {
      val tag = readByte(context).toInt()
      return values.getOrNull(tag) ?: fail(context, "unknown tag $tag")
    }

    private inline fun <T> model(context: String, constructor: () -> T): T =
      try {
        constructor()
      } catch (problem: IllegalArgumentException) {
        fail(context, problem.message ?: "invalid protocol model")
      }

    private fun requireAvailable(count: Int, context: String) {
      if (count < 0 || offset > bytes.size - count) {
        fail(context, "unexpected end of diagnostic document")
      }
    }
  }
}
