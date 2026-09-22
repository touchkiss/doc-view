Doc View
=======

[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/15305-doc-view.svg)](https://plugins.jetbrains.com/plugin/15305-doc-view)
[![Version](http://phpstorm.espend.de/badge/15305/version)](https://plugins.jetbrains.com/plugin/15305-doc-view/versions)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/15305-doc-view.svg)](https://plugins.jetbrains.com/plugin/15305-doc-view)
[![License](https://img.shields.io/badge/license-MIT-red.svg)](https://github.com/liuzhihang/toolkit/blob/master/LICENSE)

特征
----

- [x] Controller/Dubbo 接口文档生成
- [x] 支持 validation、swagger 等注解
- [x] Markdown 接口查看、预览、复制、导出
- [x] 支持自定义生成接口的 Markdown 模版
- [x] 支持界面编辑文档、注释、并同步保存到代码注释或 Swagger 注解中
- [x] 支持在编辑实体界面, 将实体复制为 Json 字符串
- [x] 支持上传文档到 YApi
- [x] 支持上传文档到 ShowDoc
- [x] 支持自定义配置

演示
----

![](https://cdn.jsdelivr.net/gh/liuzhihang/oss/pic/article/122-zbZ9ps.gif)
![](https://cdn.jsdelivr.net/gh/liuzhihang/oss/pic/article/0YyY3m-CQdmD6.gif)

[更多截图演示](https://github.com/liuzhihang/doc-view/discussions/17)

安装
----

- **在线安装:**
    - `File` -> `Setting` -> `Plugins` -> 搜索 `Doc View`

- **手动安装:**
    - [下载插件](https://github.com/liuzhihang/doc-view/releases) -> `File` -> `Setting` -> `Plugins`
      -> `Install Plugin from Disk...`

使用
----

- 右键菜单选择 `Doc View`

本地 MCP Agent 接入
-----------------

插件启动后会在本机回环地址启动 MCP Streamable HTTP 服务。打开 IDE 日志并搜索
`Local MCP endpoint:`，将日志中的完整地址（形如 `http://127.0.0.1:<port>/mcp`）填入 Agent 的
MCP server URL；端口由每次启动时动态选择。若 Agent 或插件状态页已显示该 endpoint，以显示的完整地址为准。

服务只提供一个 YApi 工具：`upload_yapi_api_doc`。调用时必须提供：

- `projectPath`：已经在当前 IDE 中打开的项目根目录绝对路径；工具不会打开任意目录。
- `reference`：Java Controller 的全限定类名（如 `com.example.OrderController`），或带方法的
  `Class#method` 引用（如 `com.example.OrderController#list`）。类级引用会展开上传该类全部受支持的 API 方法；
  方法级引用只处理指定方法。

上传按 HTTP 方法和路径匹配已有 YApi 接口：匹配时更新，不匹配时创建。批量处理中的单个方法失败不会中止其他方法，
调用结果会分别返回创建、更新、跳过和失败项目。该 MCP 工具仅上传到目标项目已配置的 YApi，不支持 ShowDoc 或其他文档平台。

该服务没有 MCP token，也不接受远程访问：它只绑定 `127.0.0.1`，不应通过端口转发、反向代理或共享网络暴露给其他机器。

更新
----

[查看历史更新记录](https://github.com/liuzhihang/doc-view/releases)

关于我
----

欢迎关注公众号：『 程序员小航 』

![wechat-vxgNsq](https://cdn.jsdelivr.net/gh/liuzhihang/oss/pic/article/wechat-vxgNsq.png)


小伙伴们
----

感谢以下小伙伴的参与:

[lvgo](https://github.com/lvgocc)
[知一](https://github.com/zh-d-d)
[大斌](https://github.com/dabinaa)
[ayang0422](https://github.com/ayang0422)



其他插件
----

&emsp;Toolkit: [https://github.com/liuzhihang/toolkit](https://github.com/liuzhihang/toolkit)


&emsp;copy-as-json: [https://github.com/liuzhihang/copy-as-json](https://github.com/liuzhihang/copy-as-json)

本工具使用 JetBrains IDEA 进行开发
----
<a href="https://www.jetbrains.com/?from=Toolkit"><img src="https://cdn.jsdelivr.net/gh/liuzhihang/oss/pic/article/jetbrains-logo-MrNwcp.png" width="20%" height="20%"></a><a href="https://www.jetbrains.com/?from=Toolkit"><img src="https://cdn.jsdelivr.net/gh/liuzhihang/oss/pic/article/idea-logo-XpnqgG.png" width="20%" height="20%"> </a>


<script defer src="https://plugins.jetbrains.com/assets/scripts/mp-widget.js"></script>
