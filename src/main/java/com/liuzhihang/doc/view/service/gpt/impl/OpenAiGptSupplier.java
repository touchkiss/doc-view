package com.liuzhihang.doc.view.service.gpt.impl;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.liuzhihang.doc.view.common.util.HttpUtil;
import com.liuzhihang.doc.view.service.gpt.ModelRequestException;
import org.apache.commons.lang3.StringUtils;

/**
 * 通用大模型（OpenAI格式）
 * 支持所有兼容OpenAI API格式的大模型接口，如OpenAI、DeepSeek、通义千问、Moonshot等
 *
 * @author wangchao
 * @date 2024/04/12
 */
public class OpenAiGptSupplier extends AbstractGptSupplier {

    /** 超时 */
    private static final int TIMEOUT = 60 * 1000;

    @Override
    public String chat(String content) {
        URI endpoint = resolveEndpoint(getConfig().getOpenAiApiUrl());
        String apiKey = StringUtils.stripToNull(getConfig().getOpenAiApiKey());
        if (apiKey == null && !HttpUtil.isLoopback(endpoint.getHost())) {
            throw new ModelRequestException("请在源码注释设置中填写 API 密钥。");
        }
        String model = StringUtils.defaultIfBlank(StringUtils.stripToNull(getConfig().getOpenAiModel()), "gpt-4o");

        Map<String, String> headers = Maps.newHashMap();
        if (apiKey != null) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        headers.put("Content-Type", "application/json;charset=utf-8");

        Message message = new Message();
        message.setContent(content);
        message.setRole("user");

        OpenAiRequest request = new OpenAiRequest();
        request.setModel(model);
        request.setMessages(Lists.newArrayList(message));

        HttpUtil.PostResponse response;
        try {
            response = HttpUtil.postResponse(endpoint.toASCIIString(), headers, JSON.toJSONString(request), TIMEOUT);
        } catch (ConnectException e) {
            throw new ModelRequestException("无法连接模型服务，请确认服务已启动，并检查地址和端口。");
        } catch (SocketTimeoutException e) {
            throw new ModelRequestException("模型服务响应超时，请检查模型负载和网络连接。");
        } catch (UnknownHostException e) {
            throw new ModelRequestException("无法解析模型服务的主机地址。");
        } catch (IOException e) {
            throw new ModelRequestException("连接模型服务失败（" + e.getClass().getSimpleName() + "）。");
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new ModelRequestException(httpError(response.status()));
        }

        Object parsed;
        try {
            parsed = JSON.parse(response.body());
        } catch (RuntimeException e) {
            throw new ModelRequestException("模型接口返回了无法解析的 JSON，请检查 API 地址。");
        }
        if (!(parsed instanceof JSONObject payload)) {
            throw new ModelRequestException("模型接口未返回 Chat Completions JSON 对象。");
        }
        if (payload.get("error") != null) {
            throw new ModelRequestException("模型服务返回错误，请检查模型名称和服务端日志。");
        }
        if (!(payload.get("choices") instanceof JSONArray choices) || choices.isEmpty()
                || !(choices.getFirst() instanceof JSONObject choice)
                || !(choice.get("message") instanceof JSONObject completion)
                || !(completion.get("content") instanceof String result) || result.isBlank()) {
            throw new ModelRequestException("模型返回了空文本或缺少 choices[0].message.content。");
        }
        return result;
    }

    public static URI resolveEndpoint(String apiUrl) {
        try {
            URI base = URI.create(apiUrl == null ? "" : apiUrl.strip());
            if (base.getHost() == null || base.getUserInfo() != null || base.getFragment() != null
                    || !("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))) {
                throw new IllegalArgumentException();
            }
            String path = base.getRawPath().replaceFirst("/+$", "");
            if (path.isEmpty()) {
                path = "/v1";
            }
            if (!path.endsWith("/chat/completions")) {
                path += "/chat/completions";
            }
            String query = base.getRawQuery() == null ? "" : "?" + base.getRawQuery();
            return URI.create(base.getScheme() + "://" + base.getRawAuthority() + path + query);
        } catch (IllegalArgumentException e) {
            throw new ModelRequestException("API 地址无效，请填写 HTTP(S) 基础地址或完整的 Chat Completions 地址。");
        }
    }

    private static String httpError(int status) {
        String detail = switch (status) {
            case 400, 422 -> "请确认模型名称和接口参数是否受服务支持。";
            case 401, 403 -> "请检查 API 密钥和访问权限。";
            case 404 -> "请检查 API 地址和模型名称。";
            case 429 -> "请求受限，请检查服务配额或稍后重试。";
            default -> "请查看模型服务日志。";
        };
        return "模型接口返回 HTTP " + status + "。" + detail;
    }

    private static class OpenAiRequest {
        private String model;
        private List<Message> messages;
        private Boolean stream = false;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }

        public Boolean getStream() {
            return stream;
        }

        public void setStream(Boolean stream) {
            this.stream = stream;
        }
    }

    private static class Message {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
