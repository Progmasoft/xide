// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy;

import org.jetbrains.kotlin.com.intellij.lang.Language;

/**
 * The Groovy language of the element types in this module.
 *
 * It stands in for the class of the same name in the IntelliJ Groovy plugin, which also carries IDE behaviour
 * that is not available here.
 */
public final class GroovyLanguage extends Language {
  /** The only instance; a platform language is identified by its object. */
  public static final GroovyLanguage INSTANCE = new GroovyLanguage();

  private GroovyLanguage() {
    super("Groovy");
  }
}
