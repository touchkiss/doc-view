## Why

JDK 16 introduced `record` as a first-class type, but Doc View currently blocks or silently ignores record classes — users cannot right-click a record to generate docs, and records used as request/response types in Spring or Dubbo interfaces render with missing or incorrect field descriptions. As records become increasingly common in modern Java codebases, this gap is a visible, recurring pain point.

## What Changes

- **Allow record classes in `AbstractAction`**: Remove the implicit exclusion of records (currently the check only guards against enum/annotation; records slip through but fail downstream).
- **Add record detection in `DocViewService.getInstance()`**: Recognize records as a POJO-like type and route them to `PojoDocViewServiceImpl`.
- **Extend `PojoUtils.isPojoClass()`**: Return `true` for any `PsiClass` that is a record.
- **Record-aware field iteration in `PojoUtils.buildBody()`**: Iterate over `PsiRecordComponent[]` (via `psiClass.getRecordComponents()`) instead of `getAllFields()` for record classes, so descriptions come from component-level JavaDoc/annotations rather than the synthetic backing fields.
- **Record-aware field iteration in `ParamPsiUtils.buildBodyList()` and `buildRespBody()`**: When traversing a record type as a nested parameter, use record components for field extraction.
- **Record component description extraction in `DocViewUtils`**: Add helpers to read description, required status, and field name from `PsiRecordComponent`, reusing the existing annotation-checking logic (Swagger `@Schema`, `@ApiModelProperty`, inline comments).
- **`getFieldsAndDefaultValue()` record support**: Guard against treating records as plain classes; iterate record components for default value extraction.

## Capabilities

### New Capabilities
- `record-type-support`: End-to-end Doc View support for JDK 16+ record classes — both as top-level targets (right-click → Doc View on a record) and as nested field types within Spring/Dubbo/POJO interfaces (field descriptions, required flags, and example values extracted from record components).

### Modified Capabilities
<!-- No existing spec-level requirement documents exist — this is greenfield spec work -->

## Impact

- **`action/AbstractAction.java`**: No change needed (records are not enums/annotation types; existing guard already passes them through). Verify `targetClass.isRecord()` does not need explicit handling.
- **`service/DocViewService.java`**: Add record branch before POJO check using `psiClass.isRecord()`.
- **`utils/PojoUtils.java`**: `isPojoClass()` returns `true` for records; `buildBody()` branches on `psiClass.isRecord()` to iterate `getRecordComponents()`.
- **`utils/ParamPsiUtils.java`**: `buildBodyList()` and `getFieldsAndDefaultValue()` branch on `psiClass.isRecord()` to iterate components.
- **`utils/DocViewUtils.java`**: New overloads `fieldDesc(PsiRecordComponent)`, `fieldName(PsiRecordComponent)`, `isRequired(PsiRecordComponent)` mirroring existing `PsiField` variants.
- **No template changes required**: Velocity templates consume `Body` DTOs which are framework-agnostic.
- **No upload service changes required**: Upload adapters consume `DocView` which is already framework-agnostic.
- **Dependencies**: Requires IntelliJ PSI API `PsiRecordComponent` (available since IDEA 2021.1+; platform target is 2026.1.1 so no compatibility risk).
