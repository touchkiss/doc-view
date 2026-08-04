## Why

The base URL in generated curl commands is hardcoded in two places and configurable in neither:

- `CopyCurlAction:60` does `curl.replace("{{host}}", "http://localhost:8080")` — a literal, unconfigurable localhost.
- `GrpcCurlUtils.DEFAULT_GRPC_HOST` is the constant `"localhost:9090"`, and `ProtoGrpcCopyCurlAction` calls the 3-arg `build(service, method, json)` overload, so the existing `build(host, service, method, json)` overload is dead code. The archived `grpc-copy-curl` spec already requires "The system SHALL use a gRPC host from settings" — that requirement was specified but never implemented.

Developers work against per-project gateways, not localhost. Every copied curl has to be hand-edited before it can be run. The host needs to be a project-level setting, and the value must be accepted verbatim so a project can use its own gateway token form (e.g. `{{order-web}}`) rather than only a literal URL.

## What Changes

Per the decision to keep the two paths separate, **copying** and **document generation** get independent host settings. `CurlUtils.build()` feeds both paths, so a single setting would have forced one behavior on both.

- Add three project-level settings to `Settings` (persisted per project in `.idea/`):
  - **Copy cURL host** — used when the right-click **Copy cURL** action writes to the clipboard. Default `http://localhost:8080`, preserving today's hardcoded behavior.
  - **Doc cURL host** — used for the curl example embedded in generated documents (preview popup, Markdown export, uploads to YApi/ShowDoc/YuQue). Default `{{host}}`, preserving today's behavior exactly and keeping uploaded docs environment-agnostic.
  - **gRPC cURL host** — used by the right-click **Copy gRPC cURL** action. Default `http://localhost:9090`.
- Add a **Curl Host** section to the Doc View settings page (`Settings.form` / `SettingsForm`) with the three text fields, wired through the existing `isModified` / `apply` / `reset` contract.
- Replace `CopyCurlAction`'s hardcoded substitution with the configured Copy cURL host.
- Change `CurlUtils` so the `{{host}}` token it emits is substituted from the Doc cURL host on the document path, instead of always being left literal.
- Wire `ProtoGrpcCopyCurlAction` to the configured gRPC host via the already-present `GrpcCurlUtils.build(host, ...)` overload.
- Values are used **verbatim** with no URL validation, so `{{order-web}}` is a legal setting. A blank setting falls back to that setting's default.
- Trailing slashes are trimmed when joining host and path, so `http://order-web/` and `http://order-web` behave identically.
- **Not changed:** the `{{host}}` in `.http` file export (`CustomFileUtils:113`). That is IntelliJ HTTP client environment-variable syntax and must stay literal for the exported file to work.
- **Not added:** a gRPC doc-generation host. `DocViewData.curlExample` is populated only for `FrameworkEnum.SPRING`, and proto message documents carry no curl example, so there is no gRPC document path to configure.

## Capabilities

### New Capabilities
- `curl-host-settings`: Project-level Copy cURL / Doc cURL / gRPC cURL host settings — persistence, the settings-page UI section, verbatim value handling, blank-value fallback to defaults, and trailing-slash normalization when joining host and path.

### Modified Capabilities
- `copy-curl-editor-action`: The requirement that the copied curl contains the literal `{{host}}` placeholder is replaced by substitution of the configured Copy cURL host. (The current code already violates the existing requirement by substituting `http://localhost:8080`; this change makes spec and code agree.)
- `rest-doc-curl`: The requirement that the curl URL always uses the literal `{{host}}` placeholder becomes a configurable Doc cURL host that defaults to `{{host}}`, so unconfigured projects are unaffected.
- `grpc-copy-curl`: The already-specified but unimplemented "Host configuration" requirement becomes real, and its default changes from `localhost:9090` to `http://localhost:9090`, which also updates the command-format scenario under "Curl command generation".

## Impact

**Modified**
- `config/Settings.java` — three new `String` fields with defaults (`@Data`, so accessors are generated).
- `ui/Settings.form` — new `curlHostPanel` grid; `rootPanel` `row-count` goes from 11 to 12.
- `ui/SettingsForm.java` — three `JBTextField` bindings, a titled border, plus `isModified` / `apply` / `reset` handling.
- `messages/DocViewBundle.properties` — labels and tooltips for the new section (Unicode-escaped, matching file convention).
- `action/CopyCurlAction.java` — substitute the configured Copy cURL host.
- `utils/CurlUtils.java` — accept a host for the document path; `build(DocView)` currently takes no `Project`, so the host has to be threaded in from `DocViewData`, which does have one.
- `action/ProtoGrpcCopyCurlAction.java` — pass the configured gRPC host.
- `utils/GrpcCurlUtils.java` — default constant changes to `http://localhost:9090`.

**Risks**
- `CurlUtils.build(DocView)` has no `Project` handle, while `DocViewData` reaches `Settings` via `docView.getPsiClass().getProject()`. Threading the host through without breaking either caller is the main design question, and it interacts with the fact that `DocView.psiClass` is non-null-assumed at `DocViewData:114`.
- Setting a non-URL value such as `{{order-web}}` for the Copy cURL host reintroduces the exact problem the comment at `CopyCurlAction:59` describes — pasting into IntelliJ's HTTP client will not resolve it. This is the user's explicit choice; the setting is documented as verbatim rather than validated.
- Changing the gRPC default to include `http://` alters output for existing users who never configured anything.
