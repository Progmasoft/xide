/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.compiler

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class CompilerRequest(
  val source: Path,
  val workingDirectory: Path = source.toAbsolutePath().normalize().parent ?: source.toAbsolutePath().normalize(),
  val executable: Path = Path.of("vxs"),
  val timeout: Duration = Duration.ofSeconds(30),
) {
  init {
    require(source.isAbsolute) { "compiler source path must be absolute" }
    require(workingDirectory.isAbsolute) { "compiler working directory must be absolute" }
    require(!timeout.isNegative && !timeout.isZero) { "compiler timeout must be positive" }
  }
}

data class CompilerResult(
  val exitCode: Int,
  val diagnostics: DiagnosticDocument,
  val standardOutput: String,
  val standardError: String,
  val timedOut: Boolean,
) {
  val succeeded: Boolean
    get() = exitCode == 0 && !timedOut && diagnostics.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/** A narrow process abstraction keeps command construction and stale-file handling independently testable. */
fun interface CompilerProcessRunner {
  fun run(invocation: CompilerInvocation): CompilerProcessResult
}

data class CompilerInvocation(
  val command: List<String>,
  val workingDirectory: Path,
  val environment: Map<String, String>,
  val timeout: Duration,
)

data class CompilerProcessResult(
  val exitCode: Int,
  val standardOutput: ByteArray,
  val standardError: ByteArray,
  val timedOut: Boolean,
)

/**
 * Invokes the single public `vxs` driver and consumes diagnostics from its private protocol file.
 *
 * The client never parses terminal text. It creates a unique side-channel path for each request, removes the empty
 * placeholder before launch, and deletes the protocol file in a `finally` block. A crashed compiler therefore cannot
 * make Xide consume a previous request's diagnostics.
 */
class VisualXSharpCompilerClient(
  private val runner: CompilerProcessRunner = SystemCompilerProcessRunner(),
  private val limits: DiagnosticProtocolLimits = DiagnosticProtocolLimits(),
) {
  fun check(request: CompilerRequest): CompilerResult {
    require(Files.isRegularFile(request.source)) { "compiler source must identify a regular file" }
    require(Files.isDirectory(request.workingDirectory)) { "compiler working directory must exist" }

    val diagnosticsPath = Files.createTempFile("xide-diagnostics-", ".vxdg")
    Files.deleteIfExists(diagnosticsPath)
    try {
      val invocation =
        CompilerInvocation(
          command = listOf(request.executable.toString(), "check", "-File", request.source.toString()),
          workingDirectory = request.workingDirectory,
          environment = mapOf(DIAGNOSTICS_ENVIRONMENT to diagnosticsPath.toString()),
          timeout = request.timeout,
        )
      val process = runner.run(invocation)
      val document = readDocument(diagnosticsPath, process)
      return CompilerResult(
        exitCode = process.exitCode,
        diagnostics = document,
        standardOutput = decodeTerminalText(process.standardOutput, "standard output"),
        standardError = decodeTerminalText(process.standardError, "standard error"),
        timedOut = process.timedOut,
      )
    } finally {
      Files.deleteIfExists(diagnosticsPath)
    }
  }

  private fun readDocument(path: Path, process: CompilerProcessResult): DiagnosticDocument {
    if (!Files.exists(path)) {
      if (process.timedOut) return DiagnosticDocument(emptyList())
      throw CompilerClientException("compiler did not produce a structured diagnostic document")
    }
    val size = Files.size(path)
    if (size > limits.maximumWireBytes) {
      throw CompilerClientException("compiler diagnostic document exceeds ${limits.maximumWireBytes} bytes")
    }
    return try {
      DiagnosticProtocol.decode(Files.readAllBytes(path), limits)
    } catch (problem: DiagnosticProtocolException) {
      throw CompilerClientException("compiler produced an invalid diagnostic document", problem)
    }
  }

  private fun decodeTerminalText(bytes: ByteArray, streamName: String): String {
    if (bytes.size > MAXIMUM_TERMINAL_BYTES) {
      throw CompilerClientException("compiler $streamName exceeds $MAXIMUM_TERMINAL_BYTES bytes")
    }
    return String(bytes, StandardCharsets.UTF_8)
  }

  private companion object {
    const val DIAGNOSTICS_ENVIRONMENT = "VXS_DIAGNOSTICS_FILE"
    const val MAXIMUM_TERMINAL_BYTES = 4 * 1024 * 1024
  }
}

class CompilerClientException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Production runner with concurrent bounded stream drains to prevent child-process pipe deadlocks. */
class SystemCompilerProcessRunner(
  private val maximumStreamBytes: Int = 4 * 1024 * 1024,
) : CompilerProcessRunner {
  init {
    require(maximumStreamBytes > 0) { "maximum stream byte count must be positive" }
  }

  override fun run(invocation: CompilerInvocation): CompilerProcessResult {
    val builder = ProcessBuilder(invocation.command)
    builder.directory(invocation.workingDirectory.toFile())
    builder.environment().putAll(invocation.environment)
    val process = builder.start()
    process.outputStream.close()

    val standardOutput = StreamCapture(process.inputStream, maximumStreamBytes, "standard output")
    val standardError = StreamCapture(process.errorStream, maximumStreamBytes, "standard error")
    val outputThread = thread(name = "xide-vxs-stdout", isDaemon = true) { standardOutput.read() }
    val errorThread = thread(name = "xide-vxs-stderr", isDaemon = true) { standardError.read() }

    val completed = process.waitFor(invocation.timeout.toMillis(), TimeUnit.MILLISECONDS)
    if (!completed) {
      process.destroy()
      if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
      process.waitFor()
    }
    outputThread.join()
    errorThread.join()

    standardOutput.failure?.let { throw CompilerClientException("could not capture compiler standard output", it) }
    standardError.failure?.let { throw CompilerClientException("could not capture compiler standard error", it) }
    return CompilerProcessResult(
      exitCode = if (completed) process.exitValue() else TIMEOUT_EXIT_CODE,
      standardOutput = standardOutput.bytes(),
      standardError = standardError.bytes(),
      timedOut = !completed,
    )
  }

  private class StreamCapture(
    private val input: InputStream,
    private val limit: Int,
    private val name: String,
  ) {
    private val output = ByteArrayOutputStream()
    @Volatile var failure: Throwable? = null
      private set

    fun read() {
      try {
        input.use { stream ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() > limit - count) {
              throw CompilerClientException("compiler $name exceeds $limit bytes")
            }
            output.write(buffer, 0, count)
          }
        }
      } catch (problem: Exception) {
        failure = problem
      }
    }

    fun bytes(): ByteArray = output.toByteArray()
  }

  private companion object {
    const val TIMEOUT_EXIT_CODE = -1
  }
}
