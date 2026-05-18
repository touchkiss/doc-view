## Context

Doc View parses Java source via IntelliJ PSI and generates Markdown documentation. The framework-detection pipeline (`DocViewService.getInstance()`) routes each `PsiClass` to a service implementation (`SpringDocViewServiceImpl`, `DubboDocViewServiceImpl`, `PojoDocViewServiceImpl`). Field extraction traverses `PsiField` instances returned by `psiClass.getAllFields()`.

Java 16 records are first-class nominal types: their components are declared in the record header (e.g., `record Point(int x, int y)`), and the compiler synthesises private-final backing fields, accessor methods, `equals`, `hashCode`, and `toString`. In IntelliJ PSI, the record-component declarations are accessible via `psiClass.getRecordComponents()` returning `PsiRecordComponent[]`. The synthetic backing fields **do** appear in `getAllFields()`, but their attached JavaDoc and annotations live on the `PsiRecordComponent` node, not on the `PsiField` node — so using `getAllFields()` alone loses all developer-written descriptions.

Current blockers for record support:
1. `DocViewService.getInstance()` has no record branch; a bare record class that does not satisfy POJO name heuristics (`*Dto`, `*Vo`, …) throws `DocViewException("not.support")`.
2. `PojoUtils.isPojoClass()` checks name suffixes and a configurable annotation — records not matching these are excluded.
3. `PojoUtils.buildBody()` and `ParamPsiUtils.buildBodyList()` call `getAllFields()`, which returns backing fields whose `PsiField` nodes carry no attached comments.
4. `DocViewUtils.fieldDesc()` / `isRequired()` / `fieldName()` only accept `PsiField`; there are no overloads for `PsiRecordComponent`.

## Goals / Non-Goals

**Goals:**
- Right-clicking any record class in the editor triggers Doc View and generates a POJO-style document with component names, types, and descriptions.
- Records used as parameter or return types inside Spring MVC / Dubbo / Feign interfaces resolve their field descriptions from record-component JavaDoc and annotations.
- Descriptions, required flags, and field names are extracted from `PsiRecordComponent` using the same Swagger/annotation/comment priority as for `PsiField`.
- No change to Velocity templates or upload adapters — consumers are `Body` DTOs which are already framework-agnostic.

**Non-Goals:**
- Generating doc for records inside enums or annotation types (these are already excluded).
- Handling Kotlin data classes or Scala case classes.
- Supporting record patterns (JDK 21 preview) in switch expressions.
- WriterService write-back to record components (editing generated docs back into source is not in scope for this change).

## Decisions

### Decision 1: Route records through existing `PojoDocViewServiceImpl`

**Choice**: Add a `psiClass.isRecord()` guard in `DocViewService.getInstance()` before the POJO check, routing all records to `PojoDocViewServiceImpl`.

**Rationale**: Records are semantically POJOs — immutable data carriers with no HTTP routing or RPC semantics. Reusing the existing impl avoids a new service class and new Velocity template. The only difference is field extraction, which is encapsulated in `PojoUtils`.

**Alternative considered**: A dedicated `RecordDocViewServiceImpl`. Rejected — it would duplicate most of `PojoDocViewServiceImpl` for no functional gain; the divergence is at the `buildBody()` level, not the doc-building level.

### Decision 2: Branch on `isRecord()` at the component-iteration boundary

**Choice**: In `PojoUtils.buildBody()` and `ParamPsiUtils.buildBodyList()` / `getFieldsAndDefaultValue()`, check `psiClass.isRecord()` and, if true, iterate `psiClass.getRecordComponents()` rather than `psiClass.getAllFields()`.

**Rationale**: `PsiRecordComponent` is the canonical PSI node for a record field; it carries the developer-written JavaDoc and annotations. The synthetic `PsiField` from `getAllFields()` has the same name and type but no attached documentation.

**Alternative considered**: Iterating `getAllFields()` and mapping each field back to its record component via name lookup. Rejected — brittle and indirect; `getRecordComponents()` is the correct API.

### Decision 3: New `DocViewUtils` overloads for `PsiRecordComponent`

**Choice**: Add `fieldDesc(PsiRecordComponent)`, `fieldName(PsiRecordComponent)`, and `isRequired(PsiRecordComponent)` as new static methods in `DocViewUtils`, following the exact same priority order as the existing `PsiField` variants (Swagger `@Schema` → `@ApiModelProperty` → inline comment/JavaDoc).

**Rationale**: `PsiRecordComponent` and `PsiField` share the `PsiAnnotationOwner` interface but not `PsiField` directly, so the existing methods cannot accept components without an unsafe cast. Clean overloads keep the call sites simple and testable.

**Alternative considered**: Extracting a shared `PsiAnnotationOwner`-based helper. Possible but over-engineered for now; the overloads share little real logic beyond annotation lookup, which is already a one-liner (`AnnotationUtil.isAnnotated`).

### Decision 4: `PojoUtils.isPojoClass()` returns `true` for any record

**Choice**: Short-circuit at the top of `isPojoClass()` with `if (psiClass.isRecord()) return true`.

**Rationale**: All records are pure data types and a natural fit for POJO-mode documentation. The existing name-suffix heuristic (`*Dto`, `*Vo`, …) is a workaround for classes that lack a definitive structural marker; records have one (`isRecord()`).

**Alternative considered**: Requiring a configurable annotation on the record class. Rejected — too much friction; records are self-evidently data carriers.

## Risks / Trade-offs

- **[Risk] Compact record constructors with complex validation** — A record can override its canonical constructor with validation logic. Doc View only reads component declarations; it will not surface any runtime constraints expressed in the constructor body. → Mitigation: This is consistent with how Doc View treats regular classes (it reads field declarations, not constructor logic). Acceptable.

- **[Risk] `getAllFields()` may still be called for nested record types via `ParamPsiUtils.buildBodyParam()`** — `buildBodyParam` receives a `PsiField` whose type might resolve to a record `PsiClass`. When it recurses into that class via `buildBodyList`, the new `isRecord()` branch will apply. However, the entry point field itself is a `PsiField`, not a `PsiRecordComponent`, so its description is extracted from the outer class's field declaration (which may have annotations). → Mitigation: This is correct behaviour — the outer field is what the developer annotated.

- **[Risk] `PsiRecordComponent` API stability** — `getRecordComponents()` is available since IDEA 2021.1. The platform target is 2026.1.1, so there is no compatibility concern at runtime, but we should verify the method is not deprecated in 2026.1.1. → Mitigation: Check IntelliJ platform changelog before final review.

- **[Trade-off] No write-back support for record components** — `WriterService` writes JavaDoc to `PsiField` nodes. For records, the component is the correct target, but this requires a separate `WriteCommandAction` path. Excluded from this change to keep scope focused.

## Open Questions

- Should the tool window (`DocViewToolWindowFactory`) display records in the class tree? Currently it scans for methods; records have zero user-defined methods by default. → Likely show the record class as a leaf with one generated doc entry. Investigate `buildClassDoc()` behaviour for zero-method classes.
- For records with compact canonical constructors, should parameter-level annotations (e.g., `@NotNull` on the constructor parameter) propagate to the component's `isRequired()` check? → Defer to a follow-up; current scope reads annotations on the component declaration only.
