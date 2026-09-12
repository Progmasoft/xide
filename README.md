<!--
SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
-->

# Xide

Xide is the planned Visual X# integrated development environment. The renewed application is implemented with Kotlin/JVM
25 and Compose Multiplatform. Xide is a future project, is not a primary development focus at present, and is not ready for
daily use. Current work is limited to small foundation slices that keep the intended architecture coherent.

## Architecture

The application shell, editor integration, project model, language services, indexing, commands, settings, and extension
host are Kotlin/JVM components. Compose Multiplatform owns the desktop UI. Development uses a system JDK 25, while the
native Xide distribution contains a minimized Java runtime image rather than requiring users to install a full JDK.

Built-in language support is prioritized in this order:

1. Visual X#
2. Kotlin
3. Java
4. Groovy
5. Python

Extensions are Kotlin/JVM JARs. They are loaded from the platform-specific extension directory:

- Windows: `%LOCALAPPDATA%\Xide\Extensions\`
- Linux and macOS: `$HOME/.xide/Extensions/`

The previous Objective-C XIT experiment remains in the repository only as historical implementation material. It is not
the renewed application toolkit or the architectural direction for new Xide code.

## Current foundation

The renewed Kotlin/JVM foundation currently has three modules. `xide-document` provides:

- immutable, versioned document snapshots;
- validated UTF-16 offset ranges and text edits;
- LF, CRLF, and CR-aware line/column conversion;
- supplementary-character handling compatible with JVM and LSP UTF-16 coordinates; and
- stale-version protection for concurrent editor consumers.

`xide-compiler` consumes the bounded VXDG v1 structured diagnostic protocol and invokes the single public `vxs` driver.
Every request uses a unique temporary side channel, drains process output concurrently, enforces a timeout, and refuses to
reconstruct diagnostics by scraping terminal text. `xide-app` binds results to the exact document version that was checked,
discards stale asynchronous results after edits, and renders accepted records in its problems surface.

The application module also owns the first real Compose desktop slice: an application window, native `.vxs` open/save
dialogs, atomic filesystem saves, dirty editor indicators, open-editor navigation, scratch documents, a text editor,
compiler Check action, Problems surface, and an activity-aware status bar. UI updates pass through the versioned document
API rather than maintaining a second mutable text model. Settings loading, project navigation, diagnostic navigation,
quick-fix application, and the extension host remain future slices.

## Settings

User settings are Kotlin scripts named `Settings.xide.kts`:

- Windows: `%APPDATA%\Xide\User\Settings.xide.kts`
- Linux and macOS: `$HOME/.config/Xide/User/Settings.xide.kts`

The renewed settings model covers appearance, editor behavior, and terminal typography. A settings file is optional; Xide
uses built-in defaults for values that are not configured.

## Installation

Xide installation and updates are managed by ProgmaIDEs Toolbox. Install the toolbox globally with Visual X#:

```text
vxs install -Global Progmasoft.IdeToolbox
```

Open ProgmaIDEs Toolbox and install Xide from there. Automatic updates belong to Toolbox rather than to the Xide process.

## Development

JDK 25 is required. Run all Kotlin, document, and Compose application checks with the Gradle wrapper:

```text
gradlew.bat check
```

Launch the current desktop shell during development with:

```text
gradlew.bat :modules:app:run
```

## License

Xide is licensed under `MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1`. The exception permits static and dynamic
linking with independent components under licenses of their choice, including proprietary licenses, while Xide files
and modifications to those files remain subject to MPL-2.0. See `LICENSE.txt` and
`LICENSES/AdditionRef-Progmasoft-Exception-1.1.txt`. The separate Progmasoft Patent Grant, Version 1.1, is documented in
`PATENTS` and `LICENSES/AdditionRef-Progmasoft-Patent-Grant-1.1.txt`.
