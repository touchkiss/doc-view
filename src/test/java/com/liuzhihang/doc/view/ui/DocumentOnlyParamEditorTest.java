package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.ui.treeview.ParamTreeTableView;

import javax.swing.JButton;
import javax.swing.JTextField;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import javax.swing.tree.DefaultMutableTreeNode;
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

/** Ordinary draft edits and explicit per-row source writes have independent behavior. */
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

    public void testCopiesDraftEditsWithoutChangingBusinessSource() throws Exception {
        myFixture.addClass("package io.swagger.v3.oas.annotations.media; public @interface Schema { String description(); }");
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("ReadOnlyEntity.java", """
                import io.swagger.v3.oas.annotations.media.Schema;
                class ReadOnlyEntity {
                    /** Business source description. */
                    @Schema(description = "Published description.") String id;
                }
                """);
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
            assertFalse("The draft toolbar must not confirm any pending edits: " + text,
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
            assertTrue(table.editCellAt(0, 3));
            ((JTextField) table.getEditorComponent()).setText("Only the generated document changes");
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

    public void testConfirmedCellEditWritesOnlyThatFieldAndRejectsStaleSource() throws Exception {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("Entity.java", """
                class Entity {
                    /** Original id. */
                    String id;
                    /** Original region. */
                    String re<caret>gion;
                }
                """);
        AtomicReference<ParamDocEditorForm> reference = new AtomicReference<>();
        EdtTestUtil.runInEdtAndWait(() -> reference.set(
                new ParamDocEditorForm(getProject(), file, myFixture.getEditor(), file.getClasses()[0])));
        var form = reference.get();
        EdtTestUtil.runInEdtAndWait(() -> {
            PlatformTestUtil.waitWithEventsDispatching("Doc Editor fields loaded", () -> field(form, "tableView") != null, 10);
            var table = (ParamTreeTableView) field(form, "tableView");
            assertEquals(6, table.getColumnCount());
            assertEquals("region", table.getValueAt(table.getSelectedRow(), 0));
            assertTrue(((JButton) field(form, "classCommentsButton")).isEnabled());
            assertTrue(table.isCellEditable(0, ParamTreeTableView.ACTIONS_COLUMN));
            assertTrue(table.editCellAt(0, 3));
            ((JTextField) table.getEditorComponent()).setText("Changed id.");
            assertTrue(file.getText().contains("Original id."));
            assertTrue(table.getCellEditor().stopCellEditing());
            assertTrue(file.getText().contains("Changed id."));
            assertTrue(file.getText().contains("Original region."));

            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                var document = myFixture.getEditor().getDocument();
                int start = document.getText().indexOf("Changed id.");
                document.replaceString(start, start + "Changed id.".length(), "Changed before editing.");
            });
            assertTrue(table.editCellAt(0, 3));
            assertEquals("Changed before editing.", ((JTextField) table.getEditorComponent()).getText());
            assertTrue(table.getCellEditor().stopCellEditing());
            assertTrue(file.getText().contains("Changed before editing."));

            assertTrue(table.editCellAt(0, 3));
            ((JTextField) table.getEditorComponent()).setText("Stale id.");
            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                var document = myFixture.getEditor().getDocument();
                int start = document.getText().indexOf("Changed before editing.");
                document.replaceString(start, start + "Changed before editing.".length(), "Externally updated id.");
            });
            assertTrue(table.getCellEditor().stopCellEditing());
            assertTrue(file.getText().contains("Externally updated id."));
            assertFalse(file.getText().contains("Stale id."));
        });
    }

    public void testOpeningAndClosingMultilineCommentDoesNotChangeSource() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("Paragraph.java", """
                class Paragraph {
                    /**
                     * First line.
                     * Second line.
                     */
                    String description;
                }
                """);
        String original = file.getText();
        EdtTestUtil.runInEdtAndWait(() -> {
            var form = new ParamDocEditorForm(getProject(), file, myFixture.getEditor(), file.getClasses()[0]);
            PlatformTestUtil.waitWithEventsDispatching("Doc Editor fields loaded", () -> field(form, "tableView") != null, 10);
            var table = (ParamTreeTableView) field(form, "tableView");
            assertTrue(table.editCellAt(0, ParamTreeTableView.SOURCE_COMMENT_COLUMN));
            assertTrue(table.getCellEditor().stopCellEditing());
            assertEquals(original, file.getText());
        });
    }

    public void testSupplementIsReadOnlyAndEditorStartsWithLocalComment() {
        myFixture.addClass("package jakarta.validation.constraints; public @interface Min { long value(); }");
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("DescriptionEntity.java", """
                import jakarta.validation.constraints.Min;
                class DescriptionEntity {
                    /** Existing amount. */
                    @Min(3) int amount;
                    @Min(7) int total;
                }
                """);
        EdtTestUtil.runInEdtAndWait(() -> {
            var form = new ParamDocEditorForm(getProject(), file, myFixture.getEditor(), file.getClasses()[0]);
            PlatformTestUtil.waitWithEventsDispatching("Doc Editor fields loaded", () -> field(form, "tableView") != null, 10);
            var table = (ParamTreeTableView) field(form, "tableView");
            assertEquals("Existing amount.", table.getValueAt(0, 3));
            assertTrue(table.getValueAt(0, 4).toString().contains("最小值:3"));
            assertFalse(table.isCellEditable(0, 4));
            assertTrue(table.editCellAt(1, 3));
            var editor = (JTextField) table.getEditorComponent();
            assertEquals("", editor.getText());
            editor.setText("New total.");
            assertTrue(table.getCellEditor().stopCellEditing());
            assertTrue(file.getText().contains("New total."));
            assertFalse(file.getText().contains("最小值"));
            assertTrue(table.getValueAt(1, 4).toString().contains("最小值:7"));
        });
    }

    public void testInlineButtonsActOnTheirOwnRowAndDisableSyntheticRows() {
        EdtTestUtil.runInEdtAndWait(() -> {
            var root = new DefaultMutableTreeNode();
            var writableRow = new DocViewParamData();
            writableRow.setName("writable");
            var syntheticRow = new DocViewParamData();
            syntheticRow.setName("synthetic");
            root.add(new DefaultMutableTreeNode(writableRow));
            root.add(new DefaultMutableTreeNode(syntheticRow));
            var model = new ListTreeTableModelOnColumns(root, ParamTreeTableView.COLUMN_INFOS);
            List<DocViewParamData> actions = new ArrayList<>();
            var table = new ParamTreeTableView(model, row -> row == writableRow, actions::add, row -> row::setDesc);

            int column = ParamTreeTableView.ACTIONS_COLUMN;
            assertFalse(table.isCellEditable(1, column));
            assertTrue(table.editCellAt(0, column));
            var editor = (Container) table.getEditorComponent();
            ((JButton) editor.getComponent(0)).doClick();
            assertEquals(List.of(writableRow), actions);
        });
    }

    public void testRegenerateButtonWritesSourceWithoutDialog() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("RegenerateEntity.java",
                "class RegenerateEntity { /** Original id. */ String regenCode; }");
        var settings = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
        String provider = settings.getTranslator();
        String previous = settings.getWordMap().put("regencode", "重新生成的标识");
        try {
            settings.setTranslator(Consts.LOCAL_DICTIONARY);
            EdtTestUtil.runInEdtAndWait(() -> {
                var form = new ParamDocEditorForm(getProject(), file, myFixture.getEditor(), file.getClasses()[0]);
                PlatformTestUtil.waitWithEventsDispatching("Doc Editor fields loaded", () -> field(form, "tableView") != null, 10);
                var table = (ParamTreeTableView) field(form, "tableView");
                assertTrue(table.editCellAt(0, ParamTreeTableView.ACTIONS_COLUMN));
                var editor = (Container) table.getEditorComponent();
                ((JButton) editor.getComponent(0)).doClick();
                PlatformTestUtil.waitWithEventsDispatching("Generated Javadoc written", () -> file.getText().contains("重新生成的标识"), 10);
                assertEquals("重新生成的标识", table.getValueAt(0, 3));
            });
        } finally {
            settings.setTranslator(provider);
            if (previous == null) {
                settings.getWordMap().remove("regencode");
            } else {
                settings.getWordMap().put("regencode", previous);
            }
        }
    }

    private static void collectActions(Component component, List<AnAction> actions) {
        if (component instanceof ActionToolbar toolbar) {
            actions.addAll(List.of(toolbar.getActionGroup().getChildren(null)));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectActions(child, actions);
            }
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
