package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

/** Resolves one invocation. A project-tree selection never inherits the active editor's caret. */
record ActionSelection(@Nullable PsiElement element, @Nullable Editor editor) {
    static ActionSelection from(AnActionEvent event) {
        if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace())
                || ToolWindowId.PROJECT_VIEW.equals(event.getData(PlatformDataKeys.TOOL_WINDOW_ID))) {
            return new ActionSelection(projectSelection(event), null);
        }
        var editor = event.getData(CommonDataKeys.EDITOR);
        var file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor != null && file == null) {
            return new ActionSelection(event.getData(CommonDataKeys.PSI_ELEMENT), editor);
        }
        if (editor != null && editor.getDocument().equals(file.getViewProvider().getDocument())) {
            int offset = Math.min(editor.getCaretModel().getOffset(), file.getTextLength() - 1);
            return new ActionSelection(offset < 0 ? file : file.findElementAt(offset), editor);
        }
        return new ActionSelection(projectSelection(event), null);
    }

    private static PsiElement projectSelection(AnActionEvent event) {
        var elements = event.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
        var files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if ((elements != null && elements.length > 1) || (files != null && files.length > 1)) {
            return null;
        }
        PsiElement selection = elements != null && elements.length == 1
                ? elements[0] : event.getData(CommonDataKeys.PSI_ELEMENT);
        var selectedFile = selection instanceof PsiFileSystemItem item ? item.getVirtualFile() : null;
        if (selectedFile == null && selection != null && selection.getContainingFile() != null) {
            selectedFile = selection.getContainingFile().getVirtualFile();
        }
        if (files != null && files.length == 1 && !files[0].equals(selectedFile) && event.getProject() != null) {
            var manager = PsiManager.getInstance(event.getProject());
            selection = files[0].isDirectory() ? manager.findDirectory(files[0]) : manager.findFile(files[0]);
        }
        return selection;
    }

    /** Class-oriented commands resolve a file to its named class; whole-file commands retain the file. */
    @Nullable PsiElement declaration() {
        if (element instanceof PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length == 1) {
                return classes[0];
            }
            for (PsiClass candidate : classes) {
                if ((candidate.getName() + ".java").equals(javaFile.getName())) {
                    return candidate;
                }
            }
        }
        return element;
    }

    @Nullable PsiFile file() {
        return element == null ? null : element.getContainingFile();
    }
}
