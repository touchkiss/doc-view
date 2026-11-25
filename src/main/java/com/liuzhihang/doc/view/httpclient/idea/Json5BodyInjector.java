package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.lang.injection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.httpClient.http.request.psi.*;
import com.intellij.httpClient.injection.http.request.HttpRequestBodyInjector;
import com.intellij.json.json5.Json5Language;
import com.liuzhihang.doc.view.httpclient.core.JsonBodyNormalizer;
import java.util.List;

/** Runs before the native injector so a JSON body has one consistent JSON5 language. */
public final class Json5BodyInjector implements MultiHostInjector {
    @Override public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(HttpRequestMessagesGroup.class);
    }
    @Override public void getLanguagesToInject(MultiHostRegistrar registrar, PsiElement context) {
        HttpRequest request = PsiTreeUtil.getParentOfType(context, HttpRequest.class);
        if (!HttpJsonBodies.isSupported(request) || !JsonBodyNormalizer.isJsonMediaType(request.getMimeType())) {
            return;
        }
        for (var message : ((HttpRequestMessagesGroup) context).getRequestMessageList()) {
            if (!(message instanceof HttpMessageBody body)) {
                continue;
            }
            var range = body.getContentRange();
            String text = range.substring(body.getText());
            var places = HttpRequestBodyInjector.injectBody(body, text, null, range.getStartOffset());
            if (!places.isEmpty()) {
                registrar.startInjecting(Json5Language.INSTANCE);
                for (var place : places) {
                    registrar.addPlace(place.getPrefix(), place.getSuffix(), place.getHost(), place.getRangeInsideHost());
                }
                registrar.doneInjecting();
            }
        }
    }
}
