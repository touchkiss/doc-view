# fork 解析能力保留

## ADDED Requirements

### Requirement: 上游文档正确性修复保留 fork 扩展

合并后的 Java PSI 解析 MUST 同时支持上游路径数组首项、path 别名、常量路径、完整参数名匹配与多行 Javadoc，以及 fork 的 record、Jackson using/contentUsing、字段常量示例、Jakarta/javax 校验、Proto getter 注释、@see/@link 枚举补充和 URL 重写。OpenAPI @Parameter 描述/示例按显式非空值优先，Spring 名称/defaultValue/required 保留上游明确优先级。

#### Scenario: record 的集合字符串反序列化
- GIVEN record component 为 List<Long>，JsonProperty 使用字符串常量，JsonDeserialize.contentUsing 指定 StringDeserializer
- WHEN 生成响应 Body 与 JSON 示例
- THEN 名称为常量值，Body 保留集合属性并显示 List<String>，示例为字符串数组，不生成空名称对象子字段
- AND record 类级 @param 的多行说明保留

#### Scenario: 兄弟分支使用同一个 record
- GIVEN 同一根 record 有两个 Leaf 类型组件
- WHEN 遍历字段树
- THEN 两个分支各自包含 Leaf 字段，不得因第一个分支访问而跳过第二个

### Requirement: 默认深度和取消适用于所有解析分支

解析 MUST 在对象、集合、Map 和 record 递归分支使用同一深度上下文；默认最大深度为 8。取消异常 MUST 继续抛出，不转换为“找不到注释”。深度截断保留当前字段但停止展开，不支持无限深度输出。

#### Scenario: 自定义深度为一
- GIVEN Root record 的 child 为 Leaf record
- WHEN 使用 DtoParsingContext.root(1) 生成 Body
- THEN child 字段存在，Leaf 子字段不展开

### Requirement: 合成容器与业务字段显式区分

仅由 buildFieldGenericsBody 创建的 element/key/value 节点 MUST 标记 syntheticContainerNode=true。真实业务集合字段及真实同名 element/key/value 字段 MUST 为 false。该标记用于无 PSI 缓存和 Markdown 展示投影，不删除或重排底层数据。

#### Scenario: 同名业务字段
- WHEN 真实字段名是 element、key 或 value，且父字段为集合或 Map
- THEN 只有 parser 创建的中间层 MUST 被标记为合成，业务字段 MUST 保留

### Requirement: requiredMode 与 fork 原有 Schema 能力

Schema.RequiredMode 明确 REQUIRED/NOT_REQUIRED MUST 优先于其他必填来源；AUTO 使用已有推断。只有注解定义不提供 requiredMode 而提供 required 时，保留 fork 原有 required 读取和写入。写回 MUST 保留无关注解属性并正确转义字符串；完全无法解析能力时在批量写入前拒绝，避免部分修改。

#### Scenario: 新旧 Schema 注解均可编辑
- WHEN 注解有 requiredMode 时指定 REQUIRED 或 NOT_REQUIRED，或旧注解只有 required
- THEN 新注解 MUST 按显式模式决策，旧注解 MUST 保留原有 required 写回功能
- AND 无法解析注解能力时 MUST 在任何批量写入前失败
