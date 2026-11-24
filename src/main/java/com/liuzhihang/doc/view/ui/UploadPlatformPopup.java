package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.enums.UploadPlatform;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Consumer;

/** Platform chooser shared by the document preview and the tool window. */
public final class UploadPlatformPopup {
    private UploadPlatformPopup() {}

    public static void show(Project project, JComponent anchor, Consumer<DocViewUploadService> upload) {
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null, DocViewUploadService.UPLOAD_OPTIONS) {
            @Override
            public @NotNull String getTextFor(UploadPlatform value) {
                return DocViewBundle.message("action.upload.platform", value.getDisplayName());
            }

            @Override
            public @Nullable PopupStep<?> onChosen(UploadPlatform value, boolean finalChoice) {
                if (!project.isDisposed()) {
                    upload.accept(DocViewUploadService.getInstance(value));
                }
                return FINAL_CHOICE;
            }
        }).showUnderneathOf(anchor);
    }
}
