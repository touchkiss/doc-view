package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.httpClient.execution.impl.HttpRequestRunSupport;
import com.intellij.httpClient.execution.RestClientRequest;
import com.intellij.httpClient.execution.common.*;
import com.intellij.openapi.project.Project;
import java.util.*;

/** Delegates protocols, settings and the real network handler to the native HTTP client. */
public final class Json5ExecutionSupport implements RequestExecutionSupport<RestClientRequest> {
    private final HttpRequestRunSupport delegate = new HttpRequestRunSupport();
    @Override public boolean canProcess(RequestContext context) { return delegate.canProcess(context); }
    @Override public RequestHandler<RestClientRequest> getRequestHandler() { return delegate.getRequestHandler(); }
    @Override public RequestConverter<RestClientRequest> getRequestConverter() { return new Json5RequestConverter(); }
    @Override public Collection<String> supportedMethods() { return delegate.supportedMethods(); }
    @Override public Collection<String> supportedMethods(Project project) { return delegate.supportedMethods(project); }
    @Override public boolean getNeedsScheme() { return delegate.getNeedsScheme(); }
    @Override public List<String> getSupportedSchemes() { return delegate.getSupportedSchemes(); }
}
