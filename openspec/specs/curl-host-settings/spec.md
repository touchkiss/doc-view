# curl-host-settings Specification

## Purpose
TBD - created by archiving change curl-host-settings. Update Purpose after archive.
## Requirements
### Requirement: Project-level curl host settings

The system SHALL provide three project-level host settings, persisted with the rest of the project's Doc View settings: a **Copy cURL host**, a **Doc cURL host**, and a **gRPC cURL host**.

#### Scenario: Settings are project-scoped

- **WHEN** a host value is configured in project A
- **THEN** project B SHALL be unaffected and continue to use its own values

#### Scenario: Values survive IDE restart

- **WHEN** a host value is configured and the IDE is restarted
- **THEN** the configured value SHALL still be in effect

#### Scenario: Existing project configuration without the new settings

- **WHEN** a project's stored Doc View settings predate this change and contain no host values
- **THEN** each setting SHALL take its default value and no error SHALL occur

### Requirement: Default host values

The system SHALL default the Copy cURL host to `http://localhost:8080`, the Doc cURL host to `{{host}}`, and the gRPC cURL host to `http://localhost:9090`.

#### Scenario: Unconfigured copy host

- **WHEN** no Copy cURL host is configured and the user invokes **Copy cURL** on endpoint path `/api/users`
- **THEN** the copied command SHALL target `http://localhost:8080/api/users`

#### Scenario: Unconfigured doc host leaves documents unchanged

- **WHEN** no Doc cURL host is configured and a document is generated for endpoint path `/api/users`
- **THEN** the curl example in that document SHALL contain `{{host}}/api/users`, identical to the output before this change

#### Scenario: Unconfigured gRPC host

- **WHEN** no gRPC cURL host is configured and the user invokes **Copy gRPC cURL**
- **THEN** the copied command SHALL target `http://localhost:9090/ServiceName/MethodName`

### Requirement: Blank values fall back to defaults

The system SHALL treat a blank or whitespace-only host setting as unset and use that setting's default value.

#### Scenario: Field cleared in settings

- **WHEN** the user clears the Copy cURL host field and applies the settings
- **THEN** **Copy cURL** SHALL produce a command targeting `http://localhost:8080/api/users` for path `/api/users`
- **AND** SHALL NOT produce a hostless URL such as `/api/users`

#### Scenario: Whitespace-only value

- **WHEN** a host setting contains only spaces
- **THEN** the system SHALL use that setting's default value

### Requirement: Host values are used verbatim

The system SHALL use the configured host value exactly as entered, without URL validation, scheme normalization, or reachability checking, so that non-URL gateway tokens are usable.

#### Scenario: Template-style token as host

- **WHEN** the Copy cURL host is set to `{{order-web}}` and the user invokes **Copy cURL** on path `/api/users`
- **THEN** the copied command SHALL target `{{order-web}}/api/users`

#### Scenario: No validation error on a non-URL value

- **WHEN** the user enters a value that is not a valid URL and applies the settings
- **THEN** the settings SHALL be accepted without a validation error

#### Scenario: Host without a scheme

- **WHEN** the gRPC cURL host is set to `localhost:9090`
- **THEN** the copied gRPC command SHALL target `localhost:9090/ServiceName/MethodName` with no scheme added

### Requirement: Trailing slash normalization

The system SHALL trim trailing `/` characters from a configured host before joining it with the endpoint path, so that a host written with or without a trailing slash produces the same URL.

#### Scenario: Host with trailing slash

- **WHEN** the Copy cURL host is `http://order-web/` and the endpoint path is `/api/users`
- **THEN** the copied command SHALL target `http://order-web/api/users` and SHALL NOT contain `//api/users`

#### Scenario: Host without trailing slash

- **WHEN** the Copy cURL host is `http://order-web` and the endpoint path is `/api/users`
- **THEN** the copied command SHALL target `http://order-web/api/users`

### Requirement: Curl host settings section in the settings page

The system SHALL present the three host settings as text fields in a labeled section of the Doc View project settings page, integrated with the page's modified/apply/reset behavior.

#### Scenario: Section renders

- **WHEN** the user opens Settings → Doc View
- **THEN** a labeled section SHALL display editable text fields for the Copy cURL host, the Doc cURL host, and the gRPC cURL host

#### Scenario: Apply button reacts to edits

- **WHEN** the user edits any of the three fields
- **THEN** the settings dialog SHALL report the page as modified so that Apply becomes enabled

#### Scenario: Apply persists values

- **WHEN** the user edits a host field and clicks Apply
- **THEN** the new value SHALL be persisted and used by the corresponding action

#### Scenario: Reset restores persisted values

- **WHEN** the user edits a host field and then resets the page
- **THEN** the fields SHALL show the persisted values again

#### Scenario: Existing settings unaffected

- **WHEN** the new section is added to the settings page
- **THEN** all pre-existing settings on that page SHALL continue to be modified, applied, and reset as before

### Requirement: HTTP client export keeps the literal placeholder

The system SHALL continue to emit the literal `{{host}}` placeholder in generated `.http` files, because that is IntelliJ HTTP client environment-variable syntax.

#### Scenario: Generated .http file

- **WHEN** the user generates an `.http` file for endpoint path `/api/users` with a Copy cURL host of `http://order-web` configured
- **THEN** the generated file SHALL contain `{{host}}/api/users`
- **AND** SHALL NOT substitute the configured host

