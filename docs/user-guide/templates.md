# Markdown 模板教程

模板设置完整路径为 **Settings → Tools（工具）→ Doc View → 模板与输出（Template and Output）**；IDE 菜单译名可能不同。本文按“选择模板 → 插入变量 → 预览 → 保存”说明操作，并提供标题和参数表的修改示例。

修改前可先备份自己的 Spring/Dubbo/POJO 模板，方便比较或恢复。

## 保存、Reset 与取消

模板配置按最近一次保存状态处理：

- **Apply / OK**：保存当前三份模板和输出选项（独立实体、合并导出、两个前缀）。
- **Reset（IDE 设置界面显示 `Revert changes`）**：恢复最近一次已保存（Apply/OK）的模板和输出选项；它不是“恢复出厂默认”，也会丢弃当前尚未应用的编辑。
- **Cancel**：丢弃尚未应用的编辑。如果先 Apply，再继续编辑后按 Cancel，前一次 Apply 的内容仍保留，只有后续草稿被丢弃。

本次整合的保存/Reset/Cancel 与编辑器释放由 `TemplateSettingsLifecycleTest` 和 `ForkDocumentUiCompatibilityTest` 回归覆盖；实际执行结果以本分支验证记录为准。

## 选择哪份模板

生成文档时按类型自动选择：Spring Controller 和 Feign 接口使用 Spring 模板，Dubbo Service 使用 Dubbo 模板，普通 POJO、record 和已解析的 Proto message 使用 POJO 模板。设置页提供 Spring、Dubbo、POJO 三个标签页，无需在生成文档时手动切换。

## 源码元数据与模板变量

标题和名称使用标准类/方法 JavaDoc、Swagger/OpenAPI 注解以及 Java 类名/方法名；必填信息使用 Validation、Spring 参数声明、Swagger/OpenAPI 的标准属性。插件专属 JavaDoc 标签不再读取、生成或提供配置入口，旧标签不会覆盖这些标准来源。

`${DocView.…}` 是模板数据对象的变量表达式，与源码 JavaDoc 标签无关；本页列出的 JSON5、cURL、版本信息和结构化参数变量继续可用。标准 `@param`、`@return`、`@since`、`@version`、`@see`、`@link` 仍按对应功能解析。

文档操作保持业务源码只读，不改写 JavaDoc 或 Swagger/OpenAPI 注解，也不自动保存业务源码。参数编辑中的描述/必填改动只作用于复制的 Markdown 文档草稿；复制纯 JSON 保持现有类型结构/示例生成行为，不会确认写回业务文件。

## 模板生效范围

基于 `DocViewData.markdownText()` 的预览、复制、导出以及 ShowDoc、YuQue 上传会使用当前接口对应的自定义模板。YApi 上传由本分支的 `YapiSaveFactory` 组装请求字段和返回结构，描述正文使用 `buildDesc()` 生成的固定 Markdown（含 JSON5 与 cURL），不调用 `DocViewData.markdownText()`；改这里的模板不会改变 YApi 上传布局。

## 第一次修改模板

1. **选择模板**：在 **Template and Output → 模板编辑** 选择 Spring、Dubbo 或 POJO。默认只显示编辑区，为模板留出空间。
2. **插入变量**：先将光标放在目标位置，再点“显示模板变量”。按英文属性名或当前界面的名称/说明搜索；例如 `requestExample`、`requestJson5`、`curlExample` 或变量说明中的关键词。选中变量后查看完整表达式和用途说明，再点“插入变量”。插入完整的 `${DocView.…}`；若编辑器有选区，所选内容会被替换。完成后可收起变量目录。
3. **预览结果**：点击对应框架的预览按钮。只读编辑区显示 Markdown 源码；它使用插件内置的虚构“查询示例用户”数据，不读取项目源码、不联网，也不保存模板。
4. **核对实际接口**：检查预览和错误提示。点击“模板编辑”返回草稿；继续编辑后旧预览失效，需要重新预览。固定样例通过后，再用项目里的接口生成文档，确认真实输入也符合预期。
5. **保存或撤销**：点击 Apply / OK 保存；Cancel 丢弃未应用内容。需要回到最近保存值时使用 Revert changes。

