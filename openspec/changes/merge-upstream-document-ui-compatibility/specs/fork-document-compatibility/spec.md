## ADDED Requirements

### Requirement: 保留 fork 文档变量与无 PSI 快照
显式 Project 与不含 PSI 的 Body SHALL 支持渲染 Markdown、JSON5、curl；requestJson5、responseJson5、curlExample、POJO 模板及字段 Markdown 换行行为 SHALL 保留。

#### Scenario: 无 PSI 集合快照
- WHEN 文档包含 fork 业务集合字段、普通字段与嵌套字段，且缓存快照不含 PSI
- THEN 字段表 SHALL 保留真实集合字段，仅折叠 parser 标记的合成中间节点
- AND JSON5 SHALL 保留数组与数字/布尔元素类型

### Requirement: fork 设置草稿与 POJO 模板生命周期
UI SHALL 保留独立 Copy/文档/gRPC cURL 域名、URL 重写规则、snake_case 和 POJO 模板编辑功能。Reset SHALL 重载已保存值，不得覆盖持久配置；Apply SHALL 保存完整草稿；关闭 SHALL 释放所有编辑器。

#### Scenario: 修改后取消或 Reset
- WHEN 用户编辑 POJO 模板、域名和重写规则但未 Apply
- THEN 已保存配置 SHALL 不变
- AND Reset SHALL 恢复已保存值

#### Scenario: Apply 后重新打开
- WHEN 用户 Apply POJO 模板与本地扩展设置
- THEN 重新打开 SHALL 展示最新保存值
