## Why

Currently, to get a curl command for a Spring REST endpoint, users must preview the doc, locate the curl block in the Markdown, and manually copy it. This is cumbersome when you just want to quickly test an endpoint. Adding a "Copy cURL" right-click action directly on REST methods in the editor provides instant access.

## What Changes

- Add a new `AnAction` that appears in the editor right-click context menu on REST controller methods
- When invoked, build a curl command from the current method's `DocView` data and copy it to the system clipboard
- Show a balloon notification confirming the copy
- The action is only visible when the cursor is on a method with a REST mapping annotation (`@GetMapping`, `@PostMapping`, etc.)

## Capabilities

### New Capabilities
- `copy-curl-editor-action`: Editor right-click action that detects REST methods and copies their curl command to clipboard

### Modified Capabilities
- (none — the existing `CurlUtils.build()` and `DocViewData.curlMarkdown()` are reused, no requirement changes)

## Impact

- New Java class: `CopyCurlAction` in `com.liuzhihang.doc.view.action`
- New entry in `plugin.xml` under the `EditorPopupMenu` group
- Reuses existing `CurlUtils.build()` — no new curl generation logic needed
- No API changes, no new dependencies
