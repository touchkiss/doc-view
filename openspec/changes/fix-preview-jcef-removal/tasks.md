## 1. 复现与依赖声明

- [x] 1.1 将 `gradle.properties` 的 `platformVersion` 升级到 2026.2；确认根因：本地 IDE 为 2026.2(IU-262.8665.258)，JCEF 已拆分至捆绑插件 `com.intellij.modules.jcef`（module `intellij.platform.ui.jcef`），未声明依赖导致 `JBCefApp` 无法解析
- [x] 1.2 在 `build.gradle` 的 `intellijPlatform` 依赖块中声明 `bundledPlugin('com.intellij.modules.jcef')`
- [x] 1.3 在 `plugin.xml` 中补充 `<depends>com.intellij.modules.jcef</depends>`
- [x] 1.4 `./gradlew compileJava` 与 `buildPlugin` 均 BUILD SUCCESSFUL，`JBCefApp`/`JBCefBrowser` 可解析。（预存阻塞：Lombok 1.18.36 在 JDK 25 崩溃，已按用户确认升级到 1.18.42 解除）

## 2. PreviewForm 迁移到 JBCefBrowser

- [x] 2.1 移除对 `MarkdownHtmlPanel`/`MarkdownHtmlPanelProvider`/`MarkdownSettings` 的 import 与字段，改为持有 `JBCefBrowser jcefBrowser` 字段
- [x] 2.2 新增 `initHtmlBrowser()`：`JBCefApp.isSupported()` 为真时经 `JBCefBrowser.createBuilder().build()` 创建浏览器，否则不创建
- [x] 2.3 在 `popup()` 中 `Disposer.register(popup, jcefBrowser)`，弹窗关闭时释放
- [x] 2.4 `choosePreviewPanel()` HTML 分支改为 `jcefBrowser.getComponent()`（条件 `previewIsHtml.get() && jcefBrowser != null`）
- [x] 2.5 `previewLeftToolbar()` 切换逻辑：`jcefBrowser == null` 时保持源码视图并弹 `notify.not.support.jcef`；否则在浏览器组件与源码滚动面板间切换
- [x] 2.6 `buildDoc()` 选中文档时：`jcefBrowser != null` 则用 `MarkdownUtil.generateMarkdownHtml(...)` 生成 HTML 并 `jcefBrowser.loadHTML(html)`；否则仅更新源码文档

## 3. 验证

- [x] 3.1 `./gradlew buildPlugin` 编译通过，无 JCEF/Markdown 面板相关未解析引用；产物 `build/distributions/doc-view-1.4.99.zip`，打包后的 `plugin.xml` 含 `<depends>com.intellij.modules.jcef</depends>`
- [ ] 3.2 【需人工】`./gradlew runIde` 手工回归：支持 JCEF 环境下打开预览、切换 HTML 视图、切换不同文档条目均正常渲染
- [ ] 3.3 【需人工】手工回归降级路径：验证不支持 JCEF 时呈现源码视图、切换 HTML 被拒并提示通知
- [ ] 3.4 【需人工】回归工具栏其余功能（上传、导出、复制、目录、Pin/Setting）不受影响
- [ ] 3.5 【需人工】确认关闭弹窗后无 JCEF 资源泄漏告警
