# Doc View

从 Java 源码生成接口文档的 IntelliJ IDEA 插件。无需启动业务服务，即可在 IDE 中查看参数结构、生成 Markdown 与请求示例，并上传到 YApi、ShowDoc 或语雀。

本仓库基于 [liuzhihang/doc-view](https://github.com/liuzhihang/doc-view) 扩展维护，包含本地 MCP、项目文档导出、JSON5 与 cURL 等能力。本文描述当前分支；上游插件市场版本的功能与兼容范围可能不同。

## 功能

| 场景 | 支持内容 |
| --- | --- |
| 接口解析 | Spring MVC Controller、Feign 接口、Dubbo 服务 |
| 数据结构 | POJO、record；安装 Protocol Buffers 插件后支持 Proto message 结构解析 |
| 源码元数据 | 标准 JavaDoc、Swagger / OpenAPI、Validation、Spring 参数注解 |
| 源码注释 | Java Javadoc、Kotlin KDoc、Proto 注释、Java 批量和包注释、模板、词典、选区翻译与 AI 项目术语 |
| 文档输出 | Markdown 预览、复制、单独或批量导出；JSON 与带字段说明的 JSON5 示例 |
| 原生 HTTP Client 增强 | `.http` / `.rest` 请求体 JSON5 编辑、单条运行、Run All、Convert to cURL and Copy |
| 请求示例 | Spring / Feign 的 cURL、IDEA HTTP Client 请求和 Postman Collection；Proto RPC 的 gRPC 请求文本 |
| 模板 | Spring、Dubbo、POJO 三类可定制模板，提供变量目录和示例预览 |
| 平台集成 | YApi、ShowDoc、语雀（YuQue） |
| Agent 接入 | 通过本机 MCP 服务将指定 Java Controller 文档上传到 YApi |

预览、复制和导出接口文档时只读取源码。通过预览工具栏的 **重新生成注释**，可以选中 Java 接口说明、请求参数或请求/响应字段，生成并编辑候选说明，确认后写回对应 Javadoc 并刷新预览。`Doc Editor` 中的描述、必填等编辑仅用于复制 Markdown 草稿，不写回 JavaDoc、Swagger / OpenAPI 注解，也不自动保存业务源码。复制 JSON 仍按类型结构生成示例。

## 生成源码注释

- 在编辑器右键选择 **Doc View 源码注释 → 生成注释或翻译选区**。光标所在的 Java 或 Kotlin 声明会生成文档注释。选中文本时可查看翻译结果，或将中文替换为英文命名。Java 支持多光标、record 组件和后台批量生成，Proto 支持单项和批量成员注释，Java 源码目录支持生成 `package-info.java`。
- 在 **设置 → 工具 → Doc View → 源码注释与 AI** 配置模板、词典、翻译及大模型，可导入原插件导出的 `easy_javadoc.json`。已有注释默认跳过，可设为补齐缺失标签或强制覆盖。默认使用 `Ctrl+\`（macOS 也可用 `⌘+\`），快捷键可在 Keymap 中配置。通用大模型支持填写基础地址（如 `http://127.0.0.1:8317/v1`）或完整的 `/chat/completions` 地址。本地服务无需认证时，密钥可留空。
- 在 **工具 → Doc View 源码注释 → AI 分析项目术语** 启动分析。确认后，插件抽取项目 Java/Kotlin 源码中的最多 400 个声明标识符，向配置的模型请求候选术语。可以编辑、勾选并逐条保存到当前项目或全局。项目映射按本机项目目录区分，生成注释时优先采用项目释义。已有映射默认不勾选。

源码注释功能基于 [Easy Javadoc](https://github.com/starcwang/easy_javadoc/tree/4facf16a83093308da805ca9792bdffc07296139) 4.5.9，另包含本地分支的多光标、record、Proto 和后台批量生成能力。插件内附 Apache-2.0 许可与来源说明。

## 安装与环境

### 安装当前分支构建

1. 获取本分支构建的插件 ZIP，或按下文自行构建。
2. 在 IDE 中打开 **Settings / Preferences → Plugins → 齿轮菜单 → Install Plugin from Disk…**。
3. 选择 `build/distributions/` 下的 ZIP，按 IDE 提示完成安装。

当前构建配置使用 Java 25，并依赖本机 `/Applications/IntelliJ IDEA.app`，目标平台为 IntelliJ IDEA Ultimate 2026.3。插件声明最低 build 为 `263`，未设置上限；该声明不代表所有后续 IDE 版本均已验证兼容。

插件依赖 Java、Markdown 和 JCEF 模块；Protocol Buffers 为可选依赖。当前开发版本号为 `1.99.99`，用于避免被上游市场版本覆盖，不代表上游发布版本。

如需安装上游版本，可在 Plugins 中搜索 `Doc View`，或访问 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/15305-doc-view)。本分支新增功能请使用对应分支构建。

## 快速使用

### 查看、复制和导出

1. 在已打开的 Java 项目中等待 IDE 索引完成。
2. 将光标放在受支持的类或方法上，右键选择 **Doc View**。
3. 在预览中查看参数表和请求、返回示例，使用工具栏复制、导出或上传。

右侧 **Doc View** 工具窗口提供按模块、类、方法组织的文档目录，可刷新、跳转源码和批量操作。若菜单未出现，先检查光标位置、类或方法是否受支持，以及索引是否完成。

需要调整文档草稿或复制实体 JSON 时，使用右键菜单 **Doc Editor**。

### 生成请求示例

将光标放在 Spring / Feign 接口方法上，右键选择 **Copy As**：

- **cURL**：复制 cURL 命令，地址使用项目设置中的 Copy cURL 域名。
- **REST Request (JSON)**：复制可粘贴到 IDEA `.http` 文件的请求，地址使用 `{{host}}` 变量。
- **REST Request (JSON5)**：同样生成 `.http` 请求，并将请求体字段描述输出为注释。
- **Postman**：复制仅包含当前接口的 Postman Collection JSON。打开 Postman 的 **Import**，粘贴并导入，再为 `{{host}}` 设置实际地址。JSON 请求体使用标准 JSON 加字段说明注释；Postman 的 raw JSON 模式发送前会移除注释。

在 `.proto` 文件的 RPC 方法上右键，**Copy As** 提供：

- **cURL**：生成 `curl -X GRPC` 文本，供支持此语法的客户端使用；它不是 `grpcurl` 命令，普通 cURL 不能直接用它完成原生 gRPC 调用。
- **REST Request (JSON)**：生成 IDEA HTTP Client 的 `GRPC` 请求，地址使用项目设置中的 gRPC cURL 域名。
- **REST Request (with field comments)**：生成同样的 gRPC 请求，并在请求前列出可识别的字段说明；请求体保持标准 JSON。

目前 gRPC 不支持复制为 Postman Collection。gRPC 示例沿用现有 Proto 解析逻辑，嵌套消息、跨文件引用等复杂结构可能无法完整展开；生成后请核对请求体。IDEA 中运行 gRPC 请求还需启用相应的 HTTP Client、Protocol Buffers 和 gRPC 支持。

在 Doc View 工具窗口目录中也可右键选择 **HTTP Client** 生成请求文件。生成的参数值均为示例，执行前需按实际环境填写。

三个域名可在 Doc View 常规设置中分别配置：

| 配置 | 用途 | 留空时的默认值 |
| --- | --- | --- |
| Copy cURL 域名 | 右键复制的 HTTP cURL | `http://localhost:8080` |
| 文档 cURL 域名 | 预览、导出及上传文档中的 cURL 示例 | `{{host}}` 占位符 |
| gRPC cURL 域名 | 右键复制的 gRPC cURL 和 HTTP Client 请求 | `http://localhost:9090` |

HTTP REST Request 和 Postman 输出保留 `{{host}}`，不使用以上域名设置。

### 在 HTTP 文件中使用 JSON5

本分支已合入 REST Client X：启用 IDEA 原生 HTTP Client 后，普通 HTTP 请求的 JSON 请求体直接支持注释、单引号、无引号键和尾逗号，无需额外标记。原生单条运行、**Run All Requests in File** 与左上角 **Convert to cURL and Copy** 均转换为标准 JSON，源文件注释保留。

功能随 Doc View 一起安装；安装前请卸载独立的 REST Client X，安装后重启 IDEA，避免重复接管原生执行和复制动作。HTTP Client 是可选依赖，未启用时不加载该增强。示例与已知边界见 [HTTP JSON5 使用说明](docs/user-guide/http-json5.md)。

### 导出项目文档

在 Doc View 工具窗口选择 **Export → Export Project Docs**，扫描当前项目中可导出的接口，并写入项目根目录：

```text
docs/doc-view/
├── index.md       # 接口索引
└── apis/          # 接口 Markdown 页面
```

项目导出与普通 Markdown 导出不同，输出位置固定为 `docs/doc-view/`。导出器会检查已有文件的生成标记和内容摘要；目标文件存在冲突时取消覆盖并提示。需要手工维护的说明建议放在其他文件中。

## 设置与模板

项目设置入口为 **Settings / Preferences → Tools → Doc View**，包含常规行为、生成规则、模板与输出、平台集成。界面名称会随 IDE 语言变化。

- **常规设置**：快捷图标、扫描行为、cURL 域名和 URL 重写规则。
- **Generation Rules**：配置名称、描述、必填信息、字段名等生成规则，读取标准注释和注解；不再使用插件专属 JavaDoc 标签。
- **Template and Output**：编辑 Spring、Dubbo、POJO 模板，设置独立实体、层级前缀和合并导出。
- **Platform Integrations**：分别配置当前项目的 YApi、ShowDoc 和语雀。

模板支持 `${DocView.name}`、`${DocView.requestJson5}`、`${DocView.curlExample}` 等变量，也可遍历结构化参数列表自定义表格。变量说明、预览方法和完整示例见 [Markdown 模板教程](docs/user-guide/templates.md)。

自定义模板用于预览、复制、导出及 ShowDoc、语雀上传。YApi 的请求字段、返回结构和描述正文由独立逻辑生成，修改模板不会改变 YApi 上传布局。

## 上传到文档平台

先在 **Doc View → Platform Integrations** 下配置目标平台，再通过编辑器右键 **Doc Upload**、预览工具栏或工具窗口上传。

| 平台 | 需要配置 |
| --- | --- |
| YApi | 服务地址、项目 ID、项目 Token |
| ShowDoc | 服务地址、API Key、API Token |
| 语雀 | 站点地址、API 地址、团队或用户标识、知识库标识、Token |

集成凭据与其他设置一起保存在当前项目的 `.idea/DocViewYApiSettings.xml`、`DocViewShowDocSettings.xml`、`DocViewYuQueSettings.xml` 中，不使用 PasswordSafe。密码框保留掩码；Reset 读取已保存值，留空后 Apply 即清除，取消不保存草稿。旧 XML 中的字段可直接读取；仅存于 PasswordSafe 的凭据需重新填写一次。

## 本地 MCP 接入

插件提供 Streamable HTTP MCP 服务，供 Agent 调用 IDE 内的解析和 YApi 上传能力。它依赖正在运行的 IDE、已打开的目标项目及该项目的 YApi 配置。

### 连接服务

在设置中搜索 **MCP Server**，检查启用状态和端口。服务默认启用，端口为 `12333`，端点为：

```text
http://127.0.0.1:12333/mcp
```

支持 `mcpServers` 配置格式的客户端可参考：

```json
{
  "mcpServers": {
    "doc-view": {
      "url": "http://127.0.0.1:12333/mcp"
    }
  }
}
```

保存端口或启用状态后服务会按新配置重启；客户端 URL 也需同步调整。客户端配置格式以其自身要求为准。

### 上传工具

服务仅提供 `upload_yapi_api_doc`，必填参数如下：

| 参数 | 含义 |
| --- | --- |
| `projectPath` | 已在当前 IDE 中打开的项目根目录绝对路径；不会自动打开任意目录 |
| `reference` | Java 类的全限定名，或 `全限定类名#方法名` |

工具参数示例：

```json
{
  "projectPath": "/path/to/your-project",
  "reference": "com.example.OrderController#list"
}
```

使用类级引用（如 `com.example.OrderController`）会展开处理该类全部受支持的接口方法；方法级引用仅处理指定方法。同名重载方法会返回引用歧义错误，目前不支持用参数签名消除歧义。

工具按 HTTP 方法和路径匹配 YApi 接口，匹配时更新，否则创建。结果分别返回 `created`、`updated`、`skipped`、`failed`；批量处理中的单个方法失败不会中止其他方法。工具会实际写入该项目配置的 YApi，不支持通过 MCP 上传到 ShowDoc 或语雀。

服务不使用 MCP Token，仅绑定 `127.0.0.1`。请保持本机使用，不要通过端口转发、反向代理或共享网络暴露服务。YApi 项目 Token 仍需在 IDE 内配置。

## 本地开发

准备 JDK 25 和本地 IntelliJ IDEA。`build.gradle` 中的 `localIde` 当前指向 macOS 安装路径；其他安装位置需先调整该配置。构建使用仓库自带的 Gradle Wrapper。

```bash
# 启动加载插件的沙箱 IDE
./gradlew runIde

# 构建可安装 ZIP，输出到 build/distributions/
./gradlew buildPlugin

# 运行主测试及独立解析回归测试
./gradlew test constantValueTest contentUsingTest

# 对配置的本地 IDE 执行插件兼容性检查
./gradlew verifyPlugin
```

`verifyPlugin` 默认使用离线模式并检查配置的本地 IDE；通过该检查不等同于覆盖所有 JetBrains 产品和 IDE 版本。

## 项目来源与许可

- 上游项目：[liuzhihang/doc-view](https://github.com/liuzhihang/doc-view)
- 本仓库：[touchkiss/doc-view](https://github.com/touchkiss/doc-view)
- 更新记录：[CHANGELOG.md](CHANGELOG.md)
- 开源许可：[MIT](LICENSE)

感谢原作者 Liu ZhiHang 以及所有参与开发、反馈和改进的贡献者。
