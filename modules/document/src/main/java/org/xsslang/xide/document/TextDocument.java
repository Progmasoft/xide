/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

import java.net.URI;
import java.util.Objects;

/**
 * Owns the current snapshot of one open editor document.
 */
public final class TextDocument {
    private DocumentSnapshot current;

    /**
     * Opens a document at version zero.
     *
     * @param uri stable document identity
     * @param text initial text
     */
    public TextDocument(URI uri, String text) {
        current = new DocumentSnapshot(uri, 0, text);
    }

    /**
     * Returns the current immutable snapshot.
     *
     * @return current snapshot
     */
    public synchronized DocumentSnapshot snapshot() {
        return current;
    }

    /**
     * Applies an edit if the caller still owns the current version.
     *
     * @param expectedVersion version observed by the caller
     * @param edit edit to apply
     * @return updated snapshot
     * @throws StaleDocumentVersionException when the expected version is stale
     */
    public synchronized DocumentSnapshot apply(long expectedVersion, TextEdit edit) {
        Objects.requireNonNull(edit, "edit");
        if (current.version() != expectedVersion) {
            throw new StaleDocumentVersionException(expectedVersion, current.version());
        }
        current = current.apply(edit);
        return current;
    }
}
