package com.liuzhihang.doc.view.render;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class TemplateParameterMapper {
    private TemplateParameterMapper() {
    }

    public static List<DocViewParamData> headerDataList(List<Header> headerList) {

        if (headerList == null || headerList.isEmpty()) {
            return new ArrayList<>();
        }

        return headerList.stream().map(header -> {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(header.getPsiElement());
            data.setName(header.getName());
            data.setExample(header.getValue());
            data.setRequired(header.getRequired());
            data.setDescription(header.getDescription().orEmpty());

            return data;
        }).collect(Collectors.toList());
    }

    public static List<DocViewParamData> paramDataList(List<Param> reqParamList) {
        if (reqParamList == null || reqParamList.isEmpty()) {
            return new ArrayList<>();
        }

        return reqParamList.stream().map(param -> {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(param.getPsiElement());
            data.setExample(param.getExample());
            data.setName(param.getName());
            data.setRequired(param.getRequired());
            data.setType(param.getType());
            data.setDescription(param.getDescription().orEmpty());

            data.setVersion(param.getVersion());
            data.setSince(param.getSince());

            return data;
        }).collect(Collectors.toList());
    }


    public static List<DocViewParamData> buildBodyDataList(Project project, List<Body> bodyList) {
        if (bodyList == null || bodyList.isEmpty()) {
            return new ArrayList<>();
        }
        return buildBodyDataList(project, bodyList, "", "", 1);
    }

    /**
     * 递归 body 生成 List<ParamData>
     *
     * @param prefixSymbol1,
     * @param bodyList
     * @param prefixSymbol2
     * @param tabCount JSON5 indentation depth
     */
    @NotNull
    private static List<DocViewParamData> buildBodyDataList(Project project, @NotNull List<Body> bodyList,
                                                            String prefixSymbol1, String prefixSymbol2, int tabCount) {

        List<DocViewParamData> dataList = new ArrayList<>();

        for (Body body : bodyList) {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(body.getPsiElement());
            data.setName(body.getName());
            data.setExample(body.getExample());
            data.setRequired(body.getRequired());
            data.setType(body.getType());
            data.setDescription(body.getDescription().orEmpty());

            data.setVersion(body.getVersion());
            data.setSince(body.getSince());

            data.setPrefixSymbol1(prefixSymbol1);
            data.setPrefixSymbol2(prefixSymbol2);
            data.setTabCount(tabCount);
            data.setCollection(body.isCollection());
            data.setMap(body.isMap());
            data.setSyntheticContainerNode(body.isSyntheticContainerNode());
            data.setOmitFromExample(body.isOmitFromExample());

            if (body.getChildList() != null && !body.getChildList().isEmpty()) {

                Project settingsProject = project == null ? body.getPsiElement().getProject() : project;
                Settings settings = Settings.getInstance(settingsProject);

                data.setChildList(
                        buildBodyDataList(settingsProject, body.getChildList(), settings.getPrefixSymbol1(),
                                prefixSymbol2 + settings.getPrefixSymbol2(), tabCount + 1));
            }
            dataList.add(data);
        }
        return dataList;
    }
}
