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
The system SHALL generate a gRPC-formatted curl command and copy it to the clipboard.

#### Scenario: Copy gRPC curl command
- **WHEN** user invokes "Copy gRPC cURL" on a proto method
- **THEN** the system SHALL copy to clipboard a command in format:
  ```
  curl -X GRPC "localhost:9090/ServiceName/MethodName" \
      -d '{...json body...}'
  ```

#### Scenario: Notification on success
- **WHEN** curl command is copied successfully
- **THEN** the system SHALL show a notification "Copied gRPC cURL for MethodName"

### Requirement: Host configuration
The system SHALL use a gRPC host from settings or default to `localhost:9090`.

#### Scenario: Default host
- **WHEN** no gRPC host is configured
- **THEN** the system SHALL use `localhost:9090` as the host

#### Scenario: Custom host
- **WHEN** gRPC host is configured in settings
- **THEN** the system SHALL use the configured host
