<!--
SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
SPDX-License-Identifier: MPL-2.0
-->

# Xide

Xide is the native integrated development environment for X#. The project is intentionally in its earliest foundation
stage; it is not ready for daily use.

## Architecture

- The IDE, project model, editor model, language services, indexing, extension host, and commands use Java 25.
- XIT (Xide Toolkit) will provide the native widget, layout, input, accessibility, and rendering layers in
  Objective-C23.
- Java and XIT will communicate through a narrow, versioned C ABI exposed to Java through FFM.
- XIT is purpose-built for Xide. It does not use GTK, Qt, Tauri, Electron, Skia, or a browser runtime.

Planned platform backends are AppKit/Metal/Core Text on macOS, Wayland or XCB/OpenGL 4.5/FreeType/HarfBuzz on Linux,
and Win32/D3D11/DirectWrite on Windows.

## Current slice

The first `xide-document` module provides a small UTF-16-aware document snapshot model:

- immutable, versioned snapshots;
- validated offset ranges and text edits;
- line/column conversion using Java and LSP-compatible UTF-16 code units;
- stale-version protection for concurrent editor consumers.

No UI, renderer, plugin API, language server, or native ABI is claimed yet.

## Build

Xide requires JDK 25. Use the pinned Gradle wrapper:

```text
./gradlew check
```

## License

Xide is licensed under the Mozilla Public License 2.0.
