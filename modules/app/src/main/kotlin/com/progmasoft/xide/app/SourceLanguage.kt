/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import java.nio.file.Path

/**
 * The languages whose sources the editor opens, in the order of their built-in support priority.
 *
 * Recognizing a language here means only that its files can be opened, edited and saved as text. Analysis is a
 * separate capability: the compiler check is connected for Visual X# alone.
 *
 * @property displayName the name shown in the status bar and in messages.
 * @property extensions the file-name extensions of the language, without the dot, compared exactly.
 */
enum class SourceLanguage(val displayName: String, val extensions: List<String>) {
  /** Visual X# sources, the language Xide exists for. */
  VISUAL_XSHARP("Visual X#", listOf("vxs")),

  /** Kotlin sources and Kotlin scripts, including Gradle Kotlin build scripts. */
  KOTLIN("Kotlin", listOf("kt", "kts")),

  /** Java sources. */
  JAVA("Java", listOf("java")),

  /** Groovy sources and Groovy-based Gradle build scripts. */
  GROOVY("Groovy", listOf("groovy", "gradle")),

  /** Python sources. */
  PYTHON("Python", listOf("py"));

  /** Language lookup by file name. */
  companion object {
    /** Every recognized extension, in language order. */
    val allExtensions: List<String> = entries.flatMap { it.extensions }

    /**
     * The language of a file name, or null when its extension is not one Xide opens.
     *
     * The extension is the text after the last dot and is compared exactly, so `Main.VXS` and a file without an
     * extension are not recognized.
     */
    fun ofFileName(name: String): SourceLanguage? {
      val dot = name.lastIndexOf('.')
      if (dot <= 0 || dot == name.length - 1) return null
      val extension = name.substring(dot + 1)
      return entries.firstOrNull { extension in it.extensions }
    }

    /** The language of a path's file name; see [ofFileName]. */
    fun of(path: Path): SourceLanguage? = path.fileName?.toString()?.let(::ofFileName)

    /** A sentence fragment that lists what can be opened, for messages. */
    fun supportedDescription(): String = entries.joinToString(", ") { "${it.displayName} (${it.extensions.joinToString(", ") { e -> ".$e" }})" }
  }
}
