/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.app

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.decodeToSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Classpath location of the vector application icon, relative to this package. */
internal const val APPLICATION_ICON_RESOURCE = "Xide-App.svg"

/**
 * Loads the bundled vector icon. A missing resource is a packaging defect, so it fails at start-up with the
 * resource name instead of silently showing the platform's default window icon.
 */
internal fun loadApplicationIcon(): Painter {
  val stream =
    checkNotNull(WorkspaceSession::class.java.getResourceAsStream(APPLICATION_ICON_RESOURCE)) {
      "application icon resource $APPLICATION_ICON_RESOURCE is missing from the classpath"
    }
  return stream.use { it.readBytes() }.decodeToSvgPainter(Density(1f))
}

fun main() = application {
  val icon = remember { loadApplicationIcon() }
  Window(
    onCloseRequest = ::exitApplication,
    title = "Xide",
    icon = icon,
  ) {
    window.minimumSize = java.awt.Dimension(720, 480)
    XideApplication(window)
  }
}
