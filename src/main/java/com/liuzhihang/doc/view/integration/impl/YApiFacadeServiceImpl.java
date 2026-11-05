package com.liuzhihang.doc.view.integration.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.YApiRemoteException;
import com.liuzhihang.doc.view.integration.dto.*;
import com.liuzhihang.doc.view.utils.HttpUtils;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import org.apache.http.client.utils.URIBuilder;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/** HTTP-backed YApi facade with explicit transport and response failure categories. */
public class YApiFacadeServiceImpl implements YApiFacadeService {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int INTERFACE_PAGE_SIZE = 100;
    private final GetRequester getRequester;
    private final PostRequester postRequester;

    @FunctionalInterface
    interface GetRequester {
        String get(String url) throws Exception;
    }

    @FunctionalInterface
    interface PostRequester {
        String post(String url, String body) throws Exception;
    }

    public YApiFacadeServiceImpl() {
        this(HttpUtils::get, HttpUtils::post);
    }

    YApiFacadeServiceImpl(GetRequester getRequester) {
        this(getRequester, HttpUtils::post);
    }

    YApiFacadeServiceImpl(GetRequester getRequester, PostRequester postRequester) {
        this.getRequester = getRequester;
        this.postRequester = postRequester;
    }

    @Override
    public void save(YapiSave save) throws Exception {
        if (StringUtils.isBlank(save.getId())) {
            save.setId(null);
        }
        JsonObject response = parseObject(post(save.getYapiUrl() + "/api/interface/save", GSON.toJson(save)), "保存接口");
        requireSuccess(response, "保存接口");
    }

    @Override
    public List<YApiCat> getCatMenu(String yapiUrl, Long projectId, String token) throws Exception {
        String url = new URIBuilder(yapiUrl + "/api/interface/getCatMenu")
                .addParameter("project_id", String.valueOf(projectId)).addParameter("token", token).build().toString();
        Type type = new TypeToken<YApiResponse<List<YApiCat>>>() { }.getType();
        YApiResponse<List<YApiCat>> response = parseResponse(get(url), type, "读取分类");
        requireSuccess(response, "读取分类");
        if (response.getData() == null || response.getData().stream().anyMatch(cat -> cat == null || cat.getId() == null || cat.getId() <= 0)) {
            throw YApiRemoteException.responseInvalid("读取分类响应缺少有效 data/id");
        }
        return response.getData();
    }

