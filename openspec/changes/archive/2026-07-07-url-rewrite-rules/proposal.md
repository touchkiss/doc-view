## Why

The URL a developer writes in a Spring controller is often an *internal* path (e.g. `/order/i/admin/seller/list`). Before reaching consumers, that path is rewritten by a security gateway into a different *external* path (e.g. `/seller/c/order/list`). Today Doc View documents and uploads the raw internal path, so the generated docs, `.http` files, and YApi/ShowDoc/YuQue uploads all show URLs that consumers cannot actually call. Users have no way to make the plugin emit the externally-facing URL.

## What Changes

- **Add a regex-based URL rewrite step** applied to the REST path immediately after it is read from the controller, before it is stored on `DocView`. Rewriting a single, central chokepoint means the rewritten path flows automatically into preview, `.http` export, and all upload targets.
- **Add multiple, ordered, user-editable rewrite rules** to the plugin settings. Each rule has a Java regex pattern, a replacement string (supporting `$1` capture-group references), and an enabled flag. Rules apply in order, each operating on the output of the previous one.
- **Make the rules configurable in the Doc View settings page** via a new "URL Rewrite" table (add / remove / edit rows) in the existing `SettingsForm`, persisted in the project-scoped `Settings`.
- **Fail safe**: an invalid regex in one rule is skipped (logged), never aborting doc generation.

## Capabilities

### New Capabilities
- `url-rewrite`: Regex-based rewriting of REST controller URLs into their externally-facing gateway form, driven by an ordered list of user-configured rules in the plugin settings, applied to every generated/exported/uploaded document.

### Modified Capabilities
<!-- No existing spec-level requirement documents cover URL handling — this is greenfield spec work -->

## Impact

- **`config/Settings.java`**: Add `List<UrlRewriteRule> urlRewriteRules` field (empty by default) with lombok getter/setter; serialized via the existing `XmlSerializerUtil.copyBean` into `DocViewSettings.xml`.
- **`config/UrlRewriteRule.java`** (new): Small persistable bean — `enabled`, `regex`, `replacement` — with a public no-arg constructor for IntelliJ XML serialization.
- **`utils/UrlRewriteUtils.java`** (new): `rewrite(Project, String path)` looks up the rules and applies each enabled rule's `path.replaceAll(regex, replacement)` in order; a pure static `rewrite(List<UrlRewriteRule>, String)` overload holds the testable core. Invalid regex → skip + log warn.
- **`service/impl/SpringDocViewServiceImpl.java`**: Wrap the single `docView.setPath(...)` call (line 62) so the path is rewritten before being set. This is the only REST-path chokepoint; Feign is routed through this impl too. Dubbo/POJO have no REST URL and are out of scope.
- **`ui/SettingsForm.java` + `ui/SettingsForm.form`**: Add a "URL Rewrite" panel containing a `ToolbarDecorator`-managed table (columns: Enabled / Regex / Replacement) with add and remove actions; wire into `isModified()` / `apply()` / `reset()`.
- **No template changes**: Velocity templates already render `DocView.path`; they receive the rewritten value transparently.
- **No upload-adapter changes**: YApi/ShowDoc/YuQue adapters read `DocView.path`, which is already rewritten.
