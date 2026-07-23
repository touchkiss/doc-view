## ADDED Requirements

### Requirement: URL rewrite rules persisted in settings
The system SHALL store an ordered list of URL rewrite rules in the project-scoped `Settings`, where each rule has an `enabled` flag, a `regex` pattern string, and a `replacement` string, persisted across IDE restarts.

#### Scenario: Rules survive restart
- **WHEN** a user adds a rewrite rule in the Doc View settings and applies it, then restarts the IDE
- **THEN** the rule is still present with its `enabled`, `regex`, and `replacement` values intact

#### Scenario: Default configuration has no rules
- **WHEN** the plugin is used with no rewrite rules configured
- **THEN** `Settings.urlRewriteRules` is an empty list and REST paths are emitted unchanged

---

### Requirement: Regex-based rewrite applied to REST controller path
The system SHALL apply the enabled rewrite rules, in list order, to the REST path read from a Spring or Feign controller before that path is stored on `DocView`, using Java regular-expression replacement with capture-group support.

#### Scenario: Single rule restructures the path
- **WHEN** a rule with regex `^/order/i/admin/seller/(.*)$` and replacement `/seller/c/order/$1` is enabled, and a controller path is `/order/i/admin/seller/list`
- **THEN** the resulting `DocView.path` is `/seller/c/order/list`

#### Scenario: Path with no matching rule is unchanged
- **WHEN** no enabled rule's regex matches a controller path `/user/profile`
- **THEN** the resulting `DocView.path` is `/user/profile`

#### Scenario: Rules apply sequentially
- **WHEN** two enabled rules are configured — rule 1 rewrites `/a/(.*)` → `/b/$1`, rule 2 rewrites `/b/(.*)` → `/c/$1` — and the input path is `/a/x`
- **THEN** rule 1 produces `/b/x` and rule 2 is applied to that result, yielding a final `DocView.path` of `/c/x`

#### Scenario: Disabled rule is skipped
- **WHEN** a rule whose regex would match the path has `enabled = false`
- **THEN** that rule is not applied and does not alter the path

---

### Requirement: Rewrite failures never break documentation
The system SHALL skip any rewrite rule whose regex is invalid (throws `PatternSyntaxException`) and continue generating documentation with the remaining rules, logging a warning for the skipped rule.

#### Scenario: Invalid regex is skipped
- **WHEN** a rule has a malformed regex (e.g. `[unclosed`) and another enabled valid rule follows it
- **THEN** the malformed rule is skipped, a warning is logged, and the valid rule is still applied to the path

---

### Requirement: Rewritten path flows to all outputs
The system SHALL use the rewritten `DocView.path` for every downstream output — Markdown preview, `.http` file export, and uploads to YApi, ShowDoc, and YuQue — without additional per-output rewrite logic.

#### Scenario: Upload uses rewritten path
- **WHEN** a rule rewrites `/order/i/admin/seller/list` to `/seller/c/order/list` and the endpoint is uploaded to YApi
- **THEN** the path sent to YApi is `/seller/c/order/list`

#### Scenario: Preview shows rewritten path
- **WHEN** a rewrite rule matches an endpoint's path
- **THEN** the Markdown preview for that endpoint displays the rewritten path

---

### Requirement: Rules editable in the settings page
The system SHALL provide a "URL Rewrite" table in the Doc View settings page allowing users to add, remove, edit, enable/disable, and order multiple rewrite rules, with changes taking effect only on Apply and revertible on Reset.

#### Scenario: Add a rule
- **WHEN** the user clicks the add action in the URL Rewrite table and enters a regex and replacement, then clicks Apply
- **THEN** a new rule with those values is persisted to `Settings.urlRewriteRules`

#### Scenario: Remove a rule
- **WHEN** the user selects a rule row, clicks the remove action, and clicks Apply
- **THEN** that rule is removed from `Settings.urlRewriteRules`

#### Scenario: Reset discards unapplied edits
- **WHEN** the user edits the URL Rewrite table but clicks Reset instead of Apply
- **THEN** the table reverts to the currently persisted `Settings.urlRewriteRules` and no changes are saved

#### Scenario: Modified state enables Apply
- **WHEN** the user changes any rule field or adds/removes a row
- **THEN** the settings page reports a modified state so the Apply button becomes active
