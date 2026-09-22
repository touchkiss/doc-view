# Task 3 Report

## Status

Complete.

## Tests

```text
./gradlew test --tests 'com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolverTest' --tests 'com.liuzhihang.doc.view.mcp.YApiUploadOrchestratorTest' --tests 'com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImplTest'
```

Result: `BUILD SUCCESSFUL`; 14 targeted tests completed.

## Commit

`21db2e79f31ee2314b33a19d56e684082322ef78` — `feat: add reusable yapi upsert workflow`

## Concerns

- Gradle emits pre-existing IntelliJ form-binding messages and Gradle deprecation notices during instrumentation; the targeted test task still completed successfully.
- The worktree contains unrelated pre-existing changes. They were neither staged nor modified by Task 3.

## Review fixes (2026-09-22)

- Extracted `YapiSaveFactory` as the UI-independent `DocView` → `YapiSave` mapper. `YApiServiceImpl` and `YApiUploadOrchestrator` now both depend on it; mapping coverage verifies HTTP fields, schema, Markdown/HTML, headers, and query parameters.
- Separated DTO/document conversion errors (`DOC_GENERATION_FAILED`) from remote request and invalid-response failures (`YAPI_REQUEST_FAILED` / `YAPI_RESPONSE_INVALID`). Per-item messages retain a bounded root-cause summary and mask configured/query/JSON tokens.
- Added typed facade failures and injected requester coverage for category creation; UI upload notification and logs now retain the returned failure summary.
- Added the update-detail URL branch test, confirming an existing interface ID resolves directly without a second lookup.

## Verification

```text
./gradlew test --rerun-tasks --tests 'com.liuzhihang.doc.view.mcp.YApiUploadOrchestratorTest' --tests 'com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImplTest' --tests 'com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolverTest'
```

Result: `BUILD SUCCESSFUL` (2026-09-22).

## Final review fixes (2026-09-22)

- After `save` succeeds, a best-effort detail URL lookup failure now preserves the `created` or `updated` result and returns the category URL. The resolver records a diagnostic warning after masking configured, query-string, and JSON token values.
- `addCat` now rejects a response whose `data` is absent or whose category `_id` is null, zero, or negative as `YAPI_RESPONSE_INVALID` instead of allowing a later null-related failure.
- Added regression coverage for successful save followed by failed detail lookup, and for a category response without an ID.

## Final verification

```text
./gradlew test --rerun-tasks --tests 'com.liuzhihang.doc.view.mcp.YApiUploadOrchestratorTest' --tests 'com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImplTest' --tests 'com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolverTest'
```

Result: `BUILD SUCCESSFUL`; 25 targeted tests completed (2026-09-22).
