# YApi Interface Detail URL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the YApi upload-success notification open the uploaded interface detail page, while retaining the current category-page URL as a safe fallback.

**Architecture:** Extend the YApi integration facade with a paginated `/api/interface/list` lookup that identifies an interface by category ID, HTTP method, and path. Add a small URL resolver that turns the lookup result into a detail URL or catches lookup failures and returns the category URL; `YApiServiceImpl` invokes it only after a successful save.

**Tech Stack:** Java 25, Gradle, Gson, Apache HttpClient, JUnit 4, IntelliJ Platform SDK.

## Global Constraints

- Query `/api/interface/list` with `project_id`, `token`, `page`, and `limit=100`.
- Match `catid`, `method`, and `path`; method comparison is case-insensitive and path comparison is exact.
- Use the final route stored in `YapiSave`, including the transformed Dubbo route `POST /Dubbo/{methodName}`.
- Stop after a page containing fewer than 100 entries.
- A lookup miss or lookup exception must not turn a successful upload into a failed upload; fall back to the existing category URL.
- Do not change ShowDoc, YuQue, YApi save behavior, or batch notification behavior.

---

## File Structure

- Create `src/main/java/com/liuzhihang/doc/view/integration/dto/YApiInterfaceSummary.java`: minimal mapping of interface-list records.
- Modify `src/main/java/com/liuzhihang/doc/view/integration/YApiFacadeService.java`: expose the interface-ID lookup contract.
- Modify `src/main/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImpl.java`: perform and parse paginated list requests.
- Create `src/test/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImplTest.java`: verify matching, pagination, termination, and response validation through an injected GET function.
- Create `src/main/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolver.java`: isolate detail-URL construction and category fallback.
- Modify `src/main/java/com/liuzhihang/doc/view/service/impl/YApiServiceImpl.java`: resolve the notification link from the saved request.
- Create `src/test/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolverTest.java`: verify detail, miss, failure, and final-route behavior.

### Task 1: Paginated YApi Interface Lookup

**Files:**
- Create: `src/main/java/com/liuzhihang/doc/view/integration/dto/YApiInterfaceSummary.java`
- Modify: `src/main/java/com/liuzhihang/doc/view/integration/YApiFacadeService.java:1-44`
- Modify: `src/main/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImpl.java:1-83`
- Test: `src/test/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImplTest.java`

**Interfaces:**
- Consumes: `HttpUtils.get(String)` and `YApiResponse<T>`.
- Produces: `Optional<Long> YApiFacadeService.findInterfaceId(String yapiUrl, Long projectId, String token, Long catId, String method, String path) throws Exception`.

- [ ] **Step 1: Write the failing first-page lookup test**

Create `YApiFacadeServiceImplTest` with a package-local injected requester so the test exercises URL construction and JSON parsing without external network access:

```java
package com.liuzhihang.doc.view.integration.impl;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YApiFacadeServiceImplTest {

    @Test
    public void findsMatchingInterfaceOnFirstPage() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            assertTrue(url.contains("project_id=299"));
            assertTrue(url.contains("token=token-value"));
            assertTrue(url.contains("page=1"));
            assertTrue(url.contains("limit=100"));
            return "{\"errcode\":0,\"errmsg\":\"成功\",\"data\":["
                    + "{\"_id\":4396,\"catid\":1376,\"path\":\"/api/group/list\",\"method\":\"GET\"}]}";
        });

        Optional<Long> result = service.findInterfaceId(
                "http://yapi.example", 299L, "token-value", 1376L, "get", "/api/group/list");

        assertEquals(Optional.of(4396L), result);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew test --tests com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImplTest
```

Expected: compilation fails because the requester constructor and `findInterfaceId` do not exist.

- [ ] **Step 3: Add the minimal DTO and facade contract**

Create the DTO:

```java
package com.liuzhihang.doc.view.integration.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class YApiInterfaceSummary {
    @SerializedName("_id")
    private Long id;
    @SerializedName("catid")
    private Long catId;
    private String path;
    private String method;
}
```

