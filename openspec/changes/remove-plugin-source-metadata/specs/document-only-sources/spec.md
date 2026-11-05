## ADDED Requirements

### Requirement: 业务代码不依赖插件专属标签
插件 SHALL 不再识别、生成或配置DocView专属源码标签。历史标签 SHALL 不影响标题/名称/必填，标准JavaDoc、Spring、Swagger/OpenAPI、Validation和Jackson元数据 SHALL 保留。插件模板变量 SHALL 不受影响。

#### Scenario: 源码同时有旧插件标签与标准注释
- WHEN 类/方法/字段含旧插件标签及普通注释或现有标准注解
- THEN 标题/名称/必填 SHALL 仅取标准来源，插件 SHALL 不删除或改写业务源码里的旧标签

### Requirement: 业务源码只读
插件 SHALL 不向业务Java/Proto写回注释/注解，也不自动保存用户的未保存业务编辑；解析 SHALL 能读取当前内存PSI。文档内编辑、复制、导出、平台上传和Scratch内容 SHALL 保留。

#### Scenario: 用户预览未保存的Java编辑
- WHEN 用户更改编辑器中的业务源码但尚未保存，并触发插件动作
- THEN 插件 SHALL 读取新的内存PSI，而磁盘业务文件 SHALL 保持不变

#### Scenario: 编辑参数文档
- WHEN 用户改文档表格中的描述/必填并复制Markdown或类型JSON示例
- THEN Markdown输出 SHALL 反映描述/必填草稿，纯JSON复制 SHALL 保持现有类型结构/示例生成行为，业务源码 SHALL 不变，UI SHALL 不提供写回确认

### Requirement: Proto必填和标准校验不依赖源码标签
Proto2 required/optional/oneof语义 SHALL 以内部模型元数据保留；生成的临时模型 SHALL 无插件专属tag；标准校验默认来源 SHALL 使用真实FQN并保持用户配置选择。

#### Scenario: Proto2必填经缓存复制
- WHEN Proto2 required字段生成Body并复制无PSI缓存
- THEN 必填 SHALL 保持true，optional/repeated/oneof SHALL 保持原语义，Java/Proto源码 SHALL 无插件标签写入
