## Context

`PreviewForm` 是文档预览弹窗的核心 UI。它有两种呈现模式：HTML 富文本预览与 Markdown 源码编辑器。HTML 模式当前通过 Markdown 插件的面板 API 实现：

- `MarkdownHtmlPanelProvider.createFromInfo(...)` + `provider.createHtmlPanel()` 创建 `MarkdownHtmlPanel`；
- `JBCefApp.isSupported()` 判断 JCEF 是否可用；
- `MarkdownUtil.generateMarkdownHtml(...)` 生成 HTML，`markdownHtmlPanel.setHtml(html, 0)` 加载。

IntelliJ 2026.2 重排了模块布局（JetBrains 官方论坛确认）：

1. JCEF 类由 `platform-api` 迁移到独立的 `ui.jcef` 模块（源码路径 `platform/ui.jcef/jcef/JBCefApp.java`）。插件若未显式声明该模块依赖，`com.intellij.ui.jcef.JBCefApp` 无法解析——这是本次编译报错的直接原因。
2. Markdown 预览类（`MarkdownJCEFHtmlPanel` 等）迁移到 `intellij.markdown.jcef` 模块，且 JetBrains 明确表示 `MarkdownHtmlPanel`/面板 API “更像实现细节而非稳定 API”，不建议依赖。

约束：当前 `build.gradle` 使用 IntelliJ Platform Gradle Plugin 2.x，`bundledPlugin('org.intellij.plugins.markdown')`；目标平台需从 `2026.1.4` 适配到 `2026.2+`；项目无自动化测试，验证依赖 `./gradlew build` 与 `runIde` 手工回归。

## Goals / Non-Goals

**Goals:**
- 使插件在 IntelliJ 2026.2+ 编译通过且 HTML 预览功能可用。
- 用平台稳定 API（`com.intellij.ui.jcef.JBCefBrowser`）替换不稳定的 Markdown 面板 API，降低未来再次被破坏的风险。
- 保留“JCEF 不可用时降级为源码视图”的既有用户体验与通知文案。
- 正确管理 JCEF 浏览器生命周期，随弹窗关闭释放资源。

**Non-Goals:**
- 不改动文档生成管线（`DocViewService` → `buildDoc` → `DocViewData`）。
- 不引入 Compose 预览方案（超出范围且需大改）。
- 不改变除预览渲染以外的工具栏、上传、导出、目录等功能。
- 不新增自动化测试框架（沿用手工回归）。

## Decisions

### 决策 1：用 `JBCefBrowser` 直接承载 HTML，弃用 Markdown 面板 API
- **选择**：以 `com.intellij.ui.jcef.JBCefBrowser`（经 `JBCefBrowserBuilder` 或构造器创建）作为 HTML 容器，`browser.getComponent()` 加入 `previewContentPanel`，通过 `browser.loadHTML(html)` 渲染由 `MarkdownUtil.generateMarkdownHtml(...)` 生成的内容。
- **理由**：`JBCefBrowser` 属于平台稳定 API；JetBrains 官方建议不要依赖 Markdown 面板类。这样预览与 Markdown 插件的内部实现解耦，只保留对 `MarkdownUtil`（HTML 生成工具）的使用。
- **替代方案**：
  - *仅补声明 Markdown 面板模块依赖*——改动最小，但仍依赖被官方标注为不稳定的 API，未来升级可能再次报错；已被用户否决。
  - *完全移除 HTML 预览*——丧失核心功能，用户否决。

### 决策 2：声明 JCEF 平台模块依赖
- **选择**：在 `build.gradle` 的 `intellijPlatform { dependencies { ... } }` 中显式声明 JCEF 所在的平台模块（如 `bundledModule(...)`），确保 `com.intellij.ui.jcef.*` 解析；如平台 v2 插件格式要求，在 `plugin.xml` 中补充对应 `<module>` 依赖。
- **理由**：2026.2 模块拆分后，JCEF 不再随 `platform-api` 隐式可用，必须显式声明。
- **替代方案**：反射调用 JCEF——脆弱、丧失编译期检查，弃用。

### 决策 3：保留 `JBCefApp.isSupported()` 能力检测与降级
- **选择**：初始化时以 `JBCefApp.isSupported()` 判定；不支持则不创建浏览器，切换动作被拒并弹出 `notify.not.support.jcef` 通知，仅呈现源码视图。
- **理由**：JCEF 在部分运行时（无 JBR/受限环境）不可用，需与现有行为一致地优雅降级。

### 决策 4：浏览器生命周期由 `Disposer` 管理
- **选择**：创建 `JBCefBrowser` 后用 `Disposer.register(...)` 绑定到弹窗/父 Disposable，弹窗关闭时释放；`JBCefBrowser` 只在支持 JCEF 时创建一次并复用，切换文档时 `loadHTML` 更新内容。
- **理由**：JCEF 浏览器持有本地资源，未显式释放会泄漏；这是官方要求。

## Risks / Trade-offs

- [`JBCefBrowser.loadHTML` 对相对资源/图片的处理与旧面板不同] → HTML 由 `MarkdownUtil` 生成为自包含内容；如遇本地图片路径问题，回归时验证并按需注入 base 标签或使用 data URI。
- [新样式与旧 Markdown 面板主题不完全一致] → 预览为只读富文本，视觉差异可接受；必要时在生成 HTML 时注入基础 CSS。
- [模块依赖声明名称随平台版本变化] → 以 2026.2 实际模块名为准，`runIde` 验证解析成功；在 tasks 中作为首个可验证步骤。
- [降级路径回归遗漏] → 明确覆盖“支持/不支持 JCEF”两条场景的手工回归。

## Migration Plan

1. 升级 `gradle.properties` 平台版本至 2026.2（或验证目标最低版本），`./gradlew build` 复现编译错误。
2. 声明 JCEF 模块依赖，确认 `com.intellij.ui.jcef.*` 可解析。
3. 重构 `PreviewForm` 的 HTML 预览到 `JBCefBrowser`，保留降级与生命周期管理。
4. `./gradlew build` 通过后 `runIde` 手工回归两条路径。
5. 回滚策略：改动集中在 `PreviewForm.java` 与 `build.gradle`，可整体还原到当前提交。

## Open Questions

- 2026.2 中 JCEF 模块的确切依赖标识（`bundledModule` 名称）需在有 SDK 的环境实测确认。
- 是否需要为生成的 HTML 注入与 IDE 主题一致的 CSS（明/暗色）——回归后决定。
