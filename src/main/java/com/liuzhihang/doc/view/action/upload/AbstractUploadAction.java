package com.liuzhihang.doc.view.action.upload;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.action.AbstractAction;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;
import org.jetbrains.annotations.NotNull;


/**
 * @author liuzhihang
 * @date 2021/10/23 23:13
 */
public abstract class AbstractUploadAction extends AbstractAction {

    /**
     * @see AnAction#actionPerformed(AnActionEvent)
     */
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 先执行抽象类逻辑
        super.actionPerformed(e);

        if (project == null || targetClass == null) {
            return;
        }

        Project uploadProject = project;
        PsiClass uploadClass = targetClass;
        PsiMethod uploadMethod = targetMethod;
        DocViewUploadService docViewUploadService = uploadService();
        DocViewBackgroundTasks.runReadTask(
                uploadProject,
                DocViewBundle.message("task.docview.upload"),
                true,
                indicator -> DocViewService.getInstance(uploadProject, uploadClass).buildDoc(uploadClass, uploadMethod),
                docViewList -> docViewUploadService.upload(uploadProject, docViewList),
                throwable -> DocViewNotification.notifyError(uploadProject, throwable.getMessage())
        );

    }

    /**
     * 具体上传服务
     */
    protected abstract DocViewUploadService uploadService();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}
