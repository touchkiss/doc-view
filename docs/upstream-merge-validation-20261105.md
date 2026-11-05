# 上游核心更新合并验证记录

## 基线与范围

- 分支：`codex/merge-upstream-1.5-core`，fork 基线 `f12c48e`，上游 `877f0c9cafefc065a92650d99989b42a53ff1cde`。
- 实际执行三方 merge，保留双方历史；三个 OpenSpec 并行实现，两个独立聊天会话审查本地功能与运行时，另有最终运行时审查。
- 合并核心修复、模板/配置、后台生成/缓存、凭据与 HTTP、字段表复制、项目导出。欢迎页、双语资源、图标改版、上游治理/维护工具及 MCP PoC 未引入；原有维护文档保留并只同步实际构建测试说明。
- 合并提交会记录上游父节点；暂缓功能以后需单独按功能引入，不应仅凭 Git 祖先关系认定这些产品功能已启用。

## 本地功能保留与交叉修复

- MCP 工具、HTTP transport/Host/Origin 校验、YApi upsert、接口 ID、详情链接与结构化结果保留；ReadAction 改用等价的公开 computeBlocking，网络/PasswordSafe 保持在 PSI 锁外。
- 保留 JSON5、示例/默认值列、cURL/三个独立域名、URL 重写、POJO 第三模板、record、Jackson/contentUsing、Jakarta/javax 与注释引用能力。
- 保留 Proto/gRPC 动作及可选依赖；Proto 标量识别从内部 API 换到公开查表，不把自定义/限定类型误认为标量。
- 合成容器使用独立标记，避免真实集合字段丢失；无 PSI 缓存保留该标记并显式传递 Project；YApi List/Map schema 正确解包并保留真实 element/key/value。
- snake_case 与 POJO 模板参与缓存失效；基础请求 Body 的缓存身份区分文件/方法/参数，避免同类型不同接口串用 name/required。
- 模板 Reset/Cancel/Apply 和资源释放迁到新 UI，原有效设置均保留；仅旧的两个 UI form 文件在迁完功能后删除。
- HTTP 加入活动读取取消/30秒完整请求时限；YApi 支持直接数组和分页对象响应，校验必要字段、拒绝重复满页；取消不转为失败结果或成功分类链接。
- 原 `gradlew.bat` 字节内容已保留；其他原有未跟踪文件未加入提交。删除失效的个人 JDK 路径配置，构建使用本机 JDK 25。

## 验证证据与范围

- 请求缓存交叉用例已先复现 secondText/secondNumber 错用 firstText/firstNumber，再验证修复后两例通过。
- YApi 容器用例已先复现字段数/动态 Map/default 失败，再验证纯工厂3项与真实 parser/cache4项通过。
- HTTP 测试先明确观察进入 EntityUtils body 读取，取消后旧实现3秒仍不退出；修复后覆盖活动读取取消和持续分块30秒总时限。
- 第四轮完整测试：主套件339项，加常量13项、contentUsing3项通过；最终新增真实Proto PSI覆盖全部标量、自定义/限定类型、map/enum/well-known/oneof/递归/import。
- 三个 OpenSpec 已通过 `openspec validate <change> --strict --no-interactive`。
- `git diff --cached --check` 与无冲突标记检查已执行。

## 使用与验证边界

- 平台凭据改用 PasswordSafe，旧 XML 不自动迁移/删除，使用新版前需要重新填写一次凭据；瞬态上传 token 不写入项目 XML。
- 保留 fork 版本号 `1.4.99`，本次没有发布、推送或安装插件，也没有修改原 master。
- 验证平台为本机 IDEA IU `262.11879.100` / JDK25，不将结果推广为所有 IDE 版本均兼容。
- 未进行真实外部 YApi/ShowDoc/语雀写入或手工 IDEA GUI 验收；自动化 PSI、UI fixture、HTTP 与 MCP 客户端测试不能代替这些环境验证。

## 最终结果

统一命令（JDK 25）：

```sh
./gradlew --offline test constantValueTest contentUsingTest buildPlugin verifyPluginStructure verifyPlugin --console=plain
```

- 统一命令退出码0：BUILD SUCCESSFUL。主测试342项、常量13项、contentUsing3项，共358项，失败/错误/跳过均为0。最后一轮主测试实际运行；部分未改变输入的编译、打包及两个独立测试任务复用了本分支前轮成功结果。
- Plugin Verifier 对 IU-262.11879.100 结果为 Compatible；内部/弃用/兼容性/override-only门禁开启并通过。最终离线验证下载0B，不隐藏门禁或使用忽略列表。
- buildPlugin 与 verifyPluginStructure 成功。产物：build/distributions/doc-view-1.4.99.zip，SHA-256 `079548e4a6f90db05f0bf8556e055aab0745ca213d9872026d571096a15d4a4e`。
- ZIP 内已检查 MCP/cURL/gRPC/Proto 核心类及可选依赖描述符存在，欢迎页与中文翻译资源不存在；原图标资源保持。
- 最终新增 Proto 测试保留原始 Javadoc 分组文字，同时按 fork 既有空白压缩语义验证输出，不为通过测试修改用户的生成格式。
- 验证中修正了空 until-build 的无效描述符；为 IDEA 2026.2 新拆出的公共模块补齐测试沙箱依赖；原始固定端口的 MCP 测试改用系统分配端口，避免占用用户 IDE 服务端口。

验证日志位于本机 `/tmp/doc-view-verified.log`；IDE兼容性结论位于 `build/reports/pluginVerifier/IU-262.11879.100/plugins/com.liuzhihang.doc-view/1.4.99/verification-verdict.txt`。
