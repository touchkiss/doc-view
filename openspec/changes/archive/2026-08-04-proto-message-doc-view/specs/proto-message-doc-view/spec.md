## ADDED Requirements

### Requirement: Proto message detection at caret

The system SHALL determine the proto `message` to document from the caret position in a `.proto` file, using Protocol Buffers plugin PSI rather than the language display name.

#### Scenario: Caret inside a message body

- **WHEN** the caret is on a field line inside `message Order { ... }` and the user invokes **Doc View**
- **THEN** the system SHALL document `Order`

#### Scenario: Caret on the message declaration line

- **WHEN** the caret is on the `message Order {` line itself
- **THEN** the system SHALL document `Order`

#### Scenario: Caret inside a nested message

- **WHEN** `message Order` contains `message Item { ... }` and the caret is inside `Item`
- **THEN** the system SHALL document `Item` as the top-level subject, not `Order`

#### Scenario: File declares several messages

- **WHEN** a `.proto` file declares `Order`, `Item` and `Address`, and the caret is inside `Address`
- **THEN** the system SHALL document `Address` only, and SHALL NOT fall back to the first message in the file

#### Scenario: Caret outside any message, single message in file

- **WHEN** the caret is outside every `message` block but the file declares exactly one top-level message
- **THEN** the system SHALL document that message

#### Scenario: Caret outside any message, several messages in file

- **WHEN** the caret is outside every `message` block and the file declares more than one top-level message
- **THEN** the system SHALL abort and show a notification instructing the user to place the caret inside a `message` definition
- **AND** the system SHALL NOT show the generic `notify.error.class` message

#### Scenario: Caret on a service or rpc definition

- **WHEN** the caret is inside a `service` block on an `rpc` line
- **THEN** Doc View SHALL NOT attempt to generate rpc interface documentation
- **AND** the system SHALL show the "place the caret inside a message" notification

### Requirement: Doc View preview for a proto message

The system SHALL render the selected proto message through the existing POJO documentation pipeline, producing a field table and a JSON request example in the Doc View preview popup.

#### Scenario: Preview opens with field table and JSON example

- **WHEN** the user invokes **Doc View** on `message Order { string order_no = 1; int32 amount = 2; }`
- **THEN** the preview popup SHALL open
- **AND** the field table SHALL list `order_no` as `String` and `amount` as `Integer`
- **AND** the JSON example SHALL contain both `order_no` and `amount` with type-appropriate default values

#### Scenario: No error notification on success

- **WHEN** Doc View succeeds on a proto message
- **THEN** no error notification SHALL be shown

#### Scenario: Previously failing invocation now succeeds

- **WHEN** the user right-clicks a proto `message` and chooses **Doc View**
- **THEN** the system SHALL NOT report `notify.error.class`
- **AND** the system SHALL NOT throw `NoClassDefFoundError` for `com.google.protobuf.DescriptorProtos`

### Requirement: Message title and field descriptions from proto comments

The system SHALL use proto comments as documentation text: the comment attached to the message as the document title/description, and the comment attached to each field as that field's description.

#### Scenario: Leading line comment on a message

- **WHEN** `message Order` is preceded by `// 订单信息`
- **THEN** the document title SHALL be `订单信息`

#### Scenario: Leading line comment on a field

- **WHEN** a field is preceded by `// 订单号`
- **THEN** that field's description SHALL be `订单号`

#### Scenario: Trailing comment on a field

- **WHEN** a field is written as `string order_no = 1; // 订单号`
- **THEN** that field's description SHALL be `订单号`

#### Scenario: Block comment

- **WHEN** a field is preceded by `/* 订单号 */`
- **THEN** that field's description SHALL be `订单号`

#### Scenario: Message without comments

- **WHEN** `message Order` has no comment
- **THEN** the document title SHALL fall back to the message name per the existing title settings, and generation SHALL succeed

### Requirement: Scalar type mapping

The system SHALL map proto scalar types to Java types whose presentable names are recognized by the existing field-type and JSON-example machinery.

#### Scenario: Integer family

- **WHEN** a message contains `int32`, `sint32`, `sfixed32`, `fixed32` or `uint32` fields
- **THEN** each SHALL be documented as `Integer`

