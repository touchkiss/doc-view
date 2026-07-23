## 1. Constants and Core Utility

- [ ] 1.1 Create `constant/JacksonConstant.java` with FQCN constants for `@JsonSerialize`, `@JsonDeserialize`, and well-known serializer classes (`ToStringSerializer`, `NumberSerializer`, `DateSerializer`, `NullSerializer`, etc.)
- [ ] 1.2 Create `dto/JsonWireType.java` (or inner result class) holding resolved JSON type string and optional example override
- [ ] 1.3 Create `utils/JacksonPsiUtils.java` with static well-known serializer FQCN → JSON type lookup map
- [ ] 1.4 Implement `JacksonPsiUtils.resolveJsonWireType(PsiModifierListOwner owner, PsiType javaType)` — read `@JsonSerialize` / `@JsonDeserialize` annotations, extract `using` class reference via PSI
- [ ] 1.5 Implement `JacksonPsiUtils.resolveSerializerClass(PsiClass serializerClass)` — check well-known map first, then infer from `JsonSerializer<T>` generic type parameter, then check inheritance from `ToStringSerializer`
- [ ] 1.6 Implement `JacksonPsiUtils.resolveContentUsing(PsiModifierListOwner owner, PsiType collectionElementType)` — read `contentUsing` attribute for collection element type override
- [ ] 1.7 Implement `JacksonPsiUtils.javaTypeToJsonType(PsiType type)` — map Java types to default JSON presentable types

## 2. ParamPsiUtils Integration

- [ ] 2.1 In `buildBodyParam()`, after `replaceFieldType()`, call `JacksonPsiUtils.resolveJsonWireType(field, type)` and override `body.setType()` with the resolved JSON wire type
- [ ] 2.2 In `buildBodyParamFromComponent()`, apply the same JSON wire type override for record components
- [ ] 2.3 In `buildBodyParam()`, when field is a collection, check `contentUsing` and override the iterable element type before building child `Body` nodes
- [ ] 2.4 In `buildBodyParam()`, when JSON wire type is `String` for a numeric Java type, set `body.setExample()` to a quoted string placeholder
- [ ] 2.5 In `getFieldsAndDefaultValue()` and `getFieldsAndDefaultValueForRecord()`, emit string literal example values when JSON wire type is `String` for numeric fields

## 3. SpringPsiUtils Integration

- [ ] 3.1 In `buildPramFromField()`, after setting `param.setType()`, apply `JacksonPsiUtils.resolveJsonWireType()` override
- [ ] 3.2 In `buildPramFromComponent()`, apply the same JSON wire type override for record component URL/query params

## 4. Response Body Integration

- [ ] 4.1 In `buildRespBody()` / `buildBodyList()` field iteration, ensure JSON wire type override is applied (via `buildBodyParam` changes — verify end-to-end)
- [ ] 4.2 In `getRespBodyJson()`, verify generated JSON string uses string literals for fields resolved to JSON wire type `String`

## 5. Verification

- [ ] 5.1 Create a test DTO with `@JsonSerialize(using = ToStringSerializer.class) Long id` and verify generated doc shows `String` type
- [ ] 5.2 Create a test DTO with `List<Long>` + `@JsonSerialize(contentUsing = ToStringSerializer.class)` and verify element type is `String`
- [ ] 5.3 Create a Spring controller with `@RequestBody` using the test DTO and verify request body table shows correct JSON types
- [ ] 5.4 Create a Spring controller returning the test DTO and verify response body table shows correct JSON types
- [ ] 5.5 Verify example JSON output uses quoted strings for `ToStringSerializer` long fields
- [ ] 5.6 Verify fields without Jackson annotations still show Java-declared types (no regression)
- [ ] 5.7 Run `./gradlew build` to confirm compilation succeeds
