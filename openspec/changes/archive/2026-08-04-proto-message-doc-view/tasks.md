## 1. Build and plugin wiring

- [x] 1.1 In `build.gradle`, add `bundledPlugin('idea.plugin.protoeditor')` to the `intellijPlatform` dependencies block
- [x] 1.2 In `build.gradle`, remove the now-unused `compileOnly 'com.google.protobuf:protobuf-java:3.21.12'` entry
- [x] 1.3 Create `src/main/resources/META-INF/doc-view-protobuf.xml` as the optional-dependency config file (empty `<idea-plugin>` shell for now; it exists so the optional dependency is well-formed)
- [x] 1.4 In `plugin.xml`, add `<depends optional="true" config-file="doc-view-protobuf.xml">idea.plugin.protoeditor</depends>`
- [x] 1.5 Run `./gradlew build` and confirm it resolves the protoeditor plugin and compiles

## 2. Proto PSI access layer

- [x] 2.1 Create `utils/ProtoPluginSupport.java` with a cached `isAvailable()` check via `PluginManagerCore.getPlugin(PluginId.getId("idea.plugin.protoeditor"))`, so no `com.intellij.protobuf.*` class loads when the plugin is absent (spec: *Graceful behavior without the Protocol Buffers plugin*)
- [x] 2.2 Create `utils/ProtoMessageResolver.java` that, given `Editor` + `PsiFile`, returns the `PbMessageType` under the caret: guard `psiFile instanceof PbFile`, then `PsiTreeUtil.getParentOfType(findElementAt(offset), PbMessageType.class)`
- [x] 2.3 In `ProtoMessageResolver`, add the single-top-level-message fallback when the caret is outside every message block; return `null` when the file has several messages and none contains the caret
- [x] 2.4 In `ProtoMessageResolver`, add a helper to expose whether a `PsiFile` is a proto file (`instanceof PbFile`), for use by `EditorAction`

## 3. Proto → Java type mapping

- [x] 3.1 Create `utils/ProtoJavaTypeMapper.java` mapping `BuiltInType` scalars per design D4 (`INT32/SINT32/SFIXED32/FIXED32/UINT32`→`Integer`, `INT64/…/UINT64`→`Long`, `DOUBLE`→`Double`, `FLOAT`→`Float`, `BOOL`→`Boolean`, `STRING`/`BYTES`→`String`)
- [x] 3.2 Add well-known-type mapping by proto qualified name: `Timestamp`/`Duration`→`String`, `*Value` wrappers→matching Java wrapper, `Struct`/`Value`/`Any`/`Empty`→`Object`
- [x] 3.3 Add enum handling: map enum-typed fields to `String` and build the "可选值: NAME(0), …" description from `PbEnumDefinition.getBody().getEnumValueList()`
- [x] 3.4 Add unresolvable-type fallback: type `Object` plus a description noting the unresolved type name

## 4. Rewrite ProtoToPsiClassConverter

- [x] 4.1 Delete the `com.google.protobuf.DescriptorProtos` / `Descriptors` / `FileInputStream` implementation from `utils/ProtoToPsiClassConverter.java`
- [x] 4.2 Implement `convert(PbMessageType, Project) : PsiClass` — emit the subject message as the top-level `public class` and every reachable message/enum as a `public static` nested class named by flattened proto qualified name (e.g. `Order_Item`)
- [x] 4.3 Emit fields from `PbMessageBody.getSimpleFieldList()`, `getMapFieldList()` and `getOneofDefinitionList()`; use `PbField.getCanonicalLabel()` for `repeated`/`required`; emit `repeated X` as `java.util.List<X>` and `map<K,V>` as `java.util.Map<K,V>` (fully qualified)
- [x] 4.4 Flatten `oneof` members into ordinary fields, each with a `[oneof <name>]` description prefix and not required
- [x] 4.5 Convert proto comments to JavaDoc: `PbCommentOwner.getLeadingComments()` then `getTrailingComments()`, stripping `//`, `/*`, `*/` and leading `*`, on both classes and fields
- [x] 4.6 Add a `Set<String>` visited guard keyed on `PbSymbol.getQualifiedName()` plus a `MAX_DEPTH` (10) cap, so recursive and mutually recursive graphs terminate (spec: *Termination on recursive message graphs*)
- [x] 4.7 Build the `PsiClass` via `PsiFileFactory.createFileFromText("DocViewProtoMessage.java", JavaLanguage.INSTANCE, text)` + `PsiTreeUtil.findChildOfType`, and confirm the generated class extends nothing under `com.google.protobuf` so `ProtoUtils.isProto` stays `false`
- [x] 4.8 Add `public static final Key<Boolean> PROTO_SYNTHETIC` and stamp the generated `PsiFile`; expose `isSyntheticProtoClass(PsiClass) : boolean`