页头说明旁的 `?` 提供模板指南链接。“恢复默认模板”与变量入口位于“模板编辑”的操作行，会把 **Spring、Dubbo 和 POJO 三份草稿** 都替换成随包模板，Apply 前不改变保存值。它与 Revert changes 不同。变量目录展开后可通过分隔条调整编辑器和目录的宽度；预览结果使用独立页签，不与编辑器争用高度。

“独立实体”、前缀符号和“合并导出”位于“输出选项”页签，纵向排列；“独立实体”将嵌套字段拆成子表，前缀控制单表中的层级标识，“合并导出”控制批量导出的文件组织。模板预览使用当前已保存的“独立实体”和前缀设置；调整后 Apply，再预览以核对参数表效果。“合并导出”只影响批量导出的文件组织，不改变单接口示例预览。

## 内置模板

下面三段是本分支随插件提供的内置模板，可分别复制到对应标签页。Dubbo 模板不单列 Header 和 Param，而使用 Body 参数变量。

**Spring / Feign**

    **接口地址**

    > ${DocView.path}

    **接口描述**
    > ${DocView.desc}

    **认证方式**
    > 登录

    **Header**

    ${DocView.requestHeader}

    **请求参数(${DocView.method})**

    ${DocView.requestParam}
    ${DocView.requestBody}
    ```
    ${DocView.requestJson5}
    ```
    **请求示例**

    ${DocView.requestExample}

    **返回值**

    ${DocView.responseParam}
    ```
    ${DocView.responseJson5}
    ```

    **返回示例**
    ${DocView.responseExample}

    **curl example**

    ${DocView.curlExample}

**Dubbo**

    **接口名称:**

    ${DocView.name}

    **接口描述:**

    ${DocView.desc}

    **请求路径:**

    ${DocView.path}

    **请求方式:**

    ${DocView.method}

    **请求参数:**

    ${DocView.requestBody}

    **请求示例:**

    ${DocView.requestExample}

    **返回参数:**

    ${DocView.responseParam}

    **返回示例:**

    ${DocView.responseExample}

**POJO / record / Proto message**

    **类名**

    ${DocView.name}

    **说明**

    > ${DocView.desc}

    **属性**

    ${DocView.requestBody}

    ```
    ${DocView.requestJson5}
    ```
    **json示例**
    ${DocView.requestExample}

## 内置文本变量

模板上下文根对象名是 `DocView`。以下字段均为 `String`，提供接口元数据、预生成 Markdown、JSON5 和 cURL 示例。

### 接口元数据

- `${DocView.name}`：接口名称；直接使用解析结果，不保证未提供时为空串。
- `${DocView.desc}`：接口描述；直接使用解析结果，不保证未提供时为空串。
- `${DocView.since}`：接口方法 Javadoc 的 `@since` 内容，可以是生效时间或版本；未声明时为空串。
- `${DocView.version}`：接口方法 Javadoc 的 `@version` 内容；未声明时为空串。
- `${DocView.path}`：接口路径文本；Dubbo 下的含义取决于解析结果，不一定是 HTTP URL。
- `${DocView.method}`：接口方法文本；直接使用解析结果，不保证未提供时为空串。

### 已生成的文档片段

- `${DocView.requestHeader}`：Header Markdown 表格；没有 Header 时为空串。
- `${DocView.requestParam}`：Param Markdown 表格；没有 Param 时为空串。
- `${DocView.requestBody}`：Body 参数 Markdown；没有参数时为空串。独立实体布局受设置影响，默认递归包含子字段。
- `${DocView.requestExample}`：带 Form / JSON 代码围栏的请求示例。Form 或 Body 只有一项时只输出该项；两者都没有时为空串。不要再套代码围栏。
- `${DocView.responseParam}`：返回参数 Markdown；没有参数时为空串。默认递归包含子字段，布局受独立实体设置影响。
- `${DocView.responseExample}`：带 JSON 代码围栏的返回示例；源示例为空或全为空白时输出空串。不要重复套代码围栏。

