// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.groovydoc.parser;

import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

/**
 * The element type of a Groovy documentation comment.
 *
 * Upstream parses the inside of such a comment lazily with a separate lexer and parser. Here the comment is one
 * token, so the Groovydoc lexer and parser are not part of this module.
 */
public interface GroovyDocElementTypes {
  /** A whole documentation comment, from its opening to its closing delimiter. */
  IElementType GROOVY_DOC_COMMENT = new GroovyElementType("GrDocComment");
}
