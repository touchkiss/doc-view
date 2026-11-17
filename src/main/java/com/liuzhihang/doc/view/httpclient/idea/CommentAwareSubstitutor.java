package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.httpClient.http.request.*;
import com.intellij.httpClient.http.request.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.liuzhihang.doc.view.httpclient.core.Json5Comments;
import java.util.*;

/** Skips only substitutions whose source positions are inside JSON5 comments. */
final class CommentAwareSubstitutor implements HttpRequestVariableSubstitutor {
    private final HttpRequestVariableSubstitutor delegate;
    private final Set<HttpRequestVariableSubstitutionKey> ignored;
    private CommentAwareSubstitutor(HttpRequestVariableSubstitutor delegate, Set<HttpRequestVariableSubstitutionKey> ignored) {
        this.delegate = delegate; this.ignored = ignored;
    }
    static HttpRequestVariableSubstitutor wrap(HttpRequest request, HttpRequestVariableSubstitutor delegate) {
        Set<HttpRequestVariableSubstitutionKey> ignored = new HashSet<>();
        if (request.getRequestBody() instanceof HttpRequestMessagesGroup group) {
            for (var message : group.getRequestMessageList()) {
                if (!(message instanceof HttpMessageBody body)) continue;
                var comments = Json5Comments.ranges(body.getText());
                var factory = HttpRequestVariableSubstitutionKey.insideElementKeyFactory(body);
                for (var variable : HttpRequestPsiUtils.collectVariablesRangesInMessageBody(body.getText())) {
                    if (comments.stream().anyMatch(r -> variable.getStartOffset() >= r.start() && variable.getStartOffset() < r.end()))
                        ignored.add(factory.withOffset(variable.getStartOffset()));
                }
            }
        }
        return new CommentAwareSubstitutor(delegate, ignored);
    }
    @Override public String getValue(PsiElement element) { return delegate.getValue(element); }
    @Override public String getValue(PsiElement element, Condition<? super PsiElement> condition) { return delegate.getValue(element, condition); }
    @Override public String getVariableValue(String name, String raw, Project project, HttpRequestVariableSubstitutionKey key) {
        return ignored.contains(key) ? raw : delegate.getVariableValue(name, raw, project, key);
    }
    @Override public HttpRequestVariableSubstitutor invalidVariablesAware(boolean aware) {
        return new CommentAwareSubstitutor(delegate.invalidVariablesAware(aware), ignored);
    }
    @Override public List<HttpRequestExecutionEnvironment.VariableInfo> getAllEnvVariables() { return delegate.getAllEnvVariables(); }
    @Override public Integer iteration() { return delegate.iteration(); }
    @Override public Object templateValue(int index) { return delegate.templateValue(index); }
}
