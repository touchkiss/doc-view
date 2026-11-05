# 设计

- Title来源改为现有标准类注释/类名；Name保留Swagger/OpenAPI/标准方法注释/方法名；Required保留既有Spring、Validation和Swagger来源。
- 删除6个标签配置状态与3个UI开关，不保留可重新启用专属tag的隐藏选项。历史XML未知属性忽略，不修改用户业务源文件来清理旧标签。
- Proto2的required从语法读入内部PSI元数据，Body与缓存保留bool，不生成源码tag，也不改.proto。
- 移除源码确认动作和WriterService，旧writeComment调用成为无副作用边界。AbstractAction仅提交项目内存PSI，不自动保存用户业务文件。预览/复制/导出/Scratch文档写入保留，因为它们不是业务Java源码。
- 模板${DocView.*}、标准JavaDoc（param/since/version/see/link/value）、既有注解识别保持，文档编辑仅影响草稿/复制结果。
- 标准NotNull/NotBlank/NotEmpty默认配置使用真实FQN集合，修复历史Arrays.toString误存集合；只迁移已知格式，不覆盖用户主动清空或自定义注解列表。
