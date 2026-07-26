/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class TextDocumentTest {
    @Test
    void editsCreateNewImmutableVersions() {
        TextDocument document = new TextDocument(URI.create("file:///workspace/main.xs"), "fn main() {}");
        DocumentSnapshot original = document.snapshot();

        DocumentSnapshot updated = document.apply(0, new TextEdit(new TextRange(3, 7), "entry"));

        assertEquals(0, original.version());
        assertEquals("fn main() {}", original.text());
        assertEquals(1, updated.version());
        assertEquals("fn entry() {}", updated.text());
        assertEquals(updated, document.snapshot());
    }

    @Test
    void rejectsStaleAndOutOfBoundsEdits() {
        TextDocument document = new TextDocument(URI.create("untitled:xsharp"), "value");
        document.apply(0, new TextEdit(new TextRange(0, 5), "next"));

        assertThrows(
                StaleDocumentVersionException.class,
                () -> document.apply(0, new TextEdit(new TextRange(0, 0), "stale")));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> document.apply(1, new TextEdit(new TextRange(0, 20), "outside")));
    }
}
