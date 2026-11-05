## ADDED Requirements

### Requirement: UI消费后台冻结的类名
预览 SHALL 在后台read action内冻结每项类的完整名和简单名。Swing选择事件和导出按钮 SHALL 只读取不可变字符串快照，不调用PsiClass名称或解析API；不能假设EDT自动持有读锁。

#### Scenario: 选择预览中的另一个接口
- WHEN 用户在已加载的预览列表切换接口
- THEN 类名标签 SHALL 正确更新，生成markdown仍在后台，EDT的PSI名称读取次数 SHALL 为0

#### Scenario: 捕获批量导出快照
- WHEN 已加载预览触发批量导出
- THEN 文件名 SHALL 取被冻结的真实类名，快照文档列表 SHALL 保持稳定，EDT的PSI名称读取次数 SHALL 为0

### Requirement: 快照服从预览生命周期
过期任务 SHALL 不替换当前快照，关闭 SHALL 释放快照引用；加载重试 SHALL 不累积本预览的选择监听器。业务源码 SHALL 继续只读。

#### Scenario: 重复加载或关闭
- WHEN 旧任务延迟完成或预览重复刷新/关闭
- THEN 只有最新且未关闭的结果 SHALL 发布，过期结果 SHALL 不变更当前UI或导出内容
