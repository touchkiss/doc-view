package com.liuzhihang.doc.view.contract;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.ListTableModel;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.TemplateSettings;
import com.liuzhihang.doc.view.config.UrlRewriteRule;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.ui.GenerationRulesForm;
import com.liuzhihang.doc.view.ui.SettingsForm;
import com.liuzhihang.doc.view.ui.TemplatePreviewRenderer;
import com.liuzhihang.doc.view.ui.TemplateSettingForm;
import org.jdom.Element;
import com.intellij.util.xmlb.XmlSerializer;

import javax.swing.JCheckBox;
import java.lang.reflect.Field;
import java.util.List;

/** Cross-tests for fork features interacting with upstream snapshots and settings lifecycle. */
public final class ForkDocumentUiCompatibilityTest extends LightJavaCodeInsightFixtureTestCase {
    private Element savedSettings;
    private String savedPojo;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        savedSettings = XmlSerializer.serialize(Settings.getInstance(getProject()));
        savedPojo = TemplateSettings.getInstance(getProject()).getPojoTemplate();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Settings.getInstance(getProject()).loadState(XmlSerializer.deserialize(savedSettings, Settings.class));
            TemplateSettings.getInstance(getProject()).setPojoTemplate(savedPojo);
        } finally {
            super.tearDown();
        }
    }

    public void testEmptyCurlDocumentKeepsTheExistingPsiFreeNoOutputBehavior() {
        assertEquals("", DocViewData.curlMarkdown(new DocView("empty")));
        assertEquals("", DocViewData.curlMarkdown(null));
    }

    public void testPsiFreeSnapshotKeepsBusinessCollectionJson5AndMarkdown() {
        Settings settings = Settings.getInstance(getProject());
        settings.setSeparateParam(false);
        settings.setCurlDocHost("https://docs.example.invalid");
        Body numbers = body("numbers", "List<Integer>", "1", "number\nlist");
        numbers.setCollection(true);
        Body nested = body("payload", "Payload", "", "");
        nested.getChildList().add(body("element", "String", "value", "real element field"));
        DocView doc = document(numbers, nested);
        DocViewData data = new DocViewData(getProject(), doc);
        assertTrue(data.getResponseParam(), data.getResponseParam().contains("|numbers|List<Integer>|"));
        assertTrue(data.getResponseParam(), data.getResponseParam().contains("number<br>list"));
        assertTrue(data.getResponseJson5(), data.getResponseJson5().contains("\"numbers\": [1]"));
        assertTrue(data.getResponseJson5(), data.getResponseJson5().contains("\"element\": \"value\""));
        assertTrue(data.getCurlExample(), data.getCurlExample().contains("https://docs.example.invalid/fork"));
        assertTrue(data.getResponseParamDataList().get(0).isCollection());
        assertEquals(2, data.getResponseParamDataList().get(1).getChildList().get(0).getTabCount());
    }

    public void testOnlyExplicitSyntheticRowsDisappearAndRawTemplateTreeRemains() {
        Body items = body("items", "List<Item>", "", "");
        items.setCollection(true);
        Body synthetic = body("element", "Item", "", "");
        synthetic.setCollection(true);
        synthetic.setSyntheticContainerNode(true);
        synthetic.getChildList().add(body("value", "String", "sample", "real field"));
        items.getChildList().add(synthetic);
        DocViewData data = new DocViewData(getProject(), document(items));
        assertTrue(data.getResponseParam(), data.getResponseParam().contains("|items|List<Item>|"));
        assertTrue(data.getResponseParam(), data.getResponseParam().contains("value|String|"));
        assertFalse(data.getResponseParam(), data.getResponseParam().contains("element|Item|"));
        assertTrue(data.getResponseParamDataList().get(0).getChildList().get(0).isSyntheticContainerNode());
        assertTrue(data.getResponseJson5(), data.getResponseJson5().contains("\"value\": \"sample\""));
        assertFalse(data.getResponseJson5(), data.getResponseJson5().contains("\"element\":"));
    }

    public void testFixedPreviewCanRenderForkVariablesWithoutPsi() {
        Settings.getInstance(getProject()).setCurlDocHost("https://preview.example.invalid");
        String markdown = TemplatePreviewRenderer.render(getProject(),
                "${DocView.requestJson5}\n${DocView.responseJson5}\n${DocView.curlExample}", FrameworkEnum.SPRING);
        assertTrue(markdown, markdown.contains("displayName"));
        assertTrue(markdown, markdown.contains("curl -X GET"));
        assertTrue(markdown, markdown.contains("https://preview.example.invalid/api/demo/users/42"));
        assertEquals("NONE_POJO", TemplatePreviewRenderer.render(getProject(), "${DocView.type}", FrameworkEnum.NONE_POJO));
        var variables = TemplateSettingForm.getTemplateVariables().stream().map(TemplateSettingForm.TemplateVariable::expression).toList();
        assertTrue(variables.contains("${DocView.requestJson5}"));
        assertTrue(variables.contains("${DocView.responseJson5}"));
        assertTrue(variables.contains("${DocView.curlExample}"));
    }

    public void testPojoResetApplyAndCloseKeepSavedTemplateAndReleaseEditor() throws Exception {
        TemplateSettings settings = TemplateSettings.getInstance(getProject());
        settings.setPojoTemplate("# saved POJO\n${DocView.responseJson5}");
        EdtTestUtil.runInEdtAndWait(() -> {
            TemplateSettingForm form = new TemplateSettingForm(getProject());
            var editor = form.getPojoTemplateEditor();
            try {
                form.reset();
                assertEquals(settings.getPojoTemplate(), editor.getDocument().getText());
                WriteCommandAction.runWriteCommandAction(getProject(), () -> editor.getDocument().setText("# draft POJO"));
                assertTrue(form.isModified());
                assertEquals("# saved POJO\n${DocView.responseJson5}", settings.getPojoTemplate());
                form.reset();
                assertFalse(form.isModified());
                WriteCommandAction.runWriteCommandAction(getProject(), () -> editor.getDocument().setText("# applied POJO"));
                form.apply();
                assertEquals("# applied POJO", settings.getPojoTemplate());
            } finally {
                form.disposeIfNeeded();
            }
            assertTrue(editor.isDisposed());
        });
    }

    public void testCurlHostsAndRewriteRulesRemainIndependentDrafts() throws Exception {
        Settings settings = Settings.getInstance(getProject());
        settings.setCurlCopyHost("https://copy.example.invalid");
        settings.setCurlDocHost("{{host}}");
        settings.setGrpcCurlHost("http://localhost:9090");
        settings.setUrlRewriteRules(List.of(new UrlRewriteRule(true, "^/old", "/new")));
        EdtTestUtil.runInEdtAndWait(() -> {
            SettingsForm form = new SettingsForm(getProject());
            form.reset();
            JBTextField copy = field(form, "curlCopyHostTextField");
            JBTextField doc = field(form, "curlDocHostTextField");
            JBTextField grpc = field(form, "grpcCurlHostTextField");
            ListTableModel<UrlRewriteRule> rules = field(form, "urlRewriteTableModel");
            copy.setText("https://draft.example.invalid");
            rules.getItem(0).replacement = "/draft";
            assertTrue(form.isModified());
            assertEquals("https://copy.example.invalid", settings.getCurlCopyHost());
            assertEquals("/new", settings.getUrlRewriteRules().get(0).replacement);
            form.reset();
            assertFalse(form.isModified());
            assertEquals("https://copy.example.invalid", copy.getText());
            doc.setText("https://doc.example.invalid");
            grpc.setText("grpc.example.invalid:9090");
            rules.getItem(0).replacement = "/applied";
            form.apply();
            assertEquals("https://copy.example.invalid", settings.getCurlCopyHost());
            assertEquals("https://doc.example.invalid", settings.getCurlDocHost());
            assertEquals("grpc.example.invalid:9090", settings.getGrpcCurlHost());
            assertEquals("/applied", settings.getUrlRewriteRules().get(0).replacement);
            rules.getItem(0).replacement = "/unapplied";
            assertEquals("/applied", settings.getUrlRewriteRules().get(0).replacement);
        });
    }

    public void testSnakeCaseSettingSurvivesGenerationRulesMove() throws Exception {
        Settings settings = Settings.getInstance(getProject());
        settings.setFieldNameCaseType(true);
        EdtTestUtil.runInEdtAndWait(() -> {
            GenerationRulesForm form = new GenerationRulesForm(getProject());
            form.reset();
            JCheckBox snake = field(form, "fieldNameSnakeCase");
            assertTrue(snake.isSelected());
            snake.setSelected(false);
            assertTrue(form.isModified());
            assertTrue(settings.getFieldNameCaseType());
            form.apply();
            assertFalse(settings.getFieldNameCaseType());
        });
    }

    private static Body body(String name, String type, String example, String description) {
        Body body = new Body();
        body.setName(name);
        body.setType(type);
        body.setExample(example);
        body.setDesc(description);
        body.setRequired(false);
        return body;
    }

    private static DocView document(Body... rows) {
        DocView doc = new DocView("fork");
        doc.setType(FrameworkEnum.SPRING);
        doc.setMethod("GET");
        doc.setPath("/fork");
        doc.getRespBody().setChildList(List.of(rows));
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
