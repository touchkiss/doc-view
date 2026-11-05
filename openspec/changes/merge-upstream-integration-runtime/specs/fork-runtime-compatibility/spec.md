## ADDED Requirements

### Requirement: 冷热缓存与容器输出保留本地语义
缓存 SHALL 随源码、snake_case、模板与影响输出的设置失效。请求缓存 SHALL 区分接口和参数身份，避免同类型请求串用参数名/必填。无 PSI 副本 SHALL 保留合成层标记；List 只解包显式合成 element，Map 使用 additionalProperties，真实 element/key/value 字段及 contentUsing 的类型/默认值 SHALL 保留。Clear Cache SHALL 只清当前项目内存。

#### Scenario: 同类型接口交叉命中
- WHEN 两个端点使用同类型的基础请求体但参数名和必填不同，并交叉重复生成
- THEN 每个端点 SHALL 始终保留自己的参数信息，Markdown/JSON5/YApi schema SHALL 不串用

#### Scenario: 无 PSI 副本
- WHEN 对包含集合、Map、record 或 contentUsing 的模型复制缓存快照再上传
- THEN schema SHALL 与冷缓存生成等价，业务字段 SHALL 不被合成层识别误删

### Requirement: 预览后台生成与生命周期
预览 SHALL 在后台生成和渲染，仅接受最新回调；关闭 SHALL 释放 editor/JCEF 并拒绝回投；缺 JCEF SHALL 保留源码预览。

#### Scenario: 生成过程中关闭
- WHEN 用户关闭预览后旧任务完成
- THEN 旧任务 SHALL 不再发布结果，资源 SHALL 已释放

### Requirement: MCP 和 UI 共用本地 YApi upsert
UI 与 MCP SHALL 共用本地编排，保留查重 ID、created/updated、详情链接、JSON5/cURL 正文和结构化错误分类。PSI SHALL 在 ReadAction 中取值，网络与 PasswordSafe SHALL 在锁外后台执行。Token SHALL 只在 PasswordSafe 与单次上传瞬态快照中使用，不写入项目 XML；旧 XML 按已批准策略不自动迁移，用户需重新填写凭据。

#### Scenario: 新增与更新接口
- WHEN 接口已存在或尚不存在
- THEN 编排 SHALL 分别携带已有 ID 更新或创建，并保留详情链接及对应结果分类

### Requirement: HTTP 有界且取消原样传播
HTTP SHALL 限制连接10秒、读取空闲30秒和完整请求30秒，包括持续分块；取消 SHALL 中止活动读取并停止批次，不能降级为 failed 或成功分类链接。HTTP SHALL 禁止自动重试/重定向并释放资源；错误/日志 SHALL 不携带远端正文。列表响应 SHALL 校验必要字段并拒绝重复满页。

#### Scenario: 持续响应中取消或超时
- WHEN 服务端持续分块，用户取消或完整请求超过30秒
- THEN 请求 SHALL 中止，取消 SHALL 继续抛出控制流异常，总时限 SHALL 返回无敏感数据的网络错误

#### Scenario: 无效或重复分页
- WHEN YApi 返回缺失 ID/分类/方法/路径的条目或重复满页
- THEN 编排 SHALL 报响应无效，不误当新接口保存或无限翻页
