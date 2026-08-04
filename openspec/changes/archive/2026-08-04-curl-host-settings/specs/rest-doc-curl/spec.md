## MODIFIED Requirements

### Requirement: Curl uses host placeholder consistent with HTTP export
The system SHALL construct the curl URL using the `{{host}}` placeholder followed by the endpoint path (after any URL rewrite rules), matching the convention used by `.http` file export. Before the curl example is embedded in a generated document, the placeholder SHALL be substituted with the project's configured **Doc cURL host**. That setting defaults to `{{host}}` itself, so an unconfigured project produces exactly the previous output and uploaded documents stay environment-agnostic.

#### Scenario: Host placeholder in URL
- **WHEN** an endpoint path is `/api/order/list`
- **THEN** the curl URL portion is `{{host}}/api/order/list` (or includes query string when applicable)

#### Scenario: Rewritten path used
- **WHEN** URL rewrite rules transform a controller path before it is stored on `DocView.path`
- **THEN** the curl command uses the rewritten `DocView.path` value

#### Scenario: Unconfigured doc host leaves the placeholder in place
- **WHEN** no Doc cURL host is configured and a document is generated for endpoint path `/api/order/list`
- **THEN** the curl example in the preview, Markdown export, and uploaded document contains `{{host}}/api/order/list`

#### Scenario: Configured doc host substituted in documents
- **WHEN** the Doc cURL host is configured as `http://order-web` and a document is generated for endpoint path `/api/order/list`
- **THEN** the curl example in the preview, Markdown export, and uploaded document targets `http://order-web/api/order/list`

#### Scenario: Copy host does not affect documents
- **WHEN** the Copy cURL host is configured as `http://copy-only` and the Doc cURL host is left at its default
- **THEN** the curl example in generated documents contains `{{host}}/api/order/list`
