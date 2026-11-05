# 文档插件的业务源码只读边界

基线：`afb3286`，分支 `codex/merge-upstream-1.5-core`。用户明确确认“全部只读：文档可编辑，但禁止写回业务源码”。

## 本轮行为

- 不再识别或生成插件专属JavaDoc标签；删除标题/名称/必填的6个标签设置字段及3个UI开关，旧配置不能重新启用。历史业务源码中的标签不自动删除。
- 标题取标准类注释/类名，接口名保留现有Swagger/OpenAPI/标准方法注释/方法名来源；必填仍读取现有Spring/Validation/Swagger语义。标准JavaDoc的param/since/version/see/link/value保持。
- `${DocView.*}`仅是插件模板变量，保留JSON5/cURL/POJO/record/Proto/MCP/YApi及导出相关功能，不作为业务代码标签。
- 删除WriterService及服务注册；参数文档编辑器的确认写回改为关闭。旧writeComment签名仅保留无副作用边界，不能修改JavaDoc/Swagger/业务源码。
- 插件动作只提交当前项目内存Document到PSI，不再自动保存业务文件；可以解析用户尚未保存的修改，磁盘原文和unsaved状态保持。
- 文档表格中的描述/必填修改用于Markdown草稿复制；纯JSON复制继续使用原有类型结构/示例逻辑。预览/模板文档、Scratch和用户主动导出的Markdown仍可写，因为它们不是业务Java/Proto源码。
- Proto2 required通过内部PSI元数据传给Body与缓存，生成的临时Java不再插标签，也不改.proto文件。
- 标准校验默认来源修复为真实FQN集合。旧版已知Arrays.toString坏值只作精确迁移，用户自定义或明确清空的来源列表保持。

## 验证设计与证据

- 旧实现下，标签可覆盖标准JavaDoc、改变字段必填、生成Proto标签；设置仍有13项开关，新增7项初始用例全部失败。
- 只读业务文件用例先复现正常/无效动作都会保存磁盘，以及编辑器仍存在Confirm；修复后验证磁盘字节不变、编辑器仍未保存、内存PSI能看到新字段而非旧字段。
- 旧Schema写回相关6项测试转为调用前后整个源码逐字不变，同时保留标准Schema读取、字符串、空值、常量和批量属性断言。
- 专项覆盖标准元数据、旧标签忽略、Proto2 required/optional/repeated/oneof及无PSI缓存、只读编辑器复制与配置迁移。
- 独立审查未发现剩余业务写入路径；包内已检查WriterService类与注册均不存在。

## 边界

没有手工操作用户正在运行的IDE，没有修改外部业务项目来删除旧标签；没有真实外部平台上传、安装或远端推送。新插件需由IDE加载后生效。

## 最终结果

- 统一命令 `./gradlew --offline test constantValueTest contentUsingTest buildPlugin verifyPluginStructure verifyPlugin --console=plain` 退出0，BUILD SUCCESSFUL。
- 主测试370、常量13、contentUsing3，共386项，失败/错误/跳过均为0。最后一轮主测试实际运行，部分输入未改变的编译/独立测试/打包任务复用前轮本分支成功结果。
- 本机IU-262.11879.100 Plugin Verifier为Compatible，原内部/弃用API门禁保持开启；结构、构建和OpenSpec严格校验通过。
- 新包：build/distributions/doc-view-1.4.99.zip，SHA-256 `f55d81024620ce2fd80fa033a15c72ee8a5e0f72ee7b0a9cd98b30de9ad78a54`。完整日志：/tmp/doc-view-document-only-verified.log。
