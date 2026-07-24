### Requirement: HTML 预览基于平台稳定 JCEF API 渲染

文档预览的 HTML 视图 SHALL 通过平台稳定的 `com.intellij.ui.jcef.JBCefBrowser` 承载与渲染，HTML 内容 SHALL 由 `MarkdownUtil.generateMarkdownHtml(...)` 生成后经 `JBCefBrowser` 加载。系统 MUST NOT 依赖 Markdown 插件的 `MarkdownHtmlPanel` 或 `MarkdownHtmlPanelProvider` 面板 API。

#### Scenario: 在 2026.2+ 平台渲染 HTML 预览
- **WHEN** 用户在支持 JCEF 的 IntelliJ 2026.2+ 中打开文档预览并切换到 HTML 视图
- **THEN** 预览面板通过 `JBCefBrowser` 显示由 Markdown 转换而来的 HTML 富文本

#### Scenario: 切换文档时更新预览内容
- **WHEN** 用户在目录中选择另一个文档条目且当前为 HTML 视图
- **THEN** 同一个 `JBCefBrowser` 实例通过 `loadHTML(...)` 加载新文档对应的 HTML，无需重建浏览器

### Requirement: JCEF 能力检测与源码视图降级

系统 SHALL 在初始化时通过 `JBCefApp.isSupported()` 检测 JCEF 可用性。当 JCEF 不可用时，系统 MUST NOT 创建浏览器，SHALL 仅呈现 Markdown 源码编辑器视图，并 SHALL 在用户尝试切换到 HTML 预览时拒绝切换并提示 `notify.not.support.jcef` 通知。

#### Scenario: JCEF 不受支持时降级为源码视图
- **WHEN** 在不支持 JCEF 的运行环境中打开文档预览
- **THEN** 预览面板呈现 Markdown 源码编辑器视图，且不抛出异常、不加载浏览器

#### Scenario: 不支持 JCEF 时拒绝切换到 HTML 预览
- **WHEN** JCEF 不可用且用户点击 HTML 预览切换按钮
- **THEN** 系统保持源码视图，预览状态保持为非 HTML，并弹出"当前 IDEA 版本不支持 JCEF，无法预览"的通知

### Requirement: JCEF 浏览器生命周期管理

创建的 `JBCefBrowser` 实例 SHALL 通过 `Disposer` 注册到预览弹窗/父 Disposable，并 SHALL 在弹窗关闭时释放，避免本地资源泄漏。

#### Scenario: 关闭预览弹窗释放浏览器资源
- **WHEN** 用户关闭文档预览弹窗且此前创建过 `JBCefBrowser`
- **THEN** 该浏览器实例被 `Disposer` 释放，不产生资源泄漏

### Requirement: 插件声明 JCEF 平台模块依赖

插件构建 SHALL 显式声明 IntelliJ 2026.2+ 中 JCEF 类所在的平台模块依赖，使 `com.intellij.ui.jcef.*` 类在编译期与运行期均可解析。

#### Scenario: 在 2026.2+ 编译通过
- **WHEN** 在目标平台 2026.2+ 上执行 `./gradlew build`
- **THEN** 构建成功，`com.intellij.ui.jcef.JBCefApp` 与 `JBCefBrowser` 被正确解析，无"找不到类"错误