- `${DocView.requestJson5}`：含字段说明的请求 JSON5 文本，不带代码围栏；集合保留数组形式，支持 JSON 类型覆盖后的字段示例。
- `${DocView.responseJson5}`：含字段说明的返回 JSON5 文本，不带代码围栏。它与默认字段表共享原始字段数据，但不会使用只服务于表格的展示投影。
- `${DocView.curlExample}`：Spring/Feign 接口的完整 cURL Markdown 代码块，包含 bash 围栏；其他框架为空串。它使用“文档 cURL 域名”，与右键 Copy cURL 的域名相互独立。不要再套围栏。

Copy cURL、文档 cURL、gRPC cURL 三个域名与 URL 重写规则仍可在 Doc View 常规设置页编辑。域名留空时使用各自的默认值；模板草稿不会修改这些设置。URL 重写先作用于生成的路由，再用于文档和 cURL。

“空串”表示 DTO 明确生成空字符串；`name`、`desc`、`path`、`method` 是直接拷贝的源字段，不能仅凭变量清单假定缺失值会被转换为空串。若希望某个可选段落完全不出现，可在模板里用 `#if` 条件包住标题和变量，并在虚构输入上先验证。

方法标签只作用于该接口，不自动继承类级标签；实体字段使用各自的标签。本分支保留原有默认模板布局，默认模板未展示方法版本标签。已有自定义模板不会被替换，可自行插入 `${DocView.since}`、`${DocView.version}` 并用 `#if($DocView.since != "")` 等条件包住可选段落；需要使用新版内置模板时，先备份自定义内容再选择“恢复默认模板”。

变量目录还提供 `${DocView.type}`，值为 `SPRING`、`DUBBO` 或 `NONE_POJO`，表示框架分类，不是 HTTP method。结构化列表需要用 `#foreach` 等指令遍历；直接插入列表表达式不会生成自定义表格。

## 结构化参数列表与字段

需要自己改表格列时，用结构化列表逐项渲染，而不是拆解已生成的 Markdown 字符串：

- `DocView.requestHeaderDataList`：请求 Header，没有层级子字段。
- `DocView.requestParamDataList`：请求 Param，当前为平铺列表。
- `DocView.requestBodyDataList`：请求 Body，可能包含 `childList`。
- `DocView.responseParamDataList`：返回参数，可能包含 `childList`。

每个元素是 `DocViewParamData`，常用字段如下：

- `name` (`String`)：参数名；`type` (`String`)：参数类型。
- `required` (`Boolean`)：是否必填；`example` (`String`)：示例值；`desc` (`String`)：描述。
- `since` / `version` (`String`)：实体字段自身 Javadoc 的生效时间或起始版本、版本；普通标量参数沿用方法标签。
- `prefixSymbol1` / `prefixSymbol2` (`String`)：Body / 返回字段的层级前缀。
- `childList` (`List<DocViewParamData>`)：下一层字段。

不要把 `psiElement` 当作模板字段使用：它是内部 PSI 引用，不是稳定的文档字段。缺少参数时各列表为空列表，`#foreach` 不会生成数据行。

下面的循环展示一个简单的 Param 列表：

    #foreach($param in $DocView.requestParamDataList)
    - $!{param.name} | $!{param.type} | $!{param.example} | $!{param.desc}
    #end

Body 和返回列表可能嵌套。只遍历 `DocView.requestBodyDataList` 或 `DocView.responseParamDataList` 的最外层时，子字段不会自动展平；若需要保留所有层级，继续遍历 `childList`。也可以继续用内置的 `${DocView.requestBody}` / `${DocView.responseParam}` 字符串，插件已为这两项递归生成 Markdown。

下面是可选的递归宏示例：它把 Body 的每一级输出成同一张表的行，并在名称前保留插件构建的层级前缀。宏声明要放在调用之前。`TemplateGuideExamplesTest` 会从本页提取该代码，覆盖三层嵌套、必填/非必填和空列表；无需自定义列时，可以直接使用预渲染字符串。

    |参数名|类型|必填|示例|描述|
    |---|---|---|---|---|
    #macro(renderRows $items)
    #foreach($param in $items)
    |$!{param.prefixSymbol1}$!{param.prefixSymbol2}$!{param.name}|$!{param.type}|#if($param.required)是#else 否#end|$!{param.example}|$!{param.desc}|
    #renderRows($param.childList)
    #end
    #end
    #renderRows($DocView.requestBodyDataList)

