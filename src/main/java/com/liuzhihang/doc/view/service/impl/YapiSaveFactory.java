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
            // Synthetic containers are parser structure, never user-defined JSON property names.
            // Real fields named element/key/value have no synthetic marker and remain untouched.
            if (body.isSyntheticContainerNode()) {
                buildProperties(requiredList, properties, body.getChildList());
                continue;
            }
            if (Boolean.TRUE.equals(body.getRequired())) {
                requiredList.add(body.getName());
            }
            properties.put(body.getName(), buildValueSchema(body));
        }
    }

    private Map<String, Object> buildValueSchema(@NotNull Body body) {
        Map<String, Object> schema = new LinkedHashMap<>();
        String schemaType = toYapiSchemaType(body);
        schema.put("type", schemaType);
        schema.put("description", body.getDesc());
        if ("array".equals(schemaType)) {
            schema.put("items", buildArrayItemsSchema(body));
        } else if ("object".equals(schemaType) && isMapBody(body)) {
            // A Java Map has dynamic keys; its synthetic key/value nodes describe the generic
            // types, rather than literal JSON fields. Preserve real key/value fields in each value.
            Body value = schemaValueBody(body, syntheticChild(body, "value"), mapValueType(body.getType()));
            schema.put("additionalProperties", buildValueSchema(value));
        } else if ("object".equals(schemaType)) {
            List<String> required = new LinkedList<>();
            Map<String, Object> properties = new LinkedHashMap<>();
            buildProperties(required, properties, body.getChildList());
            schema.put("required", required);
            schema.put("properties", properties);
        } else {
            schema.put("default", body.getExample());
        }
        return schema;
    }

    private static String toYapiSchemaType(@NotNull Body body) {
        if (body.isCollection() || isCollectionType(body.getType())) return "array";
        if (body.isMap() || isMapBody(body)) return "object";
        String type = rawType(body.getType());
        if (StringUtils.isBlank(type)) return "object";
        if ("byte".equals(type) || "short".equals(type) || "int".equals(type) || "long".equals(type)
                || "float".equals(type) || "double".equals(type) || "Byte".equals(type)
                || "Short".equals(type) || "Integer".equals(type) || "Long".equals(type)
                || "Float".equals(type) || "Double".equals(type) || "BigDecimal".equals(type)
                || "BigInteger".equals(type) || "Number".equals(type)) return "number";
        if ("boolean".equals(type) || "Boolean".equals(type)) return "boolean";
        if (FieldTypeConstant.FIELD_TYPE.containsKey(type) || CommonClassNames.JAVA_LANG_STRING_SHORT.equals(type)) return "string";
        return "object";
    }

    private Map<String, Object> buildArrayItemsSchema(@NotNull Body body) {
        Body element = syntheticChild(body, "element");
        Body item = schemaValueBody(body, element, extractCollectionItemType(body.getType()));
        // Older/flat parser paths already hold item fields directly. Only unwrap marked nodes.
        if (element == null) item.setChildList(body.getChildList());
        return buildValueSchema(item);
    }

    private static Body schemaValueBody(Body container, Body synthetic, String declaredType) {
        String type = StringUtils.isNotBlank(declaredType) ? declaredType
                : synthetic == null ? "Object" : synthetic.getType();
        Body value = new Body();
        value.setType(type);
        value.setDesc(container.getDesc());
        value.setExample(container.getExample());
        if (synthetic != null) {
            value.setChildList(synthetic.getChildList());
            if (synthetic.getExample() != null) value.setExample(synthetic.getExample());
        }
        // Do not copy collection/map flags from a synthetic node: they describe its container,
        // not the value's JSON shape. Infer nested containers from the actual generic type.
        value.setCollection(isCollectionType(type));
        value.setMap(isMapType(type));
        return value;
    }

    private static Body syntheticChild(Body body, String name) {
        for (Body child : body.getChildList()) {
            if (child.isSyntheticContainerNode() && name.equals(child.getName())) return child;
        }
        return null;
    }

    private static boolean isMapBody(Body body) {
        return isMapType(body.getType()) || body.isMap()
                || syntheticChild(body, "key") != null || syntheticChild(body, "value") != null;
    }

    private static boolean isCollectionType(String type) {
        if (type != null && type.trim().endsWith("[]")) return true;
        return switch (rawType(type)) {
            case "Iterable", "Collection", "List", "Set", "SortedSet", "NavigableSet", "ArrayList", "LinkedList",
                    "HashSet", "LinkedHashSet", "TreeSet", "Vector", "Queue", "Deque", "ArrayDeque" -> true;
            default -> false;
        };
    }

    private static boolean isMapType(String type) {
        return switch (rawType(type)) {
            case "Map", "SortedMap", "NavigableMap", "HashMap", "LinkedHashMap", "TreeMap",
                    "ConcurrentMap", "ConcurrentHashMap", "Hashtable", "EnumMap" -> true;
            default -> false;
        };
    }

    private static String rawType(String type) {
        if (type == null) return "";
        String raw = type.trim();
        int genericStart = raw.indexOf('<');
        if (genericStart >= 0) raw = raw.substring(0, genericStart);
        int packageEnd = raw.lastIndexOf('.');
        return packageEnd < 0 ? raw : raw.substring(packageEnd + 1);
    }

    private static String extractCollectionItemType(String type) {
        if (type != null && type.trim().endsWith("[]")) {
            return type.trim().substring(0, type.trim().length() - 2);
        }
        List<String> arguments = genericArguments(type);
        return arguments.isEmpty() ? null : arguments.get(0);
    }

    private static String mapValueType(String type) {
        List<String> arguments = genericArguments(type);
        return arguments.size() < 2 ? null : arguments.get(1);
    }

    /** Splits top-level Java generic arguments without breaking Map<String, List<Value>>. */
    private static List<String> genericArguments(String type) {
        if (type == null) return List.of();
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start < 0 || end <= start) return List.of();
        List<String> result = new ArrayList<>();
        int depth = 0;
        int itemStart = start + 1;
        for (int i = itemStart; i < end; i++) {
            char current = type.charAt(i);
            if (current == '<') depth++;
            else if (current == '>') depth--;
            else if (current == ',' && depth == 0) {
                result.add(type.substring(itemStart, i).trim());
                itemStart = i + 1;
            }
        }
        result.add(type.substring(itemStart, end).trim());
        return result;
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
