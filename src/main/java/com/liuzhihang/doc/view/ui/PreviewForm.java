package com.liuzhihang.doc.view.ui;

import com.intellij.find.editorHeaderActions.Utils;
import icons.DocViewIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.SettingsConfigurable;
import com.liuzhihang.doc.view.config.TemplateSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.render.DocumentMarkdownRenderer;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.service.SourceDescriptionService;
import com.liuzhihang.doc.view.utils.ExportUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 文档预览界面 UI
 *
 * @author liuzhihang
 * @date 2020/2/26 19:40
 */
public class PreviewForm implements Disposable {

    @NonNls
    public static final String DOC_VIEW_POPUP = "com.intellij.docview.popup";
    public static final AtomicBoolean myIsPinned = new AtomicBoolean(false);

    private Document markdownDocument;

    /**
     * 分割窗口
     */
    private final JBSplitter viewSplitter = new OnePixelSplitter(0.2F);

    /**
     * 左侧目录面板
     */
    private final JBPanel<?> catalogPane = new JBPanel<>(new BorderLayout());

    /**
     * 目录面板
     */
    private final JBList<String> catalogList = new JBList<>();

    /**
     * 目录面板操作栏
     */
    private final JBPanel<?> catalogToolbarPane = new JBPanel<>(new BorderLayout());

    /**
     * 预览面板
     */
    private final JBPanel<?> previewPanel = new JBPanel<>(new BorderLayout());

    /**
     * 预览面板内容
     */
    private final JBPanel<?> previewContentPanel = new JBPanel<>(new BorderLayout());

    /**
     * 预览面板操作栏
     */
    private final JBPanel<?> previewToolbarPanel = new JBPanel<>(new BorderLayout());

    private final MarkdownPreviewPane markdownPane;

    /**
     * 根面板
     */
    private JPanel rootPanel;

    /**
     * 呈现面板
     */
    private JPanel viewPanel;

    /**
     * 工具面板
     */
    private JPanel headToolbarPanel;

    /**
     * 文档名称标签
     */
    private JLabel docNameLabel;


    /**
     * 当前文本 text
     */
    private String currentMarkdownText;

    /**
     * 当前文档
     */
    private DocView currentDocView;

    /**
     * 弹出窗口
     */
    private JBPopup popup;

    /**
     * 当前类
     */
    private final PsiClass psiClass;

    private final Project project;

    /**
     * 当前方法
     */
    private final PsiMethod psiMethod;

    /**
     * 文档列表
     */
    private List<DocView> docViewList;

    /**
     * 文档 Map
     */

    private Map<DocView, ClassPresentation> classPresentations = Map.of();
    private ListSelectionListener catalogSelectionListener;

    private final Map<DocView, MarkdownCacheEntry> markdownCache = new IdentityHashMap<>();
    private final PreviewRequests renderRequests = new PreviewRequests();
    private final PreviewRequests loadRequests = new PreviewRequests();
    private boolean uiInitialized;
    private volatile boolean disposed;


    public PreviewForm(@NotNull PsiClass psiClass, PsiMethod psiMethod) {
        this(psiClass, psiMethod, MarkdownPreviewPane::createNativeMarkdownPreview);
    }

    PreviewForm(@NotNull PsiClass psiClass, PsiMethod psiMethod,
                BiFunction<Project, VirtualFile, FileEditor> previewFactory) {
        this.psiClass = psiClass;
        this.project = psiClass.getProject();
        this.psiMethod = psiMethod;
        this.markdownPane = new MarkdownPreviewPane(project, previewContentPanel, previewFactory);
    }

    /**
     * 鼠标监听
     */
    private void addMouseListeners() {
        WindowMoveListener windowMoveListener = new WindowMoveListener(rootPanel);
        rootPanel.addMouseListener(windowMoveListener);
        rootPanel.addMouseMotionListener(windowMoveListener);
        headToolbarPanel.addMouseListener(windowMoveListener);
        headToolbarPanel.addMouseMotionListener(windowMoveListener);

    }

