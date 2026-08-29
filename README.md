<!--
SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
-->

# Xide

Xide is the planned Visual X# integrated development environment. The renewed application is implemented with Kotlin/JVM
25 and Compose Multiplatform. Xide is a future project, is not a primary development focus at present, and is not ready for
daily use. Current work is limited to small foundation slices that keep the intended architecture coherent.

## Architecture

The application shell, editor integration, project model, language services, indexing, commands, settings, and extension
host are Kotlin/JVM components. Compose Multiplatform owns the desktop UI. Xide uses a suitable JDK installed on the system;
the Xide distribution does not bundle a JDK.

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

The `xide-document` module is the first renewed Kotlin/JVM 25 component. It provides:

- immutable, versioned document snapshots;
- validated UTF-16 offset ranges and text edits;
- LF, CRLF, and CR-aware line/column conversion;
- supplementary-character handling compatible with JVM and LSP UTF-16 coordinates; and
- stale-version protection for concurrent editor consumers.

Its API uses the `com.progmasoft.xide.document` package. The next application slices will build the Compose desktop shell,
settings loader, and extension host around this Kotlin foundation.

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

JDK 25 is required. Run the document foundation checks with the Gradle wrapper:

```text
gradlew.bat check
```

## License

Xide is licensed under `MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0`. The exception permits static and dynamic
linking with independent components under licenses of their choice, including proprietary licenses, while Xide files
and modifications to those files remain subject to MPL-2.0. See `LICENSE.txt` and
`LICENSES/AdditionRef-Progmasoft-Exception-1.0.txt`. The separate Progmasoft Patent Grant, Version 1.0, is documented in
`PATENTS` and `LICENSES/AdditionRef-Progmasoft-Patent-Grant-1.0.txt`.
