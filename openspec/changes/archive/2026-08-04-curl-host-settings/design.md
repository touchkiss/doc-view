## Context

`CurlUtils.build(DocView)` emits a URL of the form `{{host}}` + path (`CurlUtils.java:128`) and feeds two distinct consumers:

```
CopyCurlAction:51 ──────────────► CurlUtils.build(docView)
                                    then curl.replace("{{host}}", "http://localhost:8080")   ← hardcoded

DocViewData ctor:138 ─► curlMarkdown(docView) ─► CurlUtils.build(docView)
YApiServiceImpl:139 ──► curlMarkdown(docView) ─┘  (leaves {{host}} literal)
                          └─► preview popup / Markdown export / YApi·ShowDoc·YuQue upload
```

The gRPC side is separate and simpler: `GrpcCurlUtils` holds `DEFAULT_GRPC_HOST = "localhost:9090"` and already has an unused `build(host, service, method, json)` overload. `ProtoGrpcCopyCurlAction:58` calls the 3-arg overload, so the host is never configurable. The archived `grpc-copy-curl` spec already requires a settings-driven host — this closes a specified-but-unimplemented gap rather than inventing a requirement.

`Settings` is a project-level `PersistentStateComponent` annotated `@Data`, stored under `.idea/`. Its UI is `ui/SettingsForm.java` bound to the GUI-Designer XML `ui/Settings.form` (`bind-to-class="...SettingsForm"`), whose `rootPanel` is a `GridLayoutManager` with `row-count="11"` and rows 0–10 occupied. `SettingsForm` follows a strict three-method contract — `isModified()` (one long boolean chain), `apply()`, `reset()` — that every field must be added to in all three places.

The decision taken before this design: **copying and document generation get separate host settings.** A single setting was rejected because `CurlUtils.build()` serves both paths, so one value would have forced identical behavior on the clipboard and on documents uploaded to YApi.

## Goals / Non-Goals

**Goals:**

- Three project-level host settings — Copy cURL, Doc cURL, gRPC cURL — editable on the Doc View settings page.
- Defaults reproduce today's behavior exactly: `http://localhost:8080` for copy, `{{host}}` for documents, and for gRPC the `http://localhost:9090` value the user asked for.
- Values are honored verbatim, so `{{order-web}}` is a valid setting; no URL validation.
- A blank value falls back to that setting's default rather than producing a hostless URL.
- Existing projects with an older `.idea/` config keep working with no migration.
- `.http` export keeps its literal `{{host}}` (IntelliJ HTTP client env var syntax).

**Non-Goals:**

- Multiple named environments / environment switching. One host per setting.
- URL validation, reachability checks, or scheme normalization.
- A gRPC document-generation host — `DocViewData.curlExample` is populated only for `FrameworkEnum.SPRING`, so no gRPC document path exists to configure.
- Touching `DocView.domain` (a `List<String>` that every service impl sets to `Collections.emptyList()`; unused, and not worth repurposing here).
- Changing how `URL rewrite rules` transform paths.

## Decisions

### D1: Substitute after building, not inside `CurlUtils`

`CurlUtils.build(DocView)` keeps emitting the literal `{{host}}` token and stays a pure function of `DocView`. A new `CurlHostUtils` performs substitution as a separate step:

```java
public static String applyCopyHost(Project project, String curl)   // {{host}} → Copy cURL host
public static String applyDocHost(Project project, String curl)    // {{host}} → Doc cURL host
```

Rationale: `CurlUtils.build(DocView)` has no `Project` handle, and threading one in would change a signature with four call sites plus the exploratory `CurlUtilsTest`. Substituting afterwards means each consumer picks its own host with a one-line change, `CurlUtils` is untouched, and the token remains the single seam between building and hosting.

*Alternatives considered.* (a) `CurlUtils.build(DocView, String host)` — pushes host knowledge into the builder and forces every caller to resolve settings first, including `CustomFileUtils`, which must *not* substitute. (b) Read `Settings` inside `CurlUtils` via `docView.getPsiClass().getProject()` — makes a pure utility depend on project services and gives it no way to distinguish the copy path from the document path, which is the whole point of this change.

### D2: Where each substitution is applied

| Consumer | Host used | Change |
|---|---|---|
| `CopyCurlAction:60` | Copy cURL host | replace the hardcoded `"http://localhost:8080"` literal |
| `DocViewData.curlMarkdown` | Doc cURL host | apply after `CurlUtils.build`; resolve `Project` via `docView.getPsiClass().getProject()`, the pattern already used at `DocViewData:114` |
| `YApiServiceImpl:139` | Doc cURL host | none — it calls `curlMarkdown`, so it inherits the behavior |
| `CustomFileUtils:113` (`.http` export) | none | untouched, stays literal `{{host}}` |
| `ProtoGrpcCopyCurlAction:58` | gRPC cURL host | switch to the existing `GrpcCurlUtils.build(host, ...)` overload |

Keeping `curlMarkdown(DocView)` static preserves the `YApiServiceImpl` call site unchanged.

### D3: Default values, and the doc default as a no-op

| Setting | Field | Default |
|---|---|---|
| Copy cURL host | `curlCopyHost` | `http://localhost:8080` |
| Doc cURL host | `curlDocHost` | `{{host}}` |
| gRPC cURL host | `grpcCurlHost` | `http://localhost:9090` |

