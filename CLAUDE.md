# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Doc View is an IntelliJ IDEA plugin (plugin ID: 15305) that generates Markdown API documentation from Java source code. It supports Spring MVC controllers, Dubbo services, and plain POJOs, with upload integration to YApi, ShowDoc, and YuQue.

Platform target: IntelliJ Ultimate 2026.1.1+ (`platformType=IU`, `platformVersion=2026.1.1`). Java 17.

## Commands

```bash
# Launch sandbox IDE with plugin loaded (primary development loop)
./gradlew runIde

# Build distributable ZIP under build/distributions/
./gradlew buildPlugin

# Full compile + verification
./gradlew build

# Clean
./gradlew clean
```

No automated test suite exists. Files under `src/test/java/` are manual Swing UI exploratory tests run directly via `main()`.

Plugin version is pinned to `1.4.99` in `gradle.properties` to prevent marketplace auto-update from overwriting local builds.

## Architecture

### Document Generation Pipeline

The core flow crosses many packages and is the main thing to understand:

```
Right-click on class/method
  → action/AbstractAction (validates PSI context: not enum/annotation)
  → action/PreviewAction or upload/YApiUploadAction etc.
  → service/DocViewService.getInstance(project, psiClass)
      detects framework (priority order): Feign → Dubbo → Spring → POJO
      returns matching impl
  → impl.buildDoc(psiClass, psiMethod?) → List<DocView>
  → DocViewData wraps DocView
  → VelocityUtils.convert(template, docViewData) → Markdown string
  → PreviewForm popup  OR  upload service → external platform API
```

### Framework Detection & Service Impls

`DocViewService.getInstance()` uses static utility checks to select the right parser:
- `FeignPsiUtil.isFeignClass()` → `SpringDocViewServiceImpl` (Feign treated as Spring variant)
- `DubboPsiUtils.isDubboClass()` → `DubboDocViewServiceImpl`
- `SpringPsiUtils.isSpringClass()` → `SpringDocViewServiceImpl`
- `PojoUtils.isPojoClass()` → `PojoDocViewServiceImpl`

Each impl's `buildDoc()` uses PSI utility classes to extract annotations, types, and JavaDoc.

### Key DTOs

- **`DocView`** — the central document model: HTTP path/method, headers (`List<Header>`), request/response body trees (`Body`), URL params (`List<Param>`), framework type
- **`Body`** — recursive tree node representing a nested parameter; `buildBodyDataList()` flattens it for Markdown table rendering
- **`DocViewData`** — Velocity template context wrapping a `DocView`; provides all getter methods referenced in templates

### PSI Extraction Utils

| Util | Responsibility |
|------|---------------|
| `SpringPsiUtils` | Extract `@RequestMapping`, HTTP method, path from Spring annotations |
| `DubboPsiUtils` | Detect `@DubboService`, extract RPC method signatures |
| `ParamPsiUtils` | Recursively resolve `PsiType` → `Body` tree (handles generics, collections, maps) |
| `CustomPsiCommentUtils` | Extract JavaDoc `@param`/`@return`/`@see`/`@link` tags |
| `VelocityUtils` | Render Velocity template string → Markdown |

### Velocity Templates

Templates for Spring, Dubbo, and POJO are stored in `config/TemplateSettings` (project-scoped, user-customizable). `DocViewData` exposes all fields that templates reference. When modifying what appears in generated docs, change both the `DocViewData` getters and the default templates in `TemplateSettings`.

### Upload Integration

Three-layer separation: Action → Service → Facade:
```
*UploadAction → DocViewUploadService
  → YApiServiceImpl / ShowDocServiceImpl / YuQueServiceImpl  (maps DocView → platform DTO)
  → YApiFacadeServiceImpl / ShowDocFacadeServiceImpl / YuQueFacadeServiceImpl  (HTTP calls)
```

Platform credentials live in project-scoped `YApiSettings`, `ShowDocSettings`, `YuQueSettings`.

### Tool Window

`DocViewToolWindowFactory` creates the right-side panel on IDE startup. It builds a tree: Module → Class → Method nodes, each caching their `DocView` objects. `WindowClearAction` invalidates the cache. The window independently re-runs the detection+build pipeline when refreshed.

### PSI Write-back

`WriterService` uses `WriteCommandAction` to sync UI-edited documentation back to Java source as JavaDoc comments. This is how "Doc Editor" saves changes to code.

### Settings Persistence

Two scopes:
- **Application-level** (`ApplicationSettings`): shared across projects
- **Project-level** (`Settings`, `TemplateSettings`, `YApiSettings`, etc.): per-project, stored in `.idea/`

Each settings class has a paired `*Configurable` (UI) and `*Form` (Swing form) class.
