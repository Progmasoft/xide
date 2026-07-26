/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

/**
 * A zero-based line and UTF-16 code-unit column.
 *
 * @param line zero-based line
 * @param column zero-based UTF-16 code-unit column
 */
public record TextPosition(int line, int column) {
    /**
     * Validates a position.
     */
    public TextPosition {
        if (line < 0) {
            throw new IllegalArgumentException("line must not be negative");
        }
        if (column < 0) {
            throw new IllegalArgumentException("column must not be negative");
        }
    }
}
