/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

import java.util.Objects;

/**
 * Replaces a range in a document with new text.
 *
 * @param range replaced range
 * @param replacement replacement text
 */
public record TextEdit(TextRange range, String replacement) {
    /**
     * Validates an edit.
     */
    public TextEdit {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(replacement, "replacement");
    }
}
