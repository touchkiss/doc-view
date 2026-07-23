## ADDED Requirements

### Requirement: Curl command generated from REST DocView
The system SHALL build a `curl` command string for every Spring or Feign REST endpoint using the HTTP method, path, request headers, URL/query parameters, and request body already present on `DocView`.

#### Scenario: GET with query parameters
- **WHEN** a Spring endpoint has method `GET`, path `/user/{id}`, a path variable `id` with example `42`, and a query param `active` with example `true`
- **THEN** the generated curl targets `{{host}}/user/42?active=true` and uses `-X GET`

#### Scenario: POST with JSON body
- **WHEN** a Spring endpoint has method `POST`, path `/user`, `ContentTypeEnum.JSON`, and a non-blank `reqBodyExample` of `{"name":"Tom"}`
- **THEN** the generated curl includes `-H 'Content-Type: application/json'` and a `-d` flag containing the JSON body

#### Scenario: Headers included
- **WHEN** a REST endpoint declares request headers on `DocView.headerList`
- **THEN** the generated curl includes a `-H 'HeaderName: value'` flag for each header (using each header's example when available)

---

### Requirement: Curl rendered as Markdown in the interface description
The system SHALL expose the curl command to Velocity templates as `${DocView.curlExample}`, formatted as a fenced Markdown code block with language tag `bash`, suitable for placement in the **接口描述** section.

#### Scenario: Markdown code block format
- **WHEN** a curl command is generated for a REST endpoint
- **THEN** `DocViewData.getCurlExample()` returns text beginning with ` ```bash `, containing the curl command, and ending with ` ``` `

#### Scenario: Default Spring template shows curl under description
- **WHEN** the bundled default Spring template (`template.spring.init`) is used to render a REST endpoint document
- **THEN** the **接口描述** section displays the existing description text followed by the curl Markdown code block

#### Scenario: Empty curl for non-REST frameworks
- **WHEN** documentation is generated for a Dubbo or POJO endpoint
- **THEN** `DocViewData.getCurlExample()` returns an empty string and no curl block appears in the output

---

### Requirement: Curl uses host placeholder consistent with HTTP export
The system SHALL construct the curl URL using the `{{host}}` placeholder followed by the endpoint path (after any URL rewrite rules), matching the convention used by `.http` file export.

#### Scenario: Host placeholder in URL
- **WHEN** an endpoint path is `/api/order/list`
- **THEN** the curl URL portion is `{{host}}/api/order/list` (or includes query string when applicable)

#### Scenario: Rewritten path used
- **WHEN** URL rewrite rules transform a controller path before it is stored on `DocView.path`
- **THEN** the curl command uses the rewritten `DocView.path` value

---

### Requirement: Curl flows through all Markdown outputs
The system SHALL make the curl block available anywhere `DocViewData` is rendered through the Spring Velocity template, including preview, Markdown export, and upload adapters, without per-target curl logic.

#### Scenario: Preview shows curl
- **WHEN** a user previews a Spring REST endpoint with the default template
- **THEN** the preview Markdown contains the curl code block in the description section

#### Scenario: Upload includes curl in description area
- **WHEN** a Spring REST endpoint document is uploaded to YApi, ShowDoc, or YuQue using the default template
- **THEN** the uploaded document body includes the curl Markdown block alongside the interface description
