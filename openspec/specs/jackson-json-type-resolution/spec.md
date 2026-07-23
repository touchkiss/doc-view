## ADDED Requirements

### Requirement: JsonSerialize using attribute resolves JSON wire type
The system SHALL inspect `@JsonSerialize(using = X.class)` on a `PsiField` or `PsiRecordComponent` and resolve the JSON wire type produced by serializer class `X`, overriding the Java-declared type on the corresponding `Body` or `Param` node.

#### Scenario: Long field with ToStringSerializer documented as String
- **WHEN** a field is declared as `Long` and annotated with `@JsonSerialize(using = ToStringSerializer.class)`
- **THEN** the generated `Body.type` equals `"String"` instead of `"Long"`

#### Scenario: Field without JsonSerialize keeps Java type
- **WHEN** a field is declared as `Long` and has no `@JsonSerialize` annotation
- **THEN** the generated `Body.type` equals `"Long"`

#### Scenario: Record component with JsonSerialize override
- **WHEN** a record component is declared as `Long` and annotated with `@JsonSerialize(using = ToStringSerializer.class)`
- **THEN** the generated `Body.type` equals `"String"`

---

### Requirement: JsonDeserialize using attribute resolves JSON input type
The system SHALL inspect `@JsonDeserialize(using = X.class)` on a `PsiField` or `PsiRecordComponent` when no `@JsonSerialize` is present, and resolve the JSON wire type expected by deserializer class `X`, overriding the Java-declared type for request-body documentation.

#### Scenario: Request body field with JsonDeserialize only
- **WHEN** a `@RequestBody` class field is declared as `Long` and annotated with `@JsonDeserialize(using = CustomStringToLongDeserializer.class)` where the deserializer expects a JSON string input
- **THEN** the generated request `Body.type` equals `"String"`

#### Scenario: JsonSerialize takes priority when both present
- **WHEN** a field has both `@JsonSerialize(using = ToStringSerializer.class)` and `@JsonDeserialize(using = SomeDeserializer.class)`
- **THEN** the generated `Body.type` reflects the serialize-side JSON type (`"String"`)

---

### Requirement: Well-known Jackson serializers mapped to JSON types
The system SHALL maintain a lookup table of well-known Jackson built-in serializer FQCNs and their JSON output types, including at minimum `ToStringSerializer` → `String`, without requiring runtime Jackson execution.

#### Scenario: ToStringSerializer on BigInteger
- **WHEN** a field is declared as `BigInteger` and annotated with `@JsonSerialize(using = ToStringSerializer.class)`
- **THEN** the generated `Body.type` equals `"String"`

#### Scenario: Unknown serializer falls back to Java type
- **WHEN** a field has `@JsonSerialize(using = UnresolvableSerializer.class)` and the serializer class cannot be resolved in PSI
- **THEN** the generated `Body.type` equals the Java-declared presentable type

---

### Requirement: Custom JsonSerializer generic type inference
The system SHALL attempt to infer the JSON wire type from a custom `JsonSerializer<T>` subclass by reading its type parameter `T` via PSI when the serializer is not in the well-known lookup table.

#### Scenario: Custom serializer with String type parameter
- **WHEN** a field uses `@JsonSerialize(using = MyStringSerializer.class)` and `MyStringSerializer extends JsonSerializer<String>`
- **THEN** the generated `Body.type` equals `"String"`

---

### Requirement: contentUsing attribute overrides collection element type
The system SHALL inspect the `contentUsing` attribute of `@JsonSerialize` on collection-typed fields and override the element JSON type accordingly.

#### Scenario: List of Long with contentUsing ToStringSerializer
- **WHEN** a field is declared as `List<Long>` and annotated with `@JsonSerialize(contentUsing = ToStringSerializer.class)`
- **THEN** the collection element's documented type equals `"String"`

---

### Requirement: Example JSON values match JSON wire type
The system SHALL generate example values in `getFieldsAndDefaultValue()` and `Body.example` that match the resolved JSON wire type.

#### Scenario: Long serialized as string produces quoted example
- **WHEN** a `Long` field is resolved to JSON wire type `String` via `ToStringSerializer`
- **THEN** the example JSON value for that field is a string literal (e.g., `"0"` or `"1234567890123456789"`)

#### Scenario: Numeric type without serializer produces numeric example
- **WHEN** a `Long` field has no Jackson type override
- **THEN** the example JSON value is a numeric literal (e.g., `0`)

---

### Requirement: JSON type override applied across all documentation entry points
The system SHALL apply Jackson JSON wire type resolution in request body building, response body building, URL/query parameter building, and nested field traversal.

#### Scenario: Response body field type override
- **WHEN** a response DTO field has `@JsonSerialize(using = ToStringSerializer.class)` on a `Long` field
- **THEN** the response body table shows `String` as the field type

#### Scenario: URL parameter type override
- **WHEN** a `@RequestParam Long id` parameter has `@JsonSerialize(using = ToStringSerializer.class)` on the corresponding field in the DTO (or on the parameter itself if annotated)
- **THEN** the URL parameter table shows `String` as the parameter type

#### Scenario: Nested field inside response DTO
- **WHEN** a nested DTO class contains a `Long` field annotated with `@JsonSerialize(using = ToStringSerializer.class)`
- **THEN** the nested field row in the generated doc shows `String` as the type
