package com.liuzhihang.doc.view.action.toolbar.window.catalog;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.ui.PreviewForm;
import org.jetbrains.annotations.NotNull;

public class CatalogPreviewSourceAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var source = event.getData(CommonDataKeys.PSI_ELEMENT);
        event.getPresentation().setEnabledAndVisible(project != null && !DumbService.isDumb(project)
                && (source instanceof PsiClass || source instanceof PsiMethod) && source.isValid());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var source = event.getData(CommonDataKeys.PSI_ELEMENT);
        if (source instanceof PsiMethod method && method.isValid() && method.getContainingClass() != null) {
            PreviewForm.getInstance(method.getContainingClass(), method).popup();
        } else if (source instanceof PsiClass type && type.isValid()) {
            PreviewForm.getInstance(type, null).popup();
        }
    }
}
