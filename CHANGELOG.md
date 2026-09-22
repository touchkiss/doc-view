## [Unreleased]

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

### Fixed

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
