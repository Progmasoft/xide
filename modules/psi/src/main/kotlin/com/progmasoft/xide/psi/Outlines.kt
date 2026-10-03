/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiClass
import org.jetbrains.kotlin.com.intellij.psi.PsiClassInitializer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiField
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.com.intellij.psi.PsiPackageStatement
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes

/**
 * Builds the outline of a parsed file.
 *
 * An outline lists the declarations a reader navigates by: the package, the types and their members. Local
 * declarations, imports and statements are not part of it.
 *
 * Kotlin and Java have PSI classes that say what a node is. The Groovy and Visual X# trees consist of generic
 * elements, so their declarations are recognized by element type.
 */
internal object Outlines {
  /** The outline of [file], which was parsed as [language]. */
  fun of(language: PsiLanguage, file: PsiFile): List<OutlineEntry> =
    when (language) {
      PsiLanguage.KOTLIN -> kotlin(file)
      PsiLanguage.JAVA -> java(file)
      PsiLanguage.GROOVY -> groovy(file)
      PsiLanguage.VISUAL_XSHARP -> visualXSharp(file)
    }

  private fun entry(kind: OutlineKind, name: String, element: PsiElement, children: List<OutlineEntry> = emptyList()) =
    OutlineEntry(kind, name, element.textRange.startOffset, element.textRange.endOffset, children)

  private fun children(element: PsiElement): Sequence<PsiElement> = generateSequence(element.firstChild) { it.nextSibling }

  // Kotlin

  private fun kotlin(file: PsiFile): List<OutlineEntry> {
    if (file !is KtFile) return emptyList()
    val result = ArrayList<OutlineEntry>()
    val packageDirective = file.packageDirective
    if (packageDirective != null && !packageDirective.isRoot) {
      result += entry(OutlineKind.NAMESPACE, packageDirective.qualifiedName, packageDirective)
    }
    for (declaration in file.declarations) {
      // The declarations of a script are those of its body.
      if (declaration is KtScript) declaration.declarations.mapNotNullTo(result, ::kotlinDeclaration)
      else kotlinDeclaration(declaration)?.let { result += it }
    }
    return result
  }

  private fun kotlinDeclaration(declaration: KtDeclaration): OutlineEntry? =
    when (declaration) {
      is KtClassOrObject -> {
        val name =
          declaration.name ?: if (declaration is KtObjectDeclaration && declaration.isCompanion()) "companion object" else "<anonymous>"
        entry(OutlineKind.TYPE, name, declaration, declaration.declarations.mapNotNull(::kotlinDeclaration))
      }
      is KtTypeAlias -> entry(OutlineKind.TYPE, declaration.name ?: "<anonymous>", declaration)
      is KtNamedFunction -> entry(OutlineKind.CALLABLE, declaration.name ?: "<anonymous>", declaration)
      is KtSecondaryConstructor -> entry(OutlineKind.CALLABLE, "constructor", declaration)
      is KtProperty -> entry(OutlineKind.PROPERTY, declaration.name ?: "<anonymous>", declaration)
      is KtClassInitializer -> entry(OutlineKind.INITIALIZER, "init", declaration)
      else -> null
    }

  // Java

  private fun java(file: PsiFile): List<OutlineEntry> {
    val result = ArrayList<OutlineEntry>()
    for (child in children(file)) {
      when (child) {
        is PsiPackageStatement -> result += entry(OutlineKind.NAMESPACE, child.packageName ?: "", child)
        is PsiClass -> result += javaClass(child)
      }
    }
    return result
  }

  private fun javaClass(type: PsiClass): OutlineEntry {
    val members = ArrayList<OutlineEntry>()
    for (child in children(type)) {
      when (child) {
        is PsiClass -> members += javaClass(child)
        is PsiMethod -> members += entry(OutlineKind.CALLABLE, child.name, child)
        is PsiField -> members += entry(OutlineKind.PROPERTY, child.name, child)
        is PsiClassInitializer -> members += entry(OutlineKind.INITIALIZER, "initializer", child)
      }
    }
    return entry(OutlineKind.TYPE, type.name ?: "<anonymous>", type, members)
  }

  // Groovy

  private val groovyTypes =
    TokenSet.create(
      GroovyElementTypes.CLASS_TYPE_DEFINITION, GroovyElementTypes.INTERFACE_TYPE_DEFINITION,
      GroovyElementTypes.ENUM_TYPE_DEFINITION, GroovyElementTypes.TRAIT_TYPE_DEFINITION,
      GroovyElementTypes.RECORD_TYPE_DEFINITION, GroovyElementTypes.ANNOTATION_TYPE_DEFINITION,
    )

