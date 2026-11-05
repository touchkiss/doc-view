package com.liuzhihang.doc.view.ui;

import com.intellij.find.editorHeaderActions.Utils;
import icons.DocViewIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.SettingsConfigurable;
import com.liuzhihang.doc.view.config.TemplateSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.enums.UploadPlatform;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewService;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import com.liuzhihang.doc.view.utils.EditorUtils;
import com.liuzhihang.doc.view.utils.ExportUtils;
import lombok.extern.slf4j.Slf4j;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.stream.Collectors;

/**
 * 文档预览界面 UI
 *
 * @author liuzhihang
 * @date 2020/2/26 19:40
 */
@Slf4j
public class PreviewForm implements Disposable {

    @NonNls
    public static final String DOC_VIEW_POPUP = "com.intellij.docview.popup";
    public static final AtomicBoolean myIsPinned = new AtomicBoolean(false);
    private static final AtomicBoolean previewIsHtml = new AtomicBoolean(false);

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

    /**
     * JCEF 嵌入式浏览器，用于承载并渲染 HTML 预览。
     * 仅当 {@link JBCefApp#isSupported()} 为 true 时创建，否则为 null（降级为源码视图）。
     */
    private JBCefBrowser jcefBrowser;

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
     * 当前 markdown 编辑器
     */
    private EditorEx markdownEditor;

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
    private Map<String, DocView> docViewMap;

    private Map<DocView, ClassPresentation> classPresentations = Map.of();
    private ListSelectionListener catalogSelectionListener;

    private final Map<String, String> markdownCache = new HashMap<>();
    private final AtomicLong renderRequest = new AtomicLong();
    private final AtomicLong loadRequest = new AtomicLong();
    private final ConcurrentMap<Long, ProgressIndicator> renderIndicators = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ProgressIndicator> loadIndicators = new ConcurrentHashMap<>();
    private boolean uiInitialized;
    private volatile boolean disposed;


    public PreviewForm(@NotNull PsiClass psiClass, PsiMethod psiMethod) {

        this.psiClass = psiClass;
        this.project = psiClass.getProject();
        this.psiMethod = psiMethod;

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
            markdownDocument = EditorFactory.getInstance().createDocument("");
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
        loadRequest.incrementAndGet();
        renderRequest.incrementAndGet();
        cancelIndicators(loadIndicators);
        cancelIndicators(renderIndicators);
        markdownCache.clear();
        classPresentations = Map.of();
        if (catalogSelectionListener != null) {
            catalogList.removeListSelectionListener(catalogSelectionListener);
            catalogSelectionListener = null;
        }

        if (markdownEditor != null && !markdownEditor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(markdownEditor);
            markdownEditor = null;
        }
        if (jcefBrowser != null) {
            Disposer.dispose(jcefBrowser);
            jcefBrowser = null;
        }

        currentMarkdownText = null;
        currentDocView = null;
        docViewList = null;
        docViewMap = null;
    }

