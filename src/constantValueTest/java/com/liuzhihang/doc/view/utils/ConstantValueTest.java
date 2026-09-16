package com.liuzhihang.doc.view.utils;

import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.liuzhihang.doc.view.config.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

import static org.junit.Assert.*;

/** Exercises documentation output with real Java PSI, not mocked constant values. */
public class ConstantValueTest {
    private Disposable disposable;
    private JavaCoreProjectEnvironment environment;
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        disposable = Disposer.newDisposable();
        JavaCoreApplicationEnvironment application = new JavaCoreApplicationEnvironment(disposable);
        com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
                application.getApplication().getExtensionArea(), "com.intellij.metaLanguage",
                com.intellij.lang.MetaLanguage.class);
        com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
                application.getApplication().getExtensionArea(), "com.intellij.lang.psiAugmentProvider",
                com.intellij.psi.augment.PsiAugmentProvider.class);
        application.addExplicitExtension(com.intellij.platform.syntax.psi.ElementTypeConverters.getInstance(),
                com.intellij.lang.java.JavaLanguage.INSTANCE,
                new com.intellij.platform.syntax.psi.CommonElementTypeConverterFactory());
        application.addExplicitExtension(com.intellij.platform.syntax.psi.ElementTypeConverters.getInstance(),
                com.intellij.lang.java.JavaLanguage.INSTANCE,
                new com.intellij.lang.java.syntax.JavaElementTypeConverterExtension());
        environment = new JavaCoreProjectEnvironment(disposable, application);
        com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
                environment.getProject().getExtensionArea(), "com.intellij.java.elementFinder", PsiElementFinder.class);
        environment.getProject().getExtensionArea().getExtensionPoint("com.intellij.java.elementFinder")
                .registerExtension(new com.intellij.psi.impl.PsiElementFinderImpl(environment.getProject()), disposable);
        com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint(
                environment.getProject().getExtensionArea(), "com.intellij.jvm.elementProvider",
                com.intellij.lang.jvm.facade.JvmElementProvider.class);
        environment.getProject().registerService(com.intellij.codeInsight.ExternalAnnotationsManager.class,
                new com.intellij.codeInsight.ReadableExternalAnnotationsManager(PsiManager.getInstance(environment.getProject())) {
                    @Override protected boolean hasAnyAnnotationsRoots() { return false; }
                });
        environment.getProject().registerService(com.intellij.codeInsight.InferredAnnotationsManager.class,
                new com.intellij.java.frontend.codeInsight.annotations.DummyInferredAnnotationsManager());
        Settings settings = new Settings();
        settings.setFieldNameCaseType(false);
        environment.getProject().registerService(Settings.class, settings);
        // Core PSI has no project SDK; provide the Java types required for String constant resolution.
        Path sdk = temporaryFolder.newFolder("sdk").toPath();
        Files.createDirectories(sdk.resolve("java/lang"));
        Files.writeString(sdk.resolve("java/lang/Object.java"), "package java.lang; public class Object {}");
        Files.writeString(sdk.resolve("java/lang/String.java"), "package java.lang; public final class String {}");
        environment.addSourcesToClasspath(application.getLocalFileSystem().findFileByPath(sdk.toString()));
    }

    @After
    public void tearDown() {
        Disposer.dispose(disposable);
    }

    private PsiField field(String declaration) {
        return javaClass("class Response { static final int SUCCESS = 10000; " + declaration + " }")
                .findFieldByName("code", false);
    }

    private PsiClass javaClass(String source) {
        PsiJavaFile file = (PsiJavaFile) PsiFileFactory.getInstance(environment.getProject())
                .createFileFromText("Response.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, source);
        return file.getClasses()[0];
    }

    @Test
    public void resolvesConstantInNumericJsonExample() {
        PsiField field = field("private int code = SUCCESS;");
        assertEquals(10000, SpringPsiUtils.getDefaultValue(field, field.getType()));
    }

    @Test
    public void resolvesConstantInFieldExample() {
        assertEquals("10000", DocViewUtils.fieldExample(field("private int code = SUCCESS;")));
    }

    private String annotatedSource(String declaration) {
        return "package com.fasterxml.jackson.annotation; " + declaration
                + " @interface JsonProperty { String value() default \"\"; }"
                + " class OrderConstants { static final String REQUEST_ID = \"request_id\"; }";
    }

    @Test
    public void resolvesJsonPropertyConstantForField() {
        PsiClass cls = javaClass(annotatedSource(
                "class Response { @JsonProperty(OrderConstants.REQUEST_ID) String requestId; }"));
        assertEquals("request_id", DocViewUtils.fieldName(cls.getFields()[0], false));
    }

    @Test
    public void resolvesJsonPropertyConstantForRecord() {
        PsiClass cls = javaClass(annotatedSource(
                "record Response(@JsonProperty(OrderConstants.REQUEST_ID) String requestId) {}"));
        assertEquals("request_id", DocViewUtils.fieldName(cls.getRecordComponents()[0], false));
    }

    @Test
    public void resolvesConstantsFromCompiledClassesWithoutSources() throws Exception {
        Path sources = temporaryFolder.newFolder("sources").toPath();
        Path classes = temporaryFolder.newFolder("classes").toPath();
        Path enumSource = sources.resolve("EcommerceErrorCodeGrpcEnum.java");
        Files.writeString(enumSource, "package generated; public enum EcommerceErrorCodeGrpcEnum { "
                + "ECOMMERCE_SUCCESS; public static final int ECOMMERCE_SUCCESS_VALUE = 10000; }");
        Path namesSource = sources.resolve("OrderConstants.java");
        Files.writeString(namesSource, "package generated; public class OrderConstants { "
                + "public static final String REQUEST_ID = \"request_id\"; }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classes.toString(), enumSource.toString(), namesSource.toString()));
        environment.addSourcesToClasspath(environment.getEnvironment().getLocalFileSystem()
                .findFileByPath(classes.toString()));
        PsiClass cls = javaClass("package com.fasterxml.jackson.annotation; "
                + "import generated.EcommerceErrorCodeGrpcEnum; "
                + "import static generated.OrderConstants.REQUEST_ID; "
                + "class Response { private int code = EcommerceErrorCodeGrpcEnum.ECOMMERCE_SUCCESS_VALUE; "
                + "@JsonProperty(REQUEST_ID) private String requestId; } "
                + "@interface JsonProperty { String value(); }");
        PsiField code = cls.findFieldByName("code", false);
        PsiElement target = ((PsiReferenceExpression) code.getInitializer()).resolve();
        assertTrue("The fixture must resolve a binary class, not a source file", target instanceof PsiCompiledElement);
        assertEquals(10000, SpringPsiUtils.getDefaultValue(code, code.getType()));
        assertEquals("10000", DocViewUtils.fieldExample(code));
        assertEquals("request_id", DocViewUtils.fieldName(cls.findFieldByName("requestId", false), false));
        com.liuzhihang.doc.view.dto.Body body = new com.liuzhihang.doc.view.dto.Body();
        ParamPsiUtils.buildBodyParam(cls, code, null, body, new java.util.HashMap<>(), false);
        assertEquals("10000", body.getChildList().getFirst().getExample());
        java.util.Map<String, Object> json = ParamPsiUtils.getFieldsAndDefaultValue(cls, null);
        assertEquals(10000, json.get("code"));
        assertTrue(json.containsKey("request_id"));
        assertFalse(json.containsKey("REQUEST_ID"));
    }

    @Test
    public void evaluatesArithmeticAndPreservesPrimitiveTypes() {
        PsiField value = field("long code = SUCCESS + 2;");
        assertEquals(10002L, SpringPsiUtils.getDefaultValue(value, value.getType()));
        value = field("boolean code = SUCCESS == 10000;");
        assertEquals(true, SpringPsiUtils.getDefaultValue(value, value.getType()));
        value = field("char code = 'x';");
        assertEquals('x', SpringPsiUtils.getDefaultValue(value, value.getType()));
    }

    @Test
    public void decodesStringLiteralsWithoutRemovingEmbeddedQuotes() {
        PsiField value = field("String code = \"\\\"quoted\\\"\";");
        assertEquals("\"quoted\"", SpringPsiUtils.getDefaultValue(value, value.getType()));
        assertEquals("\"quoted\"", DocViewUtils.fieldExample(value));
    }

    @Test
    public void fallsBackForUnresolvedExpressionsAndKeepsCommentExamplePriority() {
        assertEquals("Missing.VALUE", DocViewUtils.fieldExample(field("int code = Missing.VALUE;")));
        assertEquals("123", DocViewUtils.fieldExample(field("/** @value 123 */ int code = SUCCESS;")));
    }

    @Test
    public void usesFieldNameForEmptyOrUnresolvedJsonProperty() {
        for (String expression : new String[]{"\"\"", "Missing.VALUE"}) {
            PsiClass cls = javaClass(annotatedSource(
                    "class Response { @JsonProperty(" + expression + ") String requestId; }"));
            assertEquals("requestId", DocViewUtils.fieldName(cls.getFields()[0], false));
        }
    }

    @Test
    public void keepsNonFiniteConstantsSerializableInJsonExamples() {
        PsiClass cls = javaClass("class Response { double code = 1.0 / 0.0; }");
        java.util.Map<String, Object> json = ParamPsiUtils.getFieldsAndDefaultValue(cls, null);
        assertEquals("1.0 / 0.0", json.get("code"));
        assertTrue(GsonFormatUtil.gsonFormat(json).contains("1.0 / 0.0"));
    }
}