  private val groovyCallables =
    TokenSet.create(GroovyElementTypes.METHOD, GroovyElementTypes.CONSTRUCTOR, GroovyElementTypes.ANNOTATION_METHOD)

  private val groovyBodies = TokenSet.create(GroovyElementTypes.CLASS_BODY, GroovyElementTypes.ENUM_BODY)

  private val groovyVariables = TokenSet.create(GroovyElementTypes.FIELD, GroovyElementTypes.VARIABLE)

  private fun groovy(file: PsiFile): List<OutlineEntry> {
    val result = ArrayList<OutlineEntry>()
    for (child in children(file)) groovyDeclaration(child, result)
    return result
  }

  private fun groovyDeclaration(element: PsiElement, into: MutableList<OutlineEntry>) {
    val type = element.node.elementType
    when {
      type == GroovyElementTypes.PACKAGE_DEFINITION -> {
        val reference = children(element).firstOrNull { it.node.elementType == GroovyElementTypes.CODE_REFERENCE }
        into += entry(OutlineKind.NAMESPACE, reference?.text ?: "", element)
      }
      groovyTypes.contains(type) -> {
        val members = ArrayList<OutlineEntry>()
        for (body in children(element)) {
          if (groovyBodies.contains(body.node.elementType)) {
            for (member in children(body)) groovyDeclaration(member, members)
          }
        }
        into += entry(OutlineKind.TYPE, nameOf(element, GroovyElementTypes.IDENTIFIER), element, members)
      }
      groovyCallables.contains(type) ->
        into += entry(OutlineKind.CALLABLE, nameOf(element, GroovyElementTypes.IDENTIFIER), element)
      type == GroovyElementTypes.VARIABLE_DECLARATION ->
        for (variable in children(element)) {
          if (groovyVariables.contains(variable.node.elementType)) {
            into += entry(OutlineKind.PROPERTY, nameOf(variable, GroovyElementTypes.IDENTIFIER), variable)
          }
        }
      type == GroovyElementTypes.CLASS_INITIALIZER -> into += entry(OutlineKind.INITIALIZER, "initializer", element)
    }
  }

  // Visual X#

  private fun visualXSharp(file: PsiFile): List<OutlineEntry> {
    val result = ArrayList<OutlineEntry>()
    for (child in children(file)) visualXSharpDeclaration(child, result)
    return result
  }

  private fun visualXSharpDeclaration(element: PsiElement, into: MutableList<OutlineEntry>) {
    when (element.node.elementType) {
      VisualXSharpTypes.NAMESPACE_DECLARATION ->
        into += entry(OutlineKind.NAMESPACE, nameOf(element, VisualXSharpTypes.NAME), element)
      VisualXSharpTypes.TYPE_DECLARATION -> {
        val members = ArrayList<OutlineEntry>()
        for (body in children(element)) {
          if (body.node.elementType == VisualXSharpTypes.TYPE_BODY) {
            for (member in children(body)) visualXSharpDeclaration(member, members)
          }
        }
        into += entry(OutlineKind.TYPE, nameOf(element, VisualXSharpTypes.NAME), element, members)
      }
      VisualXSharpTypes.CALLABLE_DECLARATION ->
        into += entry(OutlineKind.CALLABLE, nameOf(element, VisualXSharpTypes.NAME), element)
      VisualXSharpTypes.PROPERTY_DECLARATION, VisualXSharpTypes.FIELD_DECLARATION ->
        // A stretch of tokens that names nothing is not a declaration a reader navigates to.
        if (children(element).any { it.node.elementType == VisualXSharpTypes.NAME }) {
          into += entry(OutlineKind.PROPERTY, nameOf(element, VisualXSharpTypes.NAME), element)
        }
      VisualXSharpTypes.INITIALIZER -> into += entry(OutlineKind.INITIALIZER, "static", element)
    }
  }

  /** The text of the first direct child of [element] with [type], without the blanks inside it. */
  private fun nameOf(element: PsiElement, type: IElementType): String {
    val name = children(element).firstOrNull { it.node.elementType == type } ?: return "<anonymous>"
    return name.text.filterNot { it.isWhitespace() }
  }
}
