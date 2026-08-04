## 1. Settings persistence

- [x] 1.1 In `config/Settings.java`, add three default constants: `DEFAULT_CURL_COPY_HOST = "http://localhost:8080"`, `DEFAULT_CURL_DOC_HOST = "{{host}}"`, `DEFAULT_GRPC_CURL_HOST = "http://localhost:9090"`
- [x] 1.2 In `config/Settings.java`, add three `String` fields `curlCopyHost` / `curlDocHost` / `grpcCurlHost` initialized to those constants (`@Data` generates the accessors)
- [ ] 1.3 Verify a project whose stored settings predate this change reads the defaults — no migration code, just confirm the `PersistentStateComponent` round-trip in the sandbox later (spec: *Existing project configuration without the new settings*)

## 2. Host substitution helper

- [x] 2.1 Create `utils/CurlHostUtils.java` with `applyCopyHost(Project, String curl)` and `applyDocHost(Project, String curl)`, each reading its setting and replacing the `{{host}}` token
- [x] 2.2 In `CurlHostUtils`, add blank-value fallback to the matching default constant (spec: *Blank values fall back to defaults*)
- [x] 2.3 In `CurlHostUtils`, trim trailing `/` from the host before substitution (spec: *Trailing slash normalization*)
- [x] 2.4 In `CurlHostUtils`, short-circuit `applyDocHost` when the configured value equals `{{host}}`, so the default doc host is a true no-op
- [x] 2.5 Add a `grpcHost(Project)` accessor to `CurlHostUtils` (or resolve inline in the action) applying the same blank-fallback rule; do not trim here since `GrpcCurlUtils.buildUrl` already trims

## 3. Wire the three consumers

- [x] 3.1 In `action/CopyCurlAction.java`, replace `curl.replace("{{host}}", "http://localhost:8080")` with `CurlHostUtils.applyCopyHost(project, curl)`, keeping the explanatory comment accurate
- [x] 3.2 In `dto/DocViewData.java`, apply `CurlHostUtils.applyDocHost(...)` inside `curlMarkdown(DocView)`, resolving `Project` via `docView.getPsiClass().getProject()` (same pattern as `DocViewData:114`); keep the method `static` so `YApiServiceImpl:139` is untouched
- [x] 3.3 In `utils/GrpcCurlUtils.java`, change `DEFAULT_GRPC_HOST` to `http://localhost:9090` (spec: *Default host*)
- [x] 3.4 In `action/ProtoGrpcCopyCurlAction.java`, switch from `GrpcCurlUtils.build(service, method, json)` to the existing `build(host, service, method, json)` overload, passing the configured gRPC host
- [x] 3.5 Confirm `utils/CurlUtils.java` is unchanged and still emits `{{host}}`, and that `utils/CustomFileUtils.java:113` (`.http` export) still emits the literal placeholder (spec: *HTTP client export keeps the literal placeholder*)

## 4. Settings UI

- [x] 4.1 In `ui/Settings.form`, bump `rootPanel`'s `row-count` from `11` to `12`
- [x] 4.2 In `ui/Settings.form`, add a `curlHostPanel` grid at `row="11"` containing three `com.intellij.ui.components.JBTextField` components bound to `curlCopyHostTextField` / `curlDocHostTextField` / `grpcCurlHostTextField`, each with a sibling `JLabel` using `resource-bundle` `text` and `toolTipText` keys — follow the `prefixSymbol1TextField` pattern at `Settings.form:429`
- [x] 4.3 Add the bundle keys to `messages/DocViewBundle.properties` as `\uXXXX` escapes: labels plus tooltips, with the Copy cURL tooltip noting that a non-URL value will not resolve when pasted into IntelliJ's HTTP client
- [x] 4.4 In `ui/SettingsForm.java`, declare the three `JBTextField` fields and a `curlHostTitleBorder`, and set the border in `initTitleBorder()`
- [x] 4.5 In `ui/SettingsForm.java`, extend `isModified()` with the three trimmed-text comparisons (spec: *Apply button reacts to edits*)
- [x] 4.6 In `ui/SettingsForm.java`, extend `apply()` with the three setters using trimmed text (spec: *Apply persists values*)
- [x] 4.7 In `ui/SettingsForm.java`, extend `reset()` to load the three persisted values (spec: *Reset restores persisted values*)

## 5. Verification

- [x] 5.1 `./gradlew compileJava` passes (note: `./gradlew build` is already failing at `compileTestJava` at HEAD for unrelated reasons — `RecordSupportTest` swagger import, `CurlUtilsTest` package-private access)
- [ ] 5.2 `./gradlew runIde`; open Settings → Doc View and confirm the Curl Host section renders with three fields and correct labels/tooltips
- [ ] 5.3 In the sandbox, verify Apply/Reset round-trip: edit each field, confirm Apply enables, apply, reopen Settings, confirm values persisted; then edit and Reset, confirm values revert
- [ ] 5.4 In the sandbox, verify all pre-existing settings on that page still modify/apply/reset correctly (spec: *Existing settings unaffected*)
- [ ] 5.5 In the sandbox, right-click a REST method → Copy cURL with no host configured; confirm `http://localhost:8080/...`. Then set `http://order-web` and confirm substitution; then `{{order-web}}` and confirm verbatim use; then `http://order-web/` and confirm no doubled slash; then clear the field and confirm fallback to the default
- [ ] 5.6 In the sandbox, verify the preview popup / Markdown export curl example still shows `{{host}}` with the Doc host unconfigured, then set a Doc host and confirm the document curl changes while Copy cURL is unaffected — and vice versa (spec: *Copy host does not affect documents*, *Doc host does not affect copying*)
- [ ] 5.7 In the sandbox, generate an `.http` file with a Copy host configured and confirm it still contains the literal `{{host}}`
- [ ] 5.8 In the sandbox, open a `.proto`, put the caret on an `rpc` line → Copy gRPC cURL; confirm `http://localhost:9090/...` by default, then confirm a configured host and a bare `localhost:9090` are both used verbatim
- [ ] 5.9 Restart the sandbox IDE and confirm the configured values are still in effect (spec: *Values survive IDE restart*)
- [x] 5.10 Add a `CHANGELOG.md` entry under `[Unreleased]` covering the three new settings and calling out the gRPC default change from `localhost:9090` to `http://localhost:9090`
