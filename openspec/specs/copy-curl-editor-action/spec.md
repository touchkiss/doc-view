# Capability: Copy cURL Editor Action

## Purpose

Provide a "Copy cURL" action in the editor right-click context menu for Spring/Feign REST methods, building a curl command from the endpoint's documentation data and placing it on the system clipboard with the project's configured host substituted.
## Requirements
### Requirement: Copy cURL action in editor context menu
The system SHALL provide a "Copy cURL" action in the editor right-click context menu that copies a curl command for the REST endpoint at the cursor position to the system clipboard.

#### Scenario: Action visible on REST method
- **WHEN** the user right-clicks on a method annotated with `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, or `@RequestMapping` inside a `@RestController` or `@Controller` class
- **THEN** the context menu SHALL contain a "Copy cURL" action

#### Scenario: Action hidden on non-REST method
- **WHEN** the user right-clicks on a method without a REST mapping annotation
- **THEN** the "Copy cURL" action SHALL NOT be visible in the context menu

#### Scenario: Action hidden outside controller class
- **WHEN** the user right-clicks outside a `@RestController` or `@Controller` class
- **THEN** the "Copy cURL" action SHALL NOT be visible in the context menu

---

### Requirement: Curl command copied to clipboard
When the "Copy cURL" action is invoked, the system SHALL build a curl command from the endpoint's documentation data and copy it to the system clipboard.

#### Scenario: Successful copy
- **WHEN** the user invokes "Copy cURL" on a REST method with generated documentation
- **THEN** the system SHALL copy a curl command string to the system clipboard using `CurlUtils.build()`
- **AND** display a balloon notification confirming "Copied cURL for <methodName>"

#### Scenario: No documentation generated
- **WHEN** the user invokes "Copy cURL" on a REST method where `DocViewService.buildDoc()` returns an empty list
- **THEN** the system SHALL display a balloon notification stating "No documentation generated for this method"

---

### Requirement: Curl command uses `{{host}}` placeholder
The curl command SHALL be built with the `{{host}}` placeholder as its base URL, and the placeholder SHALL then be substituted with the project's configured **Copy cURL host** before the command is placed on the clipboard. The host previously substituted as a hardcoded `http://localhost:8080` is now the default value of that setting, so unconfigured projects behave as before.

#### Scenario: Placeholder in URL
- **WHEN** a curl command is generated for endpoint path `/api/users`
- **THEN** `CurlUtils.build()` produces `{{host}}/api/users` as the URL

#### Scenario: Configured host substituted on copy
- **WHEN** the Copy cURL host is configured as `http://order-web` and the user invokes "Copy cURL" on endpoint path `/api/users`
- **THEN** the clipboard contains a command targeting `http://order-web/api/users`
- **AND** the clipboard content SHALL NOT contain the literal `{{host}}`

#### Scenario: Default host when unconfigured
- **WHEN** no Copy cURL host is configured and the user invokes "Copy cURL" on endpoint path `/api/users`
- **THEN** the clipboard contains a command targeting `http://localhost:8080/api/users`

#### Scenario: Verbatim non-URL host substituted
- **WHEN** the Copy cURL host is configured as `{{order-web}}` and the user invokes "Copy cURL" on endpoint path `/api/users`
- **THEN** the clipboard contains a command targeting `{{order-web}}/api/users`

#### Scenario: Doc host does not affect copying
- **WHEN** the Doc cURL host is configured as `http://doc-only` and the Copy cURL host is left at its default
- **THEN** the clipboard contains a command targeting `http://localhost:8080/api/users`

