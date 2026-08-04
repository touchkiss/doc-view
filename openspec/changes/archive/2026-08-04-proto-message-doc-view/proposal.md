## Why

Right-clicking a `message` in a `.proto` file and choosing **Doc View** currently fails with `notify.error.class`（"当前位置不合法 / 该类不支持"）. The existing proto branch in `CustomPsiUtils.getTargetClass` delegates to `ProtoToPsiClassConverter`, which calls `DescriptorProtos.FileDescriptorProto.parseFrom(InputStream)` on the **`.proto` source text** — that API expects a pre-compiled binary `FileDescriptorSet`, so parsing always fails, the generated Java text is empty, and a `null` `PsiClass` propagates up to `AbstractAction`. Worse, `protobuf-java` is declared `compileOnly` in `build.gradle`, so at IDE runtime the call most likely dies with `NoClassDefFoundError` before it even fails to parse.

Developers on this codebase define their API payloads in `.proto` files. They need the same "show me this data structure as a documented field table + JSON example" experience they already get for POJOs — without leaving the proto file and without needing the generated Java sources to exist yet.

## What Changes

- Replace the broken descriptor-binary parsing with parsing based on the **bundled Protocol Buffers plugin PSI** (`idea.plugin.protoeditor`, `com.intellij.protobuf.lang.psi.*`). This resolves nested messages, cross-file `import` references, `oneof`, `map`, and enums accurately, and gives access to leading/trailing comments for field descriptions.
- Make proto handling **caret-aware**: Doc View documents the `message` the caret is actually inside, instead of always taking the first message in the file.
- Rewrite `ProtoToPsiClassConverter` to synthesize an **in-memory Java class** from the target message — target message as the top-level class, referenced messages/enums as static nested classes, proto comments as JavaDoc — so the entire existing document pipeline (`PojoDocViewServiceImpl` → `PojoUtils.buildBody` → `Body` → `DocViewData` → Velocity POJO template → `PreviewForm`) is reused unchanged.
- Route the synthesized class explicitly to `PojoDocViewServiceImpl` via a marker on the generated file, instead of relying on `PojoUtils.isPojoClass`'s name-suffix heuristic (which would reject a message named e.g. `Order` and mis-route it to `DubboDocViewServiceImpl` or throw `notify.error.not.support`).
- Ensure the synthesized class is treated as an ordinary POJO, **not** as protobuf-generated Java: the existing `isProto` code paths in `ParamPsiUtils` / `DocViewUtils.isExcludeField` assume generated-class conventions (fields ending in `_`) and would filter out every field of a clean synthetic class.
- Map proto types to documentation-friendly Java types: scalars → Java primitives/wrappers, `repeated` → `java.util.List<T>`, `map<K,V>` → `java.util.Map<K,V>`, `enum` → `String` with allowed values in the description, well-known types (`Timestamp`, `Duration`, wrappers, `Struct`, `Any`, `Empty`) → their JSON representations rather than expanded internals.
- Guard against self-referential and mutually-recursive messages with a visited-set / depth cap so generation terminates.
- **Explicitly disable Doc Editor on proto files.** `EditorAction` writes edited docs back as JavaDoc via `WriterService`; against a non-physical synthetic class that is meaningless. Its `update()` will hide the action for proto files *before* any conversion runs (it currently calls `getTargetClass` on every menu update, which would trigger proto parsing on the BGT hot path).
- Replace the generic `notify.error.class` failure with an actionable message when the caret is not inside a `message`.
- Fix the latent `StringIndexOutOfBoundsException` in `PojoDocViewServiceImpl.buildClassMethodDoc` where the `<pre>` title extraction does `substring` without checking `indexOf(...) != -1`.
- Out of scope for this change: rpc/service interface documentation (deliberately — `Copy gRPC cURL` already covers rpc methods), and Upload/Export of proto message docs.

## Capabilities

### New Capabilities
- `proto-message-doc-view`: Detect the proto `message` under the caret, parse it via protobuf-plugin PSI, synthesize a documented in-memory Java class from it (including nested/imported types, `repeated`, `map`, `oneof`, enums and well-known types), and render it through the existing POJO document pipeline as a Doc View preview. Includes disabling Doc Editor on proto files and reporting an actionable error when the caret is not inside a message.

### Modified Capabilities

None. `grpc-copy-curl` keeps its own regex-based path in `ProtoGrpcUtils` and is not touched by this change.

## Impact

**Dependencies**
- `build.gradle`: add `bundledPlugin('idea.plugin.protoeditor')`; remove the now-unused `compileOnly 'com.google.protobuf:protobuf-java'`.
- `plugin.xml`: declare `<depends optional="true" config-file="...">idea.plugin.protoeditor</depends>` (or a plain `<depends>` if the plugin is accepted as a hard requirement) so the plugin still loads where Protocol Buffers is absent.

**Rewritten**
- `utils/ProtoToPsiClassConverter.java` — proto PSI → synthetic Java class; no longer touches `com.google.protobuf.*`.

**Modified**
- `utils/CustomPsiUtils.java:38` — caret-aware proto branch; language detection via PSI file type rather than the `"protobuf"` display-name string.
- `service/DocViewService.java` — route marker-tagged synthetic proto classes to `PojoDocViewServiceImpl`.
- `service/impl/PojoDocViewServiceImpl.java` — guard the `<pre>` title extraction.
- `action/EditorAction.java` — hide Doc Editor for proto files before conversion.
- `messages/DocViewBundle.properties` — new "caret not inside a message" message.

**Risks**
- `com.intellij.protobuf.lang.psi` is not an officially stable platform API; it is bundled only in IntelliJ IDEA Ultimate and other JetBrains IDEs, not IntelliJ IDEA Community. The optional-dependency wiring keeps the rest of the plugin working when it is missing.
- Type references inside a non-physical `PsiFile` (`java.util.List`, `java.util.Map`) may resolve against a broad scope; the design must confirm `Body` rendering still works via `FieldTypeConstant` presentable-text matching if resolution fails.
