/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progmasoft.xide.compiler.DiagnosticSeverity

/**
 * The dark palette of the shell.
 *
 * The layers follow the usual IDE convention: the editor is the darkest surface, tool windows and bars sit one step
 * above it, and thin borders separate neighbouring surfaces instead of gaps.
 */
internal object XideColors {
  val editor = Color(0xFF1E1F22)
  val panel = Color(0xFF2B2D30)
  val border = Color(0xFF393B40)
  val hover = Color(0xFF35373B)
  val selection = Color(0xFF2E436E)
  val text = Color(0xFFDFE1E5)
  val mutedText = Color(0xFF868A91)
  val gutterText = Color(0xFF606366)
  val accent = Color(0xFF3574F0)
  val error = Color(0xFFF75464)
  val warning = Color(0xFFF2C55C)
  val information = Color(0xFF56A8F5)
  val folder = Color(0xFFB0895A)
  val source = Color(0xFF8E7CFF)

  fun severity(severity: DiagnosticSeverity): Color =
    when (severity) {
      DiagnosticSeverity.ERROR -> error
      DiagnosticSeverity.WARNING -> warning
      DiagnosticSeverity.INFORMATION -> information
      DiagnosticSeverity.HINT -> mutedText
    }
}

/** Sizes shared by the surfaces that must line up with each other. */
internal object XideMetrics {
  val stripeWidth = 40.dp
  val toolbarHeight = 40.dp
  val headerHeight = 32.dp
  val projectWidth = 260.dp
  val problemsHeight = 190.dp
  val statusHeight = 24.dp
  val treeIndent = 16.dp
  val editorPadding = 8.dp
}

/** Text styles of the shell. The editor and its gutter share one style so their lines stay aligned. */
internal object XideType {
  val ui = TextStyle(color = XideColors.text, fontSize = 13.sp)
  val uiMuted = TextStyle(color = XideColors.mutedText, fontSize = 13.sp)
  val small = TextStyle(color = XideColors.text, fontSize = 12.sp)
  val smallMuted = TextStyle(color = XideColors.mutedText, fontSize = 12.sp)
  val code = TextStyle(color = XideColors.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
}

/** Visible name of a severity in the Problems tool window. */
internal fun DiagnosticSeverity.label(): String =
  when (this) {
    DiagnosticSeverity.ERROR -> "Error"
    DiagnosticSeverity.WARNING -> "Warning"
    DiagnosticSeverity.INFORMATION -> "Information"
    DiagnosticSeverity.HINT -> "Hint"
  }
