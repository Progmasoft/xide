# SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
# SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1

"""Regenerates src/main from the Groovy lexer and parser sources of intellij-community.

The script is the complete record of how the upstream files are changed. UPSTREAM.md explains the changes in
prose; this file performs them. Usage:

    python adapt.py <checkout of JetBrains/intellij-community>

The checkout must be at the commit named in UPSTREAM.md. The script deletes src/main and writes it again. Every
replacement is asserted to match exactly once, so an upstream change that the script no longer fits fails loudly
instead of producing a half-adapted file.
"""

import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, 'src', 'main', 'java')
KOTLIN = os.path.join(HERE, 'src', 'main', 'kotlin')
GROOVY = 'org/jetbrains/plugins/groovy/'
GENERATED = 'plugins/groovy/groovy-psi/gen/'
SOURCES = 'plugins/groovy/groovy-psi/src/'

# Upstream path -> (target source root, path below it).
FILES = {
    GENERATED + GROOVY + 'lang/lexer/_GroovyLexer.java': (JAVA, GROOVY + 'lang/lexer/_GroovyLexer.java'),
    GENERATED + GROOVY + 'lang/parser/GroovyGeneratedParser.java':
        (JAVA, GROOVY + 'lang/parser/GroovyGeneratedParser.java'),
    GENERATED + GROOVY + 'lang/psi/GroovyElementTypes.java': (JAVA, GROOVY + 'lang/psi/GroovyElementTypes.java'),
    SOURCES + GROOVY + 'lang/lexer/GroovyLexerBase.java': (JAVA, GROOVY + 'lang/lexer/GroovyLexerBase.java'),
    SOURCES + GROOVY + 'lang/lexer/GroovyTokenTypes.java': (JAVA, GROOVY + 'lang/lexer/GroovyTokenTypes.java'),
    SOURCES + GROOVY + 'lang/lexer/TokenSets.java': (JAVA, GROOVY + 'lang/lexer/TokenSets.java'),
    SOURCES + GROOVY + 'lang/lexer/GroovyElementType.java': (JAVA, GROOVY + 'lang/lexer/GroovyElementType.java'),
    SOURCES + GROOVY + 'lang/psi/GroovyTokenSets.java': (JAVA, GROOVY + 'lang/psi/GroovyTokenSets.java'),
    SOURCES + GROOVY + 'lang/parser/GroovyParser.java': (JAVA, GROOVY + 'lang/parser/GroovyParser.java'),
    SOURCES + GROOVY + 'lang/parser/GroovyElementTypes.java': (JAVA, GROOVY + 'lang/parser/GroovyElementTypes.java'),
    SOURCES + GROOVY + 'lang/parser/GroovyStubElementTypes.java':
        (JAVA, GROOVY + 'lang/parser/GroovyStubElementTypes.java'),
    SOURCES + GROOVY + 'lang/parser/GroovyEmptyStubElementTypes.java':
        (JAVA, GROOVY + 'lang/parser/GroovyEmptyStubElementTypes.java'),
    SOURCES + GROOVY + 'lang/parser/parsing/util/ParserUtils.java':
        (JAVA, GROOVY + 'lang/parser/parsing/util/ParserUtils.java'),
    SOURCES + GROOVY + 'lang/parser/parserUtils.kt': (KOTLIN, GROOVY + 'lang/parser/parserUtils.kt'),
    SOURCES + GROOVY + 'lang/parser/GroovyGeneratedParserUtils.kt':
        (KOTLIN, GROOVY + 'lang/parser/GroovyGeneratedParserUtils.kt'),
    SOURCES + GROOVY + 'lang/parser/tokenSets.kt': (KOTLIN, GROOVY + 'lang/parser/tokenSets.kt'),
    SOURCES + GROOVY + 'util/userDataHolder.kt': (KOTLIN, GROOVY + 'util/userDataHolder.kt'),
    'platform/analysis-impl/src/com/intellij/lang/parser/GeneratedParserUtilBase.java':
        (JAVA, 'com/intellij/lang/parser/GeneratedParserUtilBase.java'),
    'platform/analysis-api/src/com/intellij/lang/BracePair.java': (JAVA, 'com/intellij/lang/BracePair.java'),
    'platform/core-impl/src/com/intellij/psi/DummyBlockType.java': (JAVA, 'com/intellij/psi/DummyBlockType.java'),
}

