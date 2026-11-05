## [Unreleased]

### Upstream integration — 2026-11-05

- 整合上游 master `877f0c9` 的文档正确性、模板/配置生命周期、后台预览/目录、DTO 缓存、字段表复制、项目文档导出、平台凭据与 HTTP 加固及 IDE API 清理。
- 保留 fork 的 MCP/YApi upsert/详情链接、JSON5、独立 cURL 域名、URL 重写、Proto/gRPC、record、Jackson/contentUsing、Jakarta/javax 和 POJO 模板；不引入欢迎页、双语 UI、图标改版或上游维护流程。
- 平台凭据改用 PasswordSafe，旧 XML 不自动迁移或删除，升级后需重新填写一次；不再将 token 保存到项目 XML。
- 修复合成集合节点与真实业务字段混淆、无 PSI 缓存转换、snake_case 缓存失效、基础请求体缓存跨端点串用及取消被吞；HTTP 活动读取支持取消和完整请求 30 秒时限。
- YApi 保留直接数组与分页对象响应，拒绝缺字段和重复满页，避免误创建和无限翻页。


### Added

- 新增本地 MCP 工具 `upload_yapi_api_doc`：按已打开项目中的 Java Controller 类或方法引用上传 API 文档到
  YApi，按 HTTP 方法和路径更新已有接口或创建缺失接口；该工具仅支持 YApi，不支持 ShowDoc 或其他文档平台。
- 新增项目级 cURL 域名配置（Settings → Doc View → cURL 域名），Copy cURL 与生成文档相互独立：
  - **Copy cURL 域名**：右键 Copy cURL 复制到剪贴板时替换 `{{host}}`，默认 `http://localhost:8080`（原为硬编码）
  - **文档 cURL 域名**：预览 / 导出 / 上传的文档中 curl 示例使用的域名，默认 `{{host}}` 即保持占位符不变，输出与之前完全一致
  - **gRPC cURL 域名**：右键 Copy gRPC cURL 使用的域名，默认 `http://localhost:9090`
  - 配置值原样使用，不做 URL 校验，可填 `{{order-web}}` 这类网关标识；留空回退到默认值；自动去掉域名末尾多余的 `/`
- 支持在 .proto 文件的 message 上右键 Doc View，解析 message 结构生成字段表与 JSON 示例；基于 Protocol Buffers 插件 PSI 解析，支持嵌套 message、跨文件 import、repeated、map、oneof、enum 及 google.protobuf well-known types，并对递归 message 做终止保护

### Changed

- Copy gRPC cURL 的默认域名由 `localhost:9090` 改为 `http://localhost:9090`；需要不带 scheme 的形式可在设置中填 `localhost:9090`

### Removed

- 移除安装/升级后的默认推广通知及其版本跟踪状态；不再自动提示前往 GitHub 点 Star 等，上传结果和错误通知保留。

- 移除 Doc View 专属 JavaDoc 标签的读取、生成和配置入口；标题、名称、必填仅使用业务代码中已有的标准注释/注解。
- 移除 Doc Editor 源码写回及自动保存业务文件；编辑只作用于文档草稿，复制/导出/上传保留。Proto2 必填改用内存模型元数据，不生成标签。

### Fixed

- 标准必填注解回归覆盖 `javax/jakarta` 的 `@NotNull/@NotEmpty/@NotBlank`；补齐 Dubbo 顶层参数识别，并修复字段/record 数组解析的类型强转异常，保持业务源码只读；数组 JSON5 示例保留维度与数字/布尔类型。

- 修复预览目录切换时在 EDT 无读锁访问 PSI 类名的异常；类名在后台读锁内冻结，批量导出也使用同一快照，刷新不再累积选择监听器。

- 修复 Spring 完整 `ValueConstants.DEFAULT_NONE` 被当作业务默认值的问题，统一 RequestParam/Header 必填、示例与默认值描述解析；回归使用真正编译的无源码注解 JAR。
- YApi 查重和详情定位改为按分类分页扫描，支持保存响应返回有效接口 ID；定位失败时通知与 MCP 明确警告，不再静默显示分类链接。

- 修复在 proto message 上右键 Doc View 报错的问题：原实现把 .proto 源码当作二进制 FileDescriptorProto 解析，必然失败且忽略光标位置
- 修复 proto message 名称不符合 POJO 命名约定（如 Order）时被误路由、提示 "在此处不支持使用 Doc View" 的问题
- .proto 文件中隐藏 Doc Editor：其编辑结果需以 JavaDoc 写回源码，对合成类无意义
- 修复 protobuf 生成类的标题不含 `<pre>` 标签时抛出 StringIndexOutOfBoundsException 的问题
- 修复校验失败给出通知后 Preview / Upload 动作仍继续执行导致空指针的问题

## 1.3.11

### Added

- 修改 DocViewData 类以特殊处理集合和 map 类型的参数，保证在展示时忽略 Map 和 List 的属性

### Changed

### Deprecated

### Removed

### Fixed

### Security
