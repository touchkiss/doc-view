# YApi MCP 能力设计

## 1. 背景与目标

为 doc-view IntelliJ 插件增加 MCP 能力，使本机 Agent 可以根据 Java 类或方法定位直接生成并上传 YApi 接口文档。

支持的定位格式：

- `com.example.SomeController`
- `com.example.SomeController#list`

类级定位上传该 Controller 中全部可识别的接口方法；方法级定位只上传指定方法。插件复用现有 PSI 解析、`DocView` 生成和 YApi 上传能力，不新增第二套文档解析流程。

第一版只支持 YApi。

## 2. 已确认的约束

- MCP 传输使用 Streamable HTTP。
- MCP Server 由 IntelliJ 插件内置并作为全局 application service 启动。
- Server 只监听 `127.0.0.1`，不增加 MCP Token 或其他鉴权逻辑。
- 调用参数显式传入 `projectPath`，用于选择已打开的 IntelliJ Project。
- YApi 地址、项目 ID 和 YApi Token 只从目标项目的 `YApiSettings` 读取。
- 已存在相同 `HTTP method + path` 的 YApi 接口时更新，不重复创建。
- 批量上传采用部分成功策略：单个方法失败不影响其他方法。

## 3. 总体架构

```text
本机 Agent
    |
    | Streamable HTTP /mcp
    v
IntelliJ application service: MCP Server
    |
    | projectPath 路由
    v
已打开的 IntelliJ Project
    |
    +-- PSI reference resolver
    +-- DocView 文档生成服务
    +-- YApiSettings
    +-- YApi upload service / facade
    v
YApi
```

MCP Server 只负责 MCP 生命周期、参数校验、项目路由和结果封装，不直接解析 PSI 或拼装 YApi 请求。业务逻辑放在独立的应用服务中，便于单元测试和后续扩展其他文档平台。

全局 Server 使用固定或动态可配置端口；具体端口配置和 Agent 连接信息属于实现阶段需要落地的配置细节，但不得改变上述传输和路由模型。

## 4. MCP 工具契约

第一版只提供一个工具：

```text
upload_yapi_api_doc
```

输入：

```json
{
  "projectPath": "/workspace/ecommerce-order",
  "reference": "com.beeto.api.ecommerceorder.entrypoint.controller.order.rest.OrderAdminOpsController#list"
}
```

字段规则：

- `projectPath`：必填，必须对应当前已打开的 IntelliJ Project；路径需要规范化后比较。
- `reference`：必填，必须是完整类名，或完整类名加 `#method`。
- 方法重载时需要识别为歧义，不能静默选择任意重载；第一版可返回 `REFERENCE_AMBIGUOUS`，后续再扩展参数签名定位。

结果使用结构化对象，至少包含：

```json
{
  "projectPath": "/workspace/ecommerce-order",
  "reference": "...#list",
  "created": [],
  "updated": [
    {
      "reference": "...#list",
      "status": "updated",
      "yapiUrl": "https://yapi.example.com/project/1/interface/api/123"
    }
  ],
  "skipped": [],
  "failed": []
}
```

每个方法结果都应包含稳定的 reference、状态和必要的 YApi URL；失败结果包含机器可判断的错误码和面向 Agent 的简短原因。

## 5. 执行流程

1. MCP Server 校验输入字段。
2. 根据 `projectPath` 查找已打开的 IntelliJ Project；未找到时返回 `PROJECT_NOT_OPEN`。
3. 解析 reference：
   - 类级 reference：枚举类中全部可识别的接口方法；
   - 方法级 reference：只定位指定方法；
   - 未找到返回 `REFERENCE_NOT_FOUND`，重载无法唯一确定时返回 `REFERENCE_AMBIGUOUS`。
4. 在正确的 IntelliJ PSI 读线程中生成每个方法的 `DocView`。
5. 校验目标项目 YApi 配置；缺失时返回 `YAPI_NOT_CONFIGURED`。
6. 复用现有分类创建、请求体生成和 YApi 上传逻辑。
7. 上传前以 `HTTP method + path` 查询目标项目接口：
   - 找到接口：将接口 ID 带入保存请求，更新已有接口；
   - 未找到接口：创建新接口。
