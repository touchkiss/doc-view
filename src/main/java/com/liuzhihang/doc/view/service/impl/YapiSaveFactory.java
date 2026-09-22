package com.liuzhihang.doc.view.service.impl;

import com.google.gson.Gson;
import com.intellij.psi.CommonClassNames;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YApiHeader;
import com.liuzhihang.doc.view.integration.dto.YApiQuery;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Pure conversion from a generated document into YApi's save DTO. */
public final class YapiSaveFactory {

    public YapiSave create(@NotNull YApiSettings settings, @NotNull YApiCat category,
                           @NotNull DocView docView) {
        YapiSave save = new YapiSave();
        save.setYapiUrl(settings.getUrl());
        save.setToken(settings.getToken());
        save.setProjectId(settings.getProjectId());
        save.setCatId(category.getId());
        if ("Dubbo".equals(docView.getMethod())) {
            save.setPath("/Dubbo/" + docView.getPsiMethod().getName());
            save.setMethod("POST");
        } else {
            save.setMethod(docView.getMethod());
            save.setPath(docView.getPath());
        }
        save.setReqBodyType(docView.getContentType().toString().toLowerCase());
        save.setReqBodyForm(new ArrayList<>());
        save.setReqParams(new ArrayList<>());
        save.setReqHeaders(buildReqHeaders(docView.getHeaderList()));
        save.setReqQuery(buildReqQuery(docView.getReqParamList()));
        save.setResBodyType("json");
        save.setResBody(buildJsonSchema(docView.getRespBody().getChildList()));
        String markdown = buildDesc(docView);
        save.setMarkdown(markdown);
        save.setTitle(docView.getPath() + docView.getName());
        Node document = Parser.builder().build().parse(markdown);
        save.setDesc(HtmlRenderer.builder().build().render(document));
        if (docView.getContentType().equals(ContentTypeEnum.JSON)) {
            save.setReqBodyIsJsonSchema(true);
            save.setReqBodyOther(buildJsonSchema(docView.getReqBody().getChildList()));
        }
        return save;
    }

    @NotNull
    private String buildDesc(DocView docView) {
        DocViewData docViewData = docView.getPsiClass() == null ? null : new DocViewData(docView);
        String requestJson5 = docViewData == null ? null : docViewData.getRequestJson5();
        String responseJson5 = docViewData == null ? null : docViewData.getResponseJson5();
        String curlMarkdown = docView.getPsiClass() == null ? "" : DocViewData.curlMarkdown(docView);
        return "**接口名称:**\n\n" + docView.getName() + "\n\n"
                + "**接口描述:**\n\n" + docView.getDesc() + "\n\n"
                + "**请求示例:**\n\n```" + docView.getContentType() + "\n"
                + (StringUtils.isBlank(requestJson5) ? docView.getReqBodyExample() : requestJson5) + "\n```\n\n"
                + "**返回示例:**\n\n```json\n"
                + (StringUtils.isBlank(responseJson5) ? docView.getRespExample() : responseJson5) + "\n```\n\n"
                + "**curl example:**\n\n" + curlMarkdown;
    }

    private String buildJsonSchema(List<Body> bodyList) {
        List<String> requiredList = new LinkedList<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        buildProperties(requiredList, properties, bodyList);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", requiredList);
        schema.put("title", " ");
        schema.put("description", " ");
        schema.put("properties", properties);
        return new Gson().toJson(schema);
    }

