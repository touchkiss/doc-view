package com.liuzhihang.doc.view.dto;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.TemplateSettings;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.utils.VelocityUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DocView 的模版 用来使用 Velocity 生成内容
 * <p>
 * Velocity 会根据 get 方法 获取值, 不提供 set 方法
 *
 * @author liuzhihang
 * @date 2020/11/21 16:39
 */
@Slf4j
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

    private final String requestJsonWithDesc;

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
    private final String responseJsonWithDesc;

    /**
     * 返回示例
     */
    private final String responseExample;

    private final String type;

    public DocViewData(DocView docView) {

        Settings settings = Settings.getInstance(docView.getPsiClass().getProject());

        this.name = docView.getName();
        this.desc = docView.getDesc();
        this.path = docView.getPath();
        this.method = docView.getMethod();
        this.type = docView.getType().toString();

        this.requestHeaderDataList = headerDataList(docView.getHeaderList());
        this.requestHeader = headerMarkdown(requestHeaderDataList);

        this.requestParamDataList = paramDataList(docView.getReqParamList());
        this.requestParam = paramMarkdown(requestParamDataList);

        this.requestBodyDataList = buildBodyDataList(docView.getReqBody().getChildList());
        this.requestBody = settings.getSeparateParam() ? separateParamMarkdown(requestBodyDataList) : paramMarkdown(requestBodyDataList);
        this.requestJsonWithDesc = buildJsonWithDesc(requestBodyDataList);
        this.requestExample = requestExample(docView);

        this.responseParamDataList = buildBodyDataList(docView.getRespBody().getChildList());
        this.responseParam = settings.getSeparateParam() ? separateParamMarkdown(responseParamDataList) : paramMarkdown(responseParamDataList);
        this.responseExample = respBodyExample(docView.getRespExample());

        this.responseJsonWithDesc = buildJsonWithDesc(responseParamDataList);
        log.info("responseJsonWithDesc:{}", responseJsonWithDesc);
    }

    /**
     * 带有描述的 JSON
     *
     * @param responseParamDataList
     * @return
     */
    private String buildJsonWithDesc(List<DocViewParamData> responseParamDataList) {
        if (CollectionUtils.isEmpty(responseParamDataList)) {
            return "";
        }
        return "{\n" + buildJsonWithDescContent(responseParamDataList) + "}";
    }

    private String buildJsonWithDescContent(List<DocViewParamData> responseParamDataList) {
        StringBuilder builder = new StringBuilder();
        for (int j = 0; j < responseParamDataList.size(); j++) {
            DocViewParamData data = responseParamDataList.get(j);
            String tab = "    ";
            for (int i = 0; i < data.getTabCount(); i++) {
                builder.append(tab);
            }
            builder.append("\"").append(data.getName()).append("\": ");
            if (CollectionUtils.isNotEmpty(data.getChildList())) {
                if (data.isArray()) {
                    builder.append("[");
                    addDesc(data, builder, tab);
                    for (int i = 0; i < data.getTabCount() + 1; i++) {
                        builder.append(tab);
                    }
                    builder.append("{\n");
                } else {
                    builder.append("{");
                    addDesc(data, builder, tab);
                }
                if (data.getChildList().size() == 1 && "element".equals(data.getChildList().get(0).getName())) {
                    builder.append(buildJsonWithDescContent(data.getChildList().get(0).getChildList()));
                } else {
                    builder.append(buildJsonWithDescContent(data.getChildList()));
                }
                if (data.isArray()) {
                    for (int i = 0; i < data.getTabCount() + 1; i++) {
                        builder.append(tab);
                    }
                    builder.append("}\n");
                    for (int i = 0; i < data.getTabCount(); i++) {
                        builder.append(tab);
                    }
                    builder.append("]");
                } else {
                    for (int i = 0; i < data.getTabCount(); i++) {
                        builder.append(tab);
                    }
                    builder.append("}\n");
                }
                if (j < responseParamDataList.size() - 1) {
                    builder.append(",\n");
                } else if (data.isArray()) {
                    builder.append("\n");
                }
            } else {
                if (doNotNeedQuote(data.getType())) {
                    builder.append(data.getExample());
                } else {
                    builder.append("\"").append(data.getExample()).append("\"");
                }
                if (j < responseParamDataList.size() - 1) {
                    builder.append(",");
                }
                addDesc(data, builder, tab);
            }
        }
        return builder.toString();
    }

    private static boolean doNotNeedQuote(String type) {
        return "int".equals(type) || "Integer".equals(type)
                || "long".equals(type) || "Long".equals(type)
                || "float".equals(type) || "Float".equals(type)
                || "double".equals(type) || "Double".equals(type)
                || "boolean".equals(type) || "Boolean".equals(type);
    }

    private static void addDesc(DocViewParamData data, StringBuilder builder, String tab) {
        if (StringUtils.isNotBlank(data.getDesc())) {
            builder.append(tab).append("//").append(" ").append(data.getDesc()).append("\n");
        } else {
            builder.append("\n");
        }
    }

    @NotNull
    @Contract("_ -> new")
    public static DocViewData getInstance(@NotNull DocView docView) {
        return new DocViewData(docView);
    }

    public static String markdownText(Project project, DocView docView) {

        DocViewData docViewData = new DocViewData(docView);

        if (docView.getType() == FrameworkEnum.DUBBO) {
            return VelocityUtils.convert(TemplateSettings.getInstance(project).getDubboTemplate(), docViewData);
        } else {
            // 按照 Spring 模版
            return VelocityUtils.convert(TemplateSettings.getInstance(project).getSpringTemplate(), docViewData);
        }
    }

    /**
     * dataList 转为 Markdown 文本
     *
     * @param dataList
     * @return
     */
    @NotNull
    private static String paramMarkdown(List<DocViewParamData> dataList) {

        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        return "|参数名|类型|必填|描述|\n"
                + "|:-----|:-----|:-----|:-----|\n"
                + paramMarkdownContent(dataList);
    }

    /**
     * 切分多个展示
     */
    private static String separateParamMarkdown(List<DocViewParamData> dataList) {
        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }
        List<DocViewParamData> paramDataList = new ArrayList<>();

        StringBuilder builder = new StringBuilder();
        builder.append("|参数名|类型|必填|描述|\n")
                .append("|:-----|:-----|:-----|:-----|\n");
        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getName())
                    .append("|").append(data.getType())
                    .append("|").append(Boolean.TRUE.equals(data.getRequired()) ? "Y" : "N")
                    .append("|").append(data.getDesc())
                    .append("|").append("\n");
            if (CollectionUtils.isNotEmpty(data.getChildList())) {
                paramDataList.add(data);
            }
        }
        builder.append(separateSubParamMarkdown(paramDataList));
        return builder.toString();
    }

    /**
     * 构造子的参数 Markdown 实体
     */
    private static String separateSubParamMarkdown(List<DocViewParamData> dataList) {
        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (DocViewParamData data : dataList) {
            builder.append("\n- ").append(data.getType()).append(" ").append(data.getName()).append("\n\n");
            builder.append(separateParamMarkdown(data.getChildList()));
        }

        return builder.toString();
    }


    /**
     * 表格内数据
     */
    private static StringBuilder paramMarkdownContent(List<DocViewParamData> dataList) {

        StringBuilder builder = new StringBuilder();

        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getPrefixSymbol1()).append(data.getPrefixSymbol2()).append(data.getName())
                    .append("|").append(data.getType())
                    .append("|").append(data.getRequired() ? "Y" : "N")
                    .append("|").append(data.getDesc())
                    .append("|").append("\n");
            if (CollectionUtils.isNotEmpty(data.getChildList())) {
                builder.append(paramMarkdownContent(data.getChildList()));
            }
        }

        return builder;
    }

    @NotNull
    private static String headerMarkdown(List<DocViewParamData> dataList) {

        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getName())
                    .append("|").append(data.getExample())
                    .append("|").append(data.getRequired() ? "Y" : "N")
                    .append("|").append(data.getDesc())
                    .append("|").append("\n");
        }

        return "|参数名|参数值|必填|描述|\n"
                + "|:-----|:-----|:-----|:-----|\n"
                + builder;
    }

    private List<DocViewParamData> headerDataList(List<Header> headerList) {

        if (CollectionUtils.isEmpty(headerList)) {
            return new ArrayList<>();
        }

        return headerList.stream().map(header -> {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(header.getPsiElement());
            data.setName(header.getName());
            data.setExample(header.getValue());
            data.setRequired(header.getRequired());
            data.setDesc(StringUtils.isNotBlank(header.getDesc()) ? header.getDesc() : "");

            return data;
        }).collect(Collectors.toList());
    }

    private List<DocViewParamData> paramDataList(List<Param> reqParamList) {
        if (CollectionUtils.isEmpty(reqParamList)) {
            return new ArrayList<>();
        }

        return reqParamList.stream().map(param -> {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(param.getPsiElement());
            data.setExample(param.getExample());
            data.setName(param.getName());
            data.setRequired(param.getRequired());
            data.setType(param.getType());
            data.setDesc(StringUtils.isNotBlank(param.getDesc()) ? param.getDesc() : "");

            return data;
        }).collect(Collectors.toList());
    }

    /**
     * 根据 bodyList 构建参数集合
     *
     * @param bodyList
     * @return
     */
    @NotNull
    public static List<DocViewParamData> buildBodyDataList(List<Body> bodyList) {

        if (CollectionUtils.isEmpty(bodyList)) {
            return new ArrayList<>();
        }

        return buildBodyDataList(bodyList, "", "", 1);
    }

    /**
     * 递归 body 生成 List<ParamData>
     *
     * @param prefixSymbol1,
     * @param bodyList
     * @param prefixSymbol2
     * @param i
     */
    @NotNull
    private static List<DocViewParamData> buildBodyDataList(@NotNull List<Body> bodyList, String prefixSymbol1, String prefixSymbol2, int tabCount) {

        List<DocViewParamData> dataList = new ArrayList<>();

        for (Body body : bodyList) {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(body.getPsiElement());
            data.setName(body.getName());
            data.setExample(body.getExample());
            data.setRequired(body.getRequired());
            data.setType(body.getType());
            data.setDesc(StringUtils.isNotBlank(body.getDesc()) ? body.getDesc() : "");

            data.setPrefixSymbol1(prefixSymbol1);
            data.setPrefixSymbol2(prefixSymbol2);
            data.setTabCount(tabCount);
            data.setArray(body.isArray());

            if (CollectionUtils.isNotEmpty(body.getChildList())) {

                Settings settings = Settings.getInstance(body.getPsiElement().getProject());

                data.setChildList(
                        buildBodyDataList(body.getChildList(), settings.getPrefixSymbol1(), prefixSymbol2 + settings.getPrefixSymbol2(), tabCount + 1));
            }
            dataList.add(data);
        }
        return dataList;
    }

    @NotNull
    private String requestExample(DocView docView) {

        String reqFormExample = reqFormExample(docView.getReqFormExample());

        String reqBodyExample = reqBodyExample(docView.getReqBodyExample());

        if (StringUtils.isBlank(reqFormExample)) {
            return reqBodyExample;
        }

        if (StringUtils.isBlank(reqBodyExample)) {
            return reqFormExample;
        }

        return reqFormExample + "\n\n" + reqBodyExample;
    }

    /**
     * 请求参数中的 Form 示例
     *
     * @param reqFormExample
     * @return
     */
    private String reqFormExample(String reqFormExample) {
        if (StringUtils.isBlank(reqFormExample)) {
            return "";
        }

        return "```Form\n" +
                reqFormExample + "\n" +
                "```";
    }

    /**
     * 请求参数中的 Body 示例
     *
     * @param reqBodyExample Body
     * @return 组装结果
     */
    @NotNull
    @Contract(pure = true)
    private String reqBodyExample(String reqBodyExample) {

        if (StringUtils.isBlank(reqBodyExample)) {
            return "";
        }

        return "```JSON\n"
                + reqBodyExample + "\n"
                + "```";
    }

    /**
     * 构建返回 body
     *
     * @param respExample 返回示例
     * @return 返回body
     */
    @NotNull
    @Contract(pure = true)
    private String respBodyExample(String respExample) {

        if (StringUtils.isBlank(respExample)) {
            return "";
        }

        return "```JSON\n"
                + respExample + "\n"
                + "```\n\n";
    }

}
