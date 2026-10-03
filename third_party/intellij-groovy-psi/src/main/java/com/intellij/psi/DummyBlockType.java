// Modified by Progmasoft for Xide. See third_party/intellij-groovy-psi/UPSTREAM.md for the changes.
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.kotlin.com.intellij.psi.PsiReference;

import org.jetbrains.kotlin.com.intellij.lang.ASTNode;
import org.jetbrains.kotlin.com.intellij.lang.Language;
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.CompositePsiElement;
import org.jetbrains.kotlin.com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class DummyBlockType {
  public static final IElementType DUMMY_BLOCK = new DummyBlockElementType();

  private static class DummyBlockElementType extends IElementType implements ICompositeElementType {
    DummyBlockElementType() {
      super("DUMMY_BLOCK", Language.ANY);
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new DummyBlock();
    }
  }

  public static class DummyBlock extends CompositePsiElement {
    public DummyBlock() {
      super(DUMMY_BLOCK);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
      return PsiReference.EMPTY_ARRAY;
    }

    @Override
    public @NotNull Language getLanguage() {
      return getParent().getLanguage();
    }
  }
}