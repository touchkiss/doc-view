## 1. Create CopyCurlAction class

- [x] 1.1 Create `CopyCurlAction.java` in `com.liuzhihang.doc.view.action`, extending `AbstractAction`
- [x] 1.2 Override `update()` to check that `targetMethod` has a REST mapping annotation from `SpringConstant.MAPPING_ANNOTATIONS` before making the action visible
- [x] 1.3 Override `actionPerformed()` to call `DocViewService.getInstance(project, targetClass).buildDoc(targetClass, targetMethod)`, take the first `DocView`, call `CurlUtils.build(docView)`, and copy the result to the system clipboard
- [x] 1.4 Show a balloon notification via `DocViewNotification.notifyInfo()` on success, or `DocViewNotification.notifyError()` if no DocView is returned

## 2. Register the action in plugin.xml

- [x] 2.1 Add a new `<action>` entry for `CopyCurlAction` inside the `liuzhihang.doc` group in `plugin.xml`, with id `liuzhihang.doc.copy.curl` and text "Copy cURL"

## 3. Add i18n messages

- [x] 3.1 Add a message key for the copy confirmation notification (e.g., `notify.copy.curl.success`) in `messages/DocViewBundle.properties`
- [x] 3.2 Add a message key for the "no documentation" error notification (e.g., `notify.copy.curl.empty`)

## 4. Verify

- [x] 4.1 Build the project with `./gradlew build` to confirm compilation
- [x] 4.2 Verify the action appears in the editor right-click menu on a REST method
- [x] 4.3 Verify the action is hidden on non-REST methods and outside controller classes
