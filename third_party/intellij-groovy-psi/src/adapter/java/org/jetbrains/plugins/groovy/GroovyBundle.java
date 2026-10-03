// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.plugins.groovy;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The messages of the Groovy parser.
 *
 * It stands in for the bundle class of the IntelliJ Groovy plugin and reads the messages the parser uses from
 * {@code messages/GroovyBundle.properties}. A missing key yields the key itself, so a parse never fails because
 * of a message.
 */
public final class GroovyBundle {
  /** The resource bundle name, as the parser sources refer to it. */
  public static final String BUNDLE = "messages.GroovyBundle";

  private GroovyBundle() {}

  /** Returns the message of a key with its parameters filled in. */
  public static String message(String key, Object... parameters) {
    try {
      String pattern = ResourceBundle.getBundle(BUNDLE).getString(key);
      return parameters.length == 0 ? pattern : MessageFormat.format(pattern, parameters);
    }
    catch (MissingResourceException e) {
      return key;
    }
  }
}