8. 按方法逐个执行，收集 `created`、`updated`、`skipped` 和 `failed`。
9. 返回结构化结果，不因单个方法失败而中止剩余方法。

PSI 访问、文档生成和网络请求不得阻塞 IntelliJ UI 线程。实现阶段需要明确读线程、后台任务和上传并发策略；第一版建议按方法顺序串行处理，优先保证结果稳定和 YApi 写入可控。

## 6. 现有代码复用与必要改造

优先复用：

- `YApiSettings`：项目级 YApi 配置；
- `YApiServiceImpl`：`DocView` 到 YApi 数据的转换和上传流程；
- `YApiFacadeService` / `YApiFacadeServiceImpl`：YApi HTTP 访问；
- 现有 Spring Controller、方法和参数解析服务；
- `YApiInterfaceUrlResolver`：上传后接口 URL 解析。

预计需要新增或调整的边界：

- MCP application service 和 Streamable HTTP transport 适配层；
- reference 解析服务，将全限定类名/方法名解析为 PSI 元素；
- 面向 MCP 的上传编排服务，负责批量、逐方法结果和错误隔离；
- YApi 更新查询与保存模型，使已有接口 ID 能被保存请求可靠使用；
- 项目路径到已打开 Project 的路由器；
- MCP 结构化结果 DTO 和错误码。

不得让 MCP 层复制 `YApiServiceImpl` 内部的文档字段转换逻辑。如果现有上传服务过度依赖 UI 通知，应抽取无 UI 的核心上传结果，让手工 Action 和 MCP 工具共享该核心能力。

## 7. 错误处理

统一错误码：

- `INVALID_ARGUMENT`
- `PROJECT_NOT_OPEN`
- `REFERENCE_NOT_FOUND`
- `REFERENCE_AMBIGUOUS`
- `UNSUPPORTED_CONTROLLER`
- `YAPI_NOT_CONFIGURED`
- `DOC_GENERATION_FAILED`
- `YAPI_REQUEST_FAILED`
- `YAPI_RESPONSE_INVALID`

MCP 协议级错误仅用于请求无法解析、工具不存在或 Server 不可用等情况。业务失败应作为结构化工具结果返回，方便 Agent 继续处理其他方法。

错误信息不得包含 YApi Token、完整敏感请求头或其他配置秘密。插件日志可以记录诊断上下文，但同样不得输出 Token。

## 8. 安全与生命周期

- 只绑定回环地址 `127.0.0.1`；
- 不设计 MCP Token、鉴权 Header 或登录流程；
- `projectPath` 只能路由到当前已打开的项目，不自动打开任意目录；
- 不允许 MCP 请求覆盖 YApi URL、Project ID 或 YApi Token；
- 插件加载时启动 Server，插件卸载或 IDE 关闭时释放端口和资源；
- Server 需要处理重复启动、端口占用和正常关闭，不能影响 IDE 启动。

## 9. 测试策略

单元测试：

- 类级和方法级 reference 解析；
- 不存在的类/方法、重载歧义和不支持的 Controller；
- projectPath 规范化、合法项目和未打开项目；
- YApi 新建、更新、分类创建、分页查询和异常响应；
- 结构化错误码和逐方法结果。

集成测试：

- MCP 初始化和工具发现；
- Streamable HTTP 请求、参数校验和并发请求；
- 类级批量上传的部分成功、全部失败和稳定执行顺序；
- PSI 读线程约束；
- 插件关闭时 HTTP Server 释放；
- 既有手工 YApi 上传流程回归。

## 10. 非目标

第一版不包含：

- ShowDoc、语雀或其他平台 MCP 能力；
- 自动打开项目或自动导入外部目录；
- 远程 HTTP 访问；
- MCP Token 和用户认证；
- 通过参数签名选择重载方法；
- 删除 YApi 接口、批量清理或双向同步。

## 11. 设计依据

MCP 官方当前将 stdio 定位为本地子进程集成，将 Streamable HTTP 定位为常驻/远程服务；传统 HTTP+SSE 属于兼容旧客户端的 legacy 传输。Java SDK 已提供 Streamable HTTP Server/Client 实现。

- https://blog.modelcontextprotocol.io/posts/2025-12-19-mcp-transport-future/
- https://ts.sdk.modelcontextprotocol.io/server
- https://java.sdk.modelcontextprotocol.io/latest/server/