    private static void cancelIndicators(@NotNull ConcurrentMap<Long, ProgressIndicator> indicators) {
        indicators.values().forEach(ProgressIndicator::cancel);
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

        // 右侧 Markdown 源码面板
        markdownSourcePanel();
        // HTML 预览浏览器
        initHtmlBrowser();
        // 选择在预览面板上呈现的面板：源码/HTML
        choosePreviewPanel();
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

        keepToolbarPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        headToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);
    }

    private static void keepToolbarPreferredMinimumSize(@NotNull ActionToolbar toolbar) {
        ToolbarLayoutStrategy delegate = toolbar.getLayoutStrategy();
        toolbar.setLayoutStrategy(new ToolbarLayoutStrategy() {
            @Override
            public @NotNull List<Rectangle> calculateBounds(@NotNull ActionToolbar actionToolbar) {
                return delegate.calculateBounds(actionToolbar);
            }

            @Override
            public @NotNull Dimension calcPreferredSize(@NotNull ActionToolbar actionToolbar) {
                return delegate.calcPreferredSize(actionToolbar);
            }

            @Override
            public @NotNull Dimension calcMinimumSize(@NotNull ActionToolbar actionToolbar) {
                return delegate.calcPreferredSize(actionToolbar);
            }
        });
    }


    /**
     * 初始化 Markdown 源码面板
     */
    private void markdownSourcePanel() {

        markdownEditor = (EditorEx) EditorFactory.getInstance().createViewer(markdownDocument, project, EditorKind.UNTYPED);

        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("md");
        markdownEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));

        EditorUtils.renderMarkdownEditor(markdownEditor);

        markdownEditor.setBorder(JBUI.Borders.emptyLeft(5));
    }

    /**
     * 初始化 HTML 预览浏览器。
     * <p>
     * 2026.2 起平台将 JCEF 拆分为独立捆绑插件（module intellij.platform.ui.jcef），
     * 且 JetBrains 官方将 Markdown 插件面板类标注为不稳定的实现细节。这里改用平台稳定的
     * {@link JBCefBrowser} 直接承载 HTML，HTML 由 {@link MarkdownUtil} 生成。
     * <p>
     * 当运行环境不支持 JCEF 时不创建浏览器，预览降级为 Markdown 源码视图。
     */
    private void initHtmlBrowser() {

        if (!JBCefApp.isSupported()) {
            // Fallback to a browser-less solution (源码视图)
            // https://plugins.jetbrains.com/docs/intellij/jcef.html#jbcefapp
            log.info("当前不支持 JCEF");
            return;
        }

        jcefBrowser = JBCefBrowser.createBuilder().build();
    }

    /**
     * 预览面板，根据设置选择 HTML 面板或者 Markdown 面板
     */
    private void choosePreviewPanel() {

        if (previewIsHtml.get() && jcefBrowser != null) {
            previewContentPanel.add(jcefBrowser.getComponent(), BorderLayout.CENTER);
        } else {
            // 展示源码
            previewContentPanel.add(ScrollPaneFactory.createScrollPane(markdownEditor.getComponent()), BorderLayout.CENTER);
        }

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
                return previewIsHtml.get();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {

                if (jcefBrowser == null) {
                    // 不支持 JCEF 不允许预览
                    previewIsHtml.set(false);
                    DocViewNotification.notifyInfo(project, DocViewBundle.message("notify.not.support.jcef"));
                } else {
                    previewIsHtml.set(state);
                    if (state) {
                        previewContentPanel.removeAll();
                        previewContentPanel.repaint();
                        previewContentPanel.add(jcefBrowser.getComponent(), BorderLayout.CENTER);
                        previewContentPanel.revalidate();
                    } else {
                        // 展示源码
                        previewContentPanel.removeAll();
                        previewContentPanel.repaint();
                        previewContentPanel.add(ScrollPaneFactory.createScrollPane(markdownEditor.getComponent()), BorderLayout.CENTER);
                        previewContentPanel.revalidate();
                    }
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

        });

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DocViewEditorLeftToolbar", leftGroup, true);
        toolbar.setTargetComponent(previewToolbarPanel);
        toolbar.getComponent().setBackground(UIUtil.getPanelBackground());

        keepToolbarPreferredMinimumSize(toolbar);
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

                Point location = previewToolbarPanel.getLocationOnScreen();
                location.x = MouseInfo.getPointerInfo().getLocation().x;
                location.y += previewToolbarPanel.getHeight();
                JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null, DocViewUploadService.UPLOAD_OPTIONS) {

                    @Override
                    public @NotNull String getTextFor(UploadPlatform value) {
                        return DocViewBundle.message("action.upload.platform", value.getDisplayName());
                    }

                    @Override
                    public @Nullable PopupStep<?> onChosen(UploadPlatform selectedValue, boolean finalChoice) {
                        if (!disposed && !project.isDisposed()) {
                            DocViewUploadService.getInstance(selectedValue).upload(project, selectedDocView);
                        }

                        return FINAL_CHOICE;
                    }
                }).showInScreenCoordinates(previewToolbarPanel, location);
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

        keepToolbarPreferredMinimumSize(toolbar);
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

                Point location = previewToolbarPanel.getLocationOnScreen();
                location.x = MouseInfo.getPointerInfo().getLocation().x;
                location.y += previewToolbarPanel.getHeight();

                myIsPinned.set(true);

                JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null, DocViewUploadService.UPLOAD_OPTIONS) {
                    @Override
                    public @NotNull String getTextFor(UploadPlatform value) {
                        return DocViewBundle.message("action.upload.platform", value.getDisplayName());
                    }

                    @Override
                    public @Nullable PopupStep<?> onChosen(UploadPlatform selectedValue, boolean finalChoice) {
                        if (!disposed && !project.isDisposed()) {
                            DocViewUploadService.getInstance(selectedValue).upload(project, selectedDocViews);
                        }

                        return FINAL_CHOICE;
                    }
                }).showInScreenCoordinates(previewToolbarPanel, location);
            }
        });


        // init toolbar
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DocViewMenuToolbar", menuGroup, true);
        toolbar.setTargetComponent(catalogToolbarPane);
        toolbar.getComponent().setBackground(UIUtil.getTextFieldBackground());

        keepToolbarPreferredMinimumSize(toolbar);
        Utils.setSmallerFontForChildren(toolbar.getComponent());

        catalogToolbarPane.setBackground(UIUtil.getTextFieldBackground());
        catalogToolbarPane.add(toolbar.getComponent(), BorderLayout.WEST);

    }

    /**
     * 构造文档
     */
    private void buildDoc() {
        long requestId = loadRequest.incrementAndGet();
        cancelIndicators(loadIndicators);
        showPreviewStatus(DocViewBundle.message("ui.preview.loading"));

        AtomicReference<PreviewLoadResult> result = new AtomicReference<>();
        new Task.Backgroundable(project, DocViewBundle.message("task.docview.preview"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                trackIndicator(loadIndicators, requestId, indicator, loadRequest, project);
                try {
                    result.set(ApplicationManager.getApplication().runReadAction((Computable<PreviewLoadResult>) () -> {
                        indicator.checkCanceled();
                        List<DocView> docs = List.copyOf(DocViewService.getInstance(project, psiClass).buildDoc(psiClass, psiMethod));
                        return new PreviewLoadResult(docs, captureClassPresentations(docs));
                    }));
                } finally {
                    loadIndicators.remove(requestId, indicator);
                }
            }

            @Override
            public void onSuccess() {
                if (isCurrentLoad(requestId)) {
                    try {
                        PreviewLoadResult loaded = result.get();
                        classPresentations = loaded.classPresentations();
                        applyDocViewList(loaded.documents(), requestId);
                    } catch (RuntimeException exception) {
                        handleLoadFailure(requestId, exception);
                    }
                }
            }

            @Override
            public void onCancel() {
                handleLoadCancellation(requestId);
            }

            @Override
            public void onThrowable(@NotNull Throwable throwable) {
                handleLoadFailure(requestId, throwable);
            }
        }.queue();
    }

    private void applyDocViewList(@NotNull List<DocView> loadedDocViewList, long requestId) {
        if (!isCurrentLoad(requestId)) {
            return;
        }

        docViewList = List.copyOf(loadedDocViewList);

        docViewMap = docViewList.stream().collect(Collectors.toMap(DocView::getName, docView -> docView));

        Vector<String> nameVector = docViewList.stream().map(DocView::getName).collect(Collectors.toCollection(Vector::new));

        if (catalogSelectionListener != null) {
            catalogList.removeListSelectionListener(catalogSelectionListener);
        }

        catalogList.setListData(nameVector);

        catalogSelectionListener = catalog -> {

            if (catalog.getValueIsAdjusting() || disposed) {
                return;
            }

            String selectedValue = catalogList.getSelectedValue();

            currentDocView = docViewMap.get(selectedValue);

            if (currentDocView == null) {
                return;
            }

            ClassPresentation presentation = classPresentations.get(currentDocView);
            docNameLabel.setText(presentation == null ? "" : presentation.qualifiedName());

            renderMarkdown(currentDocView);

        };
        catalogList.addListSelectionListener(catalogSelectionListener);

        // 默认选择第一个
        if (!docViewList.isEmpty()) {
            catalogList.setSelectedIndex(0);
        } else {
            currentDocView = null;
            showPreviewStatus(DocViewBundle.message("ui.preview.empty"));
        }
    }

    private void renderMarkdown(@NotNull DocView docView) {
        if (disposed) {
            return;
        }
        long requestId = renderRequest.incrementAndGet();
        cancelIndicators(renderIndicators);
        currentMarkdownText = null;
        showPreviewStatus(DocViewBundle.message("ui.preview.rendering"));

        String cacheKey = markdownCacheKey(docView);
        String cachedMarkdown = markdownCache.get(cacheKey);

        boolean generateHtml = jcefBrowser != null;
        AtomicReference<RenderResult> result = new AtomicReference<>();
        new Task.Backgroundable(project, DocViewBundle.message("task.docview.markdown"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (ApplicationManager.getApplication().isDispatchThread()) {
                    throw new AssertionError("Markdown preview generation must run outside the dispatch thread");
                }
                indicator.setIndeterminate(true);
                trackIndicator(renderIndicators, requestId, indicator, renderRequest, project);
                try {
                    result.set(ApplicationManager.getApplication().runReadAction((Computable<RenderResult>) () -> {
                        indicator.checkCanceled();
                        if (ApplicationManager.getApplication().isDispatchThread()) {
                            throw new AssertionError("Markdown preview generation must run outside the dispatch thread");
                        }
                        String markdownText = cachedMarkdown != null
                                ? cachedMarkdown
                                : DocViewData.markdownText(project, docView);
                        indicator.checkCanceled();
                        String htmlText = generateHtml
                                ? MarkdownUtil.INSTANCE.generateMarkdownHtml(
                                        psiClass.getContainingFile().getVirtualFile(), markdownText, project)
                                : null;
                        indicator.checkCanceled();
                        return new RenderResult(markdownText, htmlText);
                    }));
                } finally {
                    renderIndicators.remove(requestId, indicator);
                }
            }

            @Override
            public void onSuccess() {
                if (isCurrentRender(requestId, docView)) {
                    RenderResult rendered = result.get();
                    try {
                        markdownCache.put(cacheKey, rendered.markdownText());
                        applyMarkdown(docView, rendered.markdownText(), rendered.htmlText(), requestId);
                    } catch (RuntimeException exception) {
                        handleRenderFailure(requestId, exception);
                    }
                }
            }

            @Override
            public void onCancel() {
                handleRenderCancellation(requestId);
            }

            @Override
            public void onThrowable(@NotNull Throwable throwable) {
                handleRenderFailure(requestId, throwable);
            }
        }.queue();
    }

    private void applyMarkdown(@NotNull DocView docView,
                               @NotNull String markdownText,
                               @Nullable String htmlText,
                               long requestId) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (!isCurrentRender(requestId, docView)) {
            return;
        }

        currentMarkdownText = markdownText;

        if (jcefBrowser != null && htmlText != null) {
            jcefBrowser.loadHTML(htmlText);
        }

        setMarkdownText(markdownText);
    }

    private void showPreviewStatus(@NotNull String message) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed) {
            return;
        }
        currentMarkdownText = null;
        setMarkdownText(message);
        if (jcefBrowser != null) {
            jcefBrowser.loadHTML("<!doctype html><html><body><p>" + escapeHtml(message) + "</p></body></html>");
        }
    }

    private static String escapeHtml(@NotNull String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
        return !disposed && !project.isDisposed() && requestId == loadRequest.get();
    }

    private boolean isCurrentRender(long requestId, @Nullable DocView docView) {
        return !disposed && !project.isDisposed()
                && docView != null && requestId == renderRequest.get() && currentDocView == docView;
    }

    private static void trackIndicator(@NotNull ConcurrentMap<Long, ProgressIndicator> indicators,
                                       long requestId,
                                       @NotNull ProgressIndicator indicator,
                                       @NotNull AtomicLong currentRequest,
                                       @NotNull Project project) {
        indicators.put(requestId, indicator);
        if (requestId != currentRequest.get() || project.isDisposed()) {
            indicator.cancel();
        }
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

    private record RenderResult(@NotNull String markdownText, @Nullable String htmlText) {
    }

    private record SingleExportSnapshot(@NotNull String fileName, @NotNull String markdownText) {
    }

    private record AllExportSnapshot(@NotNull String className, @NotNull List<DocView> docViews) {
    }

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
