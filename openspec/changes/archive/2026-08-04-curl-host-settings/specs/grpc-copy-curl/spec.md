## MODIFIED Requirements

### Requirement: Host configuration
The system SHALL use the project's configured **gRPC cURL host** when generating the gRPC curl command, defaulting to `http://localhost:9090` when the setting is blank or unset. The value SHALL be used verbatim, with no scheme added or removed, so a project may configure either `http://host:port` or a bare `host:port`.

#### Scenario: Default host
- **WHEN** no gRPC host is configured
- **THEN** the system SHALL use `http://localhost:9090` as the host

#### Scenario: Custom host
- **WHEN** gRPC host is configured in settings
- **THEN** the system SHALL use the configured host

#### Scenario: Blank host falls back to the default
- **WHEN** the gRPC host setting is blank or whitespace-only
- **THEN** the system SHALL use `http://localhost:9090` as the host

#### Scenario: Bare host and port without scheme
- **WHEN** the gRPC host is configured as `localhost:9090`
- **THEN** the generated command SHALL target `localhost:9090/ServiceName/MethodName` with no scheme added

#### Scenario: Host with trailing slash
- **WHEN** the gRPC host is configured as `http://grpc-gw/`
- **THEN** the generated command SHALL target `http://grpc-gw/ServiceName/MethodName` with no doubled slash

### Requirement: Curl command generation
The system SHALL generate a gRPC-formatted curl command and copy it to the clipboard, using the configured gRPC cURL host as the base URL.

#### Scenario: Copy gRPC curl command
- **WHEN** user invokes "Copy gRPC cURL" on a proto method with no gRPC host configured
- **THEN** the system SHALL copy to clipboard a command in format:
  ```
  curl -X GRPC "http://localhost:9090/ServiceName/MethodName" \
      -d '{...json body...}'
  ```

#### Scenario: Configured host in generated command
- **WHEN** the gRPC host is configured as `http://grpc-gw:9090` and the user invokes "Copy gRPC cURL"
- **THEN** the copied command SHALL target `http://grpc-gw:9090/ServiceName/MethodName`

#### Scenario: Notification on success
- **WHEN** curl command is copied successfully
- **THEN** the system SHALL show a notification "Copied gRPC cURL for MethodName"
