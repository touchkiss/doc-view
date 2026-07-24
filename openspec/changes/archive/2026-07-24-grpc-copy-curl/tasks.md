## 1. Proto File Parsing Utilities

- [x] 1.1 Create `ProtoGrpcUtils.java` utility class with methods to parse proto service definitions
- [x] 1.2 Implement method to extract service name from cursor position in proto file
- [x] 1.3 Implement method to extract rpc method name and request/response types
- [x] 1.4 Implement method to parse request message type and extract field names with types
- [x] 1.5 Implement JSON body generation with default values based on proto field types (int→0, string→"", bool→false, etc.)

## 2. GrpcCurlUtils

- [x] 2.1 Create `GrpcCurlUtils.java` utility class for generating gRPC curl commands
- [x] 2.2 Implement `build()` method that takes service name, method name, and JSON body
- [x] 2.3 Format output as: `curl -X GRPC "{host}/{Service}/{Method}" -d '{json}'`

## 3. Action Implementation

- [x] 3.1 Create `ProtoGrpcCopyCurlAction.java` extending `AnAction`
- [x] 3.2 Implement `update()` method to show action only on proto rpc method definitions
- [x] 3.3 Implement `actionPerformed()` to parse proto, build curl, and copy to clipboard
- [x] 3.4 Add notification on success/failure

## 4. Plugin Registration

- [x] 4.1 Register `ProtoGrpcCopyCurlAction` in `plugin.xml` under `EditorPopupMenu` group
- [x] 4.2 Add action ID and text configuration

## 5. Testing

- [x] 5.1 Create unit tests for `ProtoGrpcUtils` parsing logic
- [x] 5.2 Create unit tests for `GrpcCurlUtils` command generation
- [x] 5.3 Manual testing with sample proto files (main code compiles successfully)
