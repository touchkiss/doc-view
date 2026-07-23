## Why

Doc View currently documents field types based on the Java declaration (`Long`, `BigDecimal`, etc.), but Jackson `@JsonSerialize` / `@JsonDeserialize` annotations can change the actual JSON wire type at runtime. A field declared as `Long` annotated with `@JsonSerialize(using = ToStringSerializer.class)` is serialized as a JSON string to avoid JavaScript number-precision loss — yet the generated API doc still shows `Long`. This mismatch misleads frontend consumers and API reviewers about the real contract.

## What Changes

- **Add Jackson serializer/deserializer type resolution**: When a field or record component carries `@JsonSerialize` or `@JsonDeserialize`, resolve the referenced `using` class (and related attributes like `contentUsing`, `keyUsing`) and determine the effective JSON type.
- **Override documented type with JSON wire type**: Replace the Java presentable type on `Body` / `Param` nodes with the resolved JSON type (e.g., `Long` → `String`).
- **Update example JSON generation**: `getFieldsAndDefaultValue()` and related helpers produce example values matching the JSON wire type (e.g., `"1234567890123456789"` instead of `1234567890123456789`).
- **Support well-known Jackson built-in serializers**: Map common serializers (`ToStringSerializer`, `NumberSerializer`, `DateSerializer`, etc.) to their JSON output types without requiring full bytecode analysis.
- **Support custom serializer inference**: For project-local `JsonSerializer` subclasses, infer output type from the handled type generic parameter or `serialize()` method signature when statically resolvable via PSI.
- **Apply consistently across all entry points**: Request body, response body, URL/query params, nested fields, record components, and collection element types.

## Capabilities

### New Capabilities
- `jackson-json-type-resolution`: Resolve effective JSON wire types from Jackson `@JsonSerialize` / `@JsonDeserialize` annotations on fields and record components, overriding Java-declared types in generated documentation and example JSON.

### Modified Capabilities
<!-- No existing spec-level requirement documents cover Jackson type resolution -->

## Impact

- **`utils/JacksonPsiUtils.java`** (new): Central logic for reading Jackson annotations and resolving serializer/deserializer classes to JSON types.
- **`utils/ParamPsiUtils.java`**: Apply JSON type override in `buildBodyParam()`, `buildBodyParamFromComponent()`, `buildRespBody()`, and `getFieldsAndDefaultValue()`.
- **`utils/SpringPsiUtils.java`**: Apply JSON type override in `buildPramFromField()` and `buildPramFromComponent()` for URL/query parameters.
- **`constant/FieldTypeConstant.java`**: May add JSON wire-type default example values (e.g., string-formatted long).
- **`constant/JacksonConstant.java`** (new, optional): FQCN constants for Jackson annotation and well-known serializer classes.
- **No template changes required**: Velocity templates consume `Body.type` which will now reflect the JSON wire type.
- **No upload service changes required**: Upload adapters consume `DocView` / `Body` DTOs.
- **Dependencies**: Relies on Jackson annotation FQCNs being resolvable in the project's classpath via IntelliJ PSI; no new Gradle dependency required (annotations are typically already on the classpath in Spring projects).
