// SPDX-FileCopyrightText: 2000-2026 JetBrains s.r.o. and contributors
// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.lexer;

import org.jetbrains.kotlin.com.intellij.lexer.DelegateLexer;
import org.jetbrains.kotlin.com.intellij.lexer.FlexAdapter;
import org.jetbrains.kotlin.com.intellij.lexer.MergingLexerAdapter;
import org.jetbrains.kotlin.com.intellij.psi.TokenType;
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet;

/**
 * The Groovy lexer: the generated lexer with adjacent comment, white-space and string-content tokens merged.
 *
 * It is the class of the same name in the IntelliJ Groovy plugin with one change. Upstream extends
 * {@code LookAheadLexer} without overriding it; that class is not in the platform copy this module is compiled
 * against, and without overrides it only passes tokens through, so the merging adapter is used directly.
 */
public class GroovyLexer extends DelegateLexer {
  private static final TokenSet tokensToMerge = TokenSet.create(
    GroovyTokenTypes.mSL_COMMENT,
    GroovyTokenTypes.mML_COMMENT,
    GroovyTokenTypes.mREGEX_CONTENT,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
    TokenType.WHITE_SPACE,
    GroovyTokenTypes.mGSTRING_CONTENT
  );

  /** Creates a lexer; call {@code start} before reading tokens. */
  public GroovyLexer() {
    super(new MergingLexerAdapter(new FlexAdapter(new _GroovyLexer(null)), tokensToMerge));
  }
}