#### Scenario: Long family

- **WHEN** a message contains `int64`, `sint64`, `sfixed64`, `fixed64` or `uint64` fields
- **THEN** each SHALL be documented as `Long`

#### Scenario: Floating point and boolean

- **WHEN** a message contains `double`, `float` and `bool` fields
- **THEN** they SHALL be documented as `Double`, `Float` and `Boolean` respectively

#### Scenario: String and bytes

- **WHEN** a message contains a `string` field and a `bytes` field
- **THEN** both SHALL be documented as `String`
- **AND** the `bytes` field description SHALL indicate that the value is base64-encoded

### Requirement: Nested and imported message expansion

The system SHALL expand message-typed fields into nested structure in the `Body` tree, resolving type references both within the file and across `import`ed `.proto` files.

#### Scenario: Field of a message type declared in the same file

- **WHEN** `message Order` has field `Address address = 3;` and `message Address` is declared in the same file
- **THEN** the field table SHALL show `address` with `Address`'s own fields as child rows

#### Scenario: Field of a message type from an imported file

- **WHEN** `message Order` has field `common.Money total = 4;` whose type is declared in an `import`ed `.proto` file
- **THEN** the field table SHALL show `total` expanded with `Money`'s fields

#### Scenario: Nested message declared inside the subject message

- **WHEN** `message Order` declares `message Item { string sku = 1; }` and has field `Item item = 5;`
- **THEN** the field table SHALL show `item` expanded with `sku`

#### Scenario: Unresolvable type reference

- **WHEN** a field references a message type that cannot be resolved (missing import)
- **THEN** that field SHALL be documented with type `Object` and a description noting the unresolved type name
- **AND** the rest of the document SHALL still be generated

### Requirement: Repeated, map and oneof handling

The system SHALL represent `repeated` fields as lists, `map<K,V>` fields as maps, and `oneof` members as individual optional fields.

#### Scenario: Repeated scalar field

- **WHEN** a message contains `repeated string tags = 1;`
- **THEN** `tags` SHALL be documented as a `List<String>`

#### Scenario: Repeated message field

- **WHEN** a message contains `repeated Item items = 2;`
- **THEN** `items` SHALL be documented as a `List<Item>` with `Item`'s fields as child rows

#### Scenario: Map field

- **WHEN** a message contains `map<string, int32> counters = 3;`
- **THEN** `counters` SHALL be documented as a `Map<String, Integer>`

#### Scenario: Oneof members

- **WHEN** a message contains `oneof payload { string text = 4; Item item = 5; }`
- **THEN** both `text` and `item` SHALL appear as fields of the message
- **AND** each description SHALL indicate that the field belongs to the `payload` oneof
- **AND** neither SHALL be marked required

#### Scenario: Required and optional labels

- **WHEN** a proto2 message contains a `required` field and an `optional` field
- **THEN** the `required` field SHALL be marked required and the `optional` field SHALL NOT be

### Requirement: Enum field documentation

The system SHALL document enum-typed fields as `String` and list the enum's allowed values in the field description.

#### Scenario: Enum field lists allowed values

- **WHEN** a message contains `Status status = 1;` and `enum Status { UNKNOWN = 0; PAID = 1; }`
- **THEN** `status` SHALL be documented as `String`
- **AND** its description SHALL list `UNKNOWN` with `0` and `PAID` with `1`

#### Scenario: Enum is not expanded as an object

- **WHEN** a message contains an enum-typed field
- **THEN** that field SHALL NOT be rendered as an empty nested object

### Requirement: Well-known type mapping

The system SHALL map `google.protobuf` well-known types to their JSON representations instead of expanding their internal fields.

#### Scenario: Timestamp and Duration

- **WHEN** a message contains a `google.protobuf.Timestamp` field and a `google.protobuf.Duration` field
- **THEN** both SHALL be documented as `String`
- **AND** neither SHALL be expanded into `seconds` / `nanos` child rows

#### Scenario: Wrapper types

- **WHEN** a message contains `google.protobuf.Int32Value`, `google.protobuf.StringValue` and `google.protobuf.BoolValue` fields
- **THEN** they SHALL be documented as `Integer`, `String` and `Boolean` respectively