# The platform classes these four files belong to are not in the Kotlin embeddable compiler, so the files keep
# their own package while everything they import is relocated.
KEEP_PACKAGE = {
    'com/intellij/lang/parser/GeneratedParserUtilBase.java',
    'com/intellij/lang/BracePair.java',
    'com/intellij/psi/DummyBlockType.java',
}

# Element types that upstream declares as stub or lazily parsed classes of the IDE. The adapter module defines
# the five block types as plain element types; every other one becomes a GroovyElementType.
BLOCK_TYPES = {
    'GrBlockElementType', 'GrClosureElementType', 'GrBlockLambdaBodyElementType', 'GrConstructorBlockElementType',
    'GrEnumDefinitionBodyElementType',
}

NOTICE = '// Modified by Progmasoft for Xide. See third_party/intellij-groovy-psi/UPSTREAM.md for the changes.\n'


def read(path):
    with open(path, encoding='utf-8') as stream:
        return stream.read().replace('\r\n', '\n')


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as stream:
        stream.write(text)


def relocate(text):
    """Point platform imports at the package the Kotlin embeddable compiler relocates them to."""
    text = re.sub(r'\bcom\.intellij\.', 'org.jetbrains.kotlin.com.intellij.', text)
    for kept in ('lang.parser.GeneratedParserUtilBase', 'lang.BracePair', 'psi.DummyBlockType',
                 'analysis.AnalysisBundle'):
        text = text.replace('org.jetbrains.kotlin.com.intellij.' + kept, 'com.intellij.' + kept)
    return text


def plain_element_types(text):
    def declaration(match):
        return match.group(0) if match.group(2) in BLOCK_TYPES else match.group(1) + 'IElementType ' + match.group(3)

    text = re.sub(r'^(\s+)(Gr\w+ElementType) (\w+ = )', declaration, text, flags=re.M)
    text = re.sub(r'= new (Gr\w+ElementType)\(',
                  lambda match: match.group(0) if match.group(1) in BLOCK_TYPES else '= new GroovyElementType(', text)
    text = re.sub(r'^import org\.jetbrains\.plugins\.groovy\.lang\.psi\.stubs\.elements\.\w+;\n', '', text, flags=re.M)
    if 'import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;' not in text:
        text = text.replace(
            '\npublic interface',
            '\nimport org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;\n'
            'import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;\n\npublic interface', 1)
    return text


def replace_once(text, name, pairs):
    for old, new in pairs:
        if text.count(old) != 1:
            raise SystemExit(f'{name}: expected exactly one occurrence of {old[:70]!r}, found {text.count(old)}')
        text = text.replace(old, new)
    return text


