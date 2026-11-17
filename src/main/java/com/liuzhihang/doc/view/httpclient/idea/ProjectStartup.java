package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

public final class ProjectStartup implements StartupActivity.DumbAware {
    @Override public void runActivity(Project project) { RuntimeInstaller.install(); }
}
