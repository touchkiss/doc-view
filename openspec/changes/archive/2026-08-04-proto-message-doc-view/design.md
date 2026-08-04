## Context

Doc View's document pipeline is Java-PSI-centric end to end:

```
AbstractAction.actionPerformed
  → CustomPsiUtils.getTargetClass(editor, psiFile) : PsiClass
  → DocViewService.getInstance(project, targetClass)
  → PojoDocViewServiceImpl.buildClassMethodDoc
      → PojoUtils.buildBody(psiClass)      // PsiField → Body tree
      → PojoUtils.reqBodyJson(psiClass)    // PsiField → JSON example
  → DocView → DocViewData → Velocity pojoTemplate → PreviewForm
```

`DocView.psiClass` is a hard dependency downstream: `DocViewData:114` calls `docView.getPsiClass().getProject()`, `PreviewForm:625` calls `getPsiClass().getQualifiedName()`, and `MenuExportAllAction`/`ExportUtils` use `getPsiClass().getName()`. Nothing in the pipeline can consume a proto PSI element directly.

A proto branch already exists at `CustomPsiUtils.getTargetClass` (`src/main/java/com/liuzhihang/doc/view/utils/CustomPsiUtils.java:38`) and takes the "synthesize a Java class" approach, but its implementation is broken in three independent ways:

1. `ProtoToPsiClassConverter.generateJavaCodeFromProto` feeds `.proto` **source text** into `DescriptorProtos.FileDescriptorProto.parseFrom(FileInputStream)`, which decodes a binary `FileDescriptorProto`. It throws, is swallowed by `catch (IOException | DescriptorValidationException)`, and returns `""`.
2. `protobuf-java` is `compileOnly` (`build.gradle:31`), so `com.google.protobuf.DescriptorProtos` is not on the plugin runtime classpath — the call likely fails earlier with `NoClassDefFoundError`.
3. It ignores the caret entirely and would return the first message in the file.

Even with parsing fixed, two downstream landmines remain:

- **Routing.** `DocViewService.getInstance` picks `PojoDocViewServiceImpl` only when `PojoUtils.isPojoClass` returns true, which for a non-annotated, non-record class falls back to a name-suffix heuristic (`dto|vo|bo|po|entity|model|bean`). A message named `Order` fails it, then falls through to `settings.getIncludeNormalInterface()` → `DubboDocViewServiceImpl` (a class with no methods → empty doc) or throws `notify.error.not.support`.
- **The `isProto` flag means "protobuf-*generated Java* class", not "came from a proto file".** `DocViewUtils.isExcludeField(field, isProto=true)` drops every field whose name does not end with `_` (`DocViewUtils.java:271`), and `ParamPsiUtils` has parallel generated-class handling for `getXxxList()` / `Map` conventions. A clean synthetic class must therefore run with `isProto == false`, i.e. look like an ordinary POJO.

Constraint from the user: document the message's **data structure only** — no rpc/service interface docs (`Copy gRPC cURL` already covers rpc methods). Scope confirmed as Doc View preview + explicit Doc Editor disable; Upload/Export and context-menu visibility narrowing are out.

## Goals / Non-Goals

**Goals:**

- Right-click inside a `message` in a `.proto` file → Doc View popup shows that message's field table and JSON example.
- Accurate structure: nested messages, messages imported from other `.proto` files, `repeated`, `map<K,V>`, `oneof`, enums, and well-known types.
- Field descriptions come from proto comments (`//` and `/* */`).
- Zero changes to `Body`, `DocViewData`, the Velocity templates, or `PreviewForm`.
- Terminate on recursive message graphs.
- Doc Editor is visibly unavailable on proto files rather than failing at runtime.
- The plugin still loads and works for Java when the Protocol Buffers plugin is absent.

**Non-Goals:**

- Parsing `service` / `rpc` definitions into interface documentation.
- Upload to YApi/ShowDoc/YuQue or Markdown export for proto messages.
- Writing documentation back into the `.proto` source (`WriterService` write-back).
- Documenting proto extensions, `group` (proto2 legacy), or custom options.
- Touching the regex path in `ProtoGrpcUtils` used by `Copy gRPC cURL`.

## Decisions

### D1: Parse with protobuf-plugin PSI, not regex or protobuf-java

