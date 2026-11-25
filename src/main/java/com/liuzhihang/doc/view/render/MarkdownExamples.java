package com.liuzhihang.doc.view.render;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.utils.CurlHostUtils;
import com.liuzhihang.doc.view.utils.CurlUtils;
import org.apache.commons.lang3.StringUtils;

public final class MarkdownExamples {
    private MarkdownExamples() {
    }

    public static String curlMarkdown(DocView docView) {
        // Keep empty/non-HTTP documents independent of PSI, as before the explicit-project overload.
        if (StringUtils.isBlank(CurlUtils.build(docView))) {
            return "";
        }
        return curlMarkdown(docView.getPsiClass().getProject(), docView);
    }

    public static String curlMarkdown(Project project, DocView docView) {
        String curl = CurlUtils.build(docView);
        if (StringUtils.isBlank(curl)) {
            return "";
        }
        // 文档中的 curl 示例使用独立的域名配置, 默认为 {{host}} 即保持占位符不变
        curl = CurlHostUtils.applyDocHost(project, curl);
        return "```bash\n" + curl + "\n```\n\n";
    }

    public static String requestExample(DocView docView) {
        String form = codeBlock("Form", docView.getReqFormExample());
        String body = codeBlock("JSON", docView.getReqBodyExample());
        if (form.isEmpty()) {
            return body;
        }
        if (body.isEmpty()) {
            return form;
        }
        return form + "\n\n" + body;
    }

    public static String responseExample(String example) {
        String block = codeBlock("JSON", example);
        return block.isEmpty() ? "" : block + "\n\n";
    }

    private static String codeBlock(String language, String content) {
        return StringUtils.isBlank(content) ? "" : "```" + language + "\n" + content + "\n```";
    }
}
