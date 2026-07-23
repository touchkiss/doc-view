## Context

Doc View builds a `DocView` model from Spring/Feign PSI, wraps it in `DocViewData`, and renders Markdown via Velocity templates (`TemplateSettings.springTemplate`, default from `DocViewBundle.properties`). The default Spring template already has an **接口描述** section:

```
**接口描述**
> ${DocView.desc}
```

`DocViewData` exposes getters consumed by Velocity (`getDesc()`, `getPath()`, `getRequestHeader()`, etc.). Request examples are already formatted as fenced blocks (`reqBodyExample` wraps JSON in ` ```JSON `). The `.http` export in `CustomFileUtils.openHttp()` uses `{{host}}` + `docView.getPath()` as the URL placeholder.

`DocView.domain` exists but is currently set to `Collections.emptyList()` in `SpringDocViewServiceImpl`; there is no user-facing base-URL setting yet.

## Goals / Non-Goals

**Goals:**
- For every Spring/Feign REST endpoint, auto-generate a copy-pasteable `curl` command from method, path, headers, query params, and body.
- Embed it in the **接口描述** section as a Markdown ` ```bash ` fenced code block, below the existing description text.
- Flow through the existing single pipeline (`DocViewData` → Velocity) so preview, Markdown export, and YApi/ShowDoc/YuQue uploads all gain the curl block without per-target changes.
- Keep user-customizable templates working: expose `${DocView.curlExample}` as an optional template variable.

**Non-Goals:**
- Generating curl for Dubbo RPC or POJO docs.
- Adding a settings UI for base URL, auth tokens, or curl formatting preferences.
- Replacing the existing `.http` file export or **请求示例** JSON blocks.
- Multi-environment curl variants (one curl per domain).

## Decisions

### Decision 1: New `curlExample` field on `DocViewData`, not mutating `desc`

**Choice**: Add `private final String curlExample` populated in the constructor; expose `getCurlExample()` for Velocity as `${DocView.curlExample}`. The field value is already a complete Markdown snippet (empty string when not applicable).

**Rationale**: `desc` comes from JavaDoc/annotation text and is also written back by `WriterService`. Appending curl to `desc` would pollute source comments and break round-tripping. A dedicated field keeps concerns separate and lets templates choose placement.

**Alternative considered**: Override `getDesc()` to append curl. Rejected — templates and upload mappers that read raw description would get unexpected content; harder for users to customize placement.

### Decision 2: Pure `CurlUtils.build(DocView)` static builder

**Choice**: New `utils/CurlUtils.java` with `String build(DocView docView)` returning the raw curl command (no Markdown fences), and `DocViewData` wrapping it:

```java
private static String curlMarkdown(DocView docView) {
    String curl = CurlUtils.build(docView);
    if (StringUtils.isBlank(curl)) return "";
    return "```bash\n" + curl + "\n```\n\n";
}
```

**Rationale**: Mirrors `reqBodyExample()` / `respBodyExample()` formatting in `DocViewData`. A pure builder is easy to unit-test with a `main()` under `src/test/java/` (repo convention).

**Alternative considered**: Inline all logic in `DocViewData`. Rejected — constructor is already large; curl assembly has enough branching to warrant its own class.

### Decision 3: URL = `{{host}}` + path (+ query string from URL params)

**Choice**: Build URL as `"{{host}}" + path`, substituting `{pathVar}` placeholders in the path with example values from `reqParamList` where the param is a path variable. Append `?key=value&...` for `@RequestParam` entries that are not in the path.

**Rationale**: Matches `CustomFileUtils.openHttp()` (`{{host}}` placeholder). Users already define `host` in HTTP Client env files.

**Alternative considered**: Use `DocView.domain` first entry. Rejected — domain is always empty today; would require new settings work.

### Decision 4: Curl command shape by HTTP method and content type

**Choice**:
- Always start with `curl -X <METHOD> '<url>'`.
- Emit `-H 'Name: Value'` for each header in `docView.getHeaderList()` (use header example or empty string).
- For `POST`/`PUT`/`PATCH` with `ContentTypeEnum.JSON` and non-blank `reqBodyExample`: add `-H 'Content-Type: application/json'` and `-d '<escaped-json>'` (single-quoted, escape embedded `'` as `'\''`).
- For form content or form example string: use `-d 'key=value'` per param or the raw `reqFormExample` if present.
- For `GET`/`DELETE` with only query params: omit `-d`.
- Use `\` line continuations for readability when the command exceeds ~120 chars or has more than two `-H`/`-d` flags.

**Rationale**: Produces idiomatic, runnable curl closest to what testers expect.

**Alternative considered**: Always single-line curl. Rejected — long JSON bodies are unreadable in docs.

### Decision 5: Update default Spring template in bundle only

**Choice**: Change `template.spring.init` in `DocViewBundle.properties`:

```
**接口描述**
> ${DocView.desc}

${DocView.curlExample}
```

**Rationale**: New installs and "reset template" get curl automatically. Users with customized templates can opt in by adding `${DocView.curlExample}` manually — no forced migration.

**Alternative considered**: Auto-inject curl without template change. Rejected — bypasses the Velocity template system and breaks user template ownership.

### Decision 6: Only populate `curlExample` for Spring/Feign framework type

**Choice**: In `DocViewData` constructor, call `curlMarkdown(docView)` only when `docView.getType() == FrameworkEnum.SPRING`; otherwise set `curlExample = ""`.

**Rationale**: Dubbo/POJO have no meaningful HTTP curl. Feign is typed as SPRING in the existing detection pipeline.

## Risks / Trade-offs

- **[Risk] Path variables without examples leave `{id}` literals in the URL** → Use param `example` when present; fall back to `{name}` unchanged. Document in verification that users should fill `@param` examples for best curl quality.
- **[Risk] JSON body with single quotes breaks shell quoting** → Escape `'` in `-d` payload using the `'\''` idiom; keep JSON on one logical line inside quotes.
- **[Trade-off] `{{host}}` is not a real URL** → Same as existing `.http` export; acceptable — consumers substitute their environment host.
- **[Trade-off] Users with old customized Spring templates won't see curl until they add `${DocView.curlExample}`** → Mitigated by updating the bundle default; documented in tasks verification step.
- **[Risk] Very large request bodies make curl blocks huge** → No truncation in v1; acceptable for typical API payloads. Could cap length in a follow-up.

## Migration Plan

1. Ship `CurlUtils` + `DocViewData.curlExample` + updated bundle default template.
2. No data migration — new field is computed at render time.
3. Rollback: revert code and bundle string; customized templates referencing `${DocView.curlExample}` would render blank (harmless).

## Open Questions

- Should curl include headers marked as optional with blank examples? → **Yes**, include all headers from `headerList`; use example value or empty string.
- Should file-upload (`@RequestPart` / multipart) be supported in v1? → **Deferred**; emit curl without `-F` unless `ContentTypeEnum` already distinguishes multipart (unlikely today).
