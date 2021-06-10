package com.liuzhihang.doc.view.service.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.TagsSettings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.utils.CustomPsiCommentUtils;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import com.liuzhihang.doc.view.utils.SpringHeaderUtils;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author liuzhihang
 * @date 2020/3/3 13:32
 */
public class SpringDocViewServiceImpl implements DocViewService {

    @Override
    public boolean checkMethod(@NotNull Project project, @NotNull PsiMethod targetMethod) {
        return SpringPsiUtils.isSpringMethod(project, targetMethod);
    }

    @NotNull
    @Override
    public List<DocView> buildClassDoc(@NotNull Project project, @NotNull PsiClass psiClass) {

        List<DocView> docViewList = new LinkedList<>();

        for (PsiMethod method : psiClass.getMethods()) {

            if (!SpringPsiUtils.isSpringMethod(project, method)) {
                continue;
            }

            DocView docView = buildClassMethodDoc(project, psiClass, method);
            docViewList.add(docView);
        }

        return docViewList;
    }

    @NotNull
    @Override
    public DocView buildClassMethodDoc(@NotNull Project project, PsiClass psiClass, @NotNull PsiMethod psiMethod) {

        Settings settings = Settings.getInstance(project);
        TagsSettings tagsSettings = TagsSettings.getInstance(project);


        // 请求路径
        String path = SpringPsiUtils.getPath(psiClass, psiMethod);

        // 请求方式
        String method = SpringPsiUtils.getMethod(psiMethod);


        // 文档注释
        String desc = CustomPsiCommentUtils.getComment(psiMethod.getDocComment());


        String name = CustomPsiCommentUtils.getComment(psiMethod.getDocComment(), tagsSettings.getName());


        DocView docView = new DocView();
        docView.setPsiMethod(psiMethod);
        docView.setFullClassName(psiClass.getQualifiedName());
        docView.setClassName(psiClass.getName());
        docView.setName(StringUtils.isBlank(name) ? psiMethod.getName() : name);
        docView.setDesc(desc);
        docView.setPath(path);
        docView.setMethod(method);
        // docView.setDomain();
        docView.setType("Spring");

        List<Header> headerList = new ArrayList<>();

        // 有参数
        if (psiMethod.hasParameters()) {

            // 获取
            PsiParameter requestBodyParam = SpringPsiUtils.getRequestBodyParam(psiMethod);

            if (requestBodyParam != null) {
                // 有requestBody
                Header jsonHeader = SpringHeaderUtils.buildJsonHeader();
                headerList.add(jsonHeader);

                List<Body> reqBody = SpringPsiUtils.buildBody(settings, requestBodyParam);
                docView.setReqBodyList(reqBody);

                String bodyJson = SpringPsiUtils.getReqBodyJson(settings, requestBodyParam);
                docView.setReqExample(bodyJson);
                docView.setReqExampleType("json");

            } else {
                Header formHeader = SpringHeaderUtils.buildFormHeader();
                headerList.add(formHeader);
                List<Param> requestParam = SpringPsiUtils.buildFormParam(settings, psiMethod);
                docView.setReqParamList(requestParam);

                String paramKV = SpringPsiUtils.getReqParamKV(requestParam);
                docView.setReqExample(paramKV);
                docView.setReqExampleType("form");

            }

            // 处理 header
            List<Header> headers = SpringPsiUtils.buildHeader(settings, psiMethod);
            headerList.addAll(headers);
        } else {
            docView.setReqExampleType("form");
            Header formHeader = SpringHeaderUtils.buildFormHeader();
            headerList.add(formHeader);
        }
        docView.setHeaderList(headerList);


        PsiType returnType = psiMethod.getReturnType();
        if (returnType != null && returnType.isValid() && !returnType.equalsToText("void")) {
            List<Body> respParamList = ParamPsiUtils.buildRespBody(settings, returnType);
            docView.setRespBodyList(respParamList);

            String bodyJson = ParamPsiUtils.getRespBodyJson(settings, returnType);

            docView.setRespExample(bodyJson);
        }
        return docView;
    }


}
