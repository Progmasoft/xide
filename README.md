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

Planned platform backends use Metal/Core Text on macOS, native Wayland and native X11 through XCB on Linux, and
Win32/D3D11/DirectWrite on Windows. The Linux renderer selects an OpenGL 4.5 core context when supported and otherwise
uses OpenGL 4.2 core. It does not route the Wayland backend through XWayland.

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

The first XIT slice provides a versioned C ABI runtime probe and an Objective-C23 X11-native presentation surface on
Linux. XCB owns the native window and event loop; EGL creates an OpenGL core context; XIT clears and presents a black
frame. GNUstep Base is used by the runtime probe, not as a GUI toolkit. The public backend boundary reserves a separate
Wayland-native implementation without silently falling back to X11.

No widget system, layout engine, plugin API, or language server is claimed yet.

## Build

Xide requires JDK 25. Use the pinned Gradle wrapper:

```text
./gradlew check
```

Build the current Linux XIT slice with Clang, LLD, Ninja, GNUstep Base, XCB, EGL, and OpenGL:

```text
cmake --preset clang-debug -S xit
cmake --build xit/build/clang-debug
ctest --test-dir xit/build/clang-debug --output-on-failure
```

Run the first native X11 surface:

```text
xit/build/clang-debug/xit_black_surface
```

## License

Xide is licensed under the Mozilla Public License 2.0.
