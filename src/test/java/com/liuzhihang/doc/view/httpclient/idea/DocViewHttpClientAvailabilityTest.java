package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.liuzhihang.doc.view.config.ApplicationSettings;
import com.liuzhihang.doc.view.service.KdocSupport;
import com.liuzhihang.doc.view.service.ProtoCommentSupport;

/** Runs with and without the optional native plugin; never links against HTTP Client API classes. */
public class DocViewHttpClientAvailabilityTest extends BasePlatformTestCase {
    public void testDocViewLoadsWithOptionalHttpClientState() {
        assertNotNull(PluginManagerCore.getPlugin(PluginId.getId("com.liuzhihang.doc-view")));
        assertTrue(PluginManagerCore.getPlugin(PluginId.getId("com.liuzhihang.doc-view")).isEnabled());
        assertNotNull(ApplicationSettings.getInstance());
        assertNotNull(ActionManager.getInstance().getAction("liuzhihang.doc.view"));
        assertNotNull(ActionManager.getInstance().getAction("liuzhihang.doc.copy.curl"));
        var http = PluginManagerCore.getPlugin(PluginId.getId("com.jetbrains.restClient"));
        boolean enabled = http != null && http.isEnabled();
        if (Boolean.getBoolean("docview.test.http.disabled")) {
            var app = ApplicationManager.getApplication();
            assertNull(app.getService(KdocSupport.class));
            assertNull(app.getService(ProtoCommentSupport.class));
            assertFalse("Test sandbox must actually disable HTTP Client", enabled);
            assertNull(ActionManager.getInstance().getAction("HTTPClientConvertToCurlAndCopy"));
        } else {
            assertTrue(enabled);
            assertEquals("com.liuzhihang.doc.view.httpclient.idea.Json5CopyAsCurlAction",
                ActionManager.getInstance().getAction("HTTPClientConvertToCurlAndCopy").getClass().getName());
        }
    }
}
