## 1. Curl Builder Utility

- [x] 1.1 Create `utils/CurlUtils.java` with `public static String build(DocView docView)`: return empty string for null/blank method or path; otherwise assemble `curl -X <METHOD> '<url>'` where URL is `{{host}}` + path with `{pathVar}` replaced by matching `Param` examples from `reqParamList`, plus `?key=value` for non-path query params.
- [x] 1.2 In `CurlUtils.build`, append `-H 'Name: Value'` for each entry in `docView.getHeaderList()` (use header example or empty value).
- [x] 1.3 For `POST`/`PUT`/`PATCH` with `ContentTypeEnum.JSON` and non-blank `reqBodyExample`, append `-H 'Content-Type: application/json'` and `-d '<body>'` with single-quote escaping for embedded apostrophes.
- [x] 1.4 For form content (`ContentTypeEnum` form or non-blank `reqFormExample`), append `-d` flags from form params / form example string.
- [x] 1.5 Use `\` line continuations when the command has more than two flags or exceeds ~120 characters, keeping each logical segment on its own line.

## 2. DocViewData Integration

- [x] 2.1 Add `private final String curlExample` field to `DocViewData` with Lombok-generated getter (`getCurlExample()` for Velocity `${DocView.curlExample}`).
- [x] 2.2 Add `private static String curlMarkdown(DocView docView)` that calls `CurlUtils.build(docView)` and wraps non-blank results in ` ```bash\n...\n```\n\n `.
- [x] 2.3 In the `DocViewData` constructor, set `this.curlExample` to `curlMarkdown(docView)` when `docView.getType() == FrameworkEnum.SPRING`, otherwise `""`.

## 3. Default Spring Template

- [x] 3.1 Update `template.spring.init` in `src/main/resources/messages/DocViewBundle.properties`: in the **接口描述** section, keep `> ${DocView.desc}` and add a blank line followed by `${DocView.curlExample}` before the **认证方式** section.
- [x] 3.2 Confirm `TemplateSettingForm` reset-to-default reads the updated bundle string (no code change expected).

## 4. Verification

- [x] 4.1 Add a minimal self-check under `src/test/java/` (repo convention: `main()` with assertions) covering: GET with query params, POST with JSON body, headers present, path-variable substitution, and empty output for a stub Dubbo `DocView`.
- [x] 4.2 `./gradlew build` compiles cleanly.
- [x] 4.3 `./gradlew runIde`: right-click a Spring controller method → Preview; confirm **接口描述** shows the JavaDoc text plus a `bash` curl code block with `{{host}}` and correct method/headers/body.
- [x] 4.4 Confirm a Dubbo service preview does not show a curl block.
- [x] 4.5 If a URL rewrite rule is configured, confirm the curl URL uses the rewritten path.
