# JsonDeserialize `contentUsing` Collection Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `List<Long>` annotated with `@JsonDeserialize(contentUsing = StringToLongDeserializer.class)` render as a collection of JSON strings with example `["0"]`.

**Architecture:** Keep Jackson annotation/type inference centralized in `JacksonPsiUtils.resolveContentUsing`. Fix the two `ParamPsiUtils` collection consumers so a simple overridden element type is represented on the collection itself (`List<String>`) rather than as a synthetic object child; existing JSON and YApi renderers can then infer string array items correctly.

**Tech Stack:** Java 25, IntelliJ Platform PSI, Gradle 9.6.1, JUnit 4.13.2.

## Global Constraints

- Preserve `@JsonSerialize(contentUsing = ...)` priority over `@JsonDeserialize(contentUsing = ...)`.
- Unknown or unresolvable deserializers must retain the declared collection element type.
- Do not change `@JsonProperty` naming behavior.
- Do not extend array, Map key, or Map value semantics.
- Do not add synthetic child nodes for simple collection elements.

---

## File Structure

- Modify `build.gradle`: add the JUnit 4 test dependency used by the new automated regression test.
- Modify `src/main/java/com/liuzhihang/doc/view/utils/ParamPsiUtils.java`: centralize application of an overridden simple collection element type and use it for fields and record components.
- Create `src/test/java/com/liuzhihang/doc/view/utils/ParamPsiUtilsCollectionContentTest.java`: verify collection type, example, child shape, and fallback behavior without requiring an IDE fixture.

### Task 1: Represent overridden simple collection elements on the collection node

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/java/com/liuzhihang/doc/view/utils/ParamPsiUtils.java:181-200`
- Modify: `src/main/java/com/liuzhihang/doc/view/utils/ParamPsiUtils.java:901-918`
- Test: `src/test/java/com/liuzhihang/doc/view/utils/ParamPsiUtilsCollectionContentTest.java`

**Interfaces:**
- Consumes: `JsonWireType.isOverridden()`, `JsonWireType.getJsonType()`, and `JsonWireType.getExampleOverride()`.
- Produces: package-private `ParamPsiUtils.applyCollectionContentOverride(Body body, JsonWireType contentWireType)` for both field and record collection branches.
- Produces: a `Body` with `type = "List<String>"`, `example = "0"`, `isCollection = true`, and no synthetic `element` child.

- [ ] **Step 1: Add JUnit and write the failing regression test**

Add this dependency next to the existing test/implementation dependencies in `build.gradle`:

```groovy
testImplementation group: 'junit', name: 'junit', version: '4.13.2'
```

Create `src/test/java/com/liuzhihang/doc/view/utils/ParamPsiUtilsCollectionContentTest.java`:

```java
package com.liuzhihang.doc.view.utils;

import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.JsonWireType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ParamPsiUtilsCollectionContentTest {

    @Test
    public void appliesStringContentOverrideWithoutCreatingObjectChild() {
        Body body = new Body();
        body.setType("List<Long>");
        body.setCollection(true);

        ParamPsiUtils.applyCollectionContentOverride(
                body,
                JsonWireType.overridden("String", "0", "0")
        );

        assertEquals("List<String>", body.getType());
        assertEquals("0", body.getExample());
        assertTrue(body.isCollection());
        assertTrue(body.getChildList().isEmpty());
    }

    @Test
    public void leavesCollectionUntouchedWhenContentTypeIsNotOverridden() {
        Body body = new Body();
        body.setType("List<Long>");
        body.setCollection(true);

        ParamPsiUtils.applyCollectionContentOverride(
                body,
                JsonWireType.ofJavaType("Long")
        );

        assertEquals("List<Long>", body.getType());
        assertNull(body.getExample());
        assertTrue(body.getChildList().isEmpty());
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew test --tests com.liuzhihang.doc.view.utils.ParamPsiUtilsCollectionContentTest
```

Expected: compilation fails because `ParamPsiUtils.applyCollectionContentOverride(Body, JsonWireType)` does not exist.

- [ ] **Step 3: Add the minimal collection override helper**

Add this package-private method near `isJsonSimpleType` in `ParamPsiUtils.java`:

```java
static void applyCollectionContentOverride(@NotNull Body body, @NotNull JsonWireType contentWireType) {
    if (!contentWireType.isOverridden()) {
        return;
    }

    String collectionType = body.getType();
    int genericStart = collectionType == null ? -1 : collectionType.indexOf('<');
    int genericEnd = collectionType == null ? -1 : collectionType.lastIndexOf('>');
    if (genericStart >= 0 && genericEnd > genericStart) {
        body.setType(collectionType.substring(0, genericStart + 1)
                + contentWireType.getJsonType()
                + collectionType.substring(genericEnd));
    }

    String example = contentWireType.getExampleOverride() != null
            ? contentWireType.getExampleOverride() : "0";
    body.setExample(example);
}
```

- [ ] **Step 4: Use the helper for ordinary DTO fields**

Replace the current synthetic `element` child block in the simple collection branch of `buildBodyParam` with:

```java
if (contentWireType.isOverridden()) {
    applyCollectionContentOverride(body, contentWireType);
    return;
}
```

Keep the existing non-overridden primitive/wrapper default example block unchanged.

- [ ] **Step 5: Use the helper for record components**

Replace the equivalent synthetic `element` child block in `buildBodyParamFromComponent` with:

```java
if (contentWireType.isOverridden()) {
    applyCollectionContentOverride(body, contentWireType);
    return;
}
```

This makes ordinary fields and record components share the same representation.

- [ ] **Step 6: Run the focused test and verify GREEN**

Run:

```bash
./gradlew test --tests com.liuzhihang.doc.view.utils.ParamPsiUtilsCollectionContentTest
```

Expected: two tests pass with zero failures.

- [ ] **Step 7: Commit the behavior change**

```bash
git add build.gradle src/main/java/com/liuzhihang/doc/view/utils/ParamPsiUtils.java src/test/java/com/liuzhihang/doc/view/utils/ParamPsiUtilsCollectionContentTest.java
git commit -m "fix: support JsonDeserialize contentUsing collections"
```

### Task 2: Verify rendering assumptions and project integrity

**Files:**
- Verify: `src/main/java/com/liuzhihang/doc/view/dto/DocViewData.java:184-221`
- Verify: `src/main/java/com/liuzhihang/doc/view/service/impl/YApiServiceImpl.java:261-304`
- Verify: all modified files

**Interfaces:**
- Consumes: collection `Body.type = "List<String>"`, `Body.example = "0"`, empty `Body.childList`.
- Verifies: JSON renderer quotes the string item and YApi schema reports array items as `string` through their existing element-type extraction.

- [ ] **Step 1: Confirm the renderers consume the new representation**

Inspect the existing branches and verify these exact paths remain unchanged:

```text
DocViewData: collection + empty childList -> extractCollectionItemType("List<String>") -> quoted example -> ["0"]
YApiServiceImpl: collection + empty childList -> extractCollectionItemType("List<String>") -> schema item type "string"
```

- [ ] **Step 2: Run all tests**

Run:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` and zero failed tests.

- [ ] **Step 3: Run the plugin build**

Run:

```bash
./gradlew buildPlugin
```

Expected: `BUILD SUCCESSFUL` with a plugin archive produced under `build/distributions/`.

- [ ] **Step 4: Check the final diff**

Run:

```bash
git diff --check HEAD~1 HEAD
git status --short
```

Expected: no whitespace errors; only pre-existing unrelated untracked files remain.
