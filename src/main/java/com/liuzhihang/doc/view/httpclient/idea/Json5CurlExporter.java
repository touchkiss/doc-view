package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.httpClient.converters.RequestBuilder;
import com.intellij.httpClient.converters.curl.*;
import com.intellij.httpClient.execution.*;
import com.intellij.httpClient.execution.auth.HttpClientAuthData;
import com.intellij.httpClient.execution.ssl.SslConfiguration;
import com.intellij.httpClient.http.request.*;
import com.intellij.httpClient.http.request.psi.HttpRequest;
import com.intellij.httpClient.http.request.run.HttpRequestValidationException;
import com.intellij.util.ThrowableConsumer;
import com.liuzhihang.doc.view.httpclient.core.JsonBodyNormalizer;
import java.io.File;
import java.util.*;
import java.util.function.Function;

public final class Json5CurlExporter {
    private Json5CurlExporter() {}
    public static String convert(HttpRequest request, HttpRequestVariableSubstitutor substitutor) throws HttpRequestValidationException {
        boolean json = HttpJsonBodies.isSupported(request) && JsonBodyNormalizer.isJsonMediaType(
            request.getHeaderField("Content-Type") == null ? null : request.getHeaderField("Content-Type").getValue(substitutor));
        if (!json) return HttpRequestPsiConverter.convertFromHttpRequest(request, substitutor, new CurlRequestBuilder(false));
        return HttpRequestPsiConverter.convertFromHttpRequest(request, CommentAwareSubstitutor.wrap(request, substitutor), new NormalizingBuilder());
    }

    /** Retains native quoting, URL/auth/protocol handling. Normalizes after native variable substitution. */
    private static final class NormalizingBuilder implements RequestBuilder<String, CurlRequestFormBody> {
        private final CurlRequestBuilder delegate = new CurlRequestBuilder(false);
        private final List<Runnable> headers = new ArrayList<>();
        private boolean changed;
        @Override public void withUrl(String url) { delegate.withUrl(url); }
        @Override public void withMethod(String method) { delegate.withMethod(method); }
        @Override public void withProtocol(HttpProtocol protocol) { delegate.withProtocol(protocol); }
        @Override public <E extends Throwable> void withQueryParameters(ThrowableConsumer<QueryBuilder,E> c) throws E { delegate.withQueryParameters(c); }
        @Override public void withAuthData(HttpClientAuthData auth) { delegate.withAuthData(auth); }
        @Override public void withHeader(String name, String value, Function<String,String> f) {
            headers.add(() -> { if (!changed || !name.equalsIgnoreCase("Content-Length")) delegate.withHeader(name, value, f); });
        }
        @Override public void withFileToSend(File file) { delegate.withFileToSend(file); }
        @Override public void withTextToSend(String body) {
            String normalized = JsonBodyNormalizer.normalize(body);
            changed = !normalized.equals(body);
            delegate.withTextToSend(normalized);
        }
        @Override public <E extends Throwable> void withMultipartBody(String boundary, ThrowableConsumer<? super MultipartBodyBuilder<CurlRequestFormBody>, E> c) throws E {
            delegate.withMultipartBody(boundary, c);
        }
        @Override public void withConfig(HttpRequestConfig config) { delegate.withConfig(config); }
        @Override public void withEmptyCredentials() { delegate.withEmptyCredentials(); }
        @Override public void withSslConfiguration(SslConfiguration ssl) { delegate.withSslConfiguration(ssl); }
        @Override public void withUserDefinedOutputFilePath(String path, boolean force) { delegate.withUserDefinedOutputFilePath(path, force); }
        @Override public String build() { headers.forEach(Runnable::run); return delegate.build(); }
    }
}
