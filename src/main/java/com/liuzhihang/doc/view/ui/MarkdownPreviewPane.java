package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.utils.EditorUtils;
import lombok.extern.slf4j.Slf4j;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor;
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditorProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** Owns the two editors displaying one in-memory Markdown document. */
@Slf4j
final class MarkdownPreviewPane implements Disposable {
    private static final AtomicBoolean PREFER_RENDERED_PREVIEW = new AtomicBoolean(true);
    private final Project project;
    private final JPanel previewContentPanel;
    private final BiFunction<Project, VirtualFile, FileEditor> previewFactory;
    private FileEditor nativeMarkdownPreview;
    private EditorEx markdownEditor;

    MarkdownPreviewPane(Project project, JPanel panel, BiFunction<Project, VirtualFile, FileEditor> factory) {
        this.project = project;
        this.previewContentPanel = panel;
        this.previewFactory = factory;
    }

    void initialize(VirtualFile file, Document document) {
        initializeSource(document);
        initNativePreview(file);
    }

    boolean isRendered() {
        return nativeMarkdownPreview != null && PREFER_RENDERED_PREVIEW.get();
    }

    void selectRendered(boolean state) {
        if (state && nativeMarkdownPreview == null) {
            DocViewNotification.notifyInfo(project, DocViewBundle.message("notify.markdown.preview.unavailable"));
            return;
        }
        PREFER_RENDERED_PREVIEW.set(state);
        showSelectedView();
    }

    @Override
    public void dispose() {
        discardNativePreview();
        if (markdownEditor != null && !markdownEditor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(markdownEditor);
        }
        markdownEditor = null;
    }

    private void initializeSource(Document markdownDocument) {

        markdownEditor = (EditorEx) EditorFactory.getInstance().createViewer(markdownDocument, project, EditorKind.UNTYPED);

        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("md");
        markdownEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));

        EditorUtils.renderMarkdownEditor(markdownEditor);

        markdownEditor.setBorder(JBUI.Borders.emptyLeft(5));
    }

    static LightVirtualFile createPreviewFile(PsiClass psiClass) {
        FileType type = FileTypeManager.getInstance().getFileTypeByExtension("md");
        VirtualFile folder = ReadAction.computeBlocking(() -> {
            var psiFile = psiClass.getContainingFile();
            var source = psiFile == null ? null : psiFile.getVirtualFile();
            return source == null ? null : source.getParent();
        });
        // The source folder supplies the base for relative images and links without a disk write.
        return new LightVirtualFile("Doc View.md", type, "") {
            @Override
            public VirtualFile getParent() {
                return folder;
            }

            @Override
            public String getPath() {
                return folder == null ? super.getPath() : folder.getPath() + "/" + getName();
            }
        };
    }

    // Keep the version-specific Markdown plugin entry points together.
    static @Nullable FileEditor createNativeMarkdownPreview(Project project, VirtualFile file) {
        return MarkdownHtmlPanelProvider.hasAvailableProviders()
                ? new MarkdownPreviewFileEditorProvider().createEditor(project, file)
                : null;
    }

    private void initNativePreview(VirtualFile file) {
        try {
            nativeMarkdownPreview = previewFactory.apply(project, file);
            if (nativeMarkdownPreview instanceof MarkdownPreviewFileEditor preview) {
                // The native renderer needs the editor's caret and document to submit its first render.
                preview.setMainEditor(markdownEditor);
            }
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (RuntimeException | LinkageError failure) {
            disableNativePreview(failure);
        }
    }

    private void discardNativePreview() {
        FileEditor preview = nativeMarkdownPreview;
        nativeMarkdownPreview = null;
        if (preview != null) {
            try {
                Disposer.dispose(preview);
            } catch (RuntimeException failure) {
                log.warn("Could not dispose the native Markdown preview", failure);
            }
        }
    }

    private void disableNativePreview(Throwable failure) {
        log.warn("Doc View native Markdown preview is unavailable; using Markdown source", failure);
        discardNativePreview();
    }

    void showSelectedView() {
        previewContentPanel.removeAll();
        if (PREFER_RENDERED_PREVIEW.get() && nativeMarkdownPreview != null) {
            try {
                previewContentPanel.add(nativeMarkdownPreview.getComponent(), BorderLayout.CENTER);
            } catch (ProcessCanceledException canceled) {
                throw canceled;
            } catch (RuntimeException | LinkageError failure) {
                disableNativePreview(failure);
            }
        }
        if (previewContentPanel.getComponentCount() == 0) {
            previewContentPanel.add(ScrollPaneFactory.createScrollPane(markdownEditor.getComponent()), BorderLayout.CENTER);
        }
        previewContentPanel.revalidate();
        previewContentPanel.repaint();
    }
}
