package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTypesUtil;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.service.SourceDescriptionService;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import com.liuzhihang.doc.view.utils.ProtoUtils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.List;

/** Parameter rows and the writable declarations they represent in Doc Editor. */
record DocEditorFields(List<DocViewParamData> data, Map<DocViewParamData, SourceDescriptionService.Target> targets) {
    static DocEditorFields read(PsiClass psiClass) {
        Body rootBody = new Body();
        rootBody.setQualifiedNameForClassType(psiClass.getQualifiedName());
        boolean isProto = ProtoUtils.isProto(PsiTypesUtil.getClassType(psiClass));
        ParamPsiUtils.buildBodyList(psiClass, null, rootBody, isProto);
        List<DocViewParamData> rows = DocViewData.buildBodyDataList(rootBody.getChildList());
        Map<DocViewParamData, SourceDescriptionService.Target> targets = new IdentityHashMap<>();
        collectSourceTargets(rows, targets, "");
        return new DocEditorFields(rows, targets);
    }

    static List<DocViewParamData> snapshotParamData(List<DocViewParamData> source) {
        List<DocViewParamData> snapshot = new ArrayList<>(source.size());
        for (DocViewParamData data : source) {
            DocViewParamData copy = new DocViewParamData();
            copy.setName(data.getName());
            copy.setType(data.getType());
            copy.setRequired(data.getRequired());
            copy.setExample(data.getExample());
            copy.setDescription(data.getDescription());
            copy.setSince(data.getSince());
            copy.setVersion(data.getVersion());
            copy.setPrefixSymbol1(data.getPrefixSymbol1());
            copy.setPrefixSymbol2(data.getPrefixSymbol2());
            copy.setCollection(data.isCollection());
            copy.setMap(data.isMap());
            copy.setTabCount(data.getTabCount());
            copy.setSyntheticContainerNode(data.isSyntheticContainerNode());
            if (data.getChildList() != null && !data.getChildList().isEmpty()) {
                copy.setChildList(snapshotParamData(data.getChildList()));
            }
            snapshot.add(copy);
        }
        return snapshot;
    }


    private static void collectSourceTargets(List<DocViewParamData> rows,
                                             Map<DocViewParamData, SourceDescriptionService.Target> targets,
                                             String prefix) {
        for (DocViewParamData row : rows) {
            String path = row.getName() == null || row.getName().isBlank() ? prefix
                    : prefix.isEmpty() ? row.getName() : prefix + " · " + row.getName();
            if (!row.isSyntheticContainerNode()) {
                var target = SourceDescriptionService.fieldDescriptionTarget(path, row.getDescription(), row.getPsiElement());
                if (target != null) {
                    targets.put(row, target);
                }
            }
            if (row.getChildList() != null) {
                collectSourceTargets(row.getChildList(), targets, path);
            }
        }
    }

    Map<SourceDescriptionService.Target, DocViewParamData> directFieldTargets(PsiClass psiClass) {
        Map<PsiElement, DocViewParamData> visible = new LinkedHashMap<>();
        for (var row : data) {
            if (row.getPsiElement() != null && !row.isSyntheticContainerNode()) {
                visible.put(row.getPsiElement(), row);
            }
        }
        Map<SourceDescriptionService.Target, DocViewParamData> result = new LinkedHashMap<>();
        if (psiClass.isValid()) {
            for (var field : psiClass.getFields()) {
                addClassField(result, visible, field, field.getName());
            }
            for (var component : psiClass.getRecordComponents()) {
                addClassField(result, visible, component, component.getName());
            }
        }
        return result;
    }

    private void addClassField(Map<SourceDescriptionService.Target, DocViewParamData> result,
                               Map<PsiElement, DocViewParamData> visible, PsiElement element, String name) {
        var row = visible.get(element);
        var target = row == null ? SourceDescriptionService.fieldTarget("字段 · " + name, null, element)
                : targets.get(row);
        if (target != null) {
            result.put(target, row);
        }
    }

    void updateRowDescription(DocViewParamData row) {
        var target = targets.get(row);
        var description = ReadAction.computeBlocking(() -> {
            var owner = target.source().getElement();
            if (owner instanceof PsiField field) {
                return DocViewUtils.fieldDescription(field);
            }
            if (owner instanceof PsiClass type && target.paramName() != null) {
                for (var component : type.getRecordComponents()) {
                    if (target.paramName().equals(component.getName())) {
                        return DocViewUtils.fieldDescription(component);
                    }
                }
            }
            return null;
        });
        if (description != null) {
            for (var entry : targets.entrySet()) {
                var other = entry.getValue();
                if (Objects.equals(target.paramName(), other.paramName()) && target.source().equals(other.source())) {
                    entry.getKey().setDescription(description);
                    entry.setValue(new SourceDescriptionService.Target(
                            other.label(), description, other.paramName(), other.source()));
                }
            }
        }
    }

}