Add to `YApiFacadeService`:

```java
import java.util.Optional;

Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                               Long catId, String method, String path) throws Exception;
```

- [ ] **Step 4: Implement one-page lookup with an injectable GET function**

Add the requester boundary and preserve the public no-argument constructor required by IntelliJ service creation:

```java
@FunctionalInterface
interface GetRequester {
    String get(String url) throws Exception;
}

private static final int INTERFACE_PAGE_SIZE = 100;
private final GetRequester getRequester;

public YApiFacadeServiceImpl() {
    this(HttpUtils::get);
}

YApiFacadeServiceImpl(GetRequester getRequester) {
    this.getRequester = getRequester;
}
```

Replace the direct `HttpUtils.get(url)` in `getCatMenu` with `getRequester.get(url)`, then add the initial lookup:

```java
@Override
public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                      Long catId, String method, String path) throws Exception {
    String url = yapiUrl + "/api/interface/list"
            + "?project_id=" + projectId
            + "&token=" + token
            + "&page=1"
            + "&limit=" + INTERFACE_PAGE_SIZE;
    String resp = getRequester.get(url);
    List<YApiInterfaceSummary> interfaces = parseInterfaceList(resp);
    return interfaces.stream()
            .filter(item -> catId.equals(item.getCatId()))
            .filter(item -> method.equalsIgnoreCase(item.getMethod()))
            .filter(item -> path.equals(item.getPath()))
            .map(YApiInterfaceSummary::getId)
            .filter(Objects::nonNull)
            .findFirst();
}

private List<YApiInterfaceSummary> parseInterfaceList(String resp) throws Exception {
    if (StringUtils.isBlank(resp)) {
        throw new Exception("YApi 接口返回为空");
    }
    Type type = new TypeToken<YApiResponse<List<YApiInterfaceSummary>>>() { }.getType();
    YApiResponse<List<YApiInterfaceSummary>> response = gson.fromJson(resp, type);
    if (response == null || response.getErrcode() == null || response.getErrcode() != 0) {
        throw new Exception("YApi 接口返回失败:" + resp);
    }
    return response.getData() == null ? List.of() : response.getData();
}
```

Add imports for `YApiInterfaceSummary`, `Objects`, and `Optional`.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: one test passes.

- [ ] **Step 6: Add failing pagination, exact-match, and termination tests**

Add these tests and helpers to `YApiFacadeServiceImplTest`:

```java
@Test
public void continuesToNextPageUntilMatchIsFound() throws Exception {
    List<String> requestedUrls = new ArrayList<>();
    YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
        requestedUrls.add(url);
        return url.contains("page=1") ? fullNonMatchingPage() : matchingPage();
    });

    assertEquals(Optional.of(4396L), service.findInterfaceId(
            "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
    assertEquals(2, requestedUrls.size());
    assertTrue(requestedUrls.get(1).contains("page=2"));
}

@Test
public void requiresCategoryMethodAndPathToMatch() throws Exception {
    String data = "{\"errcode\":0,\"data\":["
            + "{\"_id\":1,\"catid\":999,\"path\":\"/target\",\"method\":\"GET\"},"
            + "{\"_id\":2,\"catid\":1376,\"path\":\"/other\",\"method\":\"GET\"},"
            + "{\"_id\":3,\"catid\":1376,\"path\":\"/target\",\"method\":\"POST\"}]}";
    YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> data);

    assertEquals(Optional.empty(), service.findInterfaceId(
            "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
}

@Test
public void stopsAfterShortPageWithoutMatch() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
        requests.incrementAndGet();
        return "{\"errcode\":0,\"data\":[]}";
    });

    assertEquals(Optional.empty(), service.findInterfaceId(
            "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
    assertEquals(1, requests.get());
}

@Test(expected = Exception.class)
public void rejectsYApiErrorResponse() throws Exception {
    YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(
            url -> "{\"errcode\":400,\"errmsg\":\"failed\"}");
    service.findInterfaceId("http://yapi.example", 299L, "token", 1376L, "GET", "/target");
}

private static String fullNonMatchingPage() {
    String entries = LongStream.range(0, 100)
            .mapToObj(id -> "{\"_id\":" + id
                    + ",\"catid\":1376,\"path\":\"/other/" + id
                    + "\",\"method\":\"GET\"}")
            .collect(Collectors.joining(","));
    return "{\"errcode\":0,\"data\":[" + entries + "]}";
}

private static String matchingPage() {
    return "{\"errcode\":0,\"data\":["
            + "{\"_id\":4396,\"catid\":1376,\"path\":\"/target\",\"method\":\"GET\"}]}";
}
```

