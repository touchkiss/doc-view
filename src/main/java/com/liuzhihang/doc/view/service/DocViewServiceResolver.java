package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.exception.DocViewException;
import com.liuzhihang.doc.view.service.impl.DubboDocViewServiceImpl;
import com.liuzhihang.doc.view.service.impl.PojoDocViewServiceImpl;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.utils.DubboPsiUtils;
import com.liuzhihang.doc.view.utils.FeignPsiUtil;
import com.liuzhihang.doc.view.utils.PojoUtils;
import com.liuzhihang.doc.view.utils.ProtoUtils;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;
import org.jetbrains.annotations.NotNull;

/** Selects a registered parser in framework precedence order. */
final class DocViewServiceResolver {
    private DocViewServiceResolver() {
    }

    @NotNull
    static DocViewService resolve(@NotNull Project project, @NotNull PsiClass targetClass) {
        Class<? extends DocViewService> implementation = implementationFor(project, targetClass);
        return ApplicationManager.getApplication().getService(implementation);
    }

    private static Class<? extends DocViewService> implementationFor(Project project, PsiClass targetClass) {
        // Generated Proto classes may have ordinary message names that do not match POJO naming rules.
        if (ProtoUtils.isSyntheticProtoClass(targetClass)) {
            return PojoDocViewServiceImpl.class;
        }
        if (FeignPsiUtil.isFeignClass(targetClass)) {
            return SpringDocViewServiceImpl.class;
        }
        if (DubboPsiUtils.isDubboClass(targetClass)) {
            return DubboDocViewServiceImpl.class;
        }
        if (SpringPsiUtils.isSpringClass(targetClass)) {
            return SpringDocViewServiceImpl.class;
        }
        if (targetClass.isRecord() || PojoUtils.isPojoClass(targetClass)) {
            return PojoDocViewServiceImpl.class;
        }
        if (Settings.getInstance(project).getIncludeNormalInterface()) {
            return DubboDocViewServiceImpl.class;
        }
        throw new DocViewException(DocViewBundle.message("notify.error.not.support"));
    }
}