The Doc cURL default being the token itself makes substitution an identity operation for unconfigured projects, so preview, export, and upload output is byte-identical to today. `applyDocHost` short-circuits when the configured value equals `{{host}}`, avoiding a pointless self-replacement.

Blank handling: `StringUtils.isBlank` → fall back to the default constant, so clearing the field in the UI cannot produce `curl -X GET '/api/users'`.

Defaults live as constants on `Settings` (not scattered in the actions) so the form, the substitution helper, and the persisted defaults cannot drift apart.

### D4: gRPC default gains a scheme

`GrpcCurlUtils.DEFAULT_GRPC_HOST` changes from `localhost:9090` to `http://localhost:9090`, per the explicit request. Worth recording plainly: real `grpcurl` takes `host:port` with no scheme, and the archived `grpc-copy-curl` spec was written against `localhost:9090`. Since the generated command is the project's own `curl -X GRPC "..."` convention rather than actual `grpcurl` syntax, a scheme is coherent — and any user who wants the bare form can now set it, which they could not before. This is a behavior change for users who never configured anything, and it updates two scenarios in the `grpc-copy-curl` spec.

### D5: Trailing-slash normalization

Substitution trims trailing `/` from the host value, so `http://order-web/` and `http://order-web` both yield `http://order-web/api/users` rather than a doubled slash. `GrpcCurlUtils.buildUrl` already trims trailing slashes, so the gRPC path needs nothing.

Not handled: a `DocView.path` that does not start with `/`. Paths come from Spring mapping annotations after URL rewrite and are `/`-prefixed in practice; adding a second normalization rule would risk changing existing rewritten-path output for no observed benefit.

### D6: Settings UI — hand-edited GUI Designer XML

There is no way to run the GUI Designer here, so `Settings.form` is edited as XML, following the existing `prefixSymbol1TextField` pattern (`Settings.form:429`): a `JBTextField` component with a `binding` attribute plus a sibling `JLabel` whose `text` and `toolTipText` are `resource-bundle` references into `messages/DocViewBundle`.

Concretely: add a `curlHostPanel` grid at `row="11"` and bump `rootPanel`'s `row-count` from 11 to 12. Three labeled `JBTextField`s inside it, mirroring how `otherPanel` lays out its label/field pairs. Then in `SettingsForm.java`: declare the three fields, add the titled border in `initTitleBorder()`, and extend all three of `isModified()` / `apply()` / `reset()` — missing any one of them produces a field that silently fails to persist or fails to light up the Apply button.

Labels/tooltips go in `DocViewBundle.properties` as `\uXXXX` escapes, matching the file's existing convention.

### D7: No persistence migration

`Settings` is a `PersistentStateComponent<Settings>` with `@Data`; new fields with inline defaults are read as their defaults when absent from an existing `.idea/` XML. Nothing to migrate, and rollback is safe — an older plugin build simply ignores the extra XML elements.

## Risks / Trade-offs

- **A non-URL Copy cURL host breaks paste-into-HTTP-client** → The comment at `CopyCurlAction:59` records that the hardcoded localhost exists precisely because `{{host}}` was not recognized when pasting into IntelliJ's HTTP client. Setting `{{order-web}}` reintroduces that. This is the explicitly requested behavior, so the setting is documented as verbatim; the tooltip should mention that a non-URL value will not be resolvable by the HTTP client.
- **gRPC default change alters output for users who configured nothing** → Called out in the changelog; the value is now configurable, so anyone wanting the old form can set `localhost:9090`.
- **`DocViewData.curlMarkdown` depends on `docView.getPsiClass()` being non-null** → Same assumption `DocViewData:114` already makes, so no new exposure. It does mean `curlMarkdown` cannot be called on a `DocView` without a backing class; no current caller does.
- **Three settings is more surface than one** → Deliberate, per the separate-settings decision. Mitigated by grouping them in one titled "Curl Host" section so the relationship is visible.
- **Hand-editing `Settings.form` XML is error-prone** → A wrong `row-count` or a duplicate `id` yields a broken settings page. Verification is opening Settings → Doc View in the sandbox and confirming the section renders and that Apply/Reset round-trip the values.
- **No automated test suite** → `src/test/java` files are `main()`-based and `compileTestJava` is already broken at HEAD (`RecordSupportTest` imports an absent swagger package; `CurlUtilsTest` calls package-private `CurlUtils.escapeSingleQuoted` cross-package). Verification is manual in the sandbox IDE. Since D1 leaves `CurlUtils.build` untouched, the existing `CurlUtilsTest` expectations of `{{host}}` remain correct.

## Migration Plan

No data migration. Deploy is a normal plugin build; rollback is reverting the commit, after which the extra `.idea/` XML elements are ignored by the older code.

## Open Questions

- Should the tooltip warn that a non-URL value (e.g. `{{order-web}}`) will not resolve when pasted into IntelliJ's HTTP client? Assumed yes — it costs one bundle string and pre-empts a confusing failure.
- Should a future change collapse these into named environments (dev/test/prod) with a picker, as YApi-style tooling does? Out of scope; three flat fields match the request.