    @Override
    public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                          Long catId, String method, String path) throws Exception {
        Set<Long> seenIds = new HashSet<>();
        for (int page = 1; ; page++) {
            ProgressManager.checkCanceled();
            String url = new URIBuilder(yapiUrl + "/api/interface/list")
                    .addParameter("project_id", String.valueOf(projectId)).addParameter("token", token)
                    .addParameter("page", String.valueOf(page)).addParameter("limit", String.valueOf(INTERFACE_PAGE_SIZE))
                    .build().toString();
            List<YApiInterfaceSummary> interfaces = parseInterfaceList(get(url));
            if (!interfaces.isEmpty()) {
                Set<Long> pageIds = new HashSet<>();
                for (YApiInterfaceSummary item : interfaces) pageIds.add(item.getId());
                if (pageIds.size() != interfaces.size() || !seenIds.addAll(pageIds)) {
                    throw YApiRemoteException.responseInvalid("读取接口列表返回重复分页");
                }
            }
            Optional<Long> match = interfaces.stream()
                    .filter(item -> catId.equals(item.getCatId()))
                    .filter(item -> item.getMethod() != null && method.equalsIgnoreCase(item.getMethod()))
                    .filter(item -> path.equals(item.getPath()))
                    .map(YApiInterfaceSummary::getId)
                    .filter(Objects::nonNull)
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
            if (interfaces.size() < INTERFACE_PAGE_SIZE) {
                return Optional.empty();
            }
        }
    }

    @Override
    public YApiCat addCat(YApiCat cat) throws Exception {
        Type type = new TypeToken<YApiResponse<YApiCat>>() { }.getType();
        YApiResponse<YApiCat> response = parseResponse(
                post(cat.getYapiUrl() + "/api/interface/add_cat", GSON.toJson(cat)), type, "创建分类");
        requireSuccess(response, "创建分类");
        if (response.getData() == null || response.getData().getId() == null
                || response.getData().getId() <= 0) {
            throw YApiRemoteException.responseInvalid("创建分类响应缺少有效 id");
        }
        return response.getData();
    }

    private List<YApiInterfaceSummary> parseInterfaceList(String body) throws Exception {
        JsonObject response = parseObject(body, "读取接口列表");
        requireSuccess(response, "读取接口列表");
        try {
            var data = response.get("data");
            if (data == null || data.isJsonNull()) throw YApiRemoteException.responseInvalid("读取接口列表响应缺少 data");
            // YApi deployments return either a direct array or a paginated object.
            var list = data.isJsonArray() ? data : data.getAsJsonObject().get("list");
            if (list == null || !list.isJsonArray()) throw YApiRemoteException.responseInvalid("读取接口列表响应缺少 list");
            List<YApiInterfaceSummary> items = GSON.fromJson(list,
                    new TypeToken<List<YApiInterfaceSummary>>() { }.getType());
            if (items.stream().anyMatch(item -> item == null || item.getId() == null || item.getId() <= 0
                    || item.getCatId() == null || item.getCatId() <= 0
                    || StringUtils.isBlank(item.getMethod()) || StringUtils.isBlank(item.getPath()))) {
                throw YApiRemoteException.responseInvalid("读取接口列表响应包含无效接口");
            }
            return items;
        } catch (YApiRemoteException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw YApiRemoteException.responseInvalid("读取接口列表响应不是有效 JSON");
        }
    }

    private String get(String url) throws Exception {
        try {
            return getRequester.get(url);
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw YApiRemoteException.requestFailed(exception);
        }
    }

    private String post(String url, String body) throws Exception {
        try {
            return postRequester.post(url, body);
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw YApiRemoteException.requestFailed(exception);
        }
    }

    private static JsonObject parseObject(String body, String operation) throws YApiRemoteException {
        if (StringUtils.isBlank(body)) {
            throw YApiRemoteException.responseInvalid(operation + "响应为空");
        }
        try {
            JsonObject response = JsonParser.parseString(body).getAsJsonObject();
            if (response == null) {
                throw YApiRemoteException.responseInvalid(operation + "响应无效");
            }
            return response;
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw YApiRemoteException.responseInvalid(operation + "响应不是有效 JSON");
        }
    }

    private static <T> YApiResponse<T> parseResponse(String body, Type type, String operation)
            throws YApiRemoteException {
        if (StringUtils.isBlank(body)) {
            throw YApiRemoteException.responseInvalid(operation + "响应为空");
        }
        try {
            YApiResponse<T> response = GSON.fromJson(body, type);
            if (response == null || response.getErrcode() == null) {
                throw YApiRemoteException.responseInvalid(operation + "响应缺少 errcode");
            }
            return response;
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw YApiRemoteException.responseInvalid(operation + "响应不是有效 JSON");
        }
    }

    private static void requireSuccess(JsonObject response, String operation) throws YApiRemoteException {
        if (!response.has("errcode") || response.get("errcode").isJsonNull()) {
            throw YApiRemoteException.responseInvalid(operation + "响应缺少 errcode");
        }
        try {
            if (response.get("errcode").getAsLong() != 0) {
                throw YApiRemoteException.responseInvalid(operation + "响应失败，errcode="
                        + response.get("errcode").getAsLong());
            }
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw YApiRemoteException.responseInvalid(operation + "响应 errcode 无效");
        }
    }

    private static void requireSuccess(YApiResponse<?> response, String operation) throws YApiRemoteException {
        if (response == null || response.getErrcode() == null) {
            throw YApiRemoteException.responseInvalid(operation + "响应缺少 errcode");
        }
        if (response.getErrcode() != 0) {
            throw YApiRemoteException.responseInvalid(operation + "响应失败，errcode=" + response.getErrcode());
        }
    }
}
