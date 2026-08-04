package com.liuzhihang.doc.view.action.upload;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.liuzhihang.doc.view.action.AbstractAction;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

        // 抽象类中的校验失败时已经给出通知, 这里不能继续往下走
        if (project == null || targetClass == null) {
            return;
        }

        List<DocView> docViewList = DocViewService.getInstance(project, targetClass).buildDoc(targetClass, targetMethod);

        // 上传
        uploadService().upload(project, docViewList);

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
