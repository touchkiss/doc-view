# YApi 上传通知跳转接口详情页设计

## 背景

接口上传到 YApi 后，IDEA 成功通知中的 “Go to view” 当前指向接口分类页：

```text
{yapiUrl}/project/{projectId}/interface/api/cat_{catId}
```

用户需要该链接直接打开刚上传的接口详情页：

```text
{yapiUrl}/project/{projectId}/interface/api/{interfaceId}
```

YApi `/api/interface/save` 的成功响应不提供接口 ID。因此，上传成功后需要依据项目内 `docs/获取接口列表数据.md` 定义的 `/api/interface/list` 接口分页查找目标接口。

## 目标行为

1. YApi 接口保存成功后，从第一页开始查询项目接口列表。
2. 每页最多查询 100 条记录。
3. 使用以下三个字段共同识别本次上传的接口：
   - 分类 ID 等于本次上传使用的 `catId`；
   - 请求方法等于实际提交给 YApi 的 `method`；
   - 请求路径等于实际提交给 YApi 的 `path`。
4. 找到接口后，使用其 `_id` 拼接接口详情页 URL，并传给上传成功通知。
5. 当前页不足 100 条且仍未匹配时，认为列表已遍历完，停止查询。
6. 查询失败或遍历结束仍未找到接口时，上传结果仍视为成功，通知链接回退到原有分类页 URL。

## 设计

### 数据模型

新增轻量级 YApi 接口摘要 DTO，只映射查找所需字段：

- `_id`
- `catid`
- `path`
- `method`

复用现有 `YApiResponse<T>` 解析 YApi 通用响应结构。

### 集成层

在 `YApiFacadeService` 增加按上传信息查找接口 ID 的能力。实现类负责：

1. 请求：

   ```text
   GET {yapiUrl}/api/interface/list?project_id={projectId}&token={token}&page={page}&limit=100
   ```

2. 校验响应非空、JSON 可解析且 `errcode == 0`。
3. 在每页结果中用 `catId + method + path` 精确匹配接口。
4. 找到后立即返回接口 ID；整表遍历结束后返回空结果。

请求方法按不区分大小写比较，路径按原值精确比较，避免把不同接口误判为同一接口。

### 上传服务层

`YApiServiceImpl` 继续先构造并保存 `YapiSave`。保存成功后，使用 `YapiSave` 中最终确定的分类 ID、请求方法和请求路径进行查询，而不是重新读取原始 `DocView`。

这样可以覆盖 Dubbo 的转换逻辑：Dubbo 接口实际保存为 `POST /Dubbo/{methodName}`，查询时也使用相同值。

找到 ID 时生成详情页 URL；找不到或列表查询异常时生成原分类页 URL。列表查询异常应记录日志，但不能将已经成功的上传改为失败通知。

## 数据流

```text
构造 YapiSave
  -> 保存接口成功
  -> 按 projectId 分页查询接口列表
  -> 用 catId + method + path 匹配
     -> 找到：拼接接口详情 URL
     -> 未找到/查询失败：回退分类 URL
  -> 显示上传成功通知
```

## 错误处理

- 保存接口失败：保持当前行为，显示上传失败通知。
- 接口列表返回空内容、错误码、无效 JSON 或网络异常：记录警告并回退分类 URL。
- 返回记录缺少必要字段：忽略该记录，继续查询。
- 最后一页为空或数量少于 100：结束分页，防止无限请求。

## 测试

以测试驱动方式覆盖：

1. 第一页找到匹配接口并返回 ID。
2. 第一页满 100 条且未找到时继续查询下一页。
3. 只在分类 ID、方法和路径全部匹配时返回 ID。
4. 最后一页不足 100 条仍未找到时返回空结果。
5. YApi 返回错误码或无效响应时抛出查询异常，由上传服务回退分类 URL。
6. 详情 URL 使用接口 ID，不再使用 `cat_{catId}`。
7. Dubbo 接口使用转换后的 `POST /Dubbo/{methodName}` 查找。

## 非目标

- 不修改 YApi 接口保存逻辑。
- 不修改 ShowDoc、语雀的上传通知。
- 不改变批量上传时每个接口各自显示通知的现有行为。
- 不引入新的 YApi 配置项。
