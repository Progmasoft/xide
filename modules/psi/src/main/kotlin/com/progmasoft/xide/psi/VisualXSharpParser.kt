/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.xide.psi

import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lang.PsiParser
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType

/**
 * The structural parser of Visual X#.
 *
 * It recognizes the declarations of a file and of a type body and gives each one a node with its name:
 * namespace and using declarations, type declarations with their members, callables, properties, fields and
 * static initializers. That is the structure an editor shows and navigates by.
 *
 * It does not parse statements or expressions, and it reports no errors. A body in braces is one opaque block,
 * and an initializer is skipped up to its terminator. Whether a Visual X# program is correct is decided by the
 * compiler, whose diagnostics the editor shows; a second, partial judgement here could only disagree with it.
 * For the same reason the parser accepts any text: it never stops early and never loses a token, so the tree
 * always spells out the whole file.
 *
 * The shape of a declaration follows `Grammar/visual-xsharp.ebnf` of the language repository: attributes, an
 * optional template clause and modifiers, then either a keyword that names the kind of type, or a type followed
 * by the declared name.
 */
internal class VisualXSharpParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val file = builder.mark()
    while (!builder.eof()) {
      // A closing brace without an opener belongs to no declaration; it is kept as a plain token.
      if (builder.tokenText == "}") builder.advanceLexer() else parseItem(builder)
    }
    file.done(root)
    return builder.treeBuilt
  }

  /** Parses the declarations of a body up to, and not including, its closing brace. */
  private fun parseBody(builder: PsiBuilder) {
    while (!builder.eof() && builder.tokenText != "}") parseItem(builder)
  }

  private fun parseItem(builder: PsiBuilder) {
    if (builder.tokenType == VisualXSharpTypes.DIRECTIVE) {
      builder.advanceLexer()
      return
    }
    // A namespace declaration may carry attributes, which come before its keyword.
    when (keywordAfterAttributes(builder)) {
      "namespace" -> parseSimple(builder, VisualXSharpTypes.NAMESPACE_DECLARATION, "namespace", named = true)
      "using" -> parseSimple(builder, VisualXSharpTypes.USING_DECLARATION, "using", named = false)
      else -> parseDeclaration(builder)
    }
  }

  /** The text of the first token after any `#[...]` and `[...]` attributes at the builder's position. */
  private fun keywordAfterAttributes(builder: PsiBuilder): String? {
    val start = builder.mark()
    while (!builder.eof()) {
      val text = builder.tokenText
      if (text == "#") {
        builder.advanceLexer()
      } else if (text == "[") {
        var depth = 0
        while (!builder.eof()) {
          val inner = builder.tokenText
          if (inner == "{" || inner == "}" || inner == ";") break
          builder.advanceLexer()
          if (inner == "[") depth++
          if (inner == "]" && --depth == 0) break
        }
      } else {
        break
      }
    }
    val keyword = builder.tokenText
    start.rollbackTo()
    return keyword
  }

  /**
   * Parses a declaration that runs to its semicolon: `namespace a.b;` or a using declaration.
   *
   * Attributes in front of [keyword] belong to the declaration. With [named], the qualified name after the
   * keyword becomes the declaration's name.
   */
  private fun parseSimple(builder: PsiBuilder, type: IElementType, keyword: String, named: Boolean) {
    val declaration = builder.mark()
    while (!builder.eof() && builder.tokenText != keyword) builder.advanceLexer()
    builder.advanceLexer()
    if (named && builder.tokenType == VisualXSharpTypes.IDENTIFIER) {
      val name = builder.mark()
      while (builder.tokenType == VisualXSharpTypes.IDENTIFIER || builder.tokenText == ".") builder.advanceLexer()
      name.done(VisualXSharpTypes.NAME)
    }
    while (!builder.eof()) {
      val text = builder.tokenText
      if (text == ";") {
        builder.advanceLexer()
        break
      }
      // A selected using declaration lists its imports in braces before the semicolon.
      when (text) {
        "{" -> skipBalanced(builder, null)
        "}" -> break
        else -> builder.advanceLexer()
      }
    }
    declaration.done(type)
  }

  /** What the head of a declaration turned out to be. */
  private class Head(val type: IElementType, val nameIndex: Int, val nameLength: Int, val membersInBody: Boolean)

  private fun parseDeclaration(builder: PsiBuilder) {
    val head = classify(builder)
    val declaration = builder.mark()
    var index = 0
    var depth = 0
    var body = false
    var initializer = false
    while (!builder.eof()) {
      val text = builder.tokenText ?: ""
      if (index == head.nameIndex) {
        val name = builder.mark()
        repeat(head.nameLength) { builder.advanceLexer() }
        name.done(VisualXSharpTypes.NAME)
        index += head.nameLength
        continue
      }
      if (depth == 0) {
        if (text == ";") {
          builder.advanceLexer()
          break
        }
        if (text == "}") break
        if (text == "{") {
          if (initializer || body) {
            index += skipBalanced(builder, VisualXSharpTypes.BLOCK)
          } else {
            body = true
            parseBraces(builder, head.membersInBody)
            // A body ends the declaration unless an initializer or a terminator follows it, as in a property.
            if (builder.tokenText == "=") continue
            if (builder.tokenText == ";") builder.advanceLexer()
            break
          }
          continue
        }
        if (text == "=") initializer = true
      }
      when (text) {
        "(", "[" -> depth++
        ")", "]" -> if (depth > 0) depth--
        "{" -> {
          // Braces inside parentheses belong to an expression, such as a default argument.
          index += skipBalanced(builder, VisualXSharpTypes.BLOCK)
          continue
        }
        "}" -> if (depth > 0) depth = 0
      }
      if (text == "}" && depth == 0) break
      builder.advanceLexer()
      index++
    }
    declaration.done(head.type)
  }

  /** Parses `{ ... }` either as a type body with member declarations or as an opaque block. */
  private fun parseBraces(builder: PsiBuilder, members: Boolean) {
    if (!members) {
      skipBalanced(builder, VisualXSharpTypes.BLOCK)
      return
    }
    val body = builder.mark()
    builder.advanceLexer()
    parseBody(builder)
    if (builder.tokenText == "}") builder.advanceLexer()
    body.done(VisualXSharpTypes.TYPE_BODY)
  }

  /**
   * Consumes a brace-delimited stretch with its nested braces; with [type] it becomes a node.
   *
   * @return the number of tokens consumed.
   */
  private fun skipBalanced(builder: PsiBuilder, type: IElementType?): Int {
    val block = if (type == null) null else builder.mark()
    var depth = 0
    var count = 0
    while (!builder.eof()) {
      val text = builder.tokenText
      builder.advanceLexer()
      count++
      if (text == "{") depth++
      if (text == "}") {
        depth--
        if (depth == 0) break
      }
    }
    if (block != null && type != null) block.done(type)
    return count
  }

  /**
   * Looks ahead over the head of a declaration and decides its kind and where its name is.
   *
   * The builder is returned to where it stood. Offsets are counted in tokens from the start of the declaration.
   */
  private fun classify(builder: PsiBuilder): Head {
    val start = builder.mark()
    val head = scanHead(builder)
    start.rollbackTo()
    return head
  }

  private fun scanHead(builder: PsiBuilder): Head {
    var index = 0

    fun advance() {
      builder.advanceLexer()
      index++
    }

    fun skipGroup(open: String, close: String) {
      var depth = 0
      while (!builder.eof()) {
        val text = builder.tokenText
        if (text == "{" || text == "}" || text == ";") return
        advance()
        if (text == open) depth++
        // `>>` closes two template brackets at once.
        if (text == close || (close == ">" && text == ">>")) {
          depth -= if (text == ">>") 2 else 1
          if (depth <= 0) return
        }
      }
    }

    // Attributes, the template clause and modifiers come before the part that decides the kind.
    while (!builder.eof()) {
      val text = builder.tokenText
      when {
        text == "#" -> advance()
        text == "[" -> skipGroup("[", "]")
        text == "template" && lookAheadText(builder) == "<" -> {
          advance()
          skipGroup("<", ">")
        }
        text in MODIFIERS -> {
          // `static { ... }` is a static initializer, not a modifier of something that follows.
          if (text == "static" && lookAheadText(builder) == "{") {
            return Head(VisualXSharpTypes.INITIALIZER, NO_NAME, 0, membersInBody = false)
          }
          advance()
        }
        else -> break
      }
    }

    val keyword = builder.tokenText
    if (keyword in TYPE_KEYWORDS) {
      val keywordIndex = index
      val beforeKeyword = builder.mark()
      advance()
      // `data class` and `enum class` are two-word kinds.
      val classForm = builder.tokenText == "class" && (keyword == "data" || keyword == "enum")
      if (classForm) advance()
      if (builder.tokenType == VisualXSharpTypes.IDENTIFIER) {
        // The cases of an enumeration are not member declarations, so its body stays opaque.
        val members = keyword != "enum" && keyword !in BODILESS_TYPE_KEYWORDS
        beforeKeyword.drop()
        return Head(VisualXSharpTypes.TYPE_DECLARATION, index, 1, members)
      }
      // The word was a name after all, as in a field whose type is called `data`: scan it as a member.
      beforeKeyword.rollbackTo()
      index = keywordIndex
    }

    var nameIndex = NO_NAME
    var nameLength = 0
    var depth = 0
    var first = true
    while (!builder.eof()) {
      val text = builder.tokenText ?: ""
      if (depth == 0) {
        when (text) {
          "(" -> return Head(VisualXSharpTypes.CALLABLE_DECLARATION, nameIndex, nameLength, membersInBody = false)
          "{" ->
            return Head(
              if (first) VisualXSharpTypes.INITIALIZER else VisualXSharpTypes.PROPERTY_DECLARATION,
              nameIndex, nameLength, membersInBody = false,
            )
          "=", ";", "}" -> return Head(VisualXSharpTypes.FIELD_DECLARATION, nameIndex, nameLength, membersInBody = false)
        }
      }
      first = false
      when (text) {
        "[" -> depth++
        "]" -> if (depth > 0) depth--
      }
      if (depth == 0 && (builder.tokenType == VisualXSharpTypes.IDENTIFIER || text == "self")) {
        nameIndex = index
        nameLength = 1
        if (text == "Operator" && lookAheadText(builder) == "<") {
          // `Operator<+>` names an operator; the name runs to the closing bracket.
          val from = index
          advance()
          skipGroup("<", ">")
          nameLength = index - from
          continue
        }
      } else if (depth == 0 && text == "~" && nameIndex == NO_NAME) {
        // A destructor is named by the tilde and the type name after it.
        nameIndex = index
        advance()
        nameLength = if (builder.tokenType == VisualXSharpTypes.IDENTIFIER) 2 else 1
        if (nameLength == 2) advance()
        continue
      }
      advance()
    }
    return Head(VisualXSharpTypes.FIELD_DECLARATION, nameIndex, nameLength, membersInBody = false)
  }

  /** The text of the token after the current one, or null at the end. */
  private fun lookAheadText(builder: PsiBuilder): String? {
    val mark = builder.mark()
    builder.advanceLexer()
    val text = builder.tokenText
    mark.rollbackTo()
    return text
  }

  private companion object {
    const val NO_NAME = -1

    /** `declaration-modifiers` of the grammar. */
    val MODIFIERS =
      setOf(
        "public", "internal", "fileprivate", "protected", "private", "static", "final", "sealed", "abstract",
        "inline", "extern", "virtual", "override", "op", "async", "repeatable", "explicit",
      )

    /** The words that open a type-like declaration and are followed by its name. */
    val TYPE_KEYWORDS =
      setOf(
        "data", "class", "enum", "object", "interface", "type", "typealias", "concept", "extension", "attribute",
      )

    /** Type-like declarations that have no body of members. */
    val BODILESS_TYPE_KEYWORDS = setOf("typealias", "concept", "attribute")
  }
}
