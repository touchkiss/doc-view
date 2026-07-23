## Why

从 IntelliJ 2026.2 开始，平台重排了模块布局：JCEF 相关类（`JBCefApp`、`JBCefBrowser`）从 `platform-api` 迁移到独立的 `ui.jcef` 模块，Markdown 预览类（`MarkdownJCEFHtmlPanel` 及 `MarkdownHtmlPanel`/`MarkdownHtmlPanelProvider` 面板 API）迁移到 `intellij.markdown.jcef` 模块并被 JetBrains 官方标注为“实现细节而非稳定 API”。`PreviewForm` 直接引用 `com.intellij.ui.jcef.JBCefApp` 且依赖 Markdown 插件的面板 API，升级后编译报错、文档 HTML 预览无法使用。

## What Changes

- 在 `build.gradle` 中显式声明 JCEF 所需的平台模块依赖，使 `com.intellij.ui.jcef.*` 类在 2026.2+ 可正常解析。
- **BREAKING**（内部实现）：`PreviewForm` 弃用 Markdown 插件的 `MarkdownHtmlPanel` / `MarkdownHtmlPanelProvider`，改用平台稳定的 `com.intellij.ui.jcef.JBCefBrowser` 自行承载并渲染 HTML。
- HTML 内容仍由 `MarkdownUtil.generateMarkdownHtml(...)` 生成，加载方式改为 `JBCefBrowser.loadHTML(...)`。
- 保留 `JBCefApp.isSupported()` 作为能力开关：不支持 JCEF 时优雅降级为 Markdown 源码编辑器视图（沿用现有行为与通知）。
- 正确管理 `JBCefBrowser` 生命周期（随弹窗/组件销毁而 `Disposer` 释放），避免泄漏。

## Capabilities

### New Capabilities
- `markdown-preview`: 文档预览面板在 HTML 渲染视图与 Markdown 源码视图之间切换的行为，包含 JCEF 能力检测、降级策略与浏览器生命周期管理。

### Modified Capabilities
<!-- 无既有 capability 的需求变更 -->

## Impact

- 代码：`src/main/java/com/liuzhihang/doc/view/ui/PreviewForm.java`（预览面板构建、切换、内容更新逻辑）。
- 构建：`build.gradle` 平台模块依赖声明；可能涉及 `plugin.xml` 的模块依赖。
- 依赖：新增对 `com.intellij.ui.jcef.JBCefBrowser` 的直接使用；移除对 Markdown 插件面板 API（`MarkdownHtmlPanel`/`MarkdownHtmlPanelProvider`）的依赖，保留 `MarkdownUtil` 用于 HTML 生成。
- 平台：目标平台从 2026.1.4 升级适配至 2026.2+。
- 资源：沿用 `DocViewBundle` 中 `notify.not.support.jcef` 文案。
