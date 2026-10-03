// SPDX-FileCopyrightText: 2000-2026 JetBrains s.r.o. and contributors
// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.lexer;

import java.util.ArrayDeque;
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;

/** Checks on brace-delimited Groovy blocks that need only the lexer. */
public final class GroovyBlocks {
  private GroovyBlocks() {}

  /**
   * Whether a text is one brace-delimited block whose braces and parentheses balance.
   *
   * The logic is that of {@code isBlockParseable} in upstream {@code parserUtils.kt}, moved to Java unchanged:
   * the Kotlin compiler cannot resolve the supertypes of a Java source class of the same module that extends a
   * relocated platform class, so Kotlin code cannot drive {@link GroovyLexer} here.
   */
  public static boolean isBlockParseable(CharSequence text) {
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(text);
    if (lexer.getTokenType() != GroovyTokenTypes.mLCURLY) return false;
    lexer.advance();
    ArrayDeque<IElementType> leftStack = new ArrayDeque<>();
    leftStack.push(GroovyTokenTypes.mLCURLY);
    while (true) {
      IElementType type = lexer.getTokenType();
      if (type == null) return leftStack.isEmpty();
      if (leftStack.isEmpty()) return false;
      if (type == GroovyTokenTypes.mLCURLY || type == GroovyTokenTypes.mLPAREN) {
        leftStack.push(type);
      }
      else if (type == GroovyTokenTypes.mRCURLY) {
        if (leftStack.pop() != GroovyTokenTypes.mLCURLY) return false;
      }
      else if (type == GroovyTokenTypes.mRPAREN) {
        if (leftStack.pop() != GroovyTokenTypes.mLPAREN) return false;
      }
      lexer.advance();
    }
  }
}
