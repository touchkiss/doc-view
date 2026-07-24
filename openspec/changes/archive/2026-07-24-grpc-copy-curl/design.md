## Context

The Doc View plugin has an existing `CopyCurlAction` that generates curl commands for Spring REST methods. It works by:
1. Detecting Spring methods via `SpringPsiUtils.isSpringMethod()`
2. Building a `DocView` object with method, path, and request body
3. Using `CurlUtils.build()` to generate the curl command

For proto/gRPC support, we need a parallel mechanism that:
- Works on `.proto` files (not Java files)
- Parses gRPC service definitions using protobuf plugin PSI
- Generates gRPC-formatted curl commands

The plugin already has `ProtoUtils` (checks if a class is protobuf) and `ProtoToPsiClassConverter` (converts proto to PsiClass), but these are for Java-compiled proto classes, not raw `.proto` files.

## Goals / Non-Goals

**Goals:**
- Add right-click "Copy gRPC cURL" action on proto service method definitions
- Generate curl command in format: `curl -X GRPC "{host}/{Service}/{Method}" -d '{json}'`
- Parse proto file to extract service name, method name, and request message fields
- Provide sensible default values for request body fields based on proto types

**Non-Goals:**
- Support for streaming RPCs (only unary for now)
- Support for custom metadata/headers in gRPC calls
- Full proto file validation or compilation

## Decisions

### 1. Proto file parsing approach: Use protobuf plugin PSI elements

**Decision**: Use the protobuf plugin's PSI elements (`GrpcServiceDeclaration`, `GrpcMethodDeclaration`, etc.) if available, otherwise fall back to text-based regex parsing.

**Rationale**: The protobuf plugin provides structured PSI for proto files. However, it's an optional dependency. We should:
- Primary: Use protobuf plugin PSI (reliable, structured)
- Fallback: Regex-based parsing of proto file text (works without plugin)

**Alternatives considered**:
- Pure regex parsing: Simpler but fragile, handles edge cases poorly
- Full proto compilation: Too heavy for a context menu action

### 2. Request body generation: Parse message type fields

**Decision**: When the cursor is on a method, look up the request message type definition in the same proto file and generate a JSON body with default values for each field.

**Rationale**: Proto files define message types with typed fields. We can generate example JSON based on field types (string→"", int32→0, bool→false, etc.).

### 3. Host configuration: Reuse existing settings or provide default

**Decision**: Use a configurable host (from Settings) or default to `localhost:9090` for gRPC.

**Rationale**: gRPC services run on different ports than HTTP. The existing `{{host}}` pattern works for REST; we need a gRPC-specific host or fallback.

## Risks / Trade-offs

- **[Risk] Protobuf plugin not installed** → Fallback to regex parsing with clear error message
- **[Risk] Complex message types (nested, repeated, map)** → Start with flat messages, warn on complex types
- **[Risk] Proto file with imports** → Only parse definitions in the current file; don't resolve imports
