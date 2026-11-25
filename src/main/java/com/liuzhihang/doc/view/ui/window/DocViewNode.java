package com.liuzhihang.doc.view.ui.window;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.liuzhihang.doc.view.dto.DocView;

import java.util.List;

/**
 * @author liuzhihang
 * @date 2022/4/5 16:05
 */
public abstract class DocViewNode extends CachingSimpleNode {

    protected DocViewNode(SimpleNode parentNode) {
        super(parentNode);

    }

    /**
     * 构建当前节点的子节点。
     * @return 子节点数组
     */
    @Override
    protected abstract SimpleNode[] buildChildren();

    @Override
    public String getName() {
        return "Doc View";
    }

    /** Search uses presentation snapshots rather than parsing or rendering a document. */
    public String getSearchText() {
        return getName();
    }

    /**
     * 返回当前节点对应的文档。
     * @return 文档列表
     */
    public abstract List<DocView> docViewList();

    /**
     * 按当前项目刷新节点内容。
     * @param project 当前项目
     */
    public abstract void updateNode(Project project);

    /**
     * 生成的文档保存路径
     *
     * @param project project
     * @return path
     */
    public abstract String docPath(Project project);

    /**
     * 生成的 .http 文件路径
     *
     * @param project project
     * @return path
     */
    public abstract String httpPath(Project project);

}
