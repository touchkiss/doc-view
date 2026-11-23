package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public final class SourceDescriptionEdtTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    public void testApplyFromEdtWithoutImplicitReadAccess() throws Throwable {
        var file = EdtTestUtil.runInEdtAndGet(() -> (PsiJavaFile) myFixture.configureByText("Entity.java",
                "class Entity { /** Old. */ String id; }"));
        var draft = EdtTestUtil.runInEdtAndGet(() -> SourceDescriptionService.capture(
                SourceDescriptionService.fieldTarget("id", "Old.", file.getClasses()[0].getFields()[0])));
        assertNotNull(draft);
        EdtTestUtil.runInEdtAndWait(() -> {
            var app = ApplicationManager.getApplication();
            assertTrue(app.isDispatchThread());
            assertFalse("Reproduce a Swing event without the implicit lock", app.isReadAccessAllowed());
            assertTrue(SourceDescriptionService.apply(draft, "New."));
        }, false);
        assertTrue(EdtTestUtil.runInEdtAndGet(() -> file.getText().contains("New.")));
    }
}
