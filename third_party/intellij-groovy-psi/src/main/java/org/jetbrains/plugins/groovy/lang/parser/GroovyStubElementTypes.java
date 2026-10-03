// Modified by Progmasoft for Xide. See third_party/intellij-groovy-psi/UPSTREAM.md for the changes.
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;

import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

public interface GroovyStubElementTypes {
  IElementType CLASS_TYPE_DEFINITION = GroovyElementTypes.CLASS_TYPE_DEFINITION;
  IElementType RECORD_TYPE_DEFINITION = GroovyElementTypes.RECORD_TYPE_DEFINITION;
  IElementType INTERFACE_TYPE_DEFINITION = GroovyElementTypes.INTERFACE_TYPE_DEFINITION;
  IElementType ENUM_TYPE_DEFINITION = GroovyElementTypes.ENUM_TYPE_DEFINITION;
  IElementType ANNOTATION_TYPE_DEFINITION = GroovyElementTypes.ANNOTATION_TYPE_DEFINITION;
  IElementType ANONYMOUS_TYPE_DEFINITION = GroovyElementTypes.ANONYMOUS_TYPE_DEFINITION;
  IElementType TRAIT_TYPE_DEFINITION = GroovyElementTypes.TRAIT_TYPE_DEFINITION;
  IElementType ENUM_CONSTANT_INITIALIZER = GroovyElementTypes.ENUM_CONSTANT_INITIALIZER;

  IElementType ENUM_CONSTANT = GroovyElementTypes.ENUM_CONSTANT;
  IElementType FIELD = GroovyElementTypes.FIELD;
  IElementType METHOD = GroovyElementTypes.METHOD;
  IElementType ANNOTATION_METHOD = GroovyElementTypes.ANNOTATION_METHOD;

  IElementType IMPLEMENTS_CLAUSE = GroovyElementTypes.IMPLEMENTS_CLAUSE;
  IElementType EXTENDS_CLAUSE = GroovyElementTypes.EXTENDS_CLAUSE;
  IElementType PERMITS_CLAUSE = GroovyElementTypes.PERMITS_CLAUSE;

  IElementType PACKAGE_DEFINITION = GroovyElementTypes.PACKAGE_DEFINITION;

  IElementType IMPORT = GroovyElementTypes.IMPORT;

  IElementType TYPE_PARAMETER = GroovyElementTypes.TYPE_PARAMETER;

  IElementType TYPE_PARAMETER_BOUNDS_LIST = GroovyElementTypes.TYPE_PARAMETER_BOUNDS_LIST;

  IElementType CONSTRUCTOR = GroovyElementTypes.CONSTRUCTOR;

  IElementType THROWS_CLAUSE = GroovyElementTypes.THROWS_CLAUSE;

  IElementType ANNOTATION_MEMBER_VALUE_PAIR = GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR;

  IElementType ANNOTATION = GroovyElementTypes.ANNOTATION;

  IElementType PARAMETER = GroovyElementTypes.PARAMETER;

  IElementType VARIABLE_DECLARATION = GroovyElementTypes.VARIABLE_DECLARATION;
  IElementType VARIABLE = GroovyElementTypes.VARIABLE;

  IElementType MODIFIER_LIST = GroovyElementTypes.MODIFIER_LIST;
}