    @NotNull
    @Contract("_, _ -> new")
    public static PreviewForm getInstance(@NotNull PsiClass psiClass, PsiMethod psiMethod) {
        return new PreviewForm(psiClass, psiMethod);
    }

    /**
     * 弹出窗口
     */
    public void popup() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed) {
            return;
        }
        if (popup != null && !popup.isDisposed()) {
            return;
        }

        try {
            initializeUi();
            // dialog 改成 popup, 第一个为根面板，第二个为焦点面板
            popup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(rootPanel, previewToolbarPanel)
                    .setProject(project)
                    .setResizable(true)
                    .setMovable(true)
                    .setModalContext(false)
                    .setRequestFocus(true)
                    .setBelongsToGlobalPopupStack(true)
                    .setDimensionServiceKey(null, DOC_VIEW_POPUP, true)
                    .setLocateWithinScreenBounds(false)
                    // 鼠标点击外部时是否取消弹窗 外部单击, 未处于 pin 状态则可关闭
                    .setCancelOnMouseOutCallback(event ->
                            event.getID() == MouseEvent.MOUSE_PRESSED && !myIsPinned.get()
                    )
                    // 单击外部时取消弹窗
                    .setCancelOnClickOutside(false)
                    .setCancelOnOtherWindowOpen(false)
                    .setCancelOnWindowDeactivation(false)
                    .createPopup();
            Disposer.register(popup, this);
            popup.showCenteredInCurrentWindow(project);
            if (!disposed && !popup.isDisposed()) {
                buildDoc();
            }
        } catch (RuntimeException | Error exception) {
            try {
                if (popup != null && !popup.isDisposed()) {
                    popup.cancel();
                }
            } finally {
                dispose();
            }
            throw exception;
        }
    }

    private void initializeUi() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (uiInitialized || disposed) {
            return;
        }

        try {
            var previewFile = MarkdownPreviewPane.createPreviewFile(psiClass);
            markdownDocument = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(previewFile));
            markdownPane.initialize(previewFile, markdownDocument);
            layout();
            addMouseListeners();
            uiInitialized = true;
        } catch (RuntimeException | Error exception) {
            dispose();
            throw exception;
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        loadRequests.invalidate();
        renderRequests.invalidate();
        markdownCache.clear();
        classPresentations = Map.of();
        if (catalogSelectionListener != null) {
            catalogList.removeListSelectionListener(catalogSelectionListener);
            catalogSelectionListener = null;
        }

        markdownPane.dispose();

        currentMarkdownText = null;
        currentDocView = null;
        docViewList = null;
    }



    /**
     * 根面板：界面布局
     */
    private void layout() {

        // 目录面板：左侧
        layoutCatalogPane();
        layoutPreviewPanel();
        layoutHeadToolbar();

        viewPanel.setLayout(new BorderLayout());
        viewPanel.setBorder(JBUI.Borders.empty());

        // 不为空说明指向的是当前方法
        if (psiMethod != null) {
            viewPanel.add(previewPanel, BorderLayout.CENTER);
        } else {
            // 将分割窗口插入
            viewPanel.add(viewSplitter, BorderLayout.CENTER);

            // 记录当前俩个组件的比例，存放到map中，key即为该值
            viewSplitter.setSplitterProportionKey("docViewPreviewProportionKey");
            // 填充分割面板
            viewSplitter.setFirstComponent(catalogPane);
            viewSplitter.setSecondComponent(previewPanel);
            viewSplitter.setBorder(JBUI.Borders.empty());
        }

        // 顶部文字边距
        docNameLabel.setBorder(JBUI.Borders.emptyLeft(5));
    }

    /**
     * 右边预览面板
     */
    private void layoutPreviewPanel() {
        previewPanel.setMinimumSize(new Dimension(150, 0));
        previewPanel.setPreferredSize(new Dimension(0, 0));
        // 预览面板
        previewPanel.add(previewContentPanel, BorderLayout.CENTER);
        previewPanel.add(previewToolbarPanel, BorderLayout.SOUTH);
        previewToolbarPanel.setBorder(JBUI.Borders.empty());
        previewPanel.setBorder(JBUI.Borders.empty());

        // 选择原生 Markdown 预览或源码
        markdownPane.showSelectedView();
        // 预览面板操作栏
        previewLeftToolbar();
        // 预览面板右侧操作栏
        previewRightToolbar();
    }

    /**
     * 目录面板渲染，左侧面板
     */
    private void layoutCatalogPane() {
        catalogPane.setMinimumSize(new Dimension(150, 0));
        catalogPane.setPreferredSize(new Dimension(150, 0));
        // 目录面板
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(catalogList);
        scrollPane.setBorder(JBUI.Borders.customLine(JBColor.LIGHT_GRAY));

        catalogPane.add(scrollPane, BorderLayout.CENTER);
        catalogPane.add(catalogToolbarPane, BorderLayout.SOUTH);

        catalogPane.setBorder(JBUI.Borders.emptyLeft(5));
        catalogList.setBorder(JBUI.Borders.empty());
        catalogList.setBackground(UIUtil.getTextFieldBackground());

        // 目录面板操作栏
        catalogPanelToolbar();
    }

    private void layoutHeadToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        group.add(new AnAction(DocViewBundle.message("action.settings"),
                DocViewBundle.message("action.settings.description"), DocViewIcons.SETTINGS) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                popup.cancel();
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
                return myIsPinned.get();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                myIsPinned.set(state);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

        });

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DocViewRootToolbar", group, true);
        toolbar.setTargetComponent(headToolbarPanel);

        PopupToolbarLayout.keepPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        headToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);
    }



    /**
     * 预览面板左侧操作栏
     */
    private void previewLeftToolbar() {

        DefaultActionGroup leftGroup = new DefaultActionGroup();

        leftGroup.add(new ToggleAction(DocViewBundle.message("action.preview"),
                DocViewBundle.message("action.preview.markdown.description"), DocViewIcons.EDITOR_PREVIEW) {

            @Override
            public boolean isDumbAware() {
                return true;
            }

            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return markdownPane.isRendered();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {

                markdownPane.selectRendered(state);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

        });

        leftGroup.add(new AnAction("重新生成注释") {
            @Override
            public boolean displayTextInToolbar() {
                return true;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                event.getPresentation().setEnabled(!disposed && currentDocView != null
                        && !DumbService.isDumb(project));
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                if (currentDocView == null) {
                    return;
                }
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                var targets = ReadAction.computeBlocking(() -> SourceDescriptionService.targets(currentDocView));
                if (targets.isEmpty()) {
                    DocViewNotification.notifyInfo(project, "当前文档没有可写入的 Java 源码注释。");
                    return;
                }
                boolean wasPinned = myIsPinned.getAndSet(true);
                try {
                    if (new SourceDescriptionDialog(project, targets).showAndGet() && !disposed) {
                        buildDoc();
                    }
                } finally {
                    myIsPinned.set(wasPinned);
                }
            }
        });

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DocViewEditorLeftToolbar", leftGroup, true);
        toolbar.setTargetComponent(previewToolbarPanel);
        toolbar.getComponent().setBackground(UIUtil.getPanelBackground());

        PopupToolbarLayout.keepPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        previewToolbarPanel.setBackground(UIUtil.getPanelBackground());
        previewToolbarPanel.add(toolbar.getComponent(), BorderLayout.WEST);
    }

    /**
     * 预览面板右侧操作栏
     */
    private void previewRightToolbar() {
        DefaultActionGroup rightGroup = new DefaultActionGroup();

        rightGroup.add(new AnAction(DocViewBundle.message("action.upload"),
                DocViewBundle.message("action.upload.documents.description"), DocViewIcons.UPLOAD) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                myIsPinned.set(true);

                DocView selectedDocView = currentDocView;
                if (selectedDocView == null) {
                    DocViewNotification.notifyInfo(project, DocViewBundle.message("ui.docview.loading.retry"));
                    return;
                }

                UploadPlatformPopup.show(project, previewToolbarPanel, service -> {
                    if (!disposed) {
                        service.upload(project, selectedDocView);
                    }
                });
            }
        });


        rightGroup.addSeparator();

        rightGroup.add(new AnAction(DocViewBundle.message("action.export"),
                DocViewBundle.message("action.export.markdown.description"), DocViewIcons.EXPORT) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                SingleExportSnapshot snapshot = captureSingleExport();
                if (snapshot == null) {
                    DocViewNotification.notifyInfo(project, DocViewBundle.message("ui.docview.loading.retry"));
                    return;
                }

                popup.cancel();

                ExportUtils.exportMarkdown(project, snapshot.fileName(), snapshot.markdownText());
            }
        });

        rightGroup.add(new AnAction(DocViewBundle.message("action.copy"),
                DocViewBundle.message("action.copy.clipboard.description"), DocViewIcons.COPY) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                if (currentDocView == null || currentMarkdownText == null) {
                    DocViewNotification.notifyInfo(project, DocViewBundle.message("ui.docview.loading.retry"));
                    return;
                }

                StringSelection selection = new StringSelection(currentMarkdownText);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);

                DocViewNotification.notifyInfo(project, DocViewBundle.message("notify.copy.success", currentDocView.getName()));
            }
        });

        // init toolbar
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DocViewEditorRightToolbar", rightGroup, true);
        toolbar.setTargetComponent(previewToolbarPanel);
        toolbar.getComponent().setBackground(UIUtil.getPanelBackground());

        PopupToolbarLayout.keepPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        previewToolbarPanel.setBackground(UIUtil.getPanelBackground());
        previewToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);

    }

    /**
     * 构建目录
     */
    private void catalogPanelToolbar() {

        DefaultActionGroup menuGroup = new DefaultActionGroup();

        menuGroup.add(new AnAction(DocViewBundle.message("action.export.all"),
                DocViewBundle.message("action.export.markdown.description"), DocViewIcons.EXPORT) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                AllExportSnapshot snapshot = captureAllExport();
                if (snapshot == null) {
                    DocViewNotification.notifyInfo(project, DocViewBundle.message("ui.docview.loading.retry"));
                    return;
                }

                popup.cancel();
                ExportUtils.batchExportMarkdown(project, snapshot.className(), snapshot::docViews);
            }
        });
        menuGroup.addSeparator();

        menuGroup.add(new AnAction(DocViewBundle.message("action.upload.all"),
                DocViewBundle.message("action.upload.all.description"), DocViewIcons.UPLOAD) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                List<DocView> selectedDocViews = docViewList == null ? null : List.copyOf(docViewList);
                if (selectedDocViews == null) {
                    DocViewNotification.notifyInfo(project, DocViewBundle.message("ui.docview.loading.retry"));
                    return;
                }

                myIsPinned.set(true);
                UploadPlatformPopup.show(project, catalogToolbarPane, service -> {
                    if (!disposed) {
                        service.upload(project, selectedDocViews);
                    }
                });
            }
        });


        // init toolbar
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DocViewMenuToolbar", menuGroup, true);
        toolbar.setTargetComponent(catalogToolbarPane);
        toolbar.getComponent().setBackground(UIUtil.getTextFieldBackground());

        PopupToolbarLayout.keepPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        catalogToolbarPane.setBackground(UIUtil.getTextFieldBackground());
        catalogToolbarPane.add(toolbar.getComponent(), BorderLayout.WEST);

    }

    /**
     * 构造文档
     */
    private void buildDoc() {
        long requestId = loadRequests.invalidate();
        renderRequests.invalidate();
        showPreviewStatus(DocViewBundle.message("ui.preview.loading"));

        loadRequests.submit(project, DocViewBundle.message("task.docview.preview"), requestId, () -> {
            List<DocView> docs = List.copyOf(DocViewService.getInstance(project, psiClass).buildDoc(psiClass, psiMethod));
            return new PreviewLoadResult(docs, captureClassPresentations(docs));
        }, loaded -> {
            classPresentations = loaded.classPresentations();
            applyDocViewList(loaded.documents(), requestId);
        }, () -> handleLoadCancellation(requestId), error -> handleLoadFailure(requestId, error));
    }

    private void applyDocViewList(@NotNull List<DocView> loadedDocViewList, long requestId) {
        if (!isCurrentLoad(requestId)) {
            return;
        }

        markdownCache.clear();
        docViewList = List.copyOf(loadedDocViewList);

        Vector<String> nameVector = docViewList.stream().map(DocView::getName).collect(Collectors.toCollection(Vector::new));

        if (catalogSelectionListener != null) {
            catalogList.removeListSelectionListener(catalogSelectionListener);
        }

        DocView previous = currentDocView;
        catalogList.setListData(nameVector);

        catalogSelectionListener = catalog -> {

            if (catalog.getValueIsAdjusting() || disposed) {
                return;
            }

            int selectedIndex = catalogList.getSelectedIndex();
            currentDocView = selectedIndex >= 0 && selectedIndex < docViewList.size()
                    ? docViewList.get(selectedIndex) : null;

            if (currentDocView == null) {
                return;
            }

            ClassPresentation presentation = classPresentations.get(currentDocView);
            docNameLabel.setText(presentation == null ? "" : presentation.qualifiedName());

            renderMarkdown(currentDocView);

        };
        catalogList.addListSelectionListener(catalogSelectionListener);

        if (!docViewList.isEmpty()) {
            int selection = 0;
            if (previous != null && previous.getPsiMethod() != null) {
                for (int i = 0; i < docViewList.size(); i++) {
                    if (Objects.equals(previous.getPsiMethod(), docViewList.get(i).getPsiMethod())) {
                        selection = i;
                        break;
                    }
                }
            }
            catalogList.setSelectedIndex(selection);
        } else {
            currentDocView = null;
            showPreviewStatus(DocViewBundle.message("ui.preview.empty"));
        }
    }

    private void renderMarkdown(@NotNull DocView docView) {
        if (disposed) {
            return;
        }
        long requestId = renderRequests.invalidate();
        currentMarkdownText = null;
        showPreviewStatus(DocViewBundle.message("ui.preview.rendering"));

        String cacheKey = markdownCacheKey(docView);
        MarkdownCacheEntry entry = markdownCache.get(docView);
        String cachedMarkdown = entry != null && entry.key().equals(cacheKey) ? entry.markdown() : null;

        renderRequests.submit(project, DocViewBundle.message("task.docview.markdown"), requestId,
                () -> cachedMarkdown != null ? cachedMarkdown : DocumentMarkdownRenderer.render(project, docView),
                markdown -> {
                    if (isCurrentRender(requestId, docView)) {
                        markdownCache.put(docView, new MarkdownCacheEntry(cacheKey, markdown));
                        applyMarkdown(docView, markdown, requestId);
                    }
                }, () -> handleRenderCancellation(requestId), error -> handleRenderFailure(requestId, error));
    }

    private void applyMarkdown(@NotNull DocView docView,
                               @NotNull String markdownText,
                               long requestId) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (!isCurrentRender(requestId, docView)) {
            return;
        }

        currentMarkdownText = markdownText;

        setMarkdownText(markdownText);
    }

    private void showPreviewStatus(@NotNull String message) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed) {
            return;
        }
        currentMarkdownText = null;
        setMarkdownText(message);
    }

    private void handleLoadCancellation(long requestId) {
        if (isCurrentLoad(requestId)) {
            showPreviewStatus(DocViewBundle.message("ui.preview.load.cancelled"));
        }
    }

    private void handleLoadFailure(long requestId, @NotNull Throwable throwable) {
        if (!isCurrentLoad(requestId)) {
            return;
        }
        showPreviewStatus(DocViewBundle.message("ui.preview.load.failed"));
        DocViewNotification.notifyError(project, errorMessage(throwable));
    }

    private void handleRenderCancellation(long requestId) {
        if (isCurrentRender(requestId, currentDocView)) {
            showPreviewStatus(DocViewBundle.message("ui.preview.render.cancelled"));
        }
    }

    private void handleRenderFailure(long requestId, @NotNull Throwable throwable) {
        if (!isCurrentRender(requestId, currentDocView)) {
            return;
        }
        showPreviewStatus(DocViewBundle.message("ui.preview.render.failed"));
        DocViewNotification.notifyError(project, errorMessage(throwable));
    }

    private static String errorMessage(@NotNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? DocViewBundle.message("ui.preview.operation.failed") : message;
    }

    private boolean isCurrentLoad(long requestId) {
        return !disposed && !project.isDisposed() && loadRequests.isCurrent(requestId);
    }

    private boolean isCurrentRender(long requestId, @Nullable DocView docView) {
        return !disposed && !project.isDisposed()
                && docView != null && renderRequests.isCurrent(requestId) && currentDocView == docView;
    }


    private void setMarkdownText(@NotNull String markdownText) {
        if (disposed || markdownDocument == null) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 光标放在顶部
            markdownDocument.setText(markdownText);
        });
    }

    private @Nullable SingleExportSnapshot captureSingleExport() {
        if (currentDocView == null || currentMarkdownText == null) {
            return null;
        }
        return new SingleExportSnapshot(currentDocView.getName(), currentMarkdownText);
    }

    private @Nullable AllExportSnapshot captureAllExport() {
        if (currentDocView == null || docViewList == null) {
            return null;
        }
        ClassPresentation presentation = classPresentations.get(currentDocView);
        return presentation == null ? null : new AllExportSnapshot(presentation.simpleName(), List.copyOf(docViewList));
    }

    /** Freeze PSI-derived names while the generating task still owns read access. */
    private static Map<DocView, ClassPresentation> captureClassPresentations(List<DocView> documents) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        Map<DocView, ClassPresentation> names = new IdentityHashMap<>();
        for (DocView document : documents) {
            com.intellij.openapi.progress.ProgressManager.checkCanceled();
            PsiClass source = document.getPsiClass();
            String simpleName = source == null ? null : source.getName();
            if (simpleName == null || simpleName.isBlank()) {
                simpleName = document.getDocTitle() == null || document.getDocTitle().isBlank()
                        ? "DocView" : document.getDocTitle();
            }
            String qualifiedName = source == null ? null : source.getQualifiedName();
            names.put(document, new ClassPresentation(qualifiedName == null ? simpleName : qualifiedName, simpleName));
        }
        return Collections.unmodifiableMap(names);
    }

    private record ClassPresentation(String qualifiedName, String simpleName) { }

    private record PreviewLoadResult(List<DocView> documents,
                                     Map<DocView, ClassPresentation> classPresentations) { }

    private record SingleExportSnapshot(@NotNull String fileName, @NotNull String markdownText) {
    }

    private record AllExportSnapshot(@NotNull String className, @NotNull List<DocView> docViews) {
    }

    private record MarkdownCacheEntry(String key, String markdown) {}

    private String markdownCacheKey(@NotNull DocView docView) {
        Settings settings = Settings.getInstance(project);
        TemplateSettings templateSettings = TemplateSettings.getInstance(project);
        long modificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
        int versionHash = Objects.hash(
                settings.getSeparateParam(),
                settings.getPrefixSymbol1(),
                settings.getPrefixSymbol2(),
                templateSettings.getSpringTemplate(),
                templateSettings.getDubboTemplate(),
                templateSettings.getPojoTemplate(),
                settings.getCurlDocHost()
        );
        return docView.getName() + ":" + modificationCount + ":" + versionHash;
    }
}
