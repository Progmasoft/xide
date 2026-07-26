/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

import java.util.Arrays;

/**
 * Maps offsets to line positions without converting Java's native UTF-16 representation.
 */
public final class LineMap {
    private final int[] lineStarts;
    private final int[] lineEnds;
    private final int textLength;

    private LineMap(int[] lineStarts, int[] lineEnds, int textLength) {
        this.lineStarts = lineStarts;
        this.lineEnds = lineEnds;
        this.textLength = textLength;
    }

    /**
     * Scans text into an immutable line map.
     *
     * @param text source text
     * @return scanned line map
     */
    public static LineMap of(String text) {
        int[] starts = new int[Math.max(8, text.length() / 16)];
        int[] ends = new int[starts.length];
        int lineCount = 0;
        int lineStart = 0;

        for (int offset = 0; offset < text.length(); offset++) {
            char current = text.charAt(offset);
            if (current != '\r' && current != '\n') {
                continue;
            }
            if (lineCount == starts.length) {
                starts = Arrays.copyOf(starts, starts.length * 2);
                ends = Arrays.copyOf(ends, ends.length * 2);
            }
            starts[lineCount] = lineStart;
            ends[lineCount++] = offset;
            if (current == '\r' && offset + 1 < text.length() && text.charAt(offset + 1) == '\n') {
                offset++;
            }
            lineStart = offset + 1;
        }

        if (lineCount == starts.length) {
            starts = Arrays.copyOf(starts, starts.length + 1);
            ends = Arrays.copyOf(ends, ends.length + 1);
        }
        starts[lineCount] = lineStart;
        ends[lineCount++] = text.length();
        return new LineMap(Arrays.copyOf(starts, lineCount), Arrays.copyOf(ends, lineCount), text.length());
    }

    /**
     * Returns the number of logical lines, including the final empty line.
     *
     * @return line count
     */
    public int lineCount() {
        return lineStarts.length;
    }

    /**
     * Converts a line and column into a UTF-16 offset.
     *
     * @param position position to convert
     * @return UTF-16 offset
     */
    public int offsetAt(TextPosition position) {
        if (position.line() >= lineStarts.length) {
            throw new IndexOutOfBoundsException("line is outside the document");
        }
        int lineLength = lineEnds[position.line()] - lineStarts[position.line()];
        if (position.column() > lineLength) {
            throw new IndexOutOfBoundsException("column is outside the line");
        }
        return lineStarts[position.line()] + position.column();
    }

    /**
     * Converts a UTF-16 offset into a line and column.
     *
     * @param offset offset to convert
     * @return line and UTF-16 column
     */
    public TextPosition positionAt(int offset) {
        if (offset < 0 || offset > textLength) {
            throw new IndexOutOfBoundsException("offset is outside the document");
        }
        int search = Arrays.binarySearch(lineStarts, offset);
        int line = search >= 0 ? search : -search - 2;
        int column = Math.min(offset, lineEnds[line]) - lineStarts[line];
        return new TextPosition(line, column);
    }
}
