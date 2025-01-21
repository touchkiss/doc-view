package com.liuzhihang.doc.view.service.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.intellij.psi.PsiKeyword.VOID;

/**
 * Pojo doc View Service Impl
 *
 * @author necksas.liu
 * @date 2024/12/31 11:16:24
 */
public class PojoDocViewServiceImpl implements DocViewService {

    @Override
    public boolean checkMethod(@NotNull PsiMethod targetMethod) {
        return false;
    }

    @NotNull
    @Override
    public List<DocView> buildClassDoc(@NotNull PsiClass psiClass) {

        List<DocView> docViewList = new LinkedList<>();

        return docViewList;
    }

    @NotNull
    @Override
    public DocView buildClassMethodDoc(PsiClass psiClass, @NotNull PsiMethod psiMethod) {

        DocView docView = new DocView();
        docView.setPsiClass(psiClass);
        docView.setPsiMethod(psiMethod);
        docView.setDocTitle(DocViewUtils.getTitle(psiClass));
        docView.setName(DocViewUtils.getName(psiMethod));
        docView.setDesc(DocViewUtils.getMethodDesc(psiMethod));
        docView.setPath(SpringPsiUtils.path(psiClass, psiMethod));
        docView.setMethod(SpringPsiUtils.method(psiMethod));
        docView.setDomain(Collections.emptyList());
        return docView;
    }

}
