package com.liuzhihang.doc.view.common.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.proxy.CommonProxy;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * http工具类
 *
 * @author wangchao
 * @date 2019/09/01
 */
public class HttpUtil {
    private static final Logger LOGGER = Logger.getInstance(HttpUtil.class);
    private static final int CONNECT_TIMEOUT = 1000;
    private static final int SOCKET_TIMEOUT = 1000;

    /** 私有构造 */
    private HttpUtil() {}

    /**
     * get请求
     *
     * @param url url
     * @return {@link String}
     */
    public static String get(String url) {
        return get(url, CONNECT_TIMEOUT, SOCKET_TIMEOUT);
    }

    /**
     * get请求
     *
     * @param url url
     * @param timeout 超时
     * @return {@link String}
     */
    public static String get(String url, int timeout) {
        return get(url, timeout, timeout);
    }

    /**
     * get请求
     *
     * @param url url
     * @param connectTimeout 连接超时
     * @param socketTimeout 读超时
     * @return {@link String}
     */
    public static String get(String url, int connectTimeout, int socketTimeout) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String result = null;
        CloseableHttpClient httpclient = null;
        CloseableHttpResponse response = null;
        try {
            // 代理
            HttpHost httpHost = getProxy(url);
            Builder build = RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout);
            if (httpHost != null) {
                build.setProxy(httpHost);
            }

            httpclient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(url);
            httpGet.setConfig(build.build());
            response = httpclient.execute(httpGet);
            result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Translation HTTP request failed: " + e.getClass().getSimpleName());
        } finally {
            HttpClientUtils.closeQuietly(response);
            HttpClientUtils.closeQuietly(httpclient);
        }
        return result;
    }

    /**
     * get请求
     *
     * @param url url
     * @param params 参数
     * @param timeout 超时
     * @return {@link String}
     */
    public static String get(String url, Map<String, Object> params, int timeout) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String paramStr = params.entrySet().stream()
            .map(e -> e.getKey() + "=" + encode(String.valueOf(e.getValue()))).collect(Collectors.joining("&"));
        url = url.contains("?") ? url + "&" + paramStr : url + "?" + paramStr;
        return get(url, timeout);
    }

    /**
     * 编码
     *
     * @param word word
     * @return {@link String}
     */
    public static String encode(String word) {
        try {
            return URLEncoder.encode(word, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            LOGGER.warn("Translation URL encoding failed");
            return StringUtils.EMPTY;
        }
    }

    /**
     * post请求
     *
     * @param url url
     * @param headers headers
     * @param body 内容
     * @param timeout 超时时间
     * @return {@link String}
     */
    public static String post(String url, Map<String, String> headers, String body, int timeout) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        try {
            return postResponse(url, headers, body, timeout).body();
        } catch (IOException e) {
            LOGGER.warn("Translation HTTP request failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public record PostResponse(int status, String body) {}

    public static PostResponse postResponse(String url, Map<String, String> headers, String body, int timeout) throws IOException {
        HttpHost proxy = getProxy(url);
        Builder config = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout);
        if (proxy != null) {
            config.setProxy(proxy);
        }
        HttpPost request = new HttpPost(url);
        if (headers != null) {
            headers.forEach(request::addHeader);
        }
        request.setConfig(config.build());
        request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            return new PostResponse(response.getStatusLine().getStatusCode(),
                response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        }
    }

    /**
     * post请求
     *
     * @param url url
     * @param headers headers
     * @param body 内容
     * @return {@link String}
     */
    public static String post(String url, Map<String, String> headers, String body) {
        return post(url, headers, body, SOCKET_TIMEOUT);
    }

    /**
     * 获取代理
     *
     * @param url url
     * @return {@link HttpHost}
     * @throws MalformedURLException 异常
     */
    private static HttpHost getProxy(String url) throws MalformedURLException {
        URL target = new URL(url);
        if (isLoopback(target.getHost())) {
            return null;
        }
        List<Proxy> proxies = CommonProxy.getInstance().select(target);
        // 代理
        HttpHost httpHost = null;
        if (proxies != null && !proxies.isEmpty()) {
            for (Proxy proxy : proxies) {
                if (proxy == null || Type.DIRECT.equals(proxy.type())) {
                    continue;
                }
                InetSocketAddress address = (InetSocketAddress)proxy.address();
                httpHost = new HttpHost(address.getHostName(), address.getPort());
            }
        }
        return httpHost;
    }

    public static boolean isLoopback(String host) {
        return host != null && ("localhost".equalsIgnoreCase(host) || "[::1]".equals(host)
            || "[0:0:0:0:0:0:0:1]".equals(host) || host.matches("127(?:\\.\\d{1,3}){3}"));
    }

    /**
     * post请求（json）
     *
     * @param url url
     * @param headers headers
     * @param body 内容
     * @return {@link String}
     */
    public static String postJson(String url, Map<String, String> headers, String body, int timeout) {
        if (headers == null) {
            headers = Maps.newHashMap();
        }
        headers.put("Content-Type", "application/json;charset=utf-8");
        return post(url, headers, body, timeout);
    }

    /**
     * post请求（json）
     *
     * @param url url
     * @param headers headers
     * @param body 内容
     * @return {@link String}
     */
    public static String postJson(String url, Map<String, String> headers, String body) {
        return postJson(url, headers, body, SOCKET_TIMEOUT);
    }

}
