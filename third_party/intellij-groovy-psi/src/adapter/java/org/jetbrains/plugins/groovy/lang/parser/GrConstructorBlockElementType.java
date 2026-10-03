// SPDX-FileCopyrightText: 2000-2026 JetBrains s.r.o. and contributors
// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy.lang.parser;

/** A block element type; see {@link GrCodeBlockElementType}. */
public class GrConstructorBlockElementType extends GrCodeBlockElementType {
  /** Creates the type. */
  public GrConstructorBlockElementType(String debugName) {
    super(debugName, false);
  }
}
