package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/** Resolves a request path only against projects that are already open in the IDE. */
public final class McpProjectResolver {

    private final Supplier<Project[]> openProjects;

    public McpProjectResolver() {
        this(() -> ProjectManager.getInstance().getOpenProjects());
    }

    McpProjectResolver(Supplier<Project[]> openProjects) {
        this.openProjects = Objects.requireNonNull(openProjects, "openProjects");
    }

    public Project resolve(String projectPath) {
        Path normalizedRequestPath = normalize(projectPath);
        if (normalizedRequestPath == null) {
            throw projectNotOpen(projectPath);
        }

        return Arrays.stream(openProjects.get())
                .filter(Objects::nonNull)
                .filter(project -> normalizedRequestPath.equals(normalize(project.getBasePath())))
                .findFirst()
                .orElseThrow(() -> projectNotOpen(projectPath));
    }

    private static Path normalize(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }
        try {
            return Path.of(projectPath).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private static McpException projectNotOpen(String projectPath) {
        return new McpException(McpException.Code.PROJECT_NOT_OPEN,
                "Project is not open: " + (projectPath == null ? "<null>" : projectPath));
    }
}
