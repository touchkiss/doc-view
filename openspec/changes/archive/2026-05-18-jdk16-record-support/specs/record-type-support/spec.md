## ADDED Requirements

### Requirement: Record class recognized as documentable type
The system SHALL treat any `PsiClass` where `isRecord()` returns `true` as a documentable POJO-style class, returning `true` from `PojoUtils.isPojoClass()` regardless of class name or annotation presence.

#### Scenario: Plain record class is recognized
- **WHEN** `PojoUtils.isPojoClass()` is called with a record class (e.g., `record Point(int x, int y)`)
- **THEN** the method returns `true`

#### Scenario: Record name does not match POJO suffixes
- **WHEN** `PojoUtils.isPojoClass()` is called with a record whose name ends in none of the heuristic suffixes (`dto`, `vo`, `bo`, `po`, `entity`, `model`, `bean`)
- **THEN** the method still returns `true`

---

### Requirement: Record class routed to PojoDocViewServiceImpl
The system SHALL route record classes to `PojoDocViewServiceImpl` in `DocViewService.getInstance()` before the existing POJO name-heuristic check.

#### Scenario: Record class selected in DocViewService
- **WHEN** `DocViewService.getInstance()` is called with a record class that is not a Spring, Dubbo, or Feign class
- **THEN** it returns `PojoDocViewServiceImpl` without throwing `DocViewException`

---

### Requirement: Record components used for field extraction on direct record targets
The system SHALL iterate over `PsiRecordComponent[]` (via `psiClass.getRecordComponents()`) rather than `PsiField[]` (via `getAllFields()`) when building the `Body` tree for a record class target in `PojoUtils.buildBody()`.

#### Scenario: Record component names appear in generated Body
- **WHEN** `PojoUtils.buildBody()` is called on `record Person(String name, int age)`
- **THEN** the resulting `Body` tree contains child nodes with names `name` and `age`

#### Scenario: Record component descriptions come from component-level JavaDoc
- **WHEN** a record component has a JavaDoc comment (e.g., `/** The person's full name */`)
- **THEN** the corresponding `Body` node's `desc` field contains that comment text

#### Scenario: Record component description from @Schema annotation
- **WHEN** a record component is annotated with `@Schema(description = "User identifier")`
- **THEN** the corresponding `Body` node's `desc` field equals `"User identifier"`

#### Scenario: Record component description from @ApiModelProperty annotation
- **WHEN** a record component is annotated with `@ApiModelProperty(value = "User identifier")`
- **THEN** the corresponding `Body` node's `desc` field equals `"User identifier"`

---

### Requirement: Record component required flag extraction
The system SHALL extract the `required` flag from a `PsiRecordComponent` using the same annotation priority as for `PsiField`: configured required annotations → `@Schema(required=true)` → `@ApiModelProperty(required=true)` → JavaDoc required tag.

#### Scenario: Record component marked required via @Schema
- **WHEN** a record component is annotated with `@Schema(required = true)`
- **THEN** the corresponding `Body` node's `required` field is `true`

#### Scenario: Record component without required annotation is not required
- **WHEN** a record component has no required annotation
- **THEN** the corresponding `Body` node's `required` field is `false`

---

### Requirement: Record component field name extraction
The system SHALL extract the field name from a `PsiRecordComponent` using the same priority as for `PsiField`: if `@JsonProperty` is present and the feature is enabled in settings, use its value; otherwise use the component name, applying snake_case conversion if configured.

#### Scenario: Record component name used as field name
- **WHEN** a record component is named `userId` and no `@JsonProperty` override is configured
- **THEN** the corresponding `Body` node's `name` field equals `"userId"`

#### Scenario: @JsonProperty overrides record component name
- **WHEN** a record component is annotated with `@JsonProperty("user_id")` and the JSON property feature is enabled
- **THEN** the corresponding `Body` node's `name` field equals `"user_id"`

---

### Requirement: Nested record type resolved in Spring/Dubbo interface field traversal
The system SHALL resolve a `PsiClass` that is a record when encountered as a field type during `ParamPsiUtils.buildBodyList()` or `getFieldsAndDefaultValue()`, iterating the record's components rather than its backing fields.

#### Scenario: Record used as request body parameter in Spring controller
- **WHEN** a Spring controller method accepts a record class as `@RequestBody`
- **THEN** the generated doc contains the record's component names and descriptions

#### Scenario: Record used as return type in Spring controller
- **WHEN** a Spring controller method returns a record class
- **THEN** the generated response body table contains the record's component names and descriptions

#### Scenario: Record nested inside a regular class field
- **WHEN** a regular class has a field whose type is a record (e.g., `private Address address;` where `Address` is a record)
- **THEN** the generated doc expands the record's components as child rows of that field

#### Scenario: List of records used as parameter type
- **WHEN** a method parameter type is `List<SomeRecord>` and `SomeRecord` is a record
- **THEN** the generated doc shows the record's components as the element's fields

---

### Requirement: Record class displayed in tool window
The system SHALL include record classes in the tool window class tree alongside regular Spring, Dubbo, and POJO classes, showing the record as a leaf node with one generated documentation entry.

#### Scenario: Record class appears in tool window tree
- **WHEN** the Doc View tool window is refreshed in a project containing a record class
- **THEN** the record class appears in the tree under its module

#### Scenario: Selecting record in tool window shows generated markdown
- **WHEN** the user selects a record class node in the tool window
- **THEN** the preview panel shows the Markdown documentation for that record
