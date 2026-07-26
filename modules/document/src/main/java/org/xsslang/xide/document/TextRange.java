/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

/**
 * A half-open range of UTF-16 code-unit offsets.
 *
 * @param start inclusive start offset
 * @param end exclusive end offset
 */
public record TextRange(int start, int end) {
    /**
     * Validates a range.
     */
    public TextRange {
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative");
        }
        if (end < start) {
            throw new IllegalArgumentException("end must not precede start");
        }
    }

    /**
     * Returns the number of UTF-16 code units in the range.
     *
     * @return range length
     */
    public int length() {
        return end - start;
    }
}
