## Context

Doc View builds API documentation by traversing Java PSI trees and populating `Body` / `Param` DTOs with field names, Java-declared types, descriptions, and example values. Type resolution happens in `ParamPsiUtils.buildBodyParam()` (line ~115–124) where `field.getType().getPresentableText()` is set on `Body.type` after generic substitution via `replaceFieldType()`.

Jackson annotations are already partially handled for field **names** (`@JsonProperty` in `DocViewUtils.fieldName()`), but not for field **types**. In production Spring APIs, `@JsonSerialize(using = ToStringSerializer.class)` on `Long` / `BigInteger` fields is a common pattern to prevent JavaScript precision loss. The generated doc must reflect the JSON wire contract, not the Java declaration.

The resolution must work entirely via IntelliJ PSI (static analysis) — no runtime Jackson `ObjectMapper` invocation.

## Goals / Non-Goals

**Goals:**
- When a `PsiField` or `PsiRecordComponent` has `@JsonSerialize`, resolve the effective JSON output type and override `Body.type` / `Param.type`.
- When a field has `@JsonDeserialize` (without `@JsonSerialize`), resolve the expected JSON input type for request-body documentation.
- Handle the `using` attribute and collection-related attributes (`contentUsing`, `keyUsing`, `nullsUsing`) on `@JsonSerialize`.
- Map well-known Jackson built-in serializers to JSON types without classpath execution.
- Infer types from custom `JsonSerializer` / `JsonDeserializer` subclasses when the handled type is statically resolvable.
- Update example JSON values to match the JSON wire type (string-quoted longs, etc.).
- Apply the override at all type-resolution entry points: body params, response body, URL params, nested fields, record components, and collection generics.

**Non-Goals:**
- Full runtime-accurate Jackson module configuration (`ObjectMapper` mixins, `SimpleModule` registrations, global serializers).
- `@JsonFormat` shape/pattern resolution (e.g., date format strings) — only the base JSON type (string vs number).
- Kotlin `@Serializable` or Gson `@SerializedName` type overrides.
- Resolving serializer types set programmatically via `ObjectMapper.registerModule()`.
- WriterService write-back of JSON types to source.

## Decisions

### Decision 1: Centralize logic in `JacksonPsiUtils`

**Choice**: Create `JacksonPsiUtils` with a single entry point `resolveJsonWireType(PsiModifierListOwner owner, PsiType javaType)` returning a `JsonWireType` result (type string + optional example override).

**Rationale**: Jackson annotation parsing and serializer class analysis is cross-cutting — used by `ParamPsiUtils`, `SpringPsiUtils`, and example JSON builders. A single utility avoids duplication and keeps well-known serializer mappings in one place.

**Alternative considered**: Inline annotation checks in each call site. Rejected — four+ call sites would diverge over time.

### Decision 2: `@JsonSerialize` takes priority over `@JsonDeserialize`

**Choice**: For response-body / general documentation, prefer `@JsonSerialize` when both annotations are present. For request-body fields, use `@JsonDeserialize` when `@JsonSerialize` is absent.

**Rationale**: Matches Jackson's actual behaviour — serialize and deserialize can specify different wire formats, but the most common case is `@JsonSerialize` on response fields. Request docs should reflect what the client sends, so `@JsonDeserialize` applies when no serialize override exists.

**Alternative considered**: Always use serialize. Rejected — request body docs would be wrong for asymmetric annotations.

### Decision 3: Well-known serializer lookup table + PSI generic inference

**Choice**: Maintain a static `Map<String, String>` of well-known Jackson serializer FQCN → JSON type (e.g., `com.fasterxml.jackson.databind.ser.std.ToStringSerializer` → `String`). For unmapped custom classes, resolve via PSI:
1. Check if the class extends `JsonSerializer<T>` and read the `T` type parameter.
2. If `T` is a concrete type, map Java type → JSON type using standard rules (`String` → `String`, primitives → same name, objects → class name).
3. For `ToStringSerializer`-like custom serializers whose `serialize()` writes a string regardless of input, detect by checking if the class name ends with `ToStringSerializer` or if it extends `ToStringSerializer`.

