/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import javax.swing.Icon
import org.jetbrains.kotlin.com.intellij.extapi.psi.ASTWrapperPsiElement
import org.jetbrains.kotlin.com.intellij.extapi.psi.PsiFileBase
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.ParserDefinition
import org.jetbrains.kotlin.com.intellij.lang.PsiParser
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.FileType
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.FileViewProvider
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.tree.IFileElementType
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser

/** The file type that ties `.groovy` and `.gradle` text to the Groovy language. */
internal object GroovyPsiFileType : LanguageFileType(GroovyLanguage.INSTANCE) {
  override fun getName(): String = "Groovy"

  override fun getDescription(): String = "Groovy"

  override fun getDefaultExtension(): String = "groovy"

  override fun getIcon(): Icon? = null
}

/** A parsed Groovy file. */
internal class GroovyPsiFile(provider: FileViewProvider) : PsiFileBase(provider, GroovyLanguage.INSTANCE) {
  override fun getFileType(): FileType = GroovyPsiFileType
}

/**
 * The Groovy parser of IntelliJ with blocks parsed together with their file.
 *
 * The IDE leaves the body of a method or closure unparsed until something looks inside it. That needs element
 * types that can re-parse themselves and the PSI classes of the Groovy plugin; neither is part of the sources
 * taken from IntelliJ. Parsing "deep" makes the same grammar produce the same tree in one pass.
 */
private class WholeFileGroovyParser : GroovyParser() {
  override fun parseDeep(): Boolean = true
}

/**
 * Connects the Groovy lexer and parser taken from IntelliJ to the PSI platform.
 *
 * The lexer, the grammar and the element types are IntelliJ's. The PSI elements are not: the Groovy plugin has
 * one class per construct with resolution and type inference behind it, which depends on most of the IDE. Every
 * composite node is a generic PSI element here, told apart by its element type.
 */
internal class GroovyPsiParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = GroovyLexer()

  override fun createParser(project: Project?): PsiParser = WholeFileGroovyParser()

  override fun getFileNodeType(): IFileElementType = FILE

  override fun getCommentTokens(): TokenSet = TokenSets.COMMENTS_TOKEN_SET

  override fun getStringLiteralElements(): TokenSet = TokenSets.STRING_LITERALS

  override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = GroovyPsiFile(viewProvider)

  private companion object {
    val FILE = IFileElementType("GROOVY_FILE", GroovyLanguage.INSTANCE)
  }
}
