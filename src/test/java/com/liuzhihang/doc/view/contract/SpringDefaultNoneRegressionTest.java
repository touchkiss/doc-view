package com.liuzhihang.doc.view.contract;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.utils.CurlUtils;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Uses Spring's complete, real DEFAULT_NONE value instead of a shortened fake annotation default. */
public final class SpringDefaultNoneRegressionTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n";
    private Path libraryDirectory;
    private Library library;

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installBinarySpringAnnotations();
        assertEquals("Fixture must use the complete Spring sentinel", 16, DEFAULT_NONE.length());
        assertEquals("No annotation sources may be attached", 0, library.getFiles(OrderRootType.SOURCES).length);
        assertBinaryDefault("org.springframework.web.bind.annotation.RequestParam");
        assertBinaryDefault("org.springframework.web.bind.annotation.RequestHeader");
        assertTrue(binaryClass("org.springframework.web.bind.annotation.ValueConstants") instanceof PsiCompiledElement);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (library != null) PsiTestUtil.removeLibrary(getModule(), library);
        } finally {
            try {
                super.tearDown();
            } finally {
                if (libraryDirectory != null) FileUtil.delete(libraryDirectory.toFile());
            }
        }
    }

    public void testExactUserExampleHasRequiredBizKeyAndNoSentinelInAnyOutput() {
        PsiClass controller = myFixture.addClass("""
                import org.springframework.web.bind.annotation.*;
                @RestController public class EventBusController {
                    @GetMapping("event_bus/by_biz_key")
                    public String eventBusByBizKey(@RequestParam("bizKey") String bizKey,
                        @RequestParam(value="limit", required=false) Integer limit,
                        @RequestParam(value="withPayload", defaultValue="false") boolean withPayload) { return ""; }
                }
                """);
        DocView doc = new SpringDocViewServiceImpl().buildClassMethodDoc(controller, controller.getMethods()[0]);
        List<Param> params = doc.getReqParamList();
        assertEquals(3, params.size());
        assertTrue("bizKey has no real default and is required", params.get(0).getRequired());
        assertFalse(params.get(1).getRequired());
        assertFalse(params.get(2).getRequired());
        assertNull(params.get(0).getExample());
        assertNull(params.get(1).getExample());
        assertEquals("false", params.get(2).getExample());
        assertFalse(DocViewData.markdownText(getProject(), doc).contains("\uE000"));
        assertFalse(CurlUtils.build(doc).contains("\uE000"));
        assertTrue(DocViewData.markdownText(getProject(), doc).contains("|bizKey|String|Y|"));
        assertTrue(DocViewData.markdownText(getProject(), doc).contains("|withPayload|boolean|N|false|"));
    }

    public void testExplicitSentinelIsAbsentButEmptyAndConstantDefaultsAreReal() {
        PsiClass controller = myFixture.addClass("""
                import org.springframework.web.bind.annotation.*;
                public class ExplicitDefaults {
                    static final String FALSE = "false";
                    @GetMapping("/defaults") void defaults(
                        @RequestParam(defaultValue=ValueConstants.DEFAULT_NONE) String absent,
                        @RequestParam(defaultValue="") String empty,
                        @RequestParam(defaultValue=FALSE) boolean flag) {}
                }
                """);
        assertEquals("Controller constants must resolve with the fixture JDK", "false",
                JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper()
                        .computeConstantExpression(controller.findFieldByName("FALSE", false).getInitializer()));
        List<Param> params = SpringPsiUtils.buildFormParam(controller.getMethods()[0]);
        assertTrue(params.get(0).getRequired());
        assertNull(params.get(0).getExample());
        assertEquals("", params.get(0).getDesc());
        assertFalse(params.get(1).getRequired());
        assertEquals("", params.get(1).getExample());
        assertFalse(params.get(2).getRequired());
        assertEquals("false", params.get(2).getExample());
    }

    public void testRequestHeadersUseTheSameAbsentDefaultSemantics() {
        PsiClass controller = myFixture.addClass("""
                import org.springframework.web.bind.annotation.*;
                public class HeaderDefaults {
                    @GetMapping("/headers") void headers(@RequestHeader("X-Biz-Key") String bizKey,
                        @RequestHeader(value="X-Payload", defaultValue="false") String withPayload) {}
                }
                """);
        PsiMethod method = controller.getMethods()[0];
        var headers = SpringPsiUtils.buildHeader(method);
        var key = headers.stream().filter(h -> "X-Biz-Key".equals(h.getName())).findFirst().orElseThrow();
        assertTrue(key.getRequired());
        assertNull(key.getValue());
        assertTrue(headers.stream().anyMatch(h -> "X-Payload".equals(h.getName()) && !h.getRequired() && "false".equals(h.getValue())));
    }

    private void assertBinaryDefault(String name) {
        PsiClass annotation = binaryClass(name);
        assertTrue("Binding annotation must resolve to compiled PSI", annotation instanceof PsiCompiledElement);
        PsiAnnotationMethod member = (PsiAnnotationMethod) annotation.findMethodsByName("defaultValue", false)[0];
        Object value = JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper()
                .computeConstantExpression(member.getDefaultValue());
        assertEquals("Compiled defaultValue must expose all 16 sentinel characters", DEFAULT_NONE, value);
    }

    private PsiClass binaryClass(String name) {
        PsiClass result = JavaPsiFacade.getInstance(getProject()).findClass(name,
                GlobalSearchScope.allScope(getProject()));
        assertNotNull("Missing binary binding annotation " + name, result);
        return result;
    }

    private void installBinarySpringAnnotations() throws Exception {
        libraryDirectory = Files.createTempDirectory("doc-view-spring-default-none-");
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), libraryDirectory.toString());
        Path sources = Files.createDirectories(libraryDirectory.resolve("sources"));
        Path classes = Files.createDirectories(libraryDirectory.resolve("classes"));
        Map<String, String> files = new LinkedHashMap<>();
        files.put("ValueConstants", """
                package org.springframework.web.bind.annotation;
                public interface ValueConstants {
                    String DEFAULT_NONE = "\\n\\t\\t\\n\\t\\t\\n\\uE000\\uE001\\uE002\\n\\t\\t\\t\\t\\n";
                }
                """);
        for (String annotation : List.of("RequestParam", "RequestHeader")) {
            files.put(annotation, """
                    package org.springframework.web.bind.annotation;
                    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
                    public @interface %s {
                        String name() default "";
                        String value() default "";
                        boolean required() default true;
                        String defaultValue() default ValueConstants.DEFAULT_NONE;
                    }
                    """.formatted(annotation));
        }
        files.put("RestController", "package org.springframework.web.bind.annotation; public @interface RestController {}");
        for (String annotation : List.of("RequestMapping", "GetMapping")) {
            files.put(annotation, "package org.springframework.web.bind.annotation; public @interface " + annotation
                    + " { String[] value() default {}; }");
        }
        List<String> arguments = new ArrayList<>(List.of("-proc:none", "--release", "21", "-encoding", "UTF-8",
                "-d", classes.toString()));
        for (Map.Entry<String, String> source : files.entrySet()) {
            Path file = sources.resolve("org/springframework/web/bind/annotation/" + source.getKey() + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            arguments.add(file.toString());
        }
        compile(arguments);
        Path jar = libraryDirectory.resolve("spring-web-bindings.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
             var paths = Files.walk(classes)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        VirtualFile jarRoot = JarFileSystem.getInstance().refreshAndFindFileByPath(jar + "!/");
        assertNotNull("Missing compiled Spring bindings JAR", jarRoot);
        library = PsiTestUtil.newLibrary("spring-default-none-bindings").classesRoot(jarRoot).addTo(getModule());
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }

    private void compile(List<String> arguments) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler != null) {
            assertEquals("Binding annotation compilation failed", 0,
                    compiler.run(null, null, null, arguments.toArray(String[]::new)));
            return;
        }
        String executable = System.getProperty("os.name").startsWith("Windows") ? "javac.exe" : "javac";
        String configuredJdk = System.getenv("JAVA_HOME");
        if (configuredJdk != null && Files.isExecutable(Path.of(configuredJdk, "bin", executable))) {
            executable = Path.of(configuredJdk, "bin", executable).toString();
        }
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(arguments);
        Path diagnostics = libraryDirectory.resolve("compiler.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(diagnostics.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Binding annotation compilation timed out");
        }
        assertEquals("Binding annotation compilation failed: " + Files.readString(diagnostics), 0, process.exitValue());
    }

}
