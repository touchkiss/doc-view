package com.liuzhihang.doc.view.render;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.TemplateSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.utils.VelocityUtils;

public final class DocumentMarkdownRenderer {
    private DocumentMarkdownRenderer() {
    }

    public static String render(Project project, DocView document) {
        DocViewData data = new DocViewData(project, document);
        TemplateSettings templates = TemplateSettings.getInstance(project);
        String template = switch (document.getType()) {
            case DUBBO -> templates.getDubboTemplate();
            case NONE_POJO -> templates.getPojoTemplate();
            case SPRING -> templates.getSpringTemplate();
        };
        return VelocityUtils.convert(template, data);
    }
}
