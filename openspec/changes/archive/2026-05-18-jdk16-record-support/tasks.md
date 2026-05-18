## 1. Record Detection Utilities

- [x] 1.1 In `PojoUtils.isPojoClass()`, add a short-circuit at the top: `if (psiClass.isRecord()) return true;` inside the `runReadAction` lambda
- [x] 1.2 In `DocViewService.getInstance()`, add a record branch before the existing POJO check: `if (psiClass.isRecord()) return ApplicationManager.getApplication().getService(PojoDocViewServiceImpl.class);`

## 2. DocViewUtils — PsiRecordComponent Overloads

- [x] 2.1 Add `fieldDesc(PsiRecordComponent component)` in `DocViewUtils`: check `@Schema(description)` → `@ApiModelProperty(value)` → inline/JavaDoc comment, matching the priority of the existing `fieldDesc(PsiField)` method
- [x] 2.2 Add `fieldName(PsiRecordComponent component, boolean parentIsProto)` in `DocViewUtils`: check `@JsonProperty` override (if feature enabled in settings) → component name, applying snake_case conversion if configured
- [x] 2.3 Add `isRequired(PsiRecordComponent component)` in `DocViewUtils`: check configured required annotations → `@Schema(required=true)` → `@ApiModelProperty(required=true)` → JavaDoc required tag

## 3. PojoUtils — Record-Aware Body Building

- [x] 3.1 In `PojoUtils.buildBody()`, add a branch: if `psiClass.isRecord()`, iterate `psiClass.getRecordComponents()` and call a new `buildBodyParamFromComponent()` helper instead of iterating `getAllFields()`
- [x] 3.2 Implement `ParamPsiUtils.buildBodyParamFromComponent(PsiClass parentClass, PsiRecordComponent component, Map<String, PsiType> genericsMap, Body parent, Map<String, Boolean> parentChildPair)` that mirrors `buildBodyParam(PsiField)` but reads name/desc/required from the component node using the new `DocViewUtils` overloads
- [x] 3.3 In `PojoUtils.reqBodyJson()`, handle the record case: if `psiClass.isRecord()`, call `getFieldsAndDefaultValueForRecord()` instead of `getFieldsAndDefaultValue()`

## 4. ParamPsiUtils — Nested Record Field Traversal

- [x] 4.1 In `ParamPsiUtils.buildBodyList()`, add a branch: if `psiClass.isRecord()`, iterate `psiClass.getRecordComponents()` and call `buildBodyParamFromComponent()` instead of `buildBodyParam(PsiField)`
- [x] 4.2 In `ParamPsiUtils.getFieldsAndDefaultValue(PsiClass, Map, LinkedList)`, add a branch: if `psiClass.isRecord()`, iterate `psiClass.getRecordComponents()` to populate the field map (use component type to determine default value, same logic as for fields)
- [x] 4.3 Implement `getFieldsAndDefaultValueForRecord(PsiClass recordClass, Map<String, PsiType> genericMap, LinkedList<String> qualifiedNameList)` as a private helper in `ParamPsiUtils`, using `getRecordComponents()` and the same type-dispatch logic (primitive / wrapped / collection / map / object recursion) as the existing method

## 5. Tool Window — Record Class Visibility

- [x] 5.1 In `DocViewUtils.isDocViewClass()`, add a record check: if `psiClass.isRecord()`, return `true` (so the tool window tree includes record classes)
- [x] 5.2 Verify that `PojoDocViewServiceImpl.buildClassDoc()` works correctly for a record class with zero user-defined methods — the existing implementation calls `buildClassMethodDoc(psiClass, null)` which should work after steps 3.1–3.2 are complete

## 6. Verification

- [x] 6.1 Create a test record class in the sandbox project (e.g., `record UserDto(String name, int age)`) and verify right-click → Doc View generates a Markdown doc with `name` and `age` fields
- [x] 6.2 Create a Spring controller method with a record `@RequestBody` parameter and verify the generated request body table shows the record's component fields
- [x] 6.3 Create a Spring controller method returning a record and verify the response body table shows the record's component fields
- [x] 6.4 Create a record with `@Schema` annotations and verify descriptions appear correctly in the generated doc
- [x] 6.5 Create a regular class with a record-typed field and verify the record's components expand as nested child rows
- [ ] 6.6 Verify that the tool window displays record classes in the tree and the preview panel shows correct Markdown when a record node is selected
