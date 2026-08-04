## MODIFIED Requirements

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
