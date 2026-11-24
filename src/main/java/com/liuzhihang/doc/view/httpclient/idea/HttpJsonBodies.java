package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.httpClient.http.request.psi.*;
import com.intellij.httpClient.http.request.HttpRequestVariableSubstitutor;
import com.liuzhihang.doc.view.httpclient.core.JsonBodyNormalizer;

import java.util.Set;

final class HttpJsonBodies {
    private static final Set<String> OTHER_PROTOCOLS = Set.of("GRAPHQL", "GRPC", "WEBSOCKET");
    private HttpJsonBodies() {}
    static boolean isSupported(HttpRequest request) {
        return request != null && !OTHER_PROTOCOLS.contains(request.getHttpMethod())
            && request.getRequestBody() instanceof HttpRequestMessagesGroup group
            && group.getRequestMessageList().stream().allMatch(HttpMessageBody.class::isInstance);
    }

    static boolean hasJsonContentType(HttpRequest request, HttpRequestVariableSubstitutor substitutor) {
        var header = request.getHeaderField("Content-Type");
        return JsonBodyNormalizer.isJsonMediaType(header == null ? null : header.getValue(substitutor));
    }
}
