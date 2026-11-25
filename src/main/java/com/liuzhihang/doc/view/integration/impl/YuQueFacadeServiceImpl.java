package com.liuzhihang.doc.view.integration.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.liuzhihang.doc.view.integration.YuQueFacadeService;
import com.liuzhihang.doc.view.integration.dto.YuQueCreate;
import com.liuzhihang.doc.view.integration.dto.YuQueResponse;
import com.liuzhihang.doc.view.integration.dto.YuQueUpdate;
import com.liuzhihang.doc.view.utils.HttpUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.util.List;

/**
 * @author liuzhihang
 * @date 2022/3/31 23:16
 */
public class YuQueFacadeServiceImpl implements YuQueFacadeService {

    private static final int HTTP_NOT_FOUND = 404;
    private static final String TOKEN_HEADER = "X-Auth-Token";
    private static final String REPOSITORIES_PATH = "/repos/";
    private static final String DOCUMENTS_PATH = "/docs/";
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @Override
    public YuQueResponse getDoc(String url, String token, String namespace, String slug) throws Exception {

        Header header = new BasicHeader(TOKEN_HEADER, token);


        String resp;
        try {
            resp = HttpUtils.get(url + REPOSITORIES_PATH + namespace + DOCUMENTS_PATH + slug, null, List.of(header));
        } catch (HttpUtils.HttpStatusException exception) {
            if (exception.getStatusCode() == HTTP_NOT_FOUND) {
                return null;
            }
            throw exception;
        }
        return parseResponse(resp, false);
    }

    @Override
    public YuQueResponse create(String url, String token, String namespace, YuQueCreate yuQueCreate) throws Exception {

        Header header = new BasicHeader(TOKEN_HEADER, token);

        String resp = HttpUtils.post(url + REPOSITORIES_PATH + namespace + DOCUMENTS_PATH, GSON.toJson(yuQueCreate), List.of(header));

        return parseResponse(resp, true);
    }

    @Override
    public YuQueResponse update(String url, String token, String namespace, Long id, YuQueUpdate yuQueUpdate) throws Exception {

        Header header = new BasicHeader(TOKEN_HEADER, token);

        String resp = HttpUtils.put(url + REPOSITORIES_PATH + namespace + DOCUMENTS_PATH + id, GSON.toJson(yuQueUpdate), List.of(header));

        return parseResponse(resp, true);
    }
    private static YuQueResponse parseResponse(String body, boolean requireSlug) throws Exception {
        try {
            YuQueResponse response = GSON.fromJson(body, YuQueResponse.class);
            if (response != null && response.getData() != null
                    && response.getData().getId() != null && response.getData().getId() > 0
                    && (!requireSlug || StringUtils.isNotBlank(response.getData().getSlug()))) {
                return response;
            }
        } catch (com.google.gson.JsonParseException | IllegalStateException _) {
            // Malformed JSON may contain credentials or document contents.
        }
        throw new Exception("语雀接口响应无效");
    }
}