Use `com.intellij.protobuf.lang.psi.*` from the bundled Protocol Buffers plugin (`idea.plugin.protoeditor`, verified present at `/Applications/IntelliJ IDEA.app/Contents/plugins/protoeditor/lib/protoeditor.jar`).

Verified API surface (from the shipped jar):

| Need | API |
|---|---|
| Message under caret | `PsiTreeUtil.getParentOfType(element, PbMessageType.class)` |
| Message body members | `PbMessageType.getBody()` → `PbMessageBody.getSimpleFieldList() / getMapFieldList() / getOneofDefinitionList() / getMessageDefinitionList() / getEnumDefinitionList()` |
| Field label | `PbField.getCanonicalLabel()` → `OPTIONAL / REQUIRED / REPEATED`; `isRepeated()`, `isRequired()` |
| Field type | `PbField.getTypeName()` → `PbTypeName.isBuiltInType()`, `getBuiltInType()` (`BuiltInType` enum: `STRING BYTES BOOL DOUBLE FLOAT UINT32 UINT64 FIXED32 FIXED64 INT32 INT64 SINT32 SINT64 SFIXED32 SFIXED64`), `getShortName()` |
| Cross-file / nested type resolution | `PbTypeName.getEffectiveReference().resolve()` → `PbMessageType` / `PbEnumDefinition` |
| Map key/value | `PbMapField.getKeyType()`, `getValueType()` |
| Enum values | `PbEnumDefinition.getBody()` → `PbEnumBody.getEnumValueList()` → `PbEnumValue.getName()`, `getNumberValue()` |
| Comments | `PbCommentOwner.getLeadingComments()`, `getTrailingComments()` (`PbStatement extends PbCommentOwner`) |
| Qualified name for cycle keys | `PbSymbol.getQualifiedName()` |

*Alternatives considered.* (a) **Regex text parsing**, extending `ProtoGrpcUtils` — no new dependency and already proven for cURL, but cannot follow `import` across files, and handles `oneof`/`map`/comments poorly; rejected because nested and imported types are the main value of a data-structure doc. (b) **protobuf-java descriptors** — would require invoking `protoc` or shipping compiled descriptors; rejected as unavailable at edit time. (c) **PSI with regex fallback** — roughly doubles the implementation and test surface for IntelliJ Community users who are outside the project's declared target (`platformType=IU`); rejected.

### D2: Keep the synthetic-Java-class strategy, done properly

`ProtoToPsiClassConverter` is rewritten to emit one in-memory Java compilation unit per request:

- the caret's message becomes the top-level `public class`;
- every message/enum reachable from it becomes a `public static` nested class of that top-level class, named by its flattened proto qualified name (e.g. `Order_Item`) to avoid nested-name collisions;
- each proto comment becomes a JavaDoc comment on the class or field, so `DocViewUtils.getTitle` and `CustomPsiCommentUtils` pick it up with no changes;
- built via `PsiFileFactory.getInstance(project).createFileFromText("DocViewProtoMessage.java", JavaLanguage.INSTANCE, text)`, then `PsiTreeUtil.findChildOfType(..., PsiClass.class)`.

Emitting referenced types as nested classes of the *same* file is what makes intra-file type references resolvable in a non-physical `PsiFile`, which is what `ParamPsiUtils` needs to recurse into child objects.

*Alternative considered.* A native `ProtoDocViewServiceImpl` building `Body` directly from `PbMessageType` is semantically cleaner, but `DocView.psiClass` is non-null-assumed in `DocViewData:114`, `PreviewForm:625`, `ExportUtils`, and `MenuExportAllAction`; making it nullable is a cross-cutting refactor of shared code that also carries regression risk for Spring/Dubbo. Rejected for this change — revisit if proto ever needs write-back.

### D3: Route via an explicit marker, and keep `isProto == false`

The converter stamps the generated `PsiFile` with a `Key<Boolean>`:

```java
public static final Key<Boolean> PROTO_SYNTHETIC = Key.create("docview.proto.synthetic");
```

`DocViewService.getInstance` checks `ProtoToPsiClassConverter.isSyntheticProtoClass(targetClass)` **first**, before the Feign/Dubbo/Spring/POJO chain, and returns `PojoDocViewServiceImpl`.

