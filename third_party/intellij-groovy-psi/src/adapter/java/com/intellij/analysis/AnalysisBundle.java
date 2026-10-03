// SPDX-FileCopyrightText: 2000-2026 JetBrains s.r.o. and contributors
// SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
// SPDX-License-Identifier: Apache-2.0

package com.intellij.analysis;

import java.text.MessageFormat;
import java.util.Map;

/**
 * The parsing messages of the platform that {@code GeneratedParserUtilBase} reports.
 *
 * It stands in for the bundle class of the IntelliJ platform. The nine message texts are copied from
 * {@code platform/analysis-api/resources/messages/AnalysisBundle.properties} of intellij-community.
 */
public final class AnalysisBundle {
  private static final Map<String, String> MESSAGES = Map.of(
    "parsing.error.empty.element.parsed.in.at.offset", "Empty element parsed in ''{0}'' at offset {1}",
    "parsing.error.maximum.recursion.level.reached.in", "Maximum recursion level ({0}) reached in ''{1}''",
    "parsing.error.no.expected.done.marker.at.offset", "No expected done marker at offset {0}",
    "parsing.error.unmatched.input", "Unmatched input",
    "parsing.error.expected", "{0} expected",
    "parsing.error.expected.got", "{0} expected, got ''{1}''",
    "parsing.error.unexpected", "''{0}'' unexpected",
    "parsing.error.or", "or",
    "parsing.error.and.ellipsis", "and ..."
  );

  private AnalysisBundle() {}

  /** Returns the message of a key with its parameters filled in; an unknown key yields itself. */
  public static String message(String key, Object... parameters) {
    return MessageFormat.format(MESSAGES.getOrDefault(key, key), parameters);
  }
}
