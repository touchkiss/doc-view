## ADDED Requirements

### Requirement: 分类内查找接口与返回详情链接
YApi SHALL 使用list_cat和catid请求目标分类并逐页匹配HTTP方法及路径；行内catid缺省 SHALL 使用分类上下文，显式其他分类 SHALL 不能误匹配；有效保存响应ID SHALL 优先用于详情链接，缺失ID SHALL 继续查询分类。请求编码、取消、重复页保护与MCP upsert SHALL 保留。

#### Scenario: 分类多页且未重复返回分类字段
- WHEN 接口位于分类的后续页面，响应行只含_id/method/path且服务端限制页大小
- THEN 查找 SHALL 按total/count信息继续，并返回对应接口详情链接

#### Scenario: 保存响应包含或不包含ID
- WHEN 保存响应包含有效对象ID或唯一ID数组，或新建仅返回空数组
- THEN 前者 SHALL 直接返回详情链接，后者 SHALL 在目标分类继续查找

### Requirement: 降级分类链接必须明确告知
已保存但无法定位详情时 SHALL 保留保存成功状态，同时UI和MCP SHALL 明确返回定位警告；UI分类按钮 SHALL 标注打开分类，不能伪装成详情链接。警告 SHALL 不携带token、异常或远端响应正文。

#### Scenario: 保存成功但详情查询失败
- WHEN 保存已经成功而详情查找失败或未找到
- THEN 结果 SHALL 仍记为created或updated，警告 SHALL 与分类链接一起传给UI/MCP，通知 SHALL 呈现为警告
