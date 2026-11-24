package com.liuzhihang.doc.view.render;

import com.liuzhihang.doc.view.dto.DocViewParamData;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MarkdownTables {
    private MarkdownTables() {
    }

    private static final Pattern MARKDOWN_CELL_PIPE = Pattern.compile("(\\\\*)\\|");

    /**
     * Renders the current field values without changing the editor/custom-template tree.
     */
    public static String bodyMarkdown(List<DocViewParamData> dataList, boolean separate) {
        List<DocViewParamData> tableData = BodyTableData.project(dataList);
        return separate ? separateParamMarkdown(tableData) : paramMarkdown(tableData);
    }

    /**
     * dataList 转为 Markdown 文本
     *
     * @param dataList
     * @return
     */
    @NotNull
    public static String paramMarkdown(List<DocViewParamData> dataList) {

        if (dataList == null || dataList.isEmpty()) {
            return "";
        }

        return "|参数名|类型|必填|默认值|描述|版本|\n"
                + "|:-----|:-----|:-----|:-----|:-----|:-----|\n"
                + paramMarkdownContent(dataList);
    }

    /**
     * 切分多个展示
     */
    private static String separateParamMarkdown(List<DocViewParamData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return "";
        }
        List<DocViewParamData> paramDataList = new ArrayList<>();

        StringBuilder builder = new StringBuilder();
        builder.append("|参数名|类型|必填|默认值|描述|版本|\n")
                .append("|:-----|:-----|:-----|:-----|:-----|:-----|\n");
        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getName())
                    .append("|").append(data.getType())
                    .append("|").append(Boolean.TRUE.equals(data.getRequired()) ? "Y" : "N")
                    .append("|").append(data.getExample())
                    .append("|").append(markdownDescription(data.getDesc()))
                    .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                    .append("|").append("\n");
            if (data.getChildList() != null && !data.getChildList().isEmpty()) {
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
        if (dataList == null || dataList.isEmpty()) {
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
                    .append("|").append(data.getExample())
                    .append("|").append(markdownDescription(data.getDesc()))
                    .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                    .append("|").append("\n");
            if (data.getChildList() != null && !data.getChildList().isEmpty()) {
                builder.append(paramMarkdownContent(data.getChildList()));
            }
        }

        return builder;
    }

    private static String markdownDescription(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        // Regex alternation and escaped pipes belong inside the cell, including their literal slashes.
        return MARKDOWN_CELL_PIPE.matcher(text)
                .replaceAll(match -> "&#92;".repeat(match.group(1).length()) + "&#124;")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>");
    }

    @NotNull
    public static String headerMarkdown(List<DocViewParamData> dataList) {

        if (dataList == null || dataList.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getName())
                    .append("|").append(data.getExample())
                    .append("|").append(data.getRequired() ? "Y" : "N")
                    .append("|").append(markdownDescription(data.getDesc()))
                    .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                    .append("|").append("\n");
        }

        return "|参数名|参数值|必填|描述|版本|\n"
                + "|:-----|:-----|:-----|:-----|:-----|\n"
                + builder;
    }
}
