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

The previous Objective-C XIT experiment is preserved under `legacy/xit/` only as
historical implementation material. It is not the renewed application toolkit,
an active CI target, or the architectural direction for new Xide code. Current
CodeQL extraction covers Kotlin/JVM and GitHub Actions, not this retired toolkit.

## Current foundation

The renewed Kotlin/JVM foundation currently has five modules. `xide-document` provides:

- immutable, versioned document snapshots;
- validated UTF-16 offset ranges and text edits;
- LF, CRLF, and CR-aware line/column conversion;
- supplementary-character handling compatible with JVM and LSP UTF-16 coordinates;
- conversion of the compiler's zero-based line and Unicode-scalar column positions to UTF-16 offsets, rejecting
  positions outside the snapshot instead of clamping them; and
- stale-version protection for concurrent editor consumers.

`xide-compiler` consumes the bounded VXDG v1 structured diagnostic protocol and invokes the single public `vxs` driver.
Every request uses a unique temporary side channel, drains process output concurrently, enforces a timeout, and refuses to
reconstruct diagnostics by scraping terminal text. `xide-app` binds results to the exact document version that was checked,
discards stale asynchronous results after edits, and renders accepted records in its problems surface.

The application module also owns the Compose desktop shell. Its arrangement follows the familiar IDE layout, which is
the direction for the final design: a toolbar on top, a stripe of tool-window buttons on the left edge, the Project
tool window beside it, a tabbed editor in the centre with the Problems tool window below it, and a status bar at the
bottom. UI updates pass through the versioned document API rather than maintaining a second mutable text model.

- **Project tool window.** `Open Folder` shows a folder as a tree. Directories are read one level at a time when
  they are expanded, symbolic links are listed but never followed, and a directory that cannot be read or that has
  more entries than the listing limit says so instead of looking complete.
- **Exclusions.** Xide leaves nothing out on its own. The only source of exclusions is `.xide/exclude.list` in the
  opened folder, written in `.gitignore` syntax. When the file does not exist, Xide generates it from the folder's
  `.gitignore` files; patterns from a nested `.gitignore` are rewritten so they mean the same thing from the opened
  folder. An existing list, whether generated or written by hand, is only read and never regenerated. A folder with
  neither file has no exclusions.
- **Editor.** Each open document has a tab with its own close control. A modified tab does not close silently: a
  bar offers to save, discard, or cancel. The gutter shows line numbers and a marker on each line that has a
  problem; long lines scroll horizontally instead of wrapping so the gutter stays aligned.
- **Languages.** Visual X# (`.vxs`), Kotlin (`.kt`, `.kts`), Java (`.java`), Groovy (`.groovy`, `.gradle`), and
  Python (`.py`) sources open, edit, and save as text. Analysis is a separate capability: the compiler `Check` action
  is connected for Visual X# only, and it is disabled for the other languages rather than pretending to check them.
  Until the Visual X# toolchain settles, Kotlin is the first language the editor work targets.
- **Syntax colouring.** The editor colours keywords, numbers, strings with their escapes and embedded expressions,
  comments, documentation comments and annotations for all five languages. Kotlin, Java and Groovy are split into
  tokens by the IntelliJ lexers of `xide-psi`; Visual X# and Python by the tokenizers of `xide-syntax`. The
  tokenizers are total: any text, however malformed, yields tokens that cover every
  non-blank character exactly once, which `checkTokens` verifies. Colouring is lexical only; names are not coloured
  by meaning. Soft keywords such as Kotlin's `value` or Python's `match` are recognized from neighbouring tokens,
  so an unusual position may stay uncoloured. A text longer than 400,000 UTF-16 code units is shown uncoloured.
- **Syntax trees.** `xide-psi` runs the IntelliJ PSI platform without the IDE. `SourceAnalyzer` parses Kotlin and
  Java with the parsers of the Kotlin embeddable compiler and Groovy with the IntelliJ Groovy parser, and returns
  the syntax errors and a declaration outline of a text. Visual X# has its own PSI language with a structural
  parser: it recognizes namespaces, `using` directives, types and members, keeps bodies opaque, and reports no
  errors, because Visual X# errors come from the compiler. The editor does not show the outline or the PSI syntax
  errors yet, and there is no resolution or type information.
- **Problems and status.** The Problems tool window lists the active document's diagnostics with their severity,
  code and location. The status bar shows the current activity, problem counts, the caret position, the line
  separator, the encoding, and the language.
- **Shortcuts.** `Ctrl+N`, `Ctrl+O`, `Ctrl+S`, `Ctrl+W`/`Ctrl+F4` act on files and tabs; `Alt+1` and `Alt+6` toggle
  the Project and Problems tool windows.

Semantic code analysis for Kotlin, Java and Groovy, resizable tool windows, and file operations in the Project tool
window are not implemented yet.

Selecting a problem reveals its primary location in the editor. A location is resolved only while it is provably the
text the compiler read: the checked document must still have the version its diagnostics were produced for, and the
target must be an open, saved, and unmodified `.vxs` file. Locations in files that are not open, in documents edited
since the check, or outside the document are declined with a status message rather than approximated. The editor
gives up keyboard focus while it shows the range, because a focused text field keeps its own caret; clicking into the
editor resumes typing. Opening the target file on demand and navigating related locations are not implemented yet.

`ProblemNavigationUiTest` and `ShellUiTest` drive the real Compose shell: they click rendered problems and check the
editor selection, open sources from the Project tool window, toggle tool windows from the stripe and from the
keyboard, close clean and modified tabs, and read the gutter and status bar back through the semantics tree.

Settings loading, quick-fix application, and the extension host remain future slices.

## Artwork

The application icon is the vector file `modules/app/src/main/resources/com/progmasoft/xide/app/Xide-App.svg`. The
desktop shell decodes it at start-up and uses it as the window icon. `branding/Xide-Social-Preview.svg` is the
repository social-preview artwork and is not packaged with the application. Both files are self-contained vector
drawings without embedded images, fonts, scripts, or external references.

Native installers still use the platform default icon: the `.ico`, `.icns`, and `.png` files that the packaging
tasks need are not generated from the vector source yet.

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

`check` also builds the Dokka HTML documentation of every module with undocumented-declaration reporting and
warnings as failures, so a public declaration without KDoc fails the build. The pages are written to each module's
`build/dokka/html`.

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

`third_party/` holds code from other projects under their own licenses. `third_party/intellij-groovy-psi` is the
Groovy lexer and parser of IntelliJ IDEA Community Edition under the Apache License 2.0; its `UPSTREAM.md` records the
origin and every change. See `THIRD_PARTY_NOTICES.txt`.
