## 1. Rewrite Rule Model & Settings

- [ ] 1.1 Create `config/UrlRewriteRule.java`: a plain persistable bean with public fields `boolean enabled = true`, `String regex = ""`, `String replacement = ""`, plus a public no-arg constructor (required by IntelliJ `XmlSerializerUtil`). Add a convenience all-args constructor for programmatic use.
- [ ] 1.2 In `config/Settings.java`, add `private List<UrlRewriteRule> urlRewriteRules = new ArrayList<>();` (lombok `@Data` supplies getter/setter). Add the `java.util.List` / `java.util.ArrayList` imports. The existing `XmlSerializerUtil.copyBean` in `loadState` covers persistence.

## 2. Rewrite Utility

- [ ] 2.1 Create `utils/UrlRewriteUtils.java` with a pure static core `String rewrite(List<UrlRewriteRule> rules, String path)`: return `path` unchanged if rules are null/empty or path is blank; otherwise fold `path = path.replaceAll(rule.regex, rule.replacement)` over each rule where `enabled` is true and `regex` is non-blank.
- [ ] 2.2 Wrap each rule's `replaceAll` in a try/catch for `PatternSyntaxException` (and defensive `RuntimeException`): on failure, skip that rule, keep the current path, and log a warning via the `@Slf4j` logger (match the logging style used elsewhere).
- [ ] 2.3 Add an overload `String rewrite(Project project, String path)` that reads `Settings.getInstance(project).getUrlRewriteRules()` and delegates to the pure core.

## 3. Apply Rewrite at the REST Path Chokepoint

- [ ] 3.1 In `service/impl/SpringDocViewServiceImpl.buildClassMethodDoc()`, change line 62 to rewrite the path before setting it: `docView.setPath(UrlRewriteUtils.rewrite(psiClass.getProject(), SpringPsiUtils.path(psiClass, psiMethod)));`. Add the `UrlRewriteUtils` import. (This covers Spring and Feign; Dubbo/POJO have no REST path and are intentionally untouched.)

## 4. Settings UI — URL Rewrite Table

- [ ] 4.1 In `ui/SettingsForm.form`, add a titled `JPanel` (field name e.g. `urlRewritePanel`) placed with the other setting panels; leave it empty for programmatic population.
- [ ] 4.2 In `ui/SettingsForm.java`, declare the bound `urlRewritePanel` field and, in the constructor, build a `ListTableModel<UrlRewriteRule>` with three columns — Enabled (boolean/checkbox), Regex (editable string), Replacement (editable string) — backed by a `TableView<UrlRewriteRule>`. Keep a working copy list separate from `Settings`.
- [ ] 4.3 Wrap the table in `ToolbarDecorator.createDecorator(table).setAddAction(...).setRemoveAction(...).createPanel()` (add inserts a new blank enabled rule; remove deletes the selected row) and add the resulting component to `urlRewritePanel`. Add a bordered title "URL Rewrite" consistent with `initTitleBorder()`.
- [ ] 4.4 Extend `isModified()` to also return `true` when the table's working copy differs from `Settings.getUrlRewriteRules()` (compare size and each rule's `enabled`/`regex`/`replacement`).
- [ ] 4.5 Extend `apply()` to write a deep copy of the table's working-copy rules into `Settings.setUrlRewriteRules(...)`.
- [ ] 4.6 Extend `reset()` to reload the table's working copy from a deep copy of `Settings.getUrlRewriteRules()`.

## 5. Verification

- [ ] 5.1 Add a minimal self-check for the pure core: a `main()` (matching the repo's manual-test convention under `src/test/java/`) or inline `assert`s that verify `UrlRewriteUtils.rewrite(rules, "/order/i/admin/seller/list")` returns `/seller/c/order/list` for rule `^/order/i/admin/seller/(.*)$` → `/seller/c/order/$1`, that a non-matching path is unchanged, that two rules apply sequentially, that a disabled rule is skipped, and that a malformed regex is skipped without throwing.
- [ ] 5.2 `./gradlew runIde`: open Doc View settings, add a rule (`^/order/i/admin/seller/(.*)$` → `/seller/c/order/$1`), Apply, and confirm it persists after reopening settings and after an IDE restart.
- [ ] 5.3 Right-click a Spring controller whose method resolves to `/order/i/admin/seller/list` and confirm the preview shows `/seller/c/order/list`.
- [ ] 5.4 Upload that endpoint to YApi (or another configured target) and confirm the uploaded path is the rewritten `/seller/c/order/list`.
- [ ] 5.5 Confirm an endpoint whose path matches no rule is emitted unchanged, and that disabling a rule (unchecking Enabled + Apply) restores the original path.
- [ ] 5.6 Enter a deliberately malformed regex in a rule, generate a doc, and confirm generation still succeeds (rule skipped, warning logged) rather than erroring.
