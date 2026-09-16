package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nullable;

/** Evaluates Java constants from source or compiled PSI without loading user classes. */
public final class PsiConstantUtils {
    private PsiConstantUtils() {
    }

    @Nullable
    public static Object constantValue(@Nullable PsiElement expression) {
        if (!(expression instanceof PsiExpression)) {
            return null;
        }
        try {
            return JavaPsiFacade.getInstance(expression.getProject())
                    .getConstantEvaluationHelper().computeConstantExpression(expression);
        } catch (IndexNotReadyException ignored) {
            // Keep documentation available while IDEA is indexing.
            return null;
        }
    }

    @Nullable
    public static String expressionText(@Nullable PsiExpression expression) {
        if (expression == null) {
            return null;
        }
        Object value = constantValue(expression);
        return value != null ? String.valueOf(value) : expression.getText().replaceAll("^\"|\"$", "");
    }
}
