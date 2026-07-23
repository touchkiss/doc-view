## Why

Doc View already generates rich REST API Markdown (path, headers, params, JSON examples), but consumers still have to hand-craft `curl` commands to try an endpoint. Adding a ready-made `curl` example directly in the interface description makes preview, export, and upload outputs immediately actionable for API testers and downstream doc readers.

## What Changes

- **Generate a `curl` command** for every Spring/Feign REST endpoint from the existing `DocView` model (HTTP method, path, headers, query params, request body/form).
- **Render it as a Markdown fenced code block** (```bash) and place it in the **接口描述** section of the generated Spring REST document, below the existing JavaDoc/description text.
- **Expose the value through `DocViewData`** as a new Velocity-accessible field so the default Spring template and user-customized templates can reference it.
- **Update the bundled default Spring template** (`template.spring.init`) to include the curl block in the description area.
- **Use `{{host}}` as the base URL placeholder**, matching the existing `.http` file export convention; no new settings UI in this change.
- **Scope to REST only** — Dubbo and POJO templates are unchanged.

## Capabilities

### New Capabilities
- `rest-doc-curl`: Automatic generation and Markdown rendering of a `curl` example for Spring/Feign REST endpoints, embedded in the interface description section of generated documentation.

### Modified Capabilities
<!-- No existing spec-level requirements cover REST doc body composition -->

## Impact

- **`dto/DocViewData.java`**: Add a `curlExample` field (pre-formatted Markdown code block) built in the constructor for Spring-type docs; add getter for Velocity.
- **`utils/CurlUtils.java`** (new): Pure static builder that turns `DocView` fields into a single-line or multi-line `curl` command string.
- **`resources/messages/DocViewBundle.properties`**: Update `template.spring.init` to append `${DocView.curlExample}` in the 接口描述 section.
- **`ui/TemplateSettingForm.java`**: "Reset to default" path already reads from bundle — no extra change beyond the bundle string.
- **No upload-adapter changes**: Upload services render Markdown via the same `DocViewData` → Velocity pipeline; they pick up the curl block automatically once the template changes.
- **No Dubbo/POJO impact**: `markdownText()` only applies the Spring template to `FrameworkEnum.SPRING` (and Feign, which shares the Spring impl).
