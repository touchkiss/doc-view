package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Starts the application-level MCP service without delaying project startup. */
public final class McpStartupActivity implements StartupActivity, DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                ApplicationManager.getApplication().getService(McpServerService.class).start());
    }
}
