package com.liuzhihang.doc.view.service.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.PojoUtils;
import com.liuzhihang.doc.view.utils.ProtoUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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
        docViewList.add(buildClassMethodDoc(psiClass, null));
        return docViewList;
    }

    @NotNull
    @Override
    public DocView buildClassMethodDoc(PsiClass psiClass, PsiMethod psiMethod) {

        DocView docView = new DocView();
        docView.setPsiClass(psiClass);
        String title = DocViewUtils.getTitle(psiClass);
        if (ProtoUtils.isProto(psiClass)) {
//            proto只取<pre>标签中的内容; 没有 <pre> 时保持原样, 否则 substring 会抛 StringIndexOutOfBoundsException
            int start = title.indexOf("<pre>");
            int end = title.indexOf("</pre>");
            if (start >= 0 && end > start) {
                title = title.substring(start + 5, end);
            }
        }
        docView.setDocTitle(title);
        docView.setDesc(title);
        docView.setName(psiClass.getName());
        docView.setType(FrameworkEnum.NONE_POJO);
        docView.setDomain(Collections.emptyList());
        docView.setReqBody(PojoUtils.buildBody(psiClass));
        docView.setReqBodyExample(PojoUtils.reqBodyJson(psiClass));
        return docView;
    }

}
