package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.liuzhihang.doc.view.ui.PreviewForm;
import org.jetbrains.annotations.NotNull;

/**
 * 单个类或者方法中操作
 *
 * @author liuzhihang
 * @date 2020/2/26 21:57
 */
public class PreviewAction extends AbstractAction {

    /**
     * @see AnAction#actionPerformed(AnActionEvent)
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        // 先执行抽象类逻辑
        super.actionPerformed(e);

        // 抽象类中的校验失败时已经给出通知, 这里不能继续往下走
        if (targetClass == null) {
            return;
        }

        // 预览文档
        PreviewForm.getInstance(targetClass, targetMethod).popup();
    }

    /**
     * @see AnAction#update(AnActionEvent)
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}
