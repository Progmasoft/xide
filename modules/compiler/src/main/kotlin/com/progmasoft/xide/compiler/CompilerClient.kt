/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.compiler

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
 * The client never parses terminal text. It reserves a unique side-channel file for each request and keeps that file
 * in place while the compiler writes it, avoiding a name-reuse window between temporary-file creation and process
 * launch. The protocol file is deleted in a `finally` block, so a crashed compiler cannot make Xide consume a previous
 * request's diagnostics.
 */
class VisualXSharpCompilerClient(
  private val runner: CompilerProcessRunner = SystemCompilerProcessRunner(),
  private val limits: DiagnosticProtocolLimits = DiagnosticProtocolLimits(),
) {
  fun check(request: CompilerRequest): CompilerResult {
    require(Files.isRegularFile(request.source)) { "compiler source must identify a regular file" }
    require(Files.isDirectory(request.workingDirectory)) { "compiler working directory must exist" }

    val diagnosticsPath = Files.createTempFile("xide-diagnostics-", ".vxdg")
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
    if (size == 0L && process.timedOut) return DiagnosticDocument(emptyList())
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
    require(invocation.command.isNotEmpty()) { "compiler invocation must contain an executable" }
    val builder = ProcessBuilder(invocation.command)
    builder.directory(invocation.workingDirectory.toFile())
    val environment = builder.environment()
    environment.putAll(invocation.environment)
    val command = invocation.command.toMutableList()
    val pathValue = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value
    command[0] =
      resolveCompilerExecutable(command[0], invocation.workingDirectory, pathValue).toString()
    builder.command(command)
    val process = builder.start()
    process.outputStream.close()

    val standardOutput = StreamCapture(process.inputStream, maximumStreamBytes, "standard output")
    val standardError = StreamCapture(process.errorStream, maximumStreamBytes, "standard error")
    val outputThread = thread(name = "xide-vxs-stdout", isDaemon = true) { standardOutput.read() }
    val errorThread = thread(name = "xide-vxs-stderr", isDaemon = true) { standardError.read() }

    val completed = process.waitFor(invocation.timeout.toMillis(), TimeUnit.MILLISECONDS)
    if (!completed) {
      terminateProcessTree(process)
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

  /** A timed-out compiler must not leave helpers alive or keep its inherited output pipes open. */
  private fun terminateProcessTree(process: Process) {
    val descendants = process.toHandle().descendants().toList().asReversed()
    descendants.forEach { if (it.isAlive) it.destroy() }
    process.destroy()
    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
    descendants.forEach { if (it.isAlive) it.destroyForcibly() }
    if (process.isAlive) process.destroyForcibly()
    process.waitFor()
  }

  /**
   * Resolves bare tool names before setting the child working directory. In particular, Windows process creation can
   * search the current directory for a bare executable name; a project containing `vxs.exe` must not shadow the
   * compiler selected from the user's absolute PATH entries.
   */
  internal fun resolveCompilerExecutable(
    executable: String,
    workingDirectory: Path,
    pathValue: String?,
    windows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
  ): Path {
    val requested = Path.of(executable)
    if (requested.isAbsolute) return requested.normalize()
    if (requested.parent != null) return workingDirectory.resolve(requested).normalize()

    val pathEntries = pathValue?.split(File.pathSeparatorChar).orEmpty()
    val filename = requested.fileName.toString()
    val hasExtension = filename.substringAfterLast('.', missingDelimiterValue = "").isNotEmpty()
    val candidates =
      if (windows && !hasExtension) {
        listOf("$filename.exe", "$filename.com")
      } else {
        listOf(filename)
      }

    pathEntries.forEach { entry ->
      val pathEntry = entry.trim().removeSurrounding("\"")
      if (pathEntry.isEmpty()) return@forEach

      val directory = runCatching { Path.of(pathEntry) }.getOrNull() ?: return@forEach
      // Empty and relative PATH segments implicitly mean the working directory on some platforms. Ignore them so
      // opening a project cannot turn its files into executable search candidates.
      if (!directory.isAbsolute) return@forEach

      candidates.forEach { candidateName ->
        val candidate = directory.resolve(candidateName)
        if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
          return candidate.toAbsolutePath().normalize()
        }
      }
    }

    throw IOException("compiler executable '$executable' was not found in an absolute PATH entry")
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