Add imports for `ArrayList`, `List`, `AtomicInteger`, `Collectors`, and `LongStream`.

- [ ] **Step 7: Run the focused test and verify RED**

Run the command from Step 2. Expected: `continuesToNextPageUntilMatchIsFound` fails because only page 1 is queried.

- [ ] **Step 8: Generalize the lookup to page until a short page**

Replace the one-page body with:

```java
for (int page = 1; ; page++) {
    String url = yapiUrl + "/api/interface/list"
            + "?project_id=" + projectId
            + "&token=" + token
            + "&page=" + page
            + "&limit=" + INTERFACE_PAGE_SIZE;
    List<YApiInterfaceSummary> interfaces = parseInterfaceList(getRequester.get(url));
    Optional<Long> match = interfaces.stream()
            .filter(item -> catId.equals(item.getCatId()))
            .filter(item -> item.getMethod() != null && method.equalsIgnoreCase(item.getMethod()))
            .filter(item -> path.equals(item.getPath()))
            .map(YApiInterfaceSummary::getId)
            .filter(Objects::nonNull)
            .findFirst();
    if (match.isPresent()) {
        return match;
    }
    if (interfaces.size() < INTERFACE_PAGE_SIZE) {
        return Optional.empty();
    }
}
```

- [ ] **Step 9: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: all five tests pass.

- [ ] **Step 10: Commit the paginated lookup**

```bash
git add src/main/java/com/liuzhihang/doc/view/integration/YApiFacadeService.java \
  src/main/java/com/liuzhihang/doc/view/integration/dto/YApiInterfaceSummary.java \
  src/main/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImpl.java \
  src/test/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImplTest.java
git commit -m "feat: find uploaded YApi interface id"
```

### Task 2: Resolve and Use the Interface Detail URL

**Files:**
- Create: `src/main/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolver.java`
- Modify: `src/main/java/com/liuzhihang/doc/view/service/impl/YApiServiceImpl.java:108-112`
- Test: `src/test/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolverTest.java`

**Interfaces:**
- Consumes: `YApiFacadeService.findInterfaceId(...)` from Task 1 and the final route fields of `YapiSave`.
- Produces: `static String YApiInterfaceUrlResolver.resolve(YApiFacadeService facadeService, YapiSave save)`.

- [ ] **Step 1: Write failing resolver tests**

Create a test with a small facade stub:

