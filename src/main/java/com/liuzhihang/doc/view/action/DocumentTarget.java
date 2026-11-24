package com.liuzhihang.doc.view.action;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;

/** PSI and caret belonging to the same user selection. */
public record DocumentTarget(Project project, PsiFile file, @Nullable Editor editor,
                             PsiClass type, @Nullable PsiMethod method) {}
