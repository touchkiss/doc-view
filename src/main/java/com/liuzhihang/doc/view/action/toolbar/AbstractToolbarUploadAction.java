package com.liuzhihang.doc.view.action.toolbar;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractToolbarUploadAction extends AnAction {
    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        event.getPresentation().setEnabledAndVisible(project != null && !project.isDisposed() && !DumbService.isDumb(project));
    }
}
