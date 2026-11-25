package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.UpdateSession;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AbstractActionReuseTest extends LightJavaCodeInsightFixtureTestCase {
    private final RecordingAction action = new RecordingAction();

    public void testMissingEditorDoesNotDispatchPreviousTarget() {
        myFixture.configureByText("Previous.java", "class Previous { void <caret>oldMethod() {} }");
        invoke(ActionPlaces.EDITOR_POPUP, editorContext());
        invoke(ActionPlaces.UNKNOWN, key -> CommonDataKeys.PROJECT.is(key) ? getProject() : null);
        assertSize(1, action.targets);
        assertEquals("oldMethod", action.targets.getFirst().method().getName());
    }

    public void testUnsupportedEnumDoesNotDispatchPreviousTarget() {
        myFixture.configureByText("Previous.java", "class Previous { void <caret>oldMethod() {} }");
        invoke(ActionPlaces.EDITOR_POPUP, editorContext());
        myFixture.configureByText("Unsupported.java", "enum Unsupported { <caret>VALUE }");
        invoke(ActionPlaces.EDITOR_POPUP, editorContext());
        assertSize(1, action.targets);
    }

    public void testProjectSelectionWinsOverUnrelatedEditorAndSelectedText() {
        var selected = myFixture.addClass("class Selected { void wanted() {} }");
        myFixture.configureByText("Other.java", "class Other { void <caret>unrelated() {} }");
        myFixture.getEditor().getSelectionModel().setSelection(0, 5);
        var selectedMethod = selected.findMethodsByName("wanted", false)[0];
        DataContext data = key -> CommonDataKeys.PSI_ELEMENT.is(key) ? selectedMethod : editorContext().getData(key);
        invoke(ActionPlaces.PROJECT_VIEW_POPUP, data);
        var target = assertOneElement(action.targets);
        assertSame(selectedMethod, target.method());
        assertSame(selected, target.type());
        assertNull(target.editor());
        var generate = new GenerateJavadocAction();
        var event = AnActionEvent.createFromAnAction(generate, null, ActionPlaces.PROJECT_VIEW_POPUP, data);
        assertSame(selectedMethod, SourceCommentActionSupport.context(event));
        generate.update(event);
        assertEquals("生成注释", event.getPresentation().getText());
    }

    public void testProjectMultiSelectionDoesNotUseEditorOrFirstClass() {
        var first = myFixture.addClass("class First {}");
        var second = myFixture.addClass("class Second {}");
        myFixture.configureByText("Other.java", "class Other { void <caret>unrelated() {} }");
        invoke(ActionPlaces.PROJECT_VIEW_POPUP, key -> {
            if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(key)) {
                return new PsiElement[]{first, second};
            }
            if (CommonDataKeys.PSI_ELEMENT.is(key)) {
                return first;
            }
            return editorContext().getData(key);
        });
        assertEmpty(action.targets);
    }

    public void testProjectTreeShortcutUsesSelectedDeclaration() {
        var selected = myFixture.addClass("class Sidebar {}");
        myFixture.configureByText("Other.java", "class Other { void <caret>unrelated() {} }");
        invoke(ActionPlaces.KEYBOARD_SHORTCUT, key -> {
            if (PlatformDataKeys.TOOL_WINDOW_ID.is(key)) {
                return ToolWindowId.PROJECT_VIEW;
            }
            if (CommonDataKeys.PSI_ELEMENT.is(key)) {
                return selected;
            }
            return editorContext().getData(key);
        });
        assertSame(selected, assertOneElement(action.targets).type());
    }

    public void testLocalClassFieldDoesNotInheritEnclosingMethod() {
        myFixture.configureByText("Outer.java", "class Outer { void outer() { class Inner { int <caret>field; } } }");
        invoke(ActionPlaces.EDITOR_POPUP, editorContext());
        var target = assertOneElement(action.targets);
        assertEquals("Inner", target.type().getName());
        assertNull(target.method());
    }

    public void testWholeFileMemberCommandKeepsAllTopLevelClasses() {
        var file = myFixture.configureByText("Primary.java", "class Primary {} class Secondary {}");
        var event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.PROJECT_VIEW_POPUP,
                key -> CommonDataKeys.PSI_ELEMENT.is(key) ? file : editorContext().getData(key));
        assertSame(file, SourceCommentActionSupport.context(event));
        assertEquals("Primary", ((PsiClass) ActionSelection.from(event).declaration()).getName());
    }

    public void testOneRootGroupContainsAllDocumentCommandsInBothPopupMenus() {
        var manager = ActionManager.getInstance();
        var root = (ActionGroup) manager.getAction("liuzhihang.doc");
        assertTrue(root.isPopup());
        assertEquals(List.of("liuzhihang.doc.view", "liuzhihang.doc.editor", "liuzhihang.doc.copy.rest",
                        "liuzhihang.doc.export", "liuzhihang.doc.upload", "liuzhihang.doc.sourceComments"),
                Arrays.stream(root.getChildren(null)).map(manager::getId).toList());
        for (String menu : List.of("EditorPopupMenu", "ProjectViewPopupMenu")) {
            var group = (ActionGroup) manager.getAction(menu);
            var children = Arrays.stream(group.getChildren(null)).map(manager::getId).toList();
            assertEquals(1L, children.stream().filter("liuzhihang.doc"::equals).count());
            assertFalse(children.contains("liuzhihang.doc.sourceComments"));
        }
    }

    public void testPopupGroupsHideWhenTheirLastCommandIsUnavailable() {
        var manager = ActionManager.getInstance();
        var command = manager.getAction("liuzhihang.doc.view");
        for (String id : List.of("liuzhihang.doc", "liuzhihang.doc.sourceComments", "liuzhihang.doc.copy.rest",
                "liuzhihang.doc.upload", "liuzhihang.doc.tool.window.toolbar.action.export.menu",
                "liuzhihang.doc.tool.window.catalog.action.upload")) {
            var group = (ActionGroup) manager.getAction(id);
            var event = AnActionEvent.createFromAnAction(group, null, ActionPlaces.EDITOR_POPUP, DataContext.EMPTY_CONTEXT);
            var childState = new Presentation();
            event.setUpdateSession(new UpdateSession() {
                @Override
                public Iterable<? extends AnAction> expandedChildren(ActionGroup requestedGroup) {
                    return List.of(command);
                }

                @Override
                public Presentation presentation(AnAction requestedAction) {
                    return childState;
                }
            });
            childState.setEnabled(false);
            group.update(event);
            assertFalse(id + " should disappear when all commands are disabled", event.getPresentation().isVisible());
            childState.setEnabledAndVisible(true);
            group.update(event);
            assertTrue(id + " should return when a command is available", event.getPresentation().isEnabledAndVisible());
            childState.setVisible(false);
            group.update(event);
            assertFalse(id + " should disappear when all commands are hidden", event.getPresentation().isVisible());
        }
    }

    private DataContext editorContext() {
        return key -> {
            if (CommonDataKeys.PROJECT.is(key)) {
                return getProject();
            }
            if (CommonDataKeys.PSI_FILE.is(key)) {
                return myFixture.getFile();
            }
            if (CommonDataKeys.EDITOR.is(key)) {
                return myFixture.getEditor();
            }
            return null;
        };
    }

    private void invoke(String place, DataContext context) {
        action.actionPerformed(AnActionEvent.createFromAnAction(action, null, place, context));
    }

    private static final class RecordingAction extends AbstractAction {
        private final List<DocumentTarget> targets = new ArrayList<>();

        @Override
        protected void perform(DocumentTarget target) {
            targets.add(target);
        }
    }
}
