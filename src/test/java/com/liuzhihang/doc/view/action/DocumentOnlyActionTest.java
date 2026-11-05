package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.testsupport.LocalProjectJavaSources;

import java.nio.file.Files;
import java.nio.file.Path;

/** Documentation actions parse pending editor changes without persisting business source files. */
public final class DocumentOnlyActionTest extends LightJavaCodeInsightFixtureTestCase {
    private LocalProjectJavaSources sources;
    private Document document;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sources = new LocalProjectJavaSources(getProject(), getModule());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (document != null) {
                EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().reloadFromDisk(document));
            }
            if (sources != null) sources.cleanup();
        } finally {
            super.tearDown();
        }
    }

    public void testPendingEditorContentIsCommittedToPsiWithoutBeingSaved() throws Exception {
        PsiClass owner = sources.addClass("readonly/UnsavedTarget.java",
                "package readonly; public class UnsavedTarget { String diskValue; }");
        var file = owner.getContainingFile().getVirtualFile();
        myFixture.configureFromExistingVirtualFile(file);
        Path path = Path.of(file.getPath());
        String diskSource = Files.readString(path);
        document = myFixture.getEditor().getDocument();
        EdtTestUtil.runInEdtAndWait(() -> {
            WriteCommandAction.runWriteCommandAction(getProject(), () ->
                    document.setText("package readonly; public class UnsavedTarget { String pendingValue; }"));
            // A real context action is invoked inside the selected class. The fixture
            // initially leaves the caret at offset 0, inside the package declaration.
            myFixture.getEditor().getCaretModel().moveToOffset(document.getText().indexOf("pendingValue"));
        });
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
        InspectableAction action = new InspectableAction();
        DataContext context = dataId -> {
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            return null;
        };
        EdtTestUtil.runInEdtAndWait(() -> action.actionPerformed(event(action, context)));

        assertEquals("Documentation must not write the pending edit to disk", diskSource, Files.readString(path));
        assertTrue("The user's document remains unsaved", FileDocumentManager.getInstance().isDocumentUnsaved(document));
        assertNotNull("The committed in-memory PSI must be available to documentation generation", action.target());
        assertNotNull("Generation must see the pending editor value", action.target().findFieldByName("pendingValue", false));
        assertNull(action.target().findFieldByName("diskValue", false));
    }

    public void testInvalidContextDoesNotSaveAnyPendingBusinessDocument() throws Exception {
        PsiClass owner = sources.addClass("readonly/InvalidContext.java",
                "package readonly; public class InvalidContext {}");
        var file = owner.getContainingFile().getVirtualFile();
        myFixture.configureFromExistingVirtualFile(file);
        Path path = Path.of(file.getPath());
        String diskSource = Files.readString(path);
        document = myFixture.getEditor().getDocument();
        EdtTestUtil.runInEdtAndWait(() -> WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getTextLength(), "\n// pending user edit\n")));
        InspectableAction action = new InspectableAction();
        EdtTestUtil.runInEdtAndWait(() -> action.actionPerformed(event(action, dataId -> null)));
        assertEquals("Invalid context must be rejected before any file persistence", diskSource, Files.readString(path));
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
        assertNull(action.target());
    }

    private static AnActionEvent event(AbstractAction action, DataContext context) {
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context);
    }

    private static final class InspectableAction extends AbstractAction {
        private PsiClass target() { return targetClass; }
    }
}
