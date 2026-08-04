# Capability: gRPC Copy cURL

## Purpose

Provide a "Copy gRPC cURL" action for `rpc` method definitions in `.proto` files: parse the service and method signature, derive a JSON request body from the request message fields, and copy a gRPC-formatted curl command using the project's configured gRPC host.
## Requirements
### Requirement: Proto service method detection
The system SHALL detect when the cursor is on a gRPC service method definition in a `.proto` file and show the "Copy gRPC cURL" action in the right-click context menu.

#### Scenario: Cursor on rpc method definition
- **WHEN** user right-clicks on a line containing `rpc MethodName(RequestType) returns (ResponseType)` inside a `service` block
- **THEN** the "Copy gRPC cURL" action SHALL be visible and enabled

#### Scenario: Cursor outside service method
- **WHEN** user right-clicks on a line not containing an rpc method definition
- **THEN** the "Copy gRPC cURL" action SHALL be hidden

### Requirement: Proto file parsing
The system SHALL parse the `.proto` file to extract the service name and method name from the cursor position.

#### Scenario: Extract service and method names
- **WHEN** user invokes "Copy gRPC cURL" on method `listUserLivePlay` in service `BeetoLiveGrpcService`
- **THEN** the system SHALL extract service name `BeetoLiveGrpcService` and method name `listUserLivePlay`

### Requirement: Request message field extraction
The system SHALL parse the request message type definition to extract field names and types.

#### Scenario: Simple message fields
- **WHEN** request message `ListUserLivePlayRequest` has fields `uid` (int64), `begin_time_millis` (int64), `next_since_id` (int64), `is_visible` (bool)
- **THEN** the system SHALL extract field names and generate appropriate default values

### Requirement: JSON body generation
The system SHALL generate a JSON request body with default values based on proto field types.

#### Scenario: Generate JSON from fields
- **WHEN** message fields are extracted
- **THEN** the system SHALL generate JSON like `{"uid": 0, "begin_time_millis": 0, "next_since_id": 0, "is_visible": false}`

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

