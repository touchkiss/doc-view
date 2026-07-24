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
The copied curl command SHALL use the `{{host}}` placeholder for the base URL, matching the convention used by `.http` file export and the existing `rest-doc-curl` spec.

#### Scenario: Placeholder in URL
- **WHEN** a curl command is generated for endpoint path `/api/users`
- **THEN** the copied curl contains `{{host}}/api/users` as the URL
