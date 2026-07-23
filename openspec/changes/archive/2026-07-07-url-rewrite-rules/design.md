## Context

Doc View reads a Spring controller's REST path via `SpringPsiUtils.path(psiClass, psiMethod)` (class `@RequestMapping` prefix + method mapping) and stores it on the central `DocView` model. `SpringDocViewServiceImpl.buildClassMethodDoc()` is the single place that sets it:

```java
docView.setPath(SpringPsiUtils.path(psiClass, psiMethod));   // line 62
```

`DocView.path` is then consumed everywhere downstream — the Markdown preview (`DocViewData` → Velocity templates), `.http` export, and the YApi/ShowDoc/YuQue upload adapters (which map `DocView` → platform DTOs). Feign interfaces are also routed through `SpringDocViewServiceImpl`. Dubbo and POJO have no REST URL.

Settings are project-scoped and persisted through `Settings` (a `@State` `PersistentStateComponent` writing `DocViewSettings.xml`), edited in a GUI-designer-bound `SettingsForm` with the standard `isModified()` / `apply()` / `reset()` trio. Existing collection settings (e.g. `excludeParamTypes`) are `Set<String>`; there is not yet an in-form editable *table* for structured rows.

The requirement: rewrite the internal controller URL into the externally-facing gateway URL using user-configured regex rules, before the plugin outputs it.

## Goals / Non-Goals

**Goals:**
- After reading a Spring/Feign REST path, apply an ordered list of user regex rules and store the rewritten result on `DocView.path`, so preview, export, and all uploads emit the external URL.
- Let users add, edit, remove, enable/disable, and order multiple rules in the Doc View settings page.
- Support capture-group replacement (`$1`, `$2`, …) so a rule like `^/order/i/admin/seller/(.*)$` → `/seller/c/order/$1` can restructure the path.
- Never break doc generation on a malformed regex — skip the offending rule and log a warning.

**Non-Goals:**
- Rewriting Dubbo RPC signatures or POJO docs (no REST URL to rewrite).
- Rewriting query parameters or the HTTP method — only the path.
- Bidirectional (external→internal) rewrite or "test/preview this rule" tooling in settings (possible follow-up).
- Per-project rule import/export.

## Decisions

### Decision 1: Rewrite at the single `setPath` chokepoint in `SpringDocViewServiceImpl`

**Choice**: Wrap the existing path assignment:
```java
docView.setPath(UrlRewriteUtils.rewrite(psiClass.getProject(),
        SpringPsiUtils.path(psiClass, psiMethod)));
```

**Rationale**: `DocView.path` is set exactly once for Spring/Feign, and every consumer (preview, `.http`, uploads) reads that field. Rewriting here means one small edit covers all output paths with no changes to templates or upload adapters. Feign already flows through this impl, so it is covered for free.

**Alternative considered**: Rewrite inside `SpringPsiUtils.path()`. Rejected — `path()` is a low-level PSI utility with no `Project`/`Settings` access in its signature and is also called internally (`classPath`/`methodPath` recursion); injecting settings lookup there muddies a pure extraction helper.

**Alternative considered**: Rewrite in each upload adapter / preview separately. Rejected — duplicated logic across four+ call sites, and preview would disagree with uploads.

### Decision 2: Ordered `List<UrlRewriteRule>`, applied sequentially with `String.replaceAll`

**Choice**: Store rules as an ordered `List<UrlRewriteRule>` on `Settings`. `UrlRewriteUtils.rewrite` folds the path through each *enabled* rule in list order: `path = path.replaceAll(rule.regex, rule.replacement)`.

**Rationale**: `String.replaceAll` gives full Java regex plus `$n` capture-group substitution in one line — no custom matching engine. A `List` (not `Set`) preserves user-defined order so chained rules are deterministic; each rule sees the previous rule's output, enabling multi-step transforms.

**Alternative considered**: First-match-wins (apply only the first matching rule). Rejected — sequential fold is strictly more expressive (a single-rule config behaves identically) and matches how gateway rewrite chains work.

### Decision 3: `UrlRewriteRule` as a plain public-field bean

**Choice**: New `UrlRewriteRule` with public fields `boolean enabled = true`, `String regex = ""`, `String replacement = ""` and a public no-arg constructor.

**Rationale**: IntelliJ's `XmlSerializerUtil` serializes public fields (and no-arg beans) inside a `List` cleanly into `DocViewSettings.xml`. Keeping it a dumb data holder avoids lombok/final-field constructor friction with the serializer.

**Alternative considered**: Reuse `Set<String>` with an encoded `"regex||replacement"` string (matching existing collection settings). Rejected — brittle, unparseable in the UI, no per-rule enable flag, loses ordering.

### Decision 4: Editable table in `SettingsForm` via `ToolbarDecorator`

**Choice**: Add a "URL Rewrite" `JPanel` placeholder to `SettingsForm.form`, and in the constructor build a `com.intellij.util.ui.ListTableModel`-backed `TableView` (columns: Enabled checkbox / Regex / Replacement) wrapped in `ToolbarDecorator.createDecorator(...).setAddAction(...).setRemoveAction(...)`. `isModified`/`apply`/`reset` compare and copy the model rows against `Settings.urlRewriteRules` (deep copy so edits are not committed until Apply).

**Rationale**: `ToolbarDecorator` is the platform-standard add/remove table used across IDEA settings; it gives inline editing and ordering for free with minimal code, and fits the existing GUI-designer form by dropping into a placeholder panel.

**Alternative considered**: A separate `Configurable` page just for rewrite rules. Rejected — the feature is small and belongs beside the other Doc View settings; a whole new page is more scaffolding than the feature warrants.

## Risks / Trade-offs

- **[Risk] Malformed user regex throws `PatternSyntaxException`** → `UrlRewriteUtils.rewrite` wraps each rule's `replaceAll` in try/catch, skips the failing rule, and logs a warning; doc generation continues with the un-rewritten (or partially rewritten) path. Acceptable — a broken rule must never block documentation.
- **[Risk] A rule with a broad pattern rewrites unintended paths** → This is inherent to user-authored regex; mitigated by per-rule enable/disable and by anchoring guidance (`^`/`$`) in the field examples. Not the plugin's job to validate intent.
- **[Trade-off] Rewrite applies to *all* Spring/Feign output uniformly** — there is no per-endpoint opt-out. Acceptable for the stated gateway use case; a rule can be scoped narrowly via its own pattern.
- **[Risk] `List<UrlRewriteRule>` serialization** — `XmlSerializerUtil.copyBean` must round-trip the list. Verify the bean serializes/deserializes correctly in a sandbox restart. Low risk — this is the documented IntelliJ pattern for lists of beans.

## Open Questions

- Should the rewrite also apply to the `.http` file's request line specifically, or is inheriting the rewritten `DocView.path` sufficient? → Inheriting is sufficient; `.http` generation reads `DocView.path`. Confirm during verification.
- Should there be a "test rule against a sample URL" affordance in the settings table? → Deferred; out of scope for the first cut.