    private void buildProperties(List<String> requiredList, Map<String, Object> properties, List<Body> bodyList) {
        for (Body body : bodyList) {
            Map<String, Object> innerProperties = new LinkedHashMap<>();
            String schemaType = toYapiSchemaType(body);
            if ("array".equals(schemaType)) {
                innerProperties.put("type", "array");
                innerProperties.put("description", body.getDesc());
                innerProperties.put("items", buildArrayItemsSchema(body));
            } else if ("object".equals(schemaType)) {
                List<String> objectRequiredList = new LinkedList<>();
                Map<String, Object> objectProperties = new LinkedHashMap<>();
                buildProperties(objectRequiredList, objectProperties, body.getChildList());
                innerProperties.put("type", "object");
                innerProperties.put("required", objectRequiredList);
                innerProperties.put("description", body.getDesc());
                innerProperties.put("properties", objectProperties);
            } else {
                innerProperties.put("type", schemaType);
                innerProperties.put("description", body.getDesc());
                innerProperties.put("default", body.getExample());
            }
            if (Boolean.TRUE.equals(body.getRequired())) {
                requiredList.add(body.getName());
            }
            properties.put(body.getName(), innerProperties);
        }
    }

    private static String toYapiSchemaType(@NotNull Body body) {
        if (body.isCollection()) return "array";
        if (body.isMap()) return "object";
        String type = body.getType();
        if (StringUtils.isBlank(type)) return "object";
        if ("byte".equals(type) || "short".equals(type) || "int".equals(type) || "long".equals(type)
                || "float".equals(type) || "double".equals(type) || "Byte".equals(type)
                || "Short".equals(type) || "Integer".equals(type) || "Long".equals(type)
                || "Float".equals(type) || "Double".equals(type) || "BigDecimal".equals(type)) return "number";
        if ("boolean".equals(type) || "Boolean".equals(type)) return "boolean";
        if (type.endsWith("[]")) return "array";
        if (FieldTypeConstant.FIELD_TYPE.containsKey(type) || CommonClassNames.JAVA_LANG_STRING_SHORT.equals(type)) return "string";
        return "object";
    }

    private Map<String, Object> buildArrayItemsSchema(@NotNull Body body) {
        Map<String, Object> items = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(body.getChildList())) {
            List<String> itemRequiredList = new LinkedList<>();
            Map<String, Object> itemProperties = new LinkedHashMap<>();
            buildProperties(itemRequiredList, itemProperties, body.getChildList());
            items.put("type", "object");
            items.put("required", itemRequiredList);
            items.put("description", body.getDesc());
            items.put("properties", itemProperties);
            return items;
        }
        String itemSchemaType = toYapiSchemaType(simpleBodyForType(extractCollectionItemType(body.getType())));
        items.put("type", itemSchemaType);
        items.put("description", body.getDesc());
        if ("object".equals(itemSchemaType)) items.put("properties", new LinkedHashMap<>());
        return items;
    }

    private static String extractCollectionItemType(String type) {
        if (StringUtils.isBlank(type)) return type;
        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) return type;
        String inner = type.substring(lt + 1, gt).trim();
        int comma = inner.indexOf(',');
        return comma > -1 ? inner.substring(0, comma).trim() : inner;
    }

    private static Body simpleBodyForType(String type) {
        Body body = new Body();
        body.setType(type);
        body.setCollection(false);
        body.setMap(false);
        body.setRequired(false);
        body.setName("");
        body.setDesc("");
        return body;
    }

    private List<YApiQuery> buildReqQuery(List<Param> paramList) {
        if (CollectionUtils.isEmpty(paramList)) return new ArrayList<>();
        return paramList.stream().map(param -> {
            YApiQuery query = new YApiQuery();
            query.setName(param.getName());
            query.setType(param.getType());
            query.setExample(param.getExample());
            query.setDesc(param.getDesc());
            query.setRequired(param.getRequired() ? "1" : "0");
            return query;
        }).collect(Collectors.toList());
    }

    private List<YApiHeader> buildReqHeaders(List<Header> headerList) {
        if (CollectionUtils.isEmpty(headerList)) return new ArrayList<>();
        return headerList.stream().map(header -> {
            YApiHeader yapiHeader = new YApiHeader();
            yapiHeader.setName(header.getName());
            yapiHeader.setDesc(header.getDesc());
            yapiHeader.setValue(header.getValue());
            yapiHeader.setExample(header.getValue());
            yapiHeader.setRequired(header.getRequired() ? "1" : "0");
            return yapiHeader;
        }).collect(Collectors.toList());
    }
}
