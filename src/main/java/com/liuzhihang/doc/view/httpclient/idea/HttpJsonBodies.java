package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.httpClient.http.request.psi.*;
import java.util.Set;

final class HttpJsonBodies {
    private static final Set<String> OTHER_PROTOCOLS = Set.of("GRAPHQL", "GRPC", "WEBSOCKET");
    private HttpJsonBodies() {}
    static boolean isSupported(HttpRequest request) {
        return request != null && !OTHER_PROTOCOLS.contains(request.getHttpMethod())
            && request.getRequestBody() instanceof HttpRequestMessagesGroup group
            && group.getRequestMessageList().stream().allMatch(HttpMessageBody.class::isInstance);
    }
}
