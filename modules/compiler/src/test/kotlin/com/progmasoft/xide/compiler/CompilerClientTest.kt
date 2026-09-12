/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.compiler

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
        Files.write(Path.of(invocation.environment.getValue("VXS_DIAGNOSTICS_FILE")), WireWriter().header(0).toByteArray())
        CompilerProcessResult(0, "checked\n".toByteArray(), byteArrayOf(), false)
      }

      val result = VisualXSharpCompilerClient(runner).check(CompilerRequest(source))

      assertEquals(listOf("vxs", "check", "-File", source.toString()), observed?.command)
      assertEquals(source.parent, observed?.workingDirectory)
      assertTrue(result.succeeded)
      assertEquals("checked\n", result.standardOutput)
      assertTrue(result.diagnostics.diagnostics.isEmpty())
      assertFalse(Files.exists(Path.of(observed!!.environment.getValue("VXS_DIAGNOSTICS_FILE"))))
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
            .text("VXP1")
            .text("expected declaration")
            .count(0)
            .boolean(false)
            .count(0)
            .count(0)
            .toByteArray()
        Files.write(Path.of(invocation.environment.getValue("VXS_DIAGNOSTICS_FILE")), diagnostic)
        CompilerProcessResult(1, byteArrayOf(), "human fallback".toByteArray(), false)
      }

      val result = VisualXSharpCompilerClient(runner).check(CompilerRequest(source))

      assertFalse(result.succeeded)
      assertEquals(1, result.exitCode)
      assertEquals("VXP1", result.diagnostics.diagnostics.single().code)
      assertEquals("human fallback", result.standardError)
    }
  }

  @Test
  fun refusesToParseTerminalTextWhenTheProtocolFileIsMissing() {
    withSource { source ->
      val runner = CompilerProcessRunner {
        CompilerProcessResult(1, byteArrayOf(), "VXP1: tempting but unstructured".toByteArray(), false)
      }

      assertFailsWith<CompilerClientException> {
        VisualXSharpCompilerClient(runner).check(CompilerRequest(source))
      }
    }
  }

  @Test
  fun timeoutWithoutAProtocolFileReturnsAnExplicitTimeoutResult() {
    withSource { source ->
      val runner = CompilerProcessRunner { CompilerProcessResult(-1, byteArrayOf(), byteArrayOf(), true) }

      val result = VisualXSharpCompilerClient(runner).check(CompilerRequest(source))

      assertTrue(result.timedOut)
      assertFalse(result.succeeded)
      assertTrue(result.diagnostics.diagnostics.isEmpty())
    }
  }

  @Test
  fun rejectsMissingSourceFilesBeforeStartingAProcess() {
    val missing = Path.of("C:/definitely-missing-xide-test/Main.vxs").toAbsolutePath()
    var invoked = false
    val runner = CompilerProcessRunner {
      invoked = true
      error("must not run")
    }

    assertFailsWith<IllegalArgumentException> {
      VisualXSharpCompilerClient(runner).check(CompilerRequest(missing))
    }
    assertFalse(invoked)
  }

  private fun withSource(action: (Path) -> Unit) {
    val directory = createTempDirectory("xide-compiler-test-")
    val source = directory.resolve("Main.vxs")
    try {
      Files.writeString(source, "namespace Example;\n")
      action(source)
    } finally {
      Files.deleteIfExists(source)
      Files.deleteIfExists(directory)
    }
  }
}
