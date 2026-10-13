/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.compiler

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompilerClientTest {
  @Test
  fun invokesThePublicCheckCommandAndConsumesThePrivateSideChannel() {
    withSource { source ->
      var observed: CompilerInvocation? = null
      val runner = CompilerProcessRunner { invocation ->
        observed = invocation
        val diagnosticsPath = Path.of(invocation.environment.getValue("TALA_DIAGNOSTICS_FILE"))
        assertTrue(Files.isRegularFile(diagnosticsPath))
        assertEquals(0L, Files.size(diagnosticsPath))
        Files.write(diagnosticsPath, WireWriter().header(0).toByteArray())
        CompilerProcessResult(0, "checked\n".toByteArray(), byteArrayOf(), false)
      }

      val result = TalaCompilerClient(runner).check(CompilerRequest(source, "Demo.Program"))

      assertEquals(
        listOf("talac", "--entry=Demo.Program", "--emit=metadata", "--", source.toString()),
        observed?.command,
      )
      assertEquals(source.parent, observed?.workingDirectory)
      assertTrue(result.succeeded)
      assertEquals("checked\n", result.standardOutput)
      assertTrue(result.diagnostics.diagnostics.isEmpty())
      assertFalse(Files.exists(Path.of(observed!!.environment.getValue("TALA_DIAGNOSTICS_FILE"))))
    }
  }

  @Test
  fun compilerErrorsRemainAValidStructuredResult() {
    withSource { source ->
      val runner = CompilerProcessRunner { invocation ->
        val diagnostic =
          WireWriter()
            .header(1)
            .byte(DiagnosticStage.PARSER.ordinal)
            .byte(DiagnosticSeverity.ERROR.ordinal)
            .text("TAP0001")
            .text("expected declaration")
            .count(0)
            .boolean(false)
            .count(0)
            .count(0)
            .toByteArray()
        Files.write(Path.of(invocation.environment.getValue("TALA_DIAGNOSTICS_FILE")), diagnostic)
        CompilerProcessResult(1, byteArrayOf(), "human fallback".toByteArray(), false)
      }

      val result = TalaCompilerClient(runner).check(CompilerRequest(source, "Demo.Program"))

      assertFalse(result.succeeded)
      assertEquals(1, result.exitCode)
      assertEquals("TAP0001", result.diagnostics.diagnostics.single().code)
      assertEquals("human fallback", result.standardError)
    }
  }

  @Test
  fun refusesToParseTerminalTextWhenTheProtocolFileIsMissing() {
    withSource { source ->
      val runner = CompilerProcessRunner {
        CompilerProcessResult(1, byteArrayOf(), "TAP0001: tempting but unstructured".toByteArray(), false)
      }

      assertFailsWith<CompilerClientException> {
        TalaCompilerClient(runner).check(CompilerRequest(source, "Demo.Program"))
      }
    }
  }

  @Test
  fun removesTheCheckRecordItCausedAndKeepsOneThatWasThere() {
    withSource { source ->
      val record = source.resolveSibling("Main.talameta")
      val runner = CompilerProcessRunner { invocation ->
        Files.write(Path.of(invocation.environment.getValue("TALA_DIAGNOSTICS_FILE")), WireWriter().header(0).toByteArray())
        Files.writeString(record, "talameta 1\n")
        CompilerProcessResult(0, byteArrayOf(), byteArrayOf(), false)
      }

      TalaCompilerClient(runner).check(CompilerRequest(source, "Demo.Program"))
      assertFalse(Files.exists(record))

      Files.writeString(record, "kept by its owner")
      TalaCompilerClient(runner).check(CompilerRequest(source, "Demo.Program"))
      assertTrue(Files.exists(record))
      Files.delete(record)
    }
  }

  @Test
  fun anEntryIsAClassWrittenWithItsNamespace() {
    withSource { source ->
      listOf("Program", "", "Demo.", "Demo..Program", "--emit=core", "Demo.Pro gram").forEach { entry ->
        assertFailsWith<IllegalArgumentException> { CompilerRequest(source, entry) }
      }
      assertEquals("A.B.Program", CompilerRequest(source, "A.B.Program").entry)
    }
  }

  @Test
  fun timeoutWithoutAProtocolFileReturnsAnExplicitTimeoutResult() {
    withSource { source ->
      val runner = CompilerProcessRunner { CompilerProcessResult(-1, byteArrayOf(), byteArrayOf(), true) }

      val result = TalaCompilerClient(runner).check(CompilerRequest(source, "Demo.Program"))

      assertTrue(result.timedOut)
      assertFalse(result.succeeded)
      assertTrue(result.diagnostics.diagnostics.isEmpty())
    }
  }

  @Test
  fun bareCompilerLookupCannotBeShadowedByTheProjectDirectoryOrRelativePathEntries() {
    val directory = createTempDirectory("xide-compiler-search-test-")
    val projectDirectory = Files.createDirectories(directory.resolve("project"))
    val toolDirectory = Files.createDirectories(directory.resolve("tools"))
    val projectExecutable = projectDirectory.resolve("talac.exe")
    val pathExecutable = toolDirectory.resolve("talac.exe")
    try {
      Files.writeString(projectExecutable, "project-provided executable")
      Files.writeString(pathExecutable, "PATH-provided executable")

      val resolved =
        SystemCompilerProcessRunner().resolveCompilerExecutable(
          executable = "talac",
          workingDirectory = projectDirectory,
          pathValue = ".${File.pathSeparator}$toolDirectory",
          windows = true,
        )

      assertEquals(pathExecutable.toAbsolutePath().normalize(), resolved)
    } finally {
      directory.toFile().deleteRecursively()
    }
  }

  @Test
  fun rejectsMissingSourceFilesBeforeStartingAProcess() {
    val missing = Path.of("C:/definitely-missing-xide-test/Main.tala").toAbsolutePath()
    var invoked = false
    val runner = CompilerProcessRunner {
      invoked = true
      error("must not run")
    }

    assertFailsWith<IllegalArgumentException> {
      TalaCompilerClient(runner).check(CompilerRequest(missing, "Demo.Program"))
    }
    assertFalse(invoked)
  }

  private fun withSource(action: (Path) -> Unit) {
    val directory = createTempDirectory("xide-compiler-test-")
    val source = directory.resolve("Main.tala")
    try {
      Files.writeString(source, "namespace Example;\n")
      action(source)
    } finally {
      Files.deleteIfExists(source)
      Files.deleteIfExists(directory)
    }
  }
}