PATCHES = {
    'com/intellij/lang/parser/GeneratedParserUtilBase.java': [
        ('package org.jetbrains.kotlin.com.intellij.lang.parser;', 'package com.intellij.lang.parser;'),
        ('import org.jetbrains.kotlin.com.intellij.codeInsight.completion.impl.CamelHumpMatcher;\n', ''),
        ('import org.jetbrains.kotlin.com.intellij.lang.LanguageBraceMatching;\n', ''),
        ('import org.jetbrains.kotlin.com.intellij.lang.PairedBraceMatcher;\n', ''),
        ("      boolean matches = new CamelHumpMatcher(prefix, false).prefixMatches(variant.replace(' ', '_'));\n",
         '      // Xide: the IDE matches completion prefixes by camel humps; without that service a plain prefix is\n'
         '      // used.\n'
         "      boolean matches = startsWithIgnoreCase(variant.replace(' ', '_'), prefix);\n"),
        ('      PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(language);\n'
         '      state.braces = matcher == null ? null : matcher.getPairs();\n'
         '      if (state.braces != null && state.braces.length == 0) state.braces = null;\n',
         '      // Xide: there is no brace-matcher registry outside the IDE; brace-aware recovery stays off.\n'
         '      state.braces = null;\n'),
        ('    if (marker == null || marker.isCollapsed()) return null;\n',
         '    // Xide: Production.isCollapsed() is not part of the platform copy this is compiled against.\n'
         '    if (marker == null) return null;\n'),
    ],
    'com/intellij/lang/BracePair.java': [
        ('package org.jetbrains.kotlin.com.intellij.lang;', 'package com.intellij.lang;'),
    ],
    'com/intellij/psi/DummyBlockType.java': [
        ('package org.jetbrains.kotlin.com.intellij.psi;\n',
         'package com.intellij.psi;\n\nimport org.jetbrains.kotlin.com.intellij.psi.PsiReference;\n'),
    ],
    GROOVY + 'lang/lexer/GroovyLexerBase.java': [
        ('  protected abstract int getInitialState();\n',
         '  // Xide: FlexLexer of the platform copy this is compiled against does not declare yybegin; the generated\n'
         '  // lexer implements it.\n'
         '  public abstract void yybegin(int state);\n\n'
         '  protected abstract int getInitialState();\n'),
    ],
    GROOVY + 'lang/parser/parsing/util/ParserUtils.java': [
        ('import org.jetbrains.kotlin.com.intellij.openapi.util.NlsContexts.ParsingError;\n', ''),
        ('@ParsingError String errorMsg', 'String errorMsg'),
    ],
    GROOVY + 'lang/parser/parserUtils.kt': [
        ('import org.jetbrains.kotlin.com.intellij.codeInsight.completion.CompletionUtilCore.'
         'DUMMY_IDENTIFIER_TRIMMED\n', ''),
        ('text != DUMMY_IDENTIFIER_TRIMMED', 'text != "IntellijIdeaRulezzz"'),
        ('import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressManager\n', ''),
        ('import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer\n',
         'import org.jetbrains.plugins.groovy.lang.lexer.GroovyBlocks\n'),
    ],
}


def move_block_check_to_java(text):
    """Replace the body of isBlockParseable, which drives the lexer from Kotlin, with a call into Java.

    The Kotlin compiler cannot resolve the supertypes of a Java source class that extends a relocated platform
    class from the same module, so the loop lives in GroovyBlocks.java of the adapter sources.
    """
    start = text.index('fun isBlockParseable(text: CharSequence): Boolean {')
    end = text.index('fun insideParentheses(')
    return (text[:start]
            + '// Xide: the body is GroovyBlocks.isBlockParseable in the adapter sources.\n'
            + 'fun isBlockParseable(text: CharSequence): Boolean = GroovyBlocks.isBlockParseable(text)\n\n'
            + text[end:])


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    upstream = sys.argv[1]
    shutil.rmtree(os.path.join(HERE, 'src', 'main'), ignore_errors=True)
    for source, (root, relative) in FILES.items():
        text = relocate(read(os.path.join(upstream, source)))
        if relative in (GROOVY + 'lang/psi/GroovyElementTypes.java', GROOVY + 'lang/parser/GroovyStubElementTypes.java',
                        GROOVY + 'lang/parser/GroovyEmptyStubElementTypes.java'):
            text = plain_element_types(text)
        if relative == GROOVY + 'lang/parser/GroovyElementTypes.java':
            text = re.sub(r'^(\s+)GroovyElementType (\w+ = )', r'\1IElementType \2', text, flags=re.M)
        if relative == GROOVY + 'lang/parser/parserUtils.kt':
            text = move_block_check_to_java(text)
        text = replace_once(text, relative, PATCHES.get(relative, []))
        write(os.path.join(root, relative), NOTICE + text)
    print(f'wrote {len(FILES)} files')


if __name__ == '__main__':
    main()