`UserData` on the light file is safe here because the same `PsiFile` instance flows straight from `getTargetClass` into the action and service within one invocation.

Crucially the generated class does **not** extend anything under `com.google.protobuf`, so `ProtoUtils.isProto(psiClass)` stays `false` and the generated-class-only filters in `DocViewUtils.isExcludeField` / `ParamPsiUtils` never engage.

*Alternative considered.* Emitting `extends com.google.protobuf.GeneratedMessageV3` to satisfy `ProtoUtils.isProto` → `PojoUtils.isPojoClass`. Rejected: it flips `isProto` to `true`, which then discards every field (names do not end with `_`) and triggers `PojoDocViewServiceImpl`'s `<pre>` title `substring`.

### D4: Proto → Java type mapping

| Proto | Generated Java | Rationale |
|---|---|---|
| `double`, `float` | `Double`, `Float` | wrappers, so `FieldTypeConstant.FIELD_TYPE` matches on presentable text |
| `int32`, `sint32`, `sfixed32`, `fixed32`, `uint32` | `Integer` | |
| `int64`, `sint64`, `sfixed64`, `fixed64`, `uint64` | `Long` | |
| `bool` | `Boolean` | |
| `string` | `String` | |
| `bytes` | `String` | JSON representation is base64 text; desc notes `bytes (base64)` |
| `enum E` | `String` | desc appends `可选值: NAME(0), NAME(1)…`; expanding an enum `PsiClass` yields an empty object because enum constants are `static` and get excluded |
| `message M` | nested class `M` | recursed into by `ParamPsiUtils` |
| `repeated X` | `java.util.List<X>` | |
| `map<K,V>` | `java.util.Map<K,V>` | `ParamPsiUtils` already has `Map` handling |
| `oneof o { a; b; }` | each member as a normal field | desc prefixed `[oneof o]`; all members optional |
| `google.protobuf.Timestamp` / `Duration` | `String` | JSON is RFC3339 / duration string, not the internal `seconds`/`nanos` |
| `google.protobuf.{Int32,Int64,Bool,String,Double,Float,Bytes,UInt32,UInt64}Value` | corresponding wrapper | JSON is the bare scalar |
| `google.protobuf.Struct` / `Value` / `Any` / `Empty` | `Object` | free-form JSON object |
| unresolvable type name | `Object` | desc notes `未解析类型: <name>` rather than failing the whole doc |

Wrappers over primitives keep `FieldTypeConstant.FIELD_TYPE` lookups (keyed on `"Integer"`, `"Long"`, …) working for JSON example generation.

`repeated`/`map` are emitted fully qualified (`java.util.List<…>`) so they hold up even if the light file's resolve scope cannot see an import list; `ParamPsiUtils` and `FieldTypeConstant` key off `PsiType.getPresentableText()`, which renders as `List<Item>` either way.

### D5: Cycle and depth control

Generation walks the reachable type graph with a `Set<String>` of proto qualified names already emitted, keyed on `PbSymbol.getQualifiedName()`. A type already emitted is referenced by its flattened name instead of re-emitted, so `message Node { repeated Node children = 1; }` terminates. A `MAX_DEPTH` (default 10) caps pathological graphs; types beyond it degrade to `Object` with a desc note.

Note this bounds *class generation*, not `ParamPsiUtils.buildBodyParam`'s own recursion over the resulting Java types — that already carries its own `parentChildPair` guard, which handles the self-referential Java class the cycle case produces.

### D6: Caret resolution and error reporting

`CustomPsiUtils.getTargetClass` proto branch becomes:

