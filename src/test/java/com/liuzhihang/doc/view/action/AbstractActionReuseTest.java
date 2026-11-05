package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/** Reusing a registered action must never reuse the previous target after validation fails. */
public final class AbstractActionReuseTest extends LightJavaCodeInsightFixtureTestCase {

    public void testMissingEditorClearsTargetFromPreviousInvocation() {
        PsiClass previous = myFixture.addClass("class PreviousActionTarget { void oldMethod() {} }");
        InspectableAction action = new InspectableAction();
        action.seed(previous, previous.findMethodsByName("oldMethod", false)[0]);
        DataContext invalid = dataId -> CommonDataKeys.PROJECT.is(dataId) ? getProject() : null;

        action.actionPerformed(event(action, invalid));

        assertNull("A failed action must not retain the preceding class", action.target());
        assertNull("A failed action must not retain the preceding method", action.method());
    }

    public void testUnsupportedEnumDoesNotLeaveNewInvalidTargetOrOldMethod() {
        PsiClass previous = myFixture.addClass("class PreviousEnumTarget { void oldMethod() {} }");
        InspectableAction action = new InspectableAction();
        action.seed(previous, previous.findMethodsByName("oldMethod", false)[0]);
        myFixture.configureByText("Unsupported.java", "enum Unsupported { <caret>VALUE }");
        DataContext invalid = dataId -> {
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            return null;
        };

        action.actionPerformed(event(action, invalid));

        assertNull("Rejected enum must not remain available to child actions", action.target());
        assertNull("A failed action must not retain the preceding method", action.method());
    }

    private static AnActionEvent event(AbstractAction action, DataContext context) {
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context);
    }

    private static final class InspectableAction extends AbstractAction {
        private void seed(PsiClass psiClass, PsiMethod psiMethod) {
            project = psiClass.getProject();
            targetClass = psiClass;
            targetMethod = psiMethod;
        }

        private PsiClass target() { return targetClass; }
        private PsiMethod method() { return targetMethod; }
    }
}
