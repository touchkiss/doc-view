# JsonDeserialize `contentUsing` 集合元素识别设计

## 背景

Doc View 已能识别字段上的 Jackson `@JsonSerialize` / `@JsonDeserialize`，但 HTTP 接口出入参中的集合字段未可靠支持以下写法：

```java
@JsonDeserialize(contentUsing = StringToLongDeserializer.class)
@JsonProperty("benefit_nos")
private List<Long> benefitNos;
```

Java 字段类型是 `List<Long>`，而客户端传输的 JSON 集合元素是字符串。生成的参数文档和示例必须反映 JSON 线上的实际类型。

## 目标行为

- 识别集合字段或 record component 上的 `@JsonDeserialize(contentUsing = X.class)`。
- 当 `X` 表示从 JSON 字符串反序列化为 Java 数值类型时，将集合元素文档类型解析为 `String`。
- 对上述 `List<Long>` 示例，参数结构中的元素类型显示为 `String`，JSON 示例生成 `["0"]`。
- 保留现有 `@JsonSerialize(contentUsing = ...)` 行为和序列化器优先级。
- 无法可靠推断自定义反序列化器的 JSON 输入类型时，回退为集合声明的元素类型，不改变既有输出。

## 实现方案

继续以 `JacksonPsiUtils.resolveContentUsing(owner, elementType)` 作为集合元素 JSON 类型解析的唯一入口：

1. 先检查 `@JsonSerialize(contentUsing = ...)`，保持当前优先级。
2. 再检查 `@JsonDeserialize(contentUsing = ...)`。
3. 将解析出的反序列化器交给现有反序列化器类型推断逻辑；`StringToLongDeserializer` 应推断为 JSON `String`。
4. 返回带有字符串默认值和示例值的 `JsonWireType`，由现有 HTTP Body 参数树和 JSON 示例生成链路消费。

不在各个 HTTP 请求、响应、普通类和 record 分支中复制注解解析逻辑。若消费链路中存在遗漏，只补充对统一解析结果的使用。

## 数据流

`PsiField` / `PsiRecordComponent`
→ 取得集合元素 Java 类型 `Long`
→ `resolveContentUsing`
→ 解析 `StringToLongDeserializer.class`
→ 得到 JSON 元素类型 `String`
→ 参数树元素节点与示例生成器
→ 文档显示字符串元素并输出 `["0"]`

## 错误与回退

- 注解不存在、属性使用 Jackson 的 `None`、类引用无法解析或输入类型无法推断时，不抛异常。
- 上述情况返回未覆盖的 `JsonWireType`，继续使用 `Long` 等原始集合元素类型。
- 不根据任意类名强制改变类型；仅沿用并验证现有的字符串反序列化器识别规则。

## 测试

采用测试驱动方式增加最小回归用例：

- RED：构造带 `List<Long>` 和 `@JsonDeserialize(contentUsing = StringToLongDeserializer.class)` 的 PSI 字段，断言解析结果为覆盖后的 `String`，默认值/示例值均为字符串 `"0"`。
- GREEN：最小修改统一解析或消费链路使测试通过。
- 增加 record component 覆盖，确保普通 DTO 与 record 行为一致。
- 运行针对性测试后，再运行项目完整测试/构建任务。

## 非目标

- 不引入请求与响应两套新的 Jackson 注解模型。
- 不通用分析任意 `deserialize` 方法的控制流或 JsonParser 调用。
- 不改变 `@JsonProperty` 的字段命名处理。
- 不扩展数组、Map key 或 Map value 的 `contentUsing` 语义。
