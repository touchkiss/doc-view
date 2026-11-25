package com.liuzhihang.doc.view.integration.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;

/** HTTP-backed YApi facade with explicit transport and response failure categories. */
public class YApiFacadeServiceImpl implements YApiFacadeService {
    private static final String CATEGORY_ID_PARAM = "catid";
    private static final String INCONSISTENT_PAGINATION = "读取接口列表分页信息不一致";
    private static final String METHOD_KEY = "method";
    private static final String MISSING_ERROR_CODE = "响应缺少 errcode";
    private static final String ERROR_CODE_KEY = "errcode";


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
        Long savedId = savedInterfaceId(response.get("data"), save);
        if (savedId != null) {
            save.setId(String.valueOf(savedId));
        }
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
        for (long page = 1; ; page++) {
            ProgressManager.checkCanceled();
            String url = new URIBuilder(yapiUrl + "/api/interface/list_cat")
                    .addParameter(CATEGORY_ID_PARAM, String.valueOf(catId)).addParameter("token", token)
                    .addParameter("page", String.valueOf(page)).addParameter("limit", String.valueOf(INTERFACE_PAGE_SIZE))
                    .build().toString();
            InterfacePage result = parseInterfaceList(get(url));
            List<YApiInterfaceSummary> interfaces = result.items();
            validatePage(result, page, seenIds);
            Optional<Long> match = interfaces.stream()
                    // list_cat scopes rows to catId; some servers omit the redundant row field.
                    .filter(item -> item.getCatId() == null || catId.equals(item.getCatId()))
                    .filter(item -> method.equalsIgnoreCase(item.getMethod()))
                    .filter(item -> path.equals(item.getPath()))
                    .map(YApiInterfaceSummary::getId)
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
            boolean hasMore = hasMorePages(result, page, seenIds.size(), interfaces.size());
            if (!hasMore) {
                if (result.count() != null && seenIds.size() != result.count()) {
                    throw YApiRemoteException.responseInvalid(INCONSISTENT_PAGINATION);
                }
                return Optional.empty();
            }
            if (interfaces.isEmpty()) {
                throw YApiRemoteException.responseInvalid(INCONSISTENT_PAGINATION);
            }
        }
    }

    private static void validatePage(InterfacePage result, long page, Set<Long> seenIds)
            throws YApiRemoteException {
        for (YApiInterfaceSummary item : result.items()) {
            ProgressManager.checkCanceled();
            if (!seenIds.add(item.getId())) {
                throw YApiRemoteException.responseInvalid("读取接口列表返回重复分页");
            }
        }
        if ((result.totalPages() != null && page > result.totalPages() && !result.items().isEmpty())
                || (result.count() != null && seenIds.size() > result.count())) {
            throw YApiRemoteException.responseInvalid(INCONSISTENT_PAGINATION);
        }
    }

    private static boolean hasMorePages(InterfacePage result, long page, int seenCount, int pageSize) {
        if (result.totalPages() != null) {
            return page < result.totalPages();
        }
        if (result.count() != null) {
            return seenCount < result.count();
        }
        return pageSize >= INTERFACE_PAGE_SIZE;
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

    private InterfacePage parseInterfaceList(String body) throws Exception {
        JsonObject response = parseObject(body, "读取接口列表");
        requireSuccess(response, "读取接口列表");
        try {
            JsonElement data = response.get("data");
            if (data == null || data.isJsonNull()) {
                throw YApiRemoteException.responseInvalid("读取接口列表响应缺少 data");
            }
            JsonElement list = data.isJsonArray() ? data : data.getAsJsonObject().get("list");
            if (list == null || !list.isJsonArray()) {
                throw YApiRemoteException.responseInvalid("读取接口列表响应缺少 list");
            }
            Long totalPages = data.isJsonObject() ? paginationNumber(data.getAsJsonObject(), "total") : null;
            Long count = data.isJsonObject() ? paginationNumber(data.getAsJsonObject(), "count") : null;
            List<YApiInterfaceSummary> items = new ArrayList<>();
            for (JsonElement entry : list.getAsJsonArray()) {
                ProgressManager.checkCanceled();
                items.add(parseInterfaceItem(entry));
            }
            return new InterfacePage(items, totalPages, count);
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (YApiRemoteException invalid) {
            throw invalid;
        } catch (RuntimeException _) {
            throw YApiRemoteException.responseInvalid("读取接口列表响应不是有效 JSON");
        }
    }

    private static YApiInterfaceSummary parseInterfaceItem(JsonElement entry) throws YApiRemoteException {
        if (!entry.isJsonObject()) {
            throw YApiRemoteException.responseInvalid("读取接口列表响应包含无效接口");
        }
        JsonObject row = entry.getAsJsonObject();
        Long id = positiveId(row.get("_id"));
        JsonElement catIdValue = row.get(CATEGORY_ID_PARAM);
        Long catId = positiveId(catIdValue);
        String method = stringValue(row.get(METHOD_KEY));
        String path = stringValue(row.get("path"));
        if (id == null || (catIdValue != null && !catIdValue.isJsonNull() && catId == null)
                || StringUtils.isBlank(method) || StringUtils.isBlank(path)) {
            throw YApiRemoteException.responseInvalid("读取接口列表响应包含无效接口");
        }
        YApiInterfaceSummary item = new YApiInterfaceSummary();
        item.setId(id);
        item.setCatId(catId);
        item.setMethod(method);
        item.setPath(path);
        return item;
    }

    private static Long paginationNumber(JsonObject data, String name) throws YApiRemoteException {
        JsonElement value = data.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        Long parsed = integerValue(value);
        if (parsed == null || parsed < 0) {
            throw YApiRemoteException.responseInvalid("读取接口列表分页信息无效");
        }
        return parsed;
    }

    private static Long savedInterfaceId(JsonElement data, YapiSave save) {
        if (data == null || data.isJsonNull()) {
            return null;
        }
        // Official save can return a one-row _id array for updates and [] for creates.
        // A scalar write count is not an interface ID; ambiguous arrays require lookup.
        if (data.isJsonArray()) {
            if (data.getAsJsonArray().size() != 1) {
                return null;
            }
            data = data.getAsJsonArray().get(0);
        }
        if (!data.isJsonObject()) {
            return null;
        }
        JsonObject row = data.getAsJsonObject();
        Long underscoredId = positiveId(row.get("_id"));
        Long plainId = positiveId(row.get("id"));
        if (underscoredId != null && plainId != null && !underscoredId.equals(plainId)) {
            return null;
        }
        Long id = underscoredId == null ? plainId : underscoredId;
        if (id == null) {
            return null;
        }
        if (row.has(CATEGORY_ID_PARAM) && !row.get(CATEGORY_ID_PARAM).isJsonNull()
                && !java.util.Objects.equals(save.getCatId(), positiveId(row.get(CATEGORY_ID_PARAM)))) {
            return null;
        }
        if (row.has(METHOD_KEY) && !row.get(METHOD_KEY).isJsonNull()
                && (save.getMethod() == null || !save.getMethod().equalsIgnoreCase(stringValue(row.get(METHOD_KEY))))) {
            return null;
        }
        if (row.has("path") && !row.get("path").isJsonNull()
                && !java.util.Objects.equals(save.getPath(), stringValue(row.get("path")))) {
            return null;
        }
        return id;
    }

    private static String stringValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static Long positiveId(JsonElement value) {
        Long parsed = integerValue(value);
        return parsed != null && parsed > 0 ? parsed : null;
    }

    private static Long integerValue(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException _) {
            return null;
        }
    }

    private record InterfacePage(List<YApiInterfaceSummary> items, Long totalPages, Long count) { }

    private String get(String url) throws Exception {
        try {
            return getRequester.get(url);
        } catch (YApiRemoteException | ProcessCanceledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw YApiRemoteException.requestFailed(exception);
        }
    }

    private String post(String url, String body) throws Exception {
        try {
            return postRequester.post(url, body);
        } catch (YApiRemoteException | ProcessCanceledException exception) {
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
        } catch (RuntimeException _) {
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
                throw YApiRemoteException.responseInvalid(operation + MISSING_ERROR_CODE);
            }
            return response;
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (RuntimeException _) {
            throw YApiRemoteException.responseInvalid(operation + "响应不是有效 JSON");
        }
    }

    private static void requireSuccess(JsonObject response, String operation) throws YApiRemoteException {
        if (!response.has(ERROR_CODE_KEY) || response.get(ERROR_CODE_KEY).isJsonNull()) {
            throw YApiRemoteException.responseInvalid(operation + MISSING_ERROR_CODE);
        }
        try {
            if (response.get(ERROR_CODE_KEY).getAsLong() != 0) {
                throw YApiRemoteException.responseInvalid(operation + "响应失败，errcode="
                        + response.get(ERROR_CODE_KEY).getAsLong());
            }
        } catch (YApiRemoteException exception) {
            throw exception;
        } catch (RuntimeException _) {
            throw YApiRemoteException.responseInvalid(operation + "响应 errcode 无效");
        }
    }

    private static void requireSuccess(YApiResponse<?> response, String operation) throws YApiRemoteException {
        if (response == null || response.getErrcode() == null) {
            throw YApiRemoteException.responseInvalid(operation + MISSING_ERROR_CODE);
        }
        if (response.getErrcode() != 0) {
            throw YApiRemoteException.responseInvalid(operation + "响应失败，errcode=" + response.getErrcode());
        }
    }
}
