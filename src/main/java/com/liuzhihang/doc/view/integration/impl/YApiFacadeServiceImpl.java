package com.liuzhihang.doc.view.integration.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YApiInterfaceSummary;
import com.liuzhihang.doc.view.integration.dto.YApiResponse;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.liuzhihang.doc.view.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author liuzhihang
 * @date 2021/6/8 19:20
 */
@Slf4j
public class YApiFacadeServiceImpl implements YApiFacadeService {

    private static final Gson gson = new GsonBuilder().serializeNulls().create();
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
        String resp = postRequester.post(save.getYapiUrl() + "/api/interface/save", gson.toJson(save));

        if (StringUtils.isBlank(resp)) {
            throw new Exception("YApi 接口返回为空");
        }

        JsonObject jsonObject = gson.fromJson(resp, JsonObject.class);

        if (jsonObject.get("errcode").getAsInt() != 0) {
            throw new Exception("YApi 接口返回失败:" + resp);
        }
    }

    @Override
    public List<YApiCat> getCatMenu(String yapiUrl, Long projectId, String token) throws Exception {

        String url = yapiUrl + "/api/interface/getCatMenu" +
                "?project_id=" + projectId +
                "&token=" + token;

        String resp = getRequester.get(url);

        Type jsonType = new TypeToken<YApiResponse<List<YApiCat>>>() {
        }.getType();

        YApiResponse<List<YApiCat>> response = gson.fromJson(resp, jsonType);

        if (response.getErrcode() != 0) {
            throw new Exception("YApi 接口返回失败:" + resp);
        }
        return response.getData();
    }

    @Override
    public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                          Long catId, String method, String path) throws Exception {
        for (int page = 1; ; page++) {
            String url = yapiUrl + "/api/interface/list"
                    + "?project_id=" + projectId
                    + "&token=" + token
                    + "&page=" + page
                    + "&limit=" + INTERFACE_PAGE_SIZE;
            List<YApiInterfaceSummary> interfaces = parseInterfaceList(getRequester.get(url));
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

    private List<YApiInterfaceSummary> parseInterfaceList(String resp) throws Exception {
        if (StringUtils.isBlank(resp)) {
            throw new Exception("YApi 接口返回为空");
        }
        Type type = new TypeToken<YApiResponse<List<YApiInterfaceSummary>>>() {
        }.getType();
        YApiResponse<List<YApiInterfaceSummary>> response = gson.fromJson(resp, type);
        if (response == null || response.getErrcode() == null || response.getErrcode() != 0) {
            throw new Exception("YApi 接口返回失败:" + resp);
        }
        return response.getData() == null ? List.of() : response.getData();
    }

    @Override
    public YApiCat addCat(YApiCat cat) throws Exception {

        String resp = HttpUtils.post(cat.getYapiUrl() + "/api/interface/add_cat", gson.toJson(cat));

        if (StringUtils.isBlank(resp)) {
            throw new Exception("YApi 接口返回为空");
        }

        Type jsonType = new TypeToken<YApiResponse<YApiCat>>() {
        }.getType();

        YApiResponse<YApiCat> response = gson.fromJson(resp, jsonType);

        if (response == null || response.getErrcode() != 0) {
            throw new Exception("YApi 接口返回失败:" + resp);
        }
        return response.getData();

    }
}
