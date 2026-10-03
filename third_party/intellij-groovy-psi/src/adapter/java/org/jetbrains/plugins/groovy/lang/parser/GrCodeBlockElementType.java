// SPDX-FileCopyrightText: 2000-2026 JetBrains s.r.o. and contributors
// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

/**
 * The element type of a brace-delimited block.
 *
 * Upstream makes these types lazily and incrementally parseable and lets them create the PSI classes of the
 * Groovy plugin. Here a block is parsed together with its file, so the type only keeps what the parser itself
 * reads: whether the block stands inside a switch.
 */
public abstract class GrCodeBlockElementType extends GroovyElementType {
  private final boolean isInsideSwitch;

  /** Creates the type of a block; {@code isInsideSwitch} selects the switch-aware variant. */
  protected GrCodeBlockElementType(String debugName, boolean isInsideSwitch) {
    super(debugName);
    this.isInsideSwitch = isInsideSwitch;
  }

  /** Whether the block is the body of a switch, where {@code yield} and case labels are read differently. */
  public boolean isInsideSwitch() {
    return isInsideSwitch;
  }
}
