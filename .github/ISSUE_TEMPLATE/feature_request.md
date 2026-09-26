---
name: Xide improvement
about: Propose a desktop IDE workflow or UI improvement
title: "[Proposal] "
---

<!-- SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com> -->
<!-- SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1 -->

Xide is a future Visual X# IDE built with Kotlin/JVM 25 and Compose Multiplatform. The current foundation is intentionally limited; check the README before assuming a planned feature is already implemented. Language/compiler proposals belong in `Progmasoft/visual-xsharp`.

### User workflow

What task in editing, project navigation, diagnostics, settings, or extensions would this improve? Describe the current workaround and the point where it becomes difficult.

### Proposed interaction

Describe the visible behavior, keyboard/mouse path, and failure or cancellation behavior. A simple sketch is welcome, but explain the interaction in text as well.

### Architecture and compatibility

Which Xide module should own it (document, compiler integration, app/Compose UI, settings, or extension host)? Does it affect versioned documents, stale diagnostics, `vxs` invocation, or saved files?

### Acceptance checks

How would we test it on Windows and macOS, including keyboard access, high-DPI layout, and unsaved-document behavior where relevant?