## 5. Pipeline integration

- [x] 5.1 In `CustomPsiUtils.getTargetClass(Editor, PsiFile)`, replace the `"protobuf".equalsIgnoreCase(getLanguage().getDisplayName())` check with `ProtoPluginSupport.isAvailable() && ProtoMessageResolver.isProtoFile(file)`, then resolve the caret message and convert it
- [x] 5.2 In `DocViewService.getInstance`, add a first-position branch returning `PojoDocViewServiceImpl` when `ProtoToPsiClassConverter.isSyntheticProtoClass(targetClass)` (spec: *Service routing for synthesized proto classes*)
- [x] 5.3 Add `notify.error.proto.no.message` to `src/main/resources/messages/DocViewBundle.properties` (Unicode-escaped, matching file convention): "请将光标放在 proto message 定义内"
- [x] 5.4 In `AbstractAction.actionPerformed`, when the file is a proto file and no message resolves, throw `DocViewException` with the new message instead of falling through to `notify.error.class`

## 6. Doc Editor disable and defensive fix

- [x] 6.1 In `EditorAction.update`, return `setEnabledAndVisible(false)` for proto files **before** the `CustomPsiUtils.getTargetClass` call (spec: *Doc Editor unavailable on proto files*)
- [x] 6.2 In `EditorAction.actionPerformed`, add the matching proto-file guard so the action is inert even if invoked directly
- [x] 6.3 In `PojoDocViewServiceImpl.buildClassMethodDoc`, guard the `<pre>` title extraction against `indexOf(...) == -1` for both `<pre>` and `</pre>` (spec: *Safe title extraction for protobuf-generated Java classes*)

## 7. Verification

- [x] 7.1 Create a fixture `.proto` (e.g. under `src/test/resources/`) covering: all scalar families, nested message, `repeated` scalar, `repeated` message, `map<string,int32>`, `oneof`, enum, well-known types (`Timestamp`, `Int32Value`, `Struct`), and a self-referential `Node`
- [x] 7.2 Create a second `.proto` that `import`s the first, to verify cross-file type resolution
- [ ] 7.3 `./gradlew runIde`; verify Doc View on each fixture message produces the field table and JSON example the spec requires — in particular that `repeated`/`map` render as `List<…>`/`Map<…>` even if `java.util.List`/`Map` do not resolve in the light file (design risk)
- [ ] 7.4 In the sandbox, verify caret behaviors: inside nested message, on `message` line, on an `rpc` line, outside all messages with one vs. several messages in the file
- [ ] 7.5 In the sandbox, verify Doc Editor is hidden in `.proto` files and unchanged in Java files
- [ ] 7.6 In the sandbox, verify a message named without any POJO suffix (e.g. `Order`) does not report `notify.error.not.support`
- [ ] 7.7 In the sandbox, verify Doc View on an existing protobuf-generated Java class still behaves as before (no regression from the routing and `<pre>` changes)
- [x] 7.8 Add a `CHANGELOG.md` entry under `[Unreleased]` → `Fixed` / `Added` describing proto message Doc View support
