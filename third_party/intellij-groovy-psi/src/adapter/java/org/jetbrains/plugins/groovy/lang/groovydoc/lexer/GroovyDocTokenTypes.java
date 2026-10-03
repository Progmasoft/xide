// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet;

/**
 * The tokens inside a Groovy documentation comment.
 *
 * There are none here: a documentation comment is a single token, see {@code GroovyDocElementTypes}. The empty
 * set keeps the token sets of the lexer, which union it in, unchanged in every other respect.
 */
public interface GroovyDocTokenTypes {
  /** The tokens a documentation comment is split into. */
  TokenSet GROOVY_DOC_TOKENS = TokenSet.EMPTY;
}
