package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.ui.treeview.ParamTreeTableView;

import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Entity edits are document-only values, with no action that confirms changes into source. */
public final class DocumentOnlyParamEditorTest extends LightJavaCodeInsightFixtureTestCase {
    private Transferable originalClipboard;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        EdtTestUtil.runInEdtAndWait(() -> originalClipboard = CopyPasteManager.getInstance().getContents());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();
            EdtTestUtil.runInEdtAndWait(() -> CopyPasteManager.getInstance().setContents(
                    originalClipboard == null ? new StringSelection("") : originalClipboard));
        } finally {
            super.tearDown();
        }
    }

    public void testToolbarHasOnlyDocumentActionsAndCopiesEditsWithoutChangingBusinessSource() throws Exception {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("ReadOnlyEntity.java",
                "class ReadOnlyEntity { /** Business source description. */ String id; }");
        String originalSource = file.getText();
        AtomicBoolean closed = new AtomicBoolean();
        JBPopup popup = (JBPopup) Proxy.newProxyInstance(JBPopup.class.getClassLoader(), new Class<?>[]{JBPopup.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isDisposed" -> closed.get();
                    case "cancel" -> { closed.set(true); yield null; }
                    case "toString" -> "document-only-param-popup";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        AtomicReference<ParamDocEditorForm> reference = new AtomicReference<>();
        EdtTestUtil.runInEdtAndWait(() -> {
            ParamDocEditorForm form = new ParamDocEditorForm(getProject(), file, myFixture.getEditor(), file.getClasses()[0]);
            setField(form, "popup", popup);
            reference.set(form);
        });
        ParamDocEditorForm form = reference.get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (field(form, "tableView") == null && System.nanoTime() < deadline) {
            EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
            Thread.sleep(10);
        }
        assertNotNull(field(form, "tableView"));
        List<AnAction> actions = new ArrayList<>();
        EdtTestUtil.runInEdtAndWait(() -> collectActions((Component) field(form, "tailToolbarPanel"), actions));
        for (AnAction action : actions) {
            String text = action.getTemplatePresentation().getText();
            assertFalse("No confirm/write-back action may remain in the entity editor: " + text,
                    text != null && (text.contains("确认") || text.equalsIgnoreCase("Confirm")));
        }
        AnAction copyMarkdown = actions.stream().filter(a -> {
            String text = a.getTemplatePresentation().getText();
            return text != null && text.contains("Markdown");
        }).findFirst().orElseThrow();
        assertTrue("Copy JSON must remain available", actions.stream().anyMatch(a -> {
            String text = a.getTemplatePresentation().getText();
            return text != null && text.contains("JSON");
        }));
        EdtTestUtil.runInEdtAndWait(() -> {
            ParamTreeTableView table = (ParamTreeTableView) field(form, "tableView");
            table.setValueAt(true, 0, 2);
            table.setValueAt("Only the generated document changes", 0, 3);
            DataContext context = id -> CommonDataKeys.PROJECT.is(id) ? getProject() : null;
            copyMarkdown.actionPerformed(AnActionEvent.createFromAnAction(copyMarkdown, null, ActionPlaces.UNKNOWN, context));
        });
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!closed.get() && System.nanoTime() < deadline) {
            EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
            Thread.sleep(10);
        }
        assertTrue(closed.get());
        AtomicReference<String> copied = new AtomicReference<>();
        EdtTestUtil.runInEdtAndWait(() -> copied.set(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)));
        assertTrue(copied.get(), copied.get().contains("Only the generated document changes"));
        assertEquals("Editing and copying must not mutate JavaDoc or Swagger on the business class", originalSource, file.getText());
    }

    private static void collectActions(Component component, List<AnAction> actions) {
        if (component instanceof ActionToolbar toolbar) {
            actions.addAll(List.of(toolbar.getActionGroup().getChildren(null)));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collectActions(child, actions);
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
