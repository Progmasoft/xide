// SPDX-FileCopyrightText: 2000-2026 JetBrains s.r.o. and contributors
// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.parser;

/** A block element type; see {@link GrCodeBlockElementType}. */
public class GrBlockLambdaBodyElementType extends GrCodeBlockElementType {
  /** Creates the ordinary variant of the type. */
  public GrBlockLambdaBodyElementType(String debugName) {
    this(debugName, false);
  }

  /** Creates the type; {@code isInsideSwitch} selects the switch-aware variant. */
  public GrBlockLambdaBodyElementType(String debugName, boolean isInsideSwitch) {
    super(debugName, isInsideSwitch);
  }
}
