package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.httpClient.execution.RestClientRequest;
import com.intellij.httpClient.execution.impl.HttpRequestConverter;
import com.intellij.httpClient.execution.common.RequestConverter;
import com.intellij.httpClient.http.request.HttpRequestVariableSubstitutor;
import com.intellij.httpClient.http.request.psi.HttpRequest;
import com.intellij.httpClient.http.request.run.HttpRequestValidationException;
import com.intellij.psi.SmartPsiElementPointer;
import com.liuzhihang.doc.view.httpclient.core.JsonBodyNormalizer;
import com.liuzhihang.doc.view.httpclient.core.Json5Comments;
import com.intellij.httpClient.http.request.HttpRequestPsiUtils;
import kotlin.coroutines.Continuation;

public final class Json5RequestConverter extends RequestConverter<RestClientRequest> {
    private final HttpRequestConverter delegate = new HttpRequestConverter();
    @Override protected Class<RestClientRequest> getRequestType() { return RestClientRequest.class; }
    @Override protected String toExternalFormInner(RestClientRequest request, String text) { return delegate.toExternalForm(request, text); }
    @Override protected String toActualRequestInner(RestClientRequest request) { return delegate.toActualRequest(request); }
    @Override public Object prepareRequestForExecution(RestClientRequest request, Continuation<? super RestClientRequest> continuation) {
        return delegate.prepareRequestForExecution(request, continuation);
    }
    private static boolean hasActiveVariables(String text) {
        var comments = Json5Comments.ranges(text);
        return HttpRequestPsiUtils.collectVariablesRangesInMessageBody(text).stream().anyMatch(v ->
            comments.stream().noneMatch(c -> v.getStartOffset() >= c.start() && v.getStartOffset() < c.end()));
    }
    @Override public RestClientRequest psiToCommonRequest(SmartPsiElementPointer<HttpRequest> pointer, HttpRequestVariableSubstitutor substitutor)
            throws HttpRequestValidationException {
        HttpRequest psi = pointer.getElement();
        boolean supported = HttpJsonBodies.isSupported(psi);
        boolean json = supported && HttpJsonBodies.hasJsonContentType(psi, substitutor);
        RestClientRequest request = delegate.psiToCommonRequest(pointer, json ? CommentAwareSubstitutor.wrap(psi, substitutor) : substitutor);
        if (supported && request.haveTextToSend && JsonBodyNormalizer.isJsonMediaType(request.getHeaderValue("Content-Type", null)) && !hasActiveVariables(request.textToSend)) {
            try {
                String normalized = JsonBodyNormalizer.normalize(request.textToSend);
                if (!normalized.equals(request.textToSend)) request.deleteHeader("Content-Length");
                request.textToSend = normalized;
            } catch (IllegalArgumentException e) { throw new HttpRequestValidationException(e.getMessage()); }
        }
        return request;
    }
}