1. Detect proto by PSI (`psiFile instanceof PbFile`), not by the `"protobuf"` language display-name string, which is locale/branding dependent.
2. `element = file.findElementAt(offset)`; `PsiTreeUtil.getParentOfType(element, PbMessageType.class)`.
3. If null and the file contains exactly one top-level message, use it (right-clicking the file's blank tail is a common gesture).
4. Otherwise return `null` and let the caller report a new bundle message `notify.error.proto.no.message` ("请将光标放在 proto message 定义内"), instead of the misleading `notify.error.class`.

The `instanceof PbFile` reference is what forces the optional-dependency wiring in D7 — the class must not load when the plugin is absent.

### D7: Optional dependency, isolated in a config file

- `build.gradle`: add `bundledPlugin('idea.plugin.protoeditor')`; drop `compileOnly 'com.google.protobuf:protobuf-java:3.21.12'` (no remaining usage).
- `plugin.xml`: `<depends optional="true" config-file="doc-view-protobuf.xml">idea.plugin.protoeditor</depends>`.
- All `com.intellij.protobuf.*` references live in the new proto classes only. `CustomPsiUtils` reaches them through a small indirection that is skipped unless the Protocol Buffers plugin is loaded (checked once via `PluginManagerCore.getPlugin(PluginId.getId("idea.plugin.protoeditor"))`), so no `NoClassDefFoundError` on IDEs without it.

### D8: Doc Editor disabled early

`EditorAction.update()` currently calls `CustomPsiUtils.getTargetClass(editor, psiFile)` on every context-menu update on the BGT. For proto files that would run full class synthesis just to decide menu visibility. Add a proto check at the top of `update()` that sets `setEnabledAndVisible(false)` and returns **before** `getTargetClass`, and a matching guard in `actionPerformed`. This satisfies the "Doc Editor 明确禁用" scope item and removes the perf hazard in one edit.

### D9: Fix the `<pre>` title crash while nearby

`PojoDocViewServiceImpl.buildClassMethodDoc` does `title.substring(title.indexOf("<pre>") + 5, title.indexOf("</pre>"))` guarded only by `ProtoUtils.isProto(psiClass)`. For a real protobuf-generated Java class whose JavaDoc has no `<pre>` block, that is `substring(4, -1)` → `StringIndexOutOfBoundsException`. Guard both indices. This is not on the new path (synthetic classes have `isProto == false`) but it is a live crash in the code this change touches.

## Risks / Trade-offs

- **`com.intellij.protobuf.lang.psi` is not stable platform API** → Confine every reference to the new proto classes behind the optional dependency; the interfaces used (`PbFile`, `PbMessageType`, `PbMessageBody`, `PbField`, `PbTypeName`, `PbMapField`, `PbEnumDefinition`) have been stable across releases and are verified against the 2026.2 jar the project builds against.
- **Not available in IntelliJ IDEA Community** → Optional `<depends>` means the plugin loads fine; Doc View on `.proto` files simply stays unavailable there. The project already targets `platformType=IU`.
- **Non-physical `PsiFile` resolve scope may not resolve `java.util.List` / `java.util.Map`** → Fully-qualified type text plus `getPresentableText()`-based matching in `ParamPsiUtils`/`FieldTypeConstant` keeps the field table correct even without resolution. Verify explicitly in the sandbox IDE for a `repeated` and a `map` field.
- **Flattened nested-class names (`Order_Item`) leak into the rendered type column** → Accepted; the field table shows types by presentable name and the flattening keeps them unambiguous. Alternative (true nesting mirroring proto nesting) risks name shadowing when an imported message shares a simple name with a local one.
- **Marker `UserData` could be lost if a caller re-resolves the class from scratch** → The marker is only consulted in the single `getTargetClass` → `getInstance` → `buildDoc` flow within one action invocation. The tool window's independent pipeline (`CustomFileUtils`, `MethodNode`) only ever holds real Java classes, so it is unaffected.
- **No automated test suite in this project** → Verification is manual via `./gradlew runIde` against a fixture `.proto` exercising scalars, nested, imported, `repeated`, `map`, `oneof`, enum, well-known types, and a self-referential message. Optionally add a plain-`main()` exploratory harness under `src/test/java/` matching the existing convention.

## Migration Plan

No data or settings migration. Rollback is reverting the commit; the removed `protobuf-java` `compileOnly` entry and the added `bundledPlugin` are the only build-level changes, and no user-visible setting or persisted state changes shape.

## Open Questions

- Should `google.protobuf.Timestamp` render as `String` (RFC3339, matching protobuf JSON mapping) or be expanded to `seconds`/`nanos` (matching binary/gRPC-Java reality)? Design assumes `String`; revisit if the team's grpc-gateway config differs.
- Should a future change add a `FrameworkEnum.PROTO` and a dedicated proto Velocity template, instead of reusing `NONE_POJO` + `pojoTemplate`? Out of scope here; reusing the POJO template is what keeps this change small.