#### Scenario: Struct, Value, Any and Empty

- **WHEN** a message contains a `google.protobuf.Struct`, `Value`, `Any` or `Empty` field
- **THEN** that field SHALL be documented as `Object`

### Requirement: Termination on recursive message graphs

The system SHALL terminate document generation for self-referential and mutually recursive message definitions.

#### Scenario: Self-referential message

- **WHEN** the user invokes Doc View on `message Node { string id = 1; repeated Node children = 2; }`
- **THEN** generation SHALL terminate and the preview SHALL open
- **AND** the IDE SHALL NOT hang or raise `StackOverflowError`

#### Scenario: Mutually recursive messages

- **WHEN** `message A` has a field of type `B` and `message B` has a field of type `A`
- **THEN** generation SHALL terminate and the preview SHALL open

#### Scenario: Deeply nested graph

- **WHEN** the reachable type graph exceeds the generator's maximum depth
- **THEN** types beyond that depth SHALL be documented as `Object` and generation SHALL complete

### Requirement: Doc Editor unavailable on proto files

The system SHALL hide the **Doc Editor** action for `.proto` files, because it writes documentation back into Java source as JavaDoc and the proto document is backed by a non-physical synthesized class.

#### Scenario: Doc Editor hidden in proto context menu

- **WHEN** the user right-clicks anywhere in a `.proto` file
- **THEN** the **Doc Editor** action SHALL NOT be enabled or visible

#### Scenario: No class synthesis during menu update

- **WHEN** the context menu is built for a `.proto` file
- **THEN** the system SHALL decide Doc Editor visibility without synthesizing a Java class from the proto file

#### Scenario: Doc Editor still available for Java

- **WHEN** the user right-clicks inside a Java POJO class
- **THEN** the **Doc Editor** action SHALL behave exactly as before this change

### Requirement: Service routing for synthesized proto classes

The system SHALL route a synthesized proto message class to the POJO documentation service regardless of the class name, and SHALL NOT treat it as a protobuf-generated Java class.

#### Scenario: Message name does not match POJO name heuristics

- **WHEN** the documented message is named `Order`, which matches none of the `dto`/`vo`/`bo`/`po`/`entity`/`model`/`bean` suffixes
- **THEN** the system SHALL still use the POJO documentation service
- **AND** SHALL NOT fall through to the Dubbo service or report `notify.error.not.support`

#### Scenario: Fields are not filtered by generated-class conventions

- **WHEN** the synthesized class has fields whose names do not end with `_`
- **THEN** all of those fields SHALL appear in the field table

#### Scenario: Real protobuf-generated Java classes unchanged

- **WHEN** the user invokes Doc View on an actual protobuf-generated Java class (one extending a `com.google.protobuf` base type)
- **THEN** the existing generated-class handling SHALL apply as before this change

### Requirement: Graceful behavior without the Protocol Buffers plugin

The system SHALL load and function for Java sources on IDEs where the Protocol Buffers plugin is not installed.

#### Scenario: Plugin absent

- **WHEN** Doc View is installed on an IDE without the Protocol Buffers plugin
- **THEN** the plugin SHALL load successfully and all Java/Spring/Dubbo/POJO features SHALL work
- **AND** no `NoClassDefFoundError` for `com.intellij.protobuf.*` SHALL occur

#### Scenario: Plugin present

- **WHEN** Doc View is installed on IntelliJ IDEA Ultimate with the bundled Protocol Buffers plugin
- **THEN** proto message documentation SHALL be available

### Requirement: Safe title extraction for protobuf-generated Java classes

The system SHALL not fail when extracting the `<pre>` section of a protobuf-generated class's title and that section is absent.

#### Scenario: Generated class title without a pre block

- **WHEN** Doc View runs on a protobuf-generated Java class whose JavaDoc title contains no `<pre>` block
- **THEN** the system SHALL use the title as-is
- **AND** SHALL NOT throw `StringIndexOutOfBoundsException`

#### Scenario: Generated class title with a pre block

- **WHEN** the title contains a `<pre>` block
- **THEN** the system SHALL extract the content of that block, as before this change