Velocity 宏和指令语法可参考 [Apache Velocity VTL Reference](https://velocity.apache.org/engine/2.3/vtl-reference.html)。实际插件使用 IDE 提供的 Velocity 运行环境；本分支通过对应契约测试核查示例，测试执行与平台范围以本次整合验证记录为准。

## 示例一：把接口名称改成 Markdown 标题

在模板中找到接口名称部分，将原来的名称标签和变量替换为：

    # ${DocView.name}

虚构输入：`name` 为“查询用户”。预期该处输出：

    # 查询用户

这只改变生成文档里的标题格式，不会改 Java 方法名。

## 示例二：删减 Param 表列

在 Spring 模板里，用下面内容替换原来的 Param 标题及 `${DocView.requestParam}`。例子保留参数名、类型、描述三列，删除默认表格中的必填和版本列：

    **请求参数（Param）:**

    |参数名|类型|描述|
    |---|---|---|
    #foreach($param in $DocView.requestParamDataList)
    |$!{param.name}|$!{param.type}|$!{param.desc}|
    #end

虚构输入：Param 列表只有一项，`name` 为 id、`type` 为 `String`、`desc` 为用户编号。预期表格行是：

    |id|String|用户编号|

该示例只渲染 Param 列表。若你要删减 Body 或返回参数表的列，可把列表换成 `DocView.requestBodyDataList` 或 `DocView.responseParamDataList`，并像上一节那样递归 `childList`；只遍历最外层会漏掉嵌套对象的子字段。标题、Param 表格与递归宏示例已通过同一个直接读取本页代码段的测试类验证；内置模板示例也与资源文件逐字核对。

## 示例三：保留 JSON5 与 cURL 输出

在已有字段表后追加下面的内容。JSON5 自身不带围栏，cURL 已带围栏；此示例可用于 Spring/Feign 模板：

    **请求 JSON5**

    ```json5
    ${DocView.requestJson5}
    ```

    **响应 JSON5**

    ```json5
    ${DocView.responseJson5}
    ```

    ${DocView.curlExample}

在 POJO 模板中可以使用相同的 JSON5 变量；`${DocView.curlExample}` 为空，因为 POJO 没有 HTTP 方法和路由。方法版本信息可按需追加 `${DocView.since}`、`${DocView.version}`，它们不会改写现有自定义模板。

## 输出为 `ERROR` 时

`VelocityUtils` 在 `evaluate()` 返回失败时会返回字面文本 `ERROR`；这个返回值本身不包含模板行号或原因。源码还配置了 Velocity 日志类别，但用户指南不能从 `ERROR` 文本推断具体错误，也不能保证其他异常都显示相同文本。

可以按以下有限步骤排查：

1. 如果要保留出错时的编辑草稿，先把当前模板全文另存为文本。`Reset` 会丢弃未应用草稿并回读最近一次已保存的模板；只有准备放弃这份草稿时才使用。`Cancel` 会丢弃未应用内容，已成功 Apply 的内容会保留。
2. 检查最近修改的 `#if` / `#foreach` / `#macro` 是否配有对应 `#end`，变量拼写是否来自本页列出的字段。
3. 若有可信备份，确认三份模板区分 Spring、Dubbo 与 POJO，再考虑恢复对应文本；内置默认模板可从本页复制，但这会覆盖自定义内容，操作前先保留原文。
4. 若找不到可信备份、页面文本与项目配置不一致或 ERROR 持续出现，保留可用文本并请维护者检查具体 IDE 日志和项目配置；尚未确认模板内容前不要 Apply/OK。

模板设置的示例预览单独显示错误；Velocity 提供行列位置时会包含对应位置。预览使用固定样例，不扫描当前项目，也不验证真实平台上传；要判断项目结果，应再用目标接口生成文档。