```java
package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class YApiInterfaceUrlResolverTest {

    @Test
    public void buildsDetailUrlFromFoundInterfaceId() {
        YapiSave save = save("POST", "/Dubbo/getOrder");
        StubFacade facade = new StubFacade(Optional.of(4396L), null);

        String url = YApiInterfaceUrlResolver.resolve(facade, save);

        assertEquals("http://yapi.example/project/299/interface/api/4396", url);
        assertEquals("POST", facade.method);
        assertEquals("/Dubbo/getOrder", facade.path);
    }

    @Test
    public void fallsBackToCategoryUrlWhenInterfaceIsNotFound() {
        String url = YApiInterfaceUrlResolver.resolve(
                new StubFacade(Optional.empty(), null), save("GET", "/orders"));
        assertEquals("http://yapi.example/project/299/interface/api/cat_1376", url);
    }

    @Test
    public void fallsBackToCategoryUrlWhenLookupFails() {
        String url = YApiInterfaceUrlResolver.resolve(
                new StubFacade(Optional.empty(), new Exception("network")), save("GET", "/orders"));
        assertEquals("http://yapi.example/project/299/interface/api/cat_1376", url);
    }

    private static YapiSave save(String method, String path) {
        YapiSave save = new YapiSave();
        save.setYapiUrl("http://yapi.example");
        save.setProjectId(299L);
        save.setToken("token");
        save.setCatId(1376L);
        save.setMethod(method);
        save.setPath(path);
        return save;
    }

    private static final class StubFacade implements YApiFacadeService {
        private final Optional<Long> result;
        private final Exception failure;
        private String method;
        private String path;

        private StubFacade(Optional<Long> result, Exception failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                              Long catId, String method, String path) throws Exception {
            this.method = method;
            this.path = path;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override public void save(YapiSave dto) { throw new UnsupportedOperationException(); }
        @Override public List<YApiCat> getCatMenu(String url, Long projectId, String token) {
            throw new UnsupportedOperationException();
        }
        @Override public YApiCat addCat(YApiCat cat) { throw new UnsupportedOperationException(); }
    }
}
```

- [ ] **Step 2: Run the resolver test and verify RED**

Run:

```bash
./gradlew test --tests com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolverTest
```

Expected: compilation fails because `YApiInterfaceUrlResolver` does not exist.

- [ ] **Step 3: Implement the resolver**

Create:

```java
package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
final class YApiInterfaceUrlResolver {

    private YApiInterfaceUrlResolver() {
    }

    static String resolve(YApiFacadeService facadeService, YapiSave save) {
        String baseUrl = save.getYapiUrl() + "/project/" + save.getProjectId() + "/interface/api/";
        String categoryUrl = baseUrl + "cat_" + save.getCatId();
        try {
            Optional<Long> interfaceId = facadeService.findInterfaceId(
                    save.getYapiUrl(), save.getProjectId(), save.getToken(),
                    save.getCatId(), save.getMethod(), save.getPath());
            return interfaceId.map(id -> baseUrl + id).orElse(categoryUrl);
        } catch (Exception e) {
            log.warn("查询已上传的 YApi 接口 ID 失败，回退到分类地址: method={}, path={}",
                    save.getMethod(), save.getPath(), e);
            return categoryUrl;
        }
    }
}
```

- [ ] **Step 4: Run the resolver test and verify GREEN**

Run the command from Step 2. Expected: all three tests pass.

- [ ] **Step 5: Wire the resolver into the upload-success notification**

In `YApiServiceImpl`, replace the category URL construction after `facadeService.save(save)` with:

```java
String yapiInterfaceUrl = YApiInterfaceUrlResolver.resolve(facadeService, save);
DocViewNotification.uploadSuccess(project, "YApi", yapiInterfaceUrl);
```

The resolver receives `save.getMethod()` and `save.getPath()`, so the existing Dubbo transformation at lines 79-82 is used for lookup without duplicate route logic.

- [ ] **Step 6: Run focused and full verification**

Run:

```bash
./gradlew test --tests com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImplTest \
  --tests com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolverTest
./gradlew test
```

Expected: both focused test classes pass, then the complete test task exits with code 0 and no failed tests.

- [ ] **Step 7: Inspect the final diff and commit**

```bash
git diff --check
git diff -- src/main/java/com/liuzhihang/doc/view/integration \
  src/main/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolver.java \
  src/main/java/com/liuzhihang/doc/view/service/impl/YApiServiceImpl.java \
  src/test/java/com/liuzhihang/doc/view/integration \
  src/test/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolverTest.java
git add src/main/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolver.java \
  src/main/java/com/liuzhihang/doc/view/service/impl/YApiServiceImpl.java \
  src/test/java/com/liuzhihang/doc/view/service/impl/YApiInterfaceUrlResolverTest.java
git commit -m "fix: link YApi upload notification to interface"
```

Do not stage unrelated pre-existing files or changes.
