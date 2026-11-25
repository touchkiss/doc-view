package com.liuzhihang.doc.view.ui;

import com.intellij.find.editorHeaderActions.Utils;
import icons.DocViewIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.ui.settings.SettingsConfigurable;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.SourceDescriptionService;
import com.liuzhihang.doc.view.service.gpt.ModelRequestException;
import com.liuzhihang.doc.view.ui.treeview.ParamTreeTableView;
import com.liuzhihang.doc.view.utils.DocViewBackgroundTasks;
import com.liuzhihang.doc.view.utils.GsonFormatUtil;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author liuzhihang
 * @date 2021/3/31 10:49
 */
public class ParamDocEditorForm {

    private final Project project;
    private final PsiClass psiClass;
    private final String entityName;
    private final SmartPsiElementPointer<PsiElement> initialField;
    private JButton classCommentsButton;

    private ParamTreeTableView tableView;
    private DocEditorFields fields = new DocEditorFields(List.of(), Map.of());
    private final Set<DocViewParamData> generatingRows = Collections.newSetFromMap(new IdentityHashMap<>());
    private long markdownCopyRequest;

    private JPanel rootPanel;
    private JPanel headToolbarPanel;
    private JPanel tailToolbarPanel;

    private JScrollPane paramScrollPane;

    private JBPopup popup;

    @NonNls
    public static final String DOC_VIEW_POPUP = "com.intellij.docview.param.editor.popup";
    private static final AtomicBoolean PINNED = new AtomicBoolean(true);

    public ParamDocEditorForm(@NotNull Project project, @NotNull PsiFile psiFile,
                              @Nullable Editor editor, @NotNull PsiClass psiClass) {

        this.project = project;
        this.psiClass = psiClass;
        this.entityName = psiClass.getName();
        PsiElement caret = editor == null ? null : psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiElement field = PsiTreeUtil.getParentOfType(caret, PsiField.class, PsiRecordComponent.class);
        this.initialField = field == null ? null : SmartPointerManager.createPointer(field);

        // UI调整
        initUi();
        initHeadToolbar();
        initTailLeftToolbar();
        initTailRightToolbar();

        initParamTable();
        addMouseListeners();
    }


    @NotNull
    @Contract("_, _, _, _ -> new")
    public static ParamDocEditorForm getInstance(@NotNull Project project, @NotNull PsiFile psiFile,
                                                 @Nullable Editor editor, @NotNull PsiClass psiClass) {
        return new ParamDocEditorForm(project, psiFile, editor, psiClass);
    }


    private void addMouseListeners() {
        WindowMoveListener windowMoveListener = new WindowMoveListener(rootPanel);
        rootPanel.addMouseListener(windowMoveListener);
        rootPanel.addMouseMotionListener(windowMoveListener);

    }

    /**
     * 显示弹窗
     */
    public void popup() {

        // dialog 改成 popup, 第一个为根面板，第二个为焦点面板
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(rootPanel, paramScrollPane)
                .setProject(project)
                .setResizable(true)
                .setMovable(true)

                .setModalContext(false)
                .setRequestFocus(true)
                .setBelongsToGlobalPopupStack(true)
                .setDimensionServiceKey(null, DOC_VIEW_POPUP, true)
                .setLocateWithinScreenBounds(false)
                // 鼠标点击外部时是否取消弹窗 外部单击, 未处于 pin 状态则可关闭
                .setCancelOnMouseOutCallback(event -> event.getID() == MouseEvent.MOUSE_PRESSED && !PINNED.get())

                // 单击外部时取消弹窗
                .setCancelOnClickOutside(false)
                // 在其他窗口打开时取消
                .setCancelOnOtherWindowOpen(false)
                .setMinSize(new Dimension(600, 380))
                .setCancelOnWindowDeactivation(false)
                .createPopup();
        popup.showCenteredInCurrentWindow(project);

    }


    private void initUi() {
        // 边框
        paramScrollPane.setBackground(null);
        paramScrollPane.setOpaque(false);
        paramScrollPane.setBorder(JBUI.Borders.empty());
    }

