package com.liuzhihang.doc.view.utils;

import com.google.gson.*;
import com.liuzhihang.doc.view.dto.*;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import org.apache.commons.lang3.StringUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Builds a Postman Collection v2.1 containing one HTTP request. */
public final class PostmanCollectionBuilder {
    private static final String CONTENT_TYPE = "Content-Type";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private PostmanCollectionBuilder() {}

    public static String build(DocView doc) {
        if (doc == null || StringUtils.isBlank(doc.getMethod()) || StringUtils.isBlank(doc.getPath())) {
            return "";
        }
        String method = doc.getMethod().toUpperCase(Locale.ROOT);
        boolean bodyMethod = Set.of("POST", "PUT", "PATCH").contains(method);
        boolean json = doc.getContentType() == ContentTypeEnum.JSON;
        String path = doc.getPath();
        List<Param> remaining = new ArrayList<>();
        if (doc.getReqParamList() != null) {
            for (Param p : doc.getReqParamList()) {
                if (p == null || StringUtils.isBlank(p.getName())) {
                    continue;
                }
                String marker = "{" + p.getName() + "}";
                if (path.contains(marker)) {
                    path = path.replace(marker, encode(StringUtils.defaultString(p.getExample())));
                } else {
                    remaining.add(p);
                }
            }
        }
        JsonObject root = new JsonObject();
        JsonObject info = new JsonObject();
        JsonObject item = new JsonObject();
        JsonObject request = new JsonObject();
        JsonObject url = new JsonObject();
        String name = StringUtils.defaultIfBlank(doc.getName(), method + " " + path);
        info.addProperty("name", name);
        info.addProperty("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        root.add("info", info); item.addProperty("name", name); request.addProperty("method", method);
        if (StringUtils.isNotBlank(doc.getDesc())) {
            request.addProperty("description", doc.getDesc());
        }
        JsonArray headers = new JsonArray();
        boolean hasType = false;
        if (doc.getHeaderList() != null) {
            for (Header h : doc.getHeaderList()) {
                if (h == null || StringUtils.isBlank(h.getName())) {
                    continue;
                }
                JsonObject header = pair(h.getName(), h.getValue(), h.getDesc());
                headers.add(header);
                if (h.getName().equalsIgnoreCase(CONTENT_TYPE)) {
                    hasType = true;
                }
            }
        }
        addBody(doc, remaining, bodyMethod, json, hasType, headers, request);
        request.add("header", headers);
        String rawUrl = buildUrl(path, remaining, bodyMethod, json, url);
        url.addProperty("raw", rawUrl); request.add("url", url); item.add("request", request);
        JsonArray items = new JsonArray(); items.add(item); root.add("item", items);
        return GSON.toJson(root);
    }
    private static void addBody(DocView doc, List<Param> remaining, boolean bodyMethod, boolean json,
                                boolean hasType, JsonArray headers, JsonObject request) {
        if (!bodyMethod) {
            return;
        }
        if (json && StringUtils.isNotBlank(doc.getReqBodyExample())) {
            JsonObject body = new JsonObject();
            JsonObject options = new JsonObject();
            JsonObject raw = new JsonObject();
            body.addProperty("mode", "raw");
            body.addProperty("raw", RestRequestBuilder.commentedJson(doc.getReqBodyExample(), doc.getReqBody()));
            raw.addProperty("language", "json"); options.add("raw", raw); body.add("options", options); request.add("body", body);
            if (!hasType) {
                headers.add(pair(CONTENT_TYPE, "application/json", null));
            }
        } else if (!json && (!remaining.isEmpty() || StringUtils.isNotBlank(doc.getReqFormExample()))) {
            addFormBody(doc, remaining, hasType, headers, request);
        }
    }

    private static void addFormBody(DocView doc, List<Param> remaining, boolean hasType,
                                    JsonArray headers, JsonObject request) {
        JsonObject body = new JsonObject();
        JsonArray fields = new JsonArray();
        for (Param p : remaining) {
            fields.add(pair(p.getName(), p.getExample(), p.getDesc()));
        }
        if (remaining.isEmpty()) {
            for (String part : doc.getReqFormExample().split("&")) {
                String[] keyValue = part.split("=", 2);
                if (!keyValue[0].isBlank()) {
                    fields.add(pair(keyValue[0], keyValue.length > 1 ? keyValue[1] : "", null));
                }
            }
        }
        body.addProperty("mode", "urlencoded"); body.add("urlencoded", fields); request.add("body", body);
        if (!hasType) {
            headers.add(pair(CONTENT_TYPE, "application/x-www-form-urlencoded", null));
        }
    }

    private static String buildUrl(String path, List<Param> remaining, boolean bodyMethod, boolean json, JsonObject url) {
        StringBuilder rawUrl = new StringBuilder("{{host}}").append(path);
        if ((!bodyMethod || json) && !remaining.isEmpty()) {
            JsonArray query = new JsonArray();
            boolean first = !path.contains("?");
            for (Param p : remaining) {
                query.add(pair(p.getName(), p.getExample(), p.getDesc()));
                rawUrl.append(first ? '?' : '&').append(encode(p.getName())).append('=').append(encode(StringUtils.defaultString(p.getExample()))); first = false;
            }
            url.add("query", query);
        }
        return rawUrl.toString();
    }

    private static JsonObject pair(String key, String value, String desc) {
        JsonObject object = new JsonObject(); object.addProperty("key", key); object.addProperty("value", StringUtils.defaultString(value));
        if (StringUtils.isNotBlank(desc)) {
            object.addProperty("description", desc);
        }
        return object;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