**Rationale**: Covers 90%+ of real-world usage (`ToStringSerializer`, `NumberSerializer`, `DateSerializer`, `NullSerializer`) without fragile bytecode analysis. PSI generic resolution handles project-local wrappers.

**Alternative considered**: Invoke Jackson at runtime via `ObjectMapper`. Rejected — requires classpath execution, slow, and may fail in incomplete IDE indices.

### Decision 4: Apply override after generic substitution, before recursion

**Choice**: In `buildBodyParam()`, call `JacksonPsiUtils.resolveJsonWireType(field, type)` immediately after `replaceFieldType()` and before setting `body.setType()` and before collection/map recursion decisions.

**Rationale**: Generic substitution must happen first (`List<T>` → `List<UserDto>`), then Jackson override applies to the resolved element type. Collection handling (`contentUsing`) needs the collection's element type context.

**Alternative considered**: Apply at template render time. Rejected — example JSON and upload adapters also consume `Body.type` directly.

### Decision 5: JSON type mapping rules

**Choice**: Map resolved Java output types to JSON presentable types using:
| Java type | JSON type |
|-----------|-----------|
| `String`, `CharSequence` | `String` |
| `long`/`Long`, `int`/`Integer`, etc. | same (unless serializer overrides) |
| `BigDecimal`, `BigInteger` | `String` when `ToStringSerializer`; else `number` |
| `Date`, `LocalDateTime`, etc. | `String` (ISO-8601) when using date serializers |
| Custom POJO | class simple name (unchanged) |
| `null` via `NullSerializer` | `null` |

**Rationale**: Aligns with JSON Schema conventions used in API docs.

### Decision 6: Example value adjustment

**Choice**: When JSON wire type is `String` but Java type is numeric, set `body.setExample("\"0\"")` or a quoted placeholder. Update `getFieldsAndDefaultValue()` to emit string literals for these fields.

**Rationale**: Example JSON must match the documented type so copy-paste testing works.

## Risks / Trade-offs

- **[Risk] Serializer class not on IDE classpath** — Custom serializer in an unindexed dependency cannot be resolved. → Mitigation: Fall back to Java declared type; log nothing (silent fallback is current plugin convention).

- **[Risk] Complex custom serializers with runtime-only type logic** — Serializers that inspect runtime values to decide output shape cannot be statically analyzed. → Mitigation: Document as limitation; well-known table covers common cases.

- **[Risk] `@JsonSerialize` on getter instead of field** — Jackson supports annotation on getter/setter. → Mitigation: Phase 1 reads field/component annotations only; getter annotation support deferred to follow-up.

- **[Risk] `contentUsing` on collections** — `@JsonSerialize(contentUsing = ToStringSerializer.class)` on `List<Long>` means elements are strings. → Mitigation: When collection is detected, check `contentUsing` on the field annotation and override the iterable element type before building child `Body` nodes.

- **[Trade-off] Documented type changes for existing users** — Fields previously shown as `Long` will show `String`. This is the intended behaviour but changes existing generated docs. → Acceptable: docs become more accurate.

## Migration Plan

No data migration required. Change is purely in doc generation logic. Users refresh docs to see updated types. No settings toggle in v1 — always on when Jackson annotations are detected.

## Open Questions

- Should we add a settings toggle to disable JSON type override for users who prefer Java types? → Defer; default on.
- Should `@JsonFormat(shape = JsonFormat.Shape.STRING)` on a numeric field also trigger `String` documentation without an explicit serializer? → Consider for follow-up; out of scope for v1.
- For `@JsonDeserialize(using = X)` on request params (query string), should the documented type reflect JSON or the string representation in URL? → Use JSON wire type; URL params are strings at HTTP level but Jackson deserializes them.
