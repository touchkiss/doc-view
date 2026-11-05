package com.liuzhihang.doc.view.contract;

import com.intellij.protobuf.lang.psi.PbFile;
import com.intellij.protobuf.lang.psi.PbMessageType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.service.DtoSchemaCacheService;
import com.liuzhihang.doc.view.testsupport.AnnotationStubs;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import com.liuzhihang.doc.view.utils.ProtoToPsiClassConverter;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;

import java.util.Map;

/** Business source comments must never depend on plugin-specific JavaDoc tags. */
public final class NoDocViewSourceTagsTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testPluginTitleAndNameTagsDoNotOverrideStandardJavaDoc() {
        PsiClass model = myFixture.addClass("""
                package tagfree;
                /**
                 * 标准类说明
                 * @DocView.Title 插件标题不应生效
                 */
                public class Documented {
                    /**
                     * 标准方法说明
                     * @DocView.Name 插件名称不应生效
                     */
                    public void execute() {}
                }
                """);
        assertEquals("标准类说明", DocViewUtils.getTitle(model));
        assertEquals("标准方法说明", DocViewUtils.getName(model.getMethods()[0]));
    }

    public void testPluginOnlyTagsFallBackToNamesAndCannotMakeFieldsRequired() {
        PsiClass model = myFixture.addClass("""
                package tagfree;
                /** @DocView.Title IgnoredTitle */
                public class TagOnly {
                    /** @DocView.Required */
                    private String value;
                    /** @DocView.Name IgnoredName */
                    public void actualName() {}
                }
                """);
        assertEquals("TagOnly", DocViewUtils.getTitle(model));
        assertEquals("actualName", DocViewUtils.getName(model.getMethods()[0]));
        assertFalse(DocViewUtils.isRequired(model.findFieldByName("value", false)));
        PsiClass record = myFixture.addClass("""
                package tagfree;
                public record TaggedRecord(/** @DocView.Required */ String value) {}
                """);
        assertFalse(DocViewUtils.isRequired(record.getRecordComponents()[0]));
    }

    public void testStandardSwaggerSpringAndJakartaMetadataStillWorks() {
        AnnotationStubs.installOpenApi3(myFixture);
        AnnotationStubs.installSpringWeb(myFixture);
        myFixture.addClass("package jakarta.validation.constraints; public @interface NotNull {}");
        myFixture.addClass("""
                package tagfree;
                public class RequiredModel {
                    @jakarta.validation.constraints.NotNull private String standard;
                    /** @DocView.Required */ private String ignored;
                }
                """);
        PsiClass controller = myFixture.addClass("""
                package tagfree;
                public class Controller {
                    /** Standard description. @DocView.Name Ignored */
                    @io.swagger.v3.oas.annotations.Operation(summary="标准Swagger名称")
                    @org.springframework.web.bind.annotation.GetMapping("/metadata")
                    public void request(@org.springframework.web.bind.annotation.RequestParam("id") String id) {}
                }
                """);
        PsiMethod method = controller.getMethods()[0];
        assertEquals("标准Swagger名称", DocViewUtils.getName(method));
        assertEquals("/metadata", SpringPsiUtils.methodPath(method));
        assertTrue(SpringPsiUtils.buildFormParam(method).get(0).getRequired());
        PsiClass model = myFixture.findClass("tagfree.RequiredModel");
        assertTrue(DocViewUtils.isRequired(model.findFieldByName("standard", false)));
        assertFalse(DocViewUtils.isRequired(model.findFieldByName("ignored", false)));
    }

    public void testProto2RequiredUsesInternalMetadataWithoutGeneratedSourceTags() {
        PsiFile file = myFixture.configureByText("RequiredEnvelope.proto", """
                syntax = "proto2";
                package tagfree;
                message RequiredEnvelope {
                    required string id = 1;
                    optional string label = 2;
                    repeated string names = 3;
                    oneof payload { string choice = 4; }
                }
                """);
        assertTrue(file instanceof PbFile);
        PbMessageType message = PsiTreeUtil.findChildrenOfType(file, PbMessageType.class).stream()
                .filter(candidate -> "RequiredEnvelope".equals(candidate.getName())).findFirst().orElseThrow();
        PsiClass converted = ProtoToPsiClassConverter.convert(message, getProject());
        assertNotNull(converted);
        assertFalse("No plugin marker may be emitted into generated Java", converted.getText().contains("@DocView"));
        assertTrue(DocViewUtils.isRequired(converted.findFieldByName("id", false)));
        assertFalse(DocViewUtils.isRequired(converted.findFieldByName("label", false)));
        assertFalse(DocViewUtils.isRequired(converted.findFieldByName("names", false)));
        assertFalse(DocViewUtils.isRequired(converted.findFieldByName("choice", false)));
        Body parsed = ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(converted));
        Body snapshot = DtoSchemaCacheService.copyBody(parsed, null, false);
        assertTrue(snapshot.getChildList().stream().filter(row -> "id".equals(row.getName()))
                .findFirst().orElseThrow().getRequired());
    }

    public void testLegacyWriteBackCannotChangeAnyBusinessSource() {
        PsiClass model = myFixture.addClass("""
                package tagfree;
                public class Editable {
                    /** 原说明 */
                    private String value;
                }
                """);
        String originalSource = model.getContainingFile().getText();
        PsiField field = model.findFieldByName("value", false);
        DocViewParamData edit = new DocViewParamData();
        edit.setRequired(true);
        edit.setDesc("标准字段说明");
        DocViewUtils.writeComment(getProject(), Map.of(field, edit));
        assertEquals("Even legacy write-back calls must leave all business source bytes unchanged",
                originalSource, model.getContainingFile().getText());
        assertFalse("Legacy calls must not create plugin tags", model.getText().contains("@DocView"));
    }
}
