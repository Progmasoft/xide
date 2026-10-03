// Modified by Progmasoft for Xide. See third_party/intellij-groovy-psi/UPSTREAM.md for the changes.
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lang.PsiParser
import com.intellij.lang.parser.GeneratedParserUtilBase
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet

object GroovyGeneratedParserUtils {

  @Suppress("FunctionName")
  @JvmStatic
  fun adapt_builder_(root: IElementType, builder: PsiBuilder, parser: PsiParser, extendsSets: Array<TokenSet>?): PsiBuilder {
    val adapted = GeneratedParserUtilBase.adapt_builder_(root, builder, parser, extendsSets)
    GeneratedParserUtilBase.ErrorState.get(adapted).braces = null
    return adapted
  }
}
