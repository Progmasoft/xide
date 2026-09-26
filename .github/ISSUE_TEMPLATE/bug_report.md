---
name: Xide bug
about: Report a reproducible problem in the Kotlin and Compose desktop IDE
title: "[Bug] "
---

<!-- SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com> -->
<!-- SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1 -->

Xide is an early-stage Kotlin/JVM 25 and Compose Multiplatform IDE, not yet ready for daily use. Report compiler-language bugs in `Progmasoft/visual-xsharp`; report language-site or account issues in their own repositories. Do not include private projects, credentials, or extension secrets. Send security vulnerabilities privately to support@progmasoft.com.

### Affected workflow

- [ ] Document editing, save, or line endings
- [ ] Compiler Check or Problems view
- [ ] Project, navigation, settings, or extensions
- [ ] Compose window, layout, input, or accessibility
- [ ] Build, launch, or packaging

### Observed and expected behavior

What happened, and what should Xide have done? If a diagnostic is wrong, include the exact displayed message and say whether `vxs` reports the same result independently.

### Minimal reproduction

List the steps from launching Xide, using a small disposable `.vxs` file if needed. For editor bugs, specify the text, caret/selection location, line-ending type, and whether the document was saved or unsaved. For asynchronous Problems behavior, describe the edit/check sequence.

### Environment

- Xide commit or build:
- OS and version:
- JDK 25 distribution/version if running from source:
- Display scale, keyboard layout, and accessibility settings if relevant:
- Installed extensions involved, if any:

### Evidence

Attach a sanitized screenshot or log excerpt, expected/actual diagnostic, and whether the issue reproduces without extensions.
