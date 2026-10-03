// Modified by Progmasoft for Xide. See third_party/intellij-groovy-psi/UPSTREAM.md for the changes.
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;

import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

public interface GroovyEmptyStubElementTypes {
  IElementType ANNOTATION_ARGUMENT_LIST = GroovyElementTypes.ANNOTATION_ARGUMENT_LIST;
  IElementType ENUM_CONSTANTS = GroovyElementTypes.ENUM_CONSTANTS;
  IElementType TYPE_PARAMETER_LIST = GroovyElementTypes.TYPE_PARAMETER_LIST;
  IElementType PARAMETER_LIST = GroovyElementTypes.PARAMETER_LIST;
  IElementType CLASS_BODY = GroovyElementTypes.CLASS_BODY;
  GrEnumDefinitionBodyElementType ENUM_BODY = GroovyElementTypes.ENUM_BODY;
}
