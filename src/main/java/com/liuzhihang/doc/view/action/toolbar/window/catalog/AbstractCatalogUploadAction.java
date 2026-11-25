package com.liuzhihang.doc.view.action.toolbar.window.catalog;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.openapi.actionSystem.AnAction;
import com.liuzhihang.doc.view.enums.UploadPlatform;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import com.liuzhihang.doc.view.data.DocViewDataKeys;
import com.liuzhihang.doc.view.ui.window.DocViewNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author liuzhihang
 * @date 2021/10/23 23:13
 */
public abstract class AbstractCatalogUploadAction extends AnAction {

    private final UploadPlatform platform;

    protected AbstractCatalogUploadAction(UploadPlatform platform) {
        this.platform = platform;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project actionProject = e.getProject();
        SimpleTree simpleTree = e.getData(DocViewDataKeys.WINDOW_CATALOG_TREE);
        boolean available = actionProject != null
                && !actionProject.isDisposed()
                && !DumbService.isDumb(actionProject)
                && simpleTree != null;
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        // 获取当前project对象
        Project project = e.getProject();
        SimpleTree simpleTree = e.getData(DocViewDataKeys.WINDOW_CATALOG_TREE);

        if (simpleTree == null || project == null) {
            return;
        }

        SimpleNode selectedNode = simpleTree.getSelectedNode();

        if (selectedNode instanceof DocViewNode docViewNode) {
            DocViewUploadService.getInstance(platform).upload(project, docViewNode.docViewList());
        }
    }

}
