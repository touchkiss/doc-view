package com.liuzhihang.doc.view.dto;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.render.DocumentMarkdownRenderer;
import com.liuzhihang.doc.view.render.Json5ExampleRenderer;
import com.liuzhihang.doc.view.render.MarkdownExamples;
import com.liuzhihang.doc.view.render.MarkdownTables;
import com.liuzhihang.doc.view.render.TemplateParameterMapper;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Variables exposed to built-in and user-defined Velocity templates. */
@Data
public class DocViewData {

    /**
     * 文档名称
     */
    private final String name;

    /**
     * 文档描述
     */
    private final String desc;

    private final String since;

    private final String version;

    /**
     * 环境地址
     */
    // private final   String domain;

    /**
     * 接口地址
     */
    private final String path;

    /**
     * 请求方式 GET POST PUT DELETE HEAD OPTIONS PATCH
     */
    private final String method;

    /**
     * headers
     */
    private final List<DocViewParamData> requestHeaderDataList;

    private final String requestHeader;

    /**
     * 请求参数
     */
    private final List<DocViewParamData> requestParamDataList;

    private final String requestParam;

    /**
     * 请求参数
     */
    private final List<DocViewParamData> requestBodyDataList;

    /**
     * 请求中 body 参数
     */
    private final String requestBody;

    private final String requestJson5;

    /**
     * 请求示例
     */
    private final String requestExample;

    /**
     * 返回参数
     */
    private final List<DocViewParamData> responseParamDataList;
    private final String responseParam;

    /**
     * 带有 DESC 响应 JSON
     */
    private final String responseJson5;

    /**
     * 返回示例
     */
    private final String responseExample;

    /**
     * curl 示例 (Markdown 代码块)
     */
    private final String curlExample;

    private final String type;

    public DocViewData(DocView docView) {
        this(docView.getPsiClass().getProject(), docView);
    }

    /** Builds template data for a project, including PSI-free preview samples. */
    public DocViewData(Project project, DocView docView) {
        Settings settings = Settings.getInstance(project);

        this.name = docView.getName();
        this.desc = docView.getDesc();
        this.since = StringUtils.defaultString(docView.getSince());
        this.version = StringUtils.defaultString(docView.getVersion());
        this.path = docView.getPath();
        this.method = docView.getMethod();
        this.type = docView.getType().toString();

        this.requestHeaderDataList = TemplateParameterMapper.headerDataList(docView.getHeaderList());
        this.requestHeader = MarkdownTables.headerMarkdown(requestHeaderDataList);

        this.requestParamDataList = TemplateParameterMapper.paramDataList(docView.getReqParamList());
        this.requestParam = MarkdownTables.paramMarkdown(requestParamDataList);

        this.requestBodyDataList = buildBodyDataList(project, docView.getReqBody().getChildList());
        this.requestBody = bodyMarkdown(requestBodyDataList, settings.getSeparateParam());
        this.requestJson5 = Json5ExampleRenderer.render(requestBodyDataList);
        this.requestExample = MarkdownExamples.requestExample(docView);

        this.responseParamDataList = buildBodyDataList(project, docView.getRespBody().getChildList());
        this.responseParam = bodyMarkdown(responseParamDataList, settings.getSeparateParam());
        this.responseExample = MarkdownExamples.responseExample(docView.getRespExample());

        this.responseJson5 = Json5ExampleRenderer.render(responseParamDataList);
        this.curlExample = docView.getType() == FrameworkEnum.SPRING ? curlMarkdown(project, docView) : "";
    }

    @NotNull
    @Contract("_ -> new")
    public static DocViewData getInstance(@NotNull DocView docView) {
        return new DocViewData(docView);
    }

    public static String markdownText(Project project, DocView docView) {
        return DocumentMarkdownRenderer.render(project, docView);
    }

    public static String curlMarkdown(DocView docView) {
        return MarkdownExamples.curlMarkdown(docView);
    }

    public static String curlMarkdown(Project project, DocView docView) {
        return MarkdownExamples.curlMarkdown(project, docView);
    }

    public static String bodyMarkdown(List<DocViewParamData> rows, boolean separate) {
        return MarkdownTables.bodyMarkdown(rows, separate);
    }

    @NotNull
    public static List<DocViewParamData> buildBodyDataList(List<Body> bodyList) {
        return TemplateParameterMapper.buildBodyDataList(null, bodyList);
    }

    public static List<DocViewParamData> buildBodyDataList(Project project, List<Body> bodyList) {
        return TemplateParameterMapper.buildBodyDataList(project, bodyList);
    }
}
