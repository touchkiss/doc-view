## Context

Doc View is an IntelliJ IDEA plugin that generates REST API documentation from Spring/Feign controller classes. It already has:
- `CurlUtils.build(DocView)` — builds curl command strings from `DocView` data
- `DocViewLineMarkerProvider` — adds gutter icons on REST methods with a click-to-preview action
- `EditorAction` / `AbstractAction` — base classes for editor-context actions that extract `PsiClass` and `PsiMethod` from the cursor position

The plugin registers actions in `plugin.xml` under `EditorPopupMenu` for right-click menus. The existing `liuzhihang.doc` group is added to `EditorPopupMenu` with `anchor="first"`.

## Goals / Non-Goals

**Goals:**
- Add a "Copy cURL" action visible in the editor right-click menu on REST controller methods
- Copy a complete curl command to the system clipboard
- Show a balloon notification confirming the copy
- Only show the action when cursor is on a method with a REST mapping annotation

**Non-Goals:**
- Modifying the existing curl generation logic (`CurlUtils`)
- Adding curl to non-REST frameworks (Dubbo, POJO)
- Providing a UI to customize the host URL (use `{{host}}` placeholder as existing curl does)
- Adding the action to the ToolWindow catalog (already covered by `CatalogHttpClientAction` pattern)

## Decisions

### 1. New Action class extending `AbstractAction`
**Decision**: Create `CopyCurlAction extends AbstractAction` and add it to the existing `liuzhihang.doc` group in `plugin.xml`.

**Rationale**: `AbstractAction` already handles extracting `project`, `psiFile`, `editor`, `targetClass`, and `targetMethod` from the editor context. The `update()` method in `AbstractAction` already controls visibility. We only need to override `actionPerformed()` to build curl and copy to clipboard.

**Alternative considered**: Create a standalone `AnAction` like `PreviewRightCopyAction`. Rejected because it would duplicate the PsiClass/PsiMethod extraction logic that `AbstractAction` already provides.

### 2. Reuse `CurlUtils.build()` directly
**Decision**: Call `CurlUtils.build(docView)` on the first `DocView` from `DocViewService.getInstance(project, targetClass).buildDoc(targetClass, targetMethod)`.

**Rationale**: The existing `CurlUtils` handles all the complexity (path variables, query params, headers, body). No need to reinvent. The `DocViewService.buildDoc()` method already handles resolving the method's documentation data.

### 3. Visibility guard in `update()`
**Decision**: Override `update()` to check `targetMethod` has a REST mapping annotation before showing the action.

**Rationale**: `AbstractAction.update()` shows the action for all methods in doc-view-supported classes. We need to further restrict to only methods with `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, or `@RequestMapping` annotations, matching the constants in `SpringConstant.MAPPING_ANNOTATIONS`.

## Risks / Trade-offs

- **[Risk] Method has no `DocView` data** → If `buildDoc()` returns empty list, show a notification that no documentation was generated for this method.
- **[Risk] `{{host}}` placeholder not meaningful for quick copy** → Acceptable; users can find-replace in their terminal. This matches the existing `.http` export behavior.
