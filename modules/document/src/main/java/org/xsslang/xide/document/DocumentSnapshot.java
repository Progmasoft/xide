/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

import java.net.URI;
import java.util.Objects;

/**
 * An immutable document version safe to share with background language services.
 *
 * @param uri stable document identity
 * @param version monotonically increasing version
 * @param text UTF-16 document content
 */
public record DocumentSnapshot(URI uri, long version, String text) {
    /**
     * Validates a document snapshot.
     */
    public DocumentSnapshot {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(text, "text");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /**
     * Builds the line map for this snapshot.
     *
     * @return an immutable line map
     */
    public LineMap lineMap() {
        return LineMap.of(text);
    }

    /**
     * Applies one edit and returns the next immutable version.
     *
     * @param edit edit to apply
     * @return updated snapshot
     */
    public DocumentSnapshot apply(TextEdit edit) {
        Objects.requireNonNull(edit, "edit");
        if (edit.range().end() > text.length()) {
            throw new IndexOutOfBoundsException("edit range is outside the document");
        }
        String updated = text.substring(0, edit.range().start())
                + edit.replacement()
                + text.substring(edit.range().end());
        return new DocumentSnapshot(uri, Math.addExact(version, 1), updated);
    }
}
