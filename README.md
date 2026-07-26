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

Xide's built-in language priority is X#, Java, Swift, Kotlin/JVM, Objective-C, Objective-C++, C++, and C, in that order.
Xide will integrate with `xs`, SwiftPM, Gradle, CMake, and Conan. Languages outside this list, including Rust, belong to
community extensions rather than the built-in IDE; therefore Xide does not plan an official Cargo integration.

XIT is the language-independent native toolkit used by Xide. It does not own language support or build-system
integration.

## Current slice

The first `xide-document` module provides a small UTF-16-aware document snapshot model:

- immutable, versioned snapshots;
- validated offset ranges and text edits;
- line/column conversion using Java and LSP-compatible UTF-16 code units;
- stale-version protection for concurrent editor consumers.

The first XIT slice provides a versioned C ABI runtime probe backed by Objective-C23 and GNUstep Base on Linux. It
establishes the native-library boundary and FFM-compatible data layout without defining widgets or renderer behavior.

No UI, renderer, plugin API, or language server is claimed yet.

## Build

Xide requires JDK 25. Use the pinned Gradle wrapper:

```text
./gradlew check
```

Build the current Linux XIT slice with Clang, LLD, Ninja, and GNUstep Base:

```text
cmake --preset clang-debug -S xit
cmake --build xit/build/clang-debug
ctest --test-dir xit/build/clang-debug --output-on-failure
```

## License

Xide is licensed under the Mozilla Public License 2.0.
