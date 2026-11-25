package com.liuzhihang.doc.view.utils;

import com.google.gson.*;
import com.liuzhihang.doc.view.dto.*;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import org.apache.commons.lang3.StringUtils;
import java.util.*;

/** Renders an HTTP Client request from the same endpoint data as Copy cURL. */
public final class RestRequestBuilder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private RestRequestBuilder() {}

    public static String build(DocView doc, boolean json5) {
        if (doc == null || StringUtils.isBlank(doc.getMethod()) || StringUtils.isBlank(doc.getPath())) {
            return "";
        }
        String method = doc.getMethod().toUpperCase(Locale.ROOT);
        boolean bodyMethod = Set.of("POST", "PUT", "PATCH").contains(method);
        boolean json = doc.getContentType() == ContentTypeEnum.JSON;
        String path = doc.getPath();
        List<String> params = new ArrayList<>();
        if (doc.getReqParamList() != null) {
            for (Param p : doc.getReqParamList()) {
            if (p == null || StringUtils.isBlank(p.getName())) {
                continue;
            }
            String value = StringUtils.defaultString(p.getExample());
            String placeholder = "{" + p.getName() + "}";
            if (path.contains(placeholder)) {
                path = path.replace(placeholder, value);
            } else {
                params.add(p.getName() + "=" + value);
            }
        }
        }
        StringBuilder out = new StringBuilder("### ").append(StringUtils.defaultString(doc.getName()).replace('\n', ' ').replace('\r', ' ')).append('\n')
                .append(method).append(" {{host}}").append(path);
        if ((!bodyMethod || json) && !params.isEmpty()) {
            out.append(path.contains("?") ? "&" : "?").append(String.join("&", params));
        }
        out.append('\n');
        boolean contentType = false;
        if (doc.getHeaderList() != null) {
            for (Header h : doc.getHeaderList()) {
            if (h == null || StringUtils.isBlank(h.getName())) {
                continue;
            }
            out.append(h.getName()).append(": ").append(StringUtils.defaultString(h.getValue())).append('\n');
            if (h.getName().equalsIgnoreCase("Content-Type")) {
                contentType = true;
            }
        }
        }
        String body = "";
        if (bodyMethod && json && StringUtils.isNotBlank(doc.getReqBodyExample())) {
            body = doc.getReqBodyExample().trim();
            if (json5) {
                try { body = commentedJson(body, doc.getReqBody()); }
                catch (JsonParseException ignored) { /* preserve the generated example */ }
            }
            if (!contentType) {
                out.append("Content-Type: application/json\n");
            }
        } else if (bodyMethod && !json) {
            body = params.isEmpty() ? StringUtils.defaultString(doc.getReqFormExample()) : String.join("&", params);
            if (!body.isBlank() && !contentType) {
                out.append("Content-Type: application/x-www-form-urlencoded\n");
            }
        }
        if (!body.isBlank()) {
            out.append('\n').append(body).append('\n');
        }
        return out.toString();
    }

    public static String commentedJson(String json, Body schema) {
        return render(JsonParser.parseString(json), schema, 0);
    }

    private static String render(JsonElement element, Body schema, int level) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                return "[]";
            }
            List<String> items = new ArrayList<>();
            Body child = schema;
            if (schema != null && schema.getChildList().size() == 1 && schema.getChildList().getFirst().isSyntheticContainerNode()) {
                child = schema.getChildList().getFirst();
            }
            for (JsonElement item : array) {
                items.add(indent(level + 1) + render(item, child, level + 1));
            }
            return "[\n" + String.join(",\n", items) + "\n" + indent(level) + "]";
        }
        if (!element.isJsonObject()) {
            return GSON.toJson(element);
        }
        JsonObject object = element.getAsJsonObject();
        if (object.isEmpty()) {
            return "{}";
        }
        List<String> entries = new ArrayList<>();
        for (var entry : object.entrySet()) {
            Body field = null;
            if (schema != null && schema.getChildList() != null) {
                for (Body candidate : schema.getChildList()) {
                if (candidate != null && entry.getKey().equals(candidate.getName())) { field = candidate; break; }
            }
            }
            StringBuilder line = new StringBuilder();
            if (field != null && StringUtils.isNotBlank(field.getDesc())) {
                for (String comment : field.getDesc().split("\\R")) {
                    line.append(indent(level + 1)).append("// ").append(comment).append('\n');
                }
            }
            line.append(indent(level + 1)).append(GSON.toJson(entry.getKey())).append(": ").append(render(entry.getValue(), field, level + 1));
            entries.add(line.toString());
        }
        return "{\n" + String.join(",\n", entries) + "\n" + indent(level) + "}";
    }
    private static String indent(int level) { return "  ".repeat(level); }
}