    /**
     * 初始化头部工具栏
     */
    private void initHeadToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        group.add(new AnAction(DocViewBundle.message("action.settings"),
                DocViewBundle.message("action.settings.description"), DocViewIcons.SETTINGS) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), SettingsConfigurable.class);
            }
        });

        group.addSeparator();

        group.add(new ToggleAction(DocViewBundle.message("action.pin"),
                DocViewBundle.message("action.pin.description"), DocViewIcons.PIN) {

            @Override
            public boolean isDumbAware() {
                return true;
            }

            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return PINNED.get();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                PINNED.set(state);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

        });

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("ParamDocParamEditorHeadToolbar", group, true);
        toolbar.setTargetComponent(headToolbarPanel);

        PopupToolbarLayout.keepPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        headToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);
    }


    /**
     * 底部 右侧 主要是提交
     */
    private void initTailRightToolbar() {

        DefaultActionGroup rightGroup = new DefaultActionGroup();

        rightGroup.add(new AnAction(DocViewBundle.message("action.copy.json"),
                DocViewBundle.message("action.copy.json.description"), DocViewIcons.JSON) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                DocViewBackgroundTasks.runReadTask(
                        project,
                        DocViewBundle.message("task.docview.json"),
                        true,
                        indicator -> ParamPsiUtils.getFieldsAndDefaultValue(psiClass, null),
                        fieldMap -> {
                            String format = GsonFormatUtil.gsonFormat(fieldMap);
                            StringSelection selection = new StringSelection(format);
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(selection, selection);
                            DocViewNotification.notifyInfo(project, DocViewBundle.message("param.copy.success", psiClass.getName()));
                            popup.cancel();
                        },
                        throwable -> DocViewNotification.notifyError(project, throwable.getMessage())
                );
            }
        });

        rightGroup.add(createMarkdownCopyAction());

        rightGroup.add(new AnAction(DocViewBundle.message("action.close"),
                DocViewBundle.message("action.close.description"), null) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // Unconfirmed row edits are kept in the document draft until the editor closes.
                ++markdownCopyRequest;
                if (popup != null) {
                    popup.cancel();
                }
            }
        });


        // init toolbar
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("ParamDocParamEditorTailRightToolbar", rightGroup, true);
        toolbar.setTargetComponent(tailToolbarPanel);

        PopupToolbarLayout.keepPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        tailToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);

    }

    private void initTailLeftToolbar() {
        classCommentsButton = new JButton("整类注释");
        classCommentsButton.setEnabled(false);
        classCommentsButton.addActionListener(event -> showClassDialog());
        tailToolbarPanel.add(classCommentsButton, BorderLayout.WEST);
    }

    private AnAction createMarkdownCopyAction() {
        return new AnAction(DocViewBundle.message("action.copy.markdown"),
                DocViewBundle.message("action.copy.markdown.description"), DocViewIcons.COPY) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (!isMarkdownCopyAvailable()) {
                    return;
                }
                if (!tableView.finishEditingForCopy()) {
                    return;
                }

                // Capture editable values on EDT; rendering must not retain or dereference PSI.
                List<DocViewParamData> snapshot = DocEditorFields.snapshotParamData(fields.data());
                boolean separate = Boolean.TRUE.equals(Settings.getInstance(project).getSeparateParam());
                long request = ++markdownCopyRequest;
                DocViewBackgroundTasks.runReadTask(project,
                        DocViewBundle.message("task.docview.markdown"), true,
                        indicator -> DocViewData.bodyMarkdown(snapshot, separate),
                        markdown -> {
                            if (request != markdownCopyRequest || !isMarkdownCopyAvailable() || markdown.isBlank()) {
                                return;
                            }
                            CopyPasteManager.getInstance().setContents(new StringSelection(markdown));
                            DocViewNotification.notifyInfo(project, DocViewBundle.message("notify.copy.success", entityName));
                            popup.cancel();
                        },
                        throwable -> {
                            if (request == markdownCopyRequest && isMarkdownCopyAvailable()) {
                                DocViewNotification.notifyError(project, throwable.getMessage());
                            }
                        });
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(isMarkdownCopyAvailable());
            }

            @Override
            public boolean isDumbAware() {
                return true;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    private boolean isMarkdownCopyAvailable() {
        return !project.isDisposed() && popup != null && !popup.isDisposed()
                && tableView != null && !fields.data().isEmpty();
    }

    private void initParamTable() {

        paramScrollPane.setViewportView(new JLabel(
                DocViewBundle.message("task.docview.parameters"), SwingConstants.CENTER));

        DocViewBackgroundTasks.runReadTask(
                project,
                DocViewBundle.message("task.docview.parameters"),
                true,
                indicator -> DocEditorFields.read(psiClass),
                this::applyParamTable,
                throwable -> DocViewNotification.notifyError(project, throwable.getMessage())
        );
    }

    private void applyParamTable(@NotNull DocEditorFields rows) {
        fields = rows;
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        convertToTreeNode(root, fields.data());

        ListTreeTableModelOnColumns model = new ListTreeTableModelOnColumns(root, ParamTreeTableView.COLUMN_INFOS);

        tableView = new ParamTreeTableView(model,
                row -> !project.isDisposed() && !DumbService.isDumb(project) && fields.targets().containsKey(row)
                        && !generatingRows.contains(row),
                this::regenerateRow, this::beginDescriptionEdit);

        paramScrollPane.setViewportView(tableView);
        classCommentsButton.setEnabled(ReadAction.computeBlocking(() -> SourceDescriptionService.classTarget(psiClass) != null
                || !fields.directFieldTargets(psiClass).isEmpty()));
        PsiElement field = initialField != null ? initialField.getElement() : null;
        if (field != null) {
            for (int row = 0; row < tableView.getRowCount(); row++) {
                var data = (DocViewParamData) tableView.getValueAt(row, ParamTreeTableView.ACTIONS_COLUMN);
                if (Objects.equals(field, data.getPsiElement())) {
                    tableView.setRowSelectionInterval(row, row);
                    tableView.scrollRectToVisible(tableView.getCellRect(row, 0, true));
                    break;
                }
            }
        }
    }

    private void showClassDialog() {
        if (project.isDisposed() || DumbService.isDumb(project) || isPopupDisposed()) {
            return;
        }
        if (tableView.isEditing() && !tableView.getCellEditor().stopCellEditing()) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        var classFields = ReadAction.computeBlocking(() -> fields.directFieldTargets(psiClass));
        var rows = ReadAction.computeBlocking(() -> {
            var candidates = new ArrayList<ClassDescriptionDialog.Row>();
            var classTarget = SourceDescriptionService.classTarget(psiClass);
            var classDraft = classTarget == null ? null : SourceDescriptionService.capture(classTarget);
            if (classDraft != null) {
                candidates.add(new ClassDescriptionDialog.Row(classDraft, classDraft.original(), true));
            }
            classFields.forEach((target, row) -> {
                var draft = SourceDescriptionService.capture(target);
                if (draft != null) {
                    String edited = row == null ? null : editedDescription(row, target);
                    candidates.add(new ClassDescriptionDialog.Row(draft,
                            edited == null ? draft.original() : edited, row != null));
                }
            });
            return candidates;
        });
        if (rows.isEmpty()) {
            DocViewNotification.notifyInfo(project, "当前类没有可写的 Java 源码注释。");
            return;
        }
        ++markdownCopyRequest;
        boolean wasPinned = PINNED.getAndSet(true);
        try {
            var dialog = new ClassDescriptionDialog(project, rows);
            if (dialog.showAndGet()) {
                for (var edit : dialog.written()) {
                    var target = edit.draft().target();
                    var row = classFields.get(target);
                    if (row != null) {
                        fields.updateRowDescription(row);
                    }
                }
                tableView.repaint();
            }
        } finally {
            PINNED.set(wasPinned);
        }
    }

    private Consumer<String> beginDescriptionEdit(DocViewParamData row) {
        var target = fields.targets().get(row);
        if (target == null) {
            // Rows without a source declaration remain editable for Markdown.
            return text -> row.setDescription(row.getDescription().withDraft(text));
        }
        var draft = captureRow(target);
        if (draft != null && !Objects.equals(draft.original(), target.description().sourceComment())) {
            fields.updateRowDescription(row);
        }
        return text -> writeRow(row, draft, text);
    }

    private boolean isPopupDisposed() {
        return popup != null && popup.isDisposed();
    }

    private SourceDescriptionService.Draft captureRow(SourceDescriptionService.Target target) {
        if (project.isDisposed() || DumbService.isDumb(project) || isPopupDisposed()) {
            return null;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        return ReadAction.computeBlocking(() -> SourceDescriptionService.capture(target));
    }

    private void writeRow(DocViewParamData row, SourceDescriptionService.Draft draft, String description) {
        if (project.isDisposed() || isPopupDisposed()) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (draft == null || !SourceDescriptionService.apply(draft, description)) {
            DocViewNotification.notifyError(project, "源码已更改或不可写，请重新编辑后再试。");
            return;
        }
        ++markdownCopyRequest;
        fields.updateRowDescription(row);
        tableView.repaint();
    }

    private void regenerateRow(DocViewParamData row) {
        var target = fields.targets().get(row);
        if (target == null || generatingRows.contains(row)) {
            return;
        }
        var draft = captureRow(target);
        var generation = draft == null ? null : ReadAction.computeBlocking(() -> SourceDescriptionService.prepareGeneration(draft, ""));
        if (generation == null) {
            DocViewNotification.notifyError(project, "源码已更改或不可写，请重新生成。");
            return;
        }
        generatingRows.add(row);
        tableView.repaint();
        new Task.Backgroundable(project, "生成字段说明 · " + target.label(), true) {
            private String generated;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.checkCanceled();
                generated = generation.get();
                indicator.checkCanceled();
            }

            @Override
            public void onSuccess() {
                if (generated == null || generated.isBlank()) {
                    DocViewNotification.notifyInfo(project, "没有生成字段说明。");
                } else {
                    writeRow(row, draft, generated);
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                DocViewNotification.notifyError(project, error instanceof ModelRequestException
                        ? error.getMessage() : "生成字段说明失败，请检查生成设置。");
            }

            @Override
            public void onFinished() {
                generatingRows.remove(row);
                tableView.repaint();
            }
        }.queue();
    }

    private static String editedDescription(DocViewParamData row, SourceDescriptionService.Target target) {
        String source = row.getDescription().editableText();
        return Objects.equals(source, target.description().sourceComment()) ? null : source;
    }

    private void convertToTreeNode(DefaultMutableTreeNode root, List<DocViewParamData> paramDataList) {

        for (DocViewParamData data : paramDataList) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(data);
            root.add(node);

            if (data.getChildList() != null && !data.getChildList().isEmpty()) {
                convertToTreeNode(node, data.getChildList());
            }

        }
    }

}
