## Why

The Doc View plugin currently supports "Copy as cURL" for Spring REST methods via `CopyCurlAction`, but lacks equivalent support for gRPC service methods defined in `.proto` files. Developers working with gRPC services need a quick way to generate curl-compatible commands for testing gRPC endpoints, especially when using tools like `grpcurl` or gRPC-gateway that expose gRPC over HTTP.

## What Changes

- Add a new right-click context menu action "Copy gRPC cURL" for proto file service methods
- Parse `.proto` files to extract service name, method name, and request message fields
- Generate a gRPC-compatible curl command in the format:
  ```
  curl -X GRPC "{host}/{ServiceName}/{MethodName}" \
      -d '{ ... request JSON ... }'
  ```
- The action should only appear when the cursor is on a proto service method definition

## Capabilities

### New Capabilities
- `grpc-copy-curl`: Parse proto service definitions, extract method signatures, generate JSON request body from message fields, and copy a gRPC-formatted curl command to clipboard

### Modified Capabilities

## Impact

- **New files**: 
  - `ProtoGrpcCopyCurlAction.java` - new action class
  - `ProtoGrpcUtils.java` - utility for parsing proto service/method definitions
- **Modified files**:
  - `plugin.xml` - register new action in EditorPopupMenu group
- **Dependencies**: Uses existing proto file parsing infrastructure; may leverage PSI elements from the protobuf plugin if available
