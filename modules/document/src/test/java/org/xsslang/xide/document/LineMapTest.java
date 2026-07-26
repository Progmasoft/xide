/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class LineMapTest {
    @Test
    void mapsLfCrlfAndCrLineEndings() {
        LineMap map = LineMap.of("one\r\ntwo\nthree\rfour");

        assertEquals(4, map.lineCount());
        assertEquals(5, map.offsetAt(new TextPosition(1, 0)));
        assertEquals(9, map.offsetAt(new TextPosition(2, 0)));
        assertEquals(new TextPosition(3, 4), map.positionAt(19));
    }

    @Test
    void countsSupplementaryCharactersAsTwoUtf16CodeUnits() {
        LineMap map = LineMap.of("A😀\nβ");

        assertEquals(new TextPosition(0, 3), map.positionAt(3));
        assertEquals(4, map.offsetAt(new TextPosition(1, 0)));
    }

    @Test
    void rejectsPositionsOutsideTheDocument() {
        LineMap map = LineMap.of("x");

        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(1, 0)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(0, 2)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.positionAt(2));
    }
}
