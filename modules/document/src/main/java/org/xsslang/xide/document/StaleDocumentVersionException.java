/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

package org.xsslang.xide.document;

/**
 * Raised when a consumer tries to edit a document version that is no longer current.
 */
public final class StaleDocumentVersionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an error describing the requested and current versions.
     *
     * @param expected requested version
     * @param actual current version
     */
    public StaleDocumentVersionException(long expected, long actual) {
        super("expected document version " + expected + ", but current version is " + actual);
    }
}
