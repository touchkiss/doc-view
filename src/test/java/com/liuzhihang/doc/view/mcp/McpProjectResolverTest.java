package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class McpProjectResolverTest {

    @Test
    public void resolvesAnOpenProjectWithAnExactBasePath() {
        Path basePath = Path.of("build", "open-project").toAbsolutePath().normalize();
        Project project = projectAt(basePath.toString());

        Project resolved = new McpProjectResolver(() -> new Project[]{project}).resolve(basePath.toString());

        assertSame(project, resolved);
    }

    @Test
    public void normalizesTrailingSeparatorsBeforeMatchingAnOpenProject() {
        Path basePath = Path.of("build", "open-project").toAbsolutePath().normalize();
        Project project = projectAt(basePath.toString());

        Project resolved = new McpProjectResolver(() -> new Project[]{project})
                .resolve(basePath + File.separator);

        assertSame(project, resolved);
    }

    @Test
    public void rejectsAPathThatDoesNotBelongToAnOpenProject() {
        Path openPath = Path.of("build", "open-project").toAbsolutePath().normalize();
        Path closedPath = Path.of("build", "closed-project").toAbsolutePath().normalize();

        assertProjectNotOpen(() -> new McpProjectResolver(() -> new Project[]{projectAt(openPath.toString())})
                .resolve(closedPath.toString()));
    }

    @Test
    public void rejectsNullAndBlankProjectPaths() {
        McpProjectResolver resolver = new McpProjectResolver(() -> new Project[0]);

        assertProjectNotOpen(() -> resolver.resolve(null));
        assertProjectNotOpen(() -> resolver.resolve(""));
        assertProjectNotOpen(() -> resolver.resolve("   "));
    }

    @Test
    public void onlyReadsTheOpenProjectListAndNeverOpensADirectory() {
        AtomicBoolean directoryOpened = new AtomicBoolean();
        AtomicInteger openProjectLookups = new AtomicInteger();
        Path basePath = Path.of("build", "open-project").toAbsolutePath().normalize();
        Supplier<Project[]> openProjects = () -> {
            openProjectLookups.incrementAndGet();
            return new Project[]{projectAt(basePath.toString())};
        };

        Project resolved = new McpProjectResolver(openProjects).resolve(basePath.toString());

        assertEquals(basePath.toString(), resolved.getBasePath());
        assertEquals(1, openProjectLookups.get());
        assertFalse(directoryOpened.get());
    }

    private static Project projectAt(String basePath) {
        return (Project) Proxy.newProxyInstance(
                McpProjectResolverTest.class.getClassLoader(),
                new Class<?>[]{Project.class},
                (proxy, method, arguments) -> {
                    if ("getBasePath".equals(method.getName())) {
                        return basePath;
                    }
                    if ("toString".equals(method.getName())) {
                        return "Project(" + basePath + ')';
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static void assertProjectNotOpen(Runnable action) {
        try {
            action.run();
            fail("Expected PROJECT_NOT_OPEN");
        } catch (McpException exception) {
            assertEquals(McpException.Code.PROJECT_NOT_OPEN, exception.getCode());
        }
    }
}
