# GKD FastQuery and Cache Reconstruction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement robust support for GKD `quickFind` (fastQuery) selectors and native connected selectors by reconstructing the node hierarchy cache natively inside `Transform` and `SelectToSpeakService`.

**Architecture:** We will implement the `traverseFastQueryDescendants` interface inside the Accessibility `Transform` and add a `cacheAncestors` method to `NodeCache`. When nodes are located natively, their complete parent, child, index, and depth lineages will be traced and cached in `NodeCache`, allowing connected selectors (e.g. `A > B`) to evaluate perfectly.

**Tech Stack:** Kotlin, Android Accessibility Service, GKD-kit Selector library, JUnit 4, Mockito.

---

### Task 1: Add Mockito Test Dependencies

**Files:**
- Modify: [app/build.gradle.kts](file:///Users/xera/GitHub/x-clicker/app/build.gradle.kts)

- [ ] **Step 1: Write minimal implementation to add dependencies**

Add the Mockito dependencies to the `dependencies` block of `app/build.gradle.kts`.

```kotlin
    // In app/build.gradle.kts inside dependencies block
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
```

- [ ] **Step 2: Verify the build and gradle sync**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew testClasses`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "test: add mockito dependencies for unit testing accessibility components"
```

---

### Task 2: Write Failing Unit Test for NodeCache Rebuilding

**Files:**
- Create: `app/src/test/java/dev/xera/xclicker/TestFastQuery.kt`

- [ ] **Step 1: Write the failing test**

Create the `TestFastQuery.kt` file containing a test that exercises `cacheAncestors` but fails because `cacheAncestors` is not yet defined.

```kotlin
package dev.xera.xclicker

import android.view.accessibility.AccessibilityNodeInfo
import dev.xera.xclicker.service.selector.NodeCache
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TestFastQuery {
    @Test
    fun testCacheAncestors() {
        val rootNode = mock<AccessibilityNodeInfo>()
        val parentNode = mock<AccessibilityNodeInfo>()
        val targetNode = mock<AccessibilityNodeInfo>()

        // Setup parent hierarchy
        whenever(targetNode.parent).thenReturn(parentNode)
        whenever(parentNode.parent).thenReturn(rootNode)
        whenever(rootNode.parent).thenReturn(null)

        // Setup parent's child relation
        whenever(parentNode.childCount).thenReturn(1)
        whenever(parentNode.getChild(0)).thenReturn(targetNode)

        // Setup root's child relation
        whenever(rootNode.childCount).thenReturn(1)
        whenever(rootNode.getChild(0)).thenReturn(parentNode)

        val nodeCache = NodeCache()
        // Call cacheAncestors (fails to compile/run because it's not implemented yet)
        nodeCache.cacheAncestors(targetNode, rootNode)

        // Assert caches are reconstructed
        Assert.assertEquals(parentNode, nodeCache.getParent(targetNode))
        Assert.assertEquals(rootNode, nodeCache.getParent(parentNode))
        Assert.assertEquals(0, nodeCache.getIndex(targetNode))
        Assert.assertEquals(0, nodeCache.getIndex(parentNode))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestFastQuery"`
Expected: FAIL / Compilation Error (cacheAncestors unresolved reference)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/dev/xera/xclicker/TestFastQuery.kt
git commit -m "test: add failing unit test for cacheAncestors"
```

---

### Task 3: Implement `cacheAncestors` and FastQuery Transform

**Files:**
- Modify: [app/src/main/java/dev/xera/xclicker/service/selector/AndroidNodeTransform.kt](file:///Users/xera/GitHub/x-clicker/app/src/main/java/dev/xera/xclicker/service/selector/AndroidNodeTransform.kt)

- [ ] **Step 1: Implement `cacheAncestors` inside `NodeCache`**

Add `cacheAncestors` to `NodeCache` in `AndroidNodeTransform.kt`.

```kotlin
    // In NodeCache inside dev/xera/xclicker/service/selector/AndroidNodeTransform.kt
    fun cacheAncestors(startNode: AccessibilityNodeInfo, limitNode: AccessibilityNodeInfo?) {
        var curr = startNode
        var depthLimit = 15
        while (curr != limitNode && depthLimit-- > 0) {
            val p = curr.parent ?: break
            parentCache[curr] = p
            for (i in 0 until p.childCount) {
                val child = p.getChild(i)
                if (child == curr) {
                    childCache[Pair(p, i)] = curr
                    indexCache[curr] = i
                    break
                } else {
                    try { child?.recycle() } catch (_: Exception) {}
                }
            }
            curr = p
        }
    }
```

- [ ] **Step 2: Update `createTransformWithFastQuery` to include `packageName` and `traverseFastQueryDescendants`**

```kotlin
import li.songe.selector.FastQuery

fun createTransformWithFastQuery(
    nodeCache: NodeCache,
    packageName: String,
    getRoot: () -> AccessibilityNodeInfo?
): Transform<AccessibilityNodeInfo> {
    return Transform(
        getAttr = androidNodeTransform.getAttr,
        getInvoke = androidNodeTransform.getInvoke,
        getName = androidNodeTransform.getName,
        getChildren = { node ->
            nodeCache.getChildren(node)
        },
        getParent = { node ->
            nodeCache.getParent(node)
        },
        traverseFastQueryDescendants = { node, fastQueryList ->
            sequence {
                for (fq in fastQueryList) {
                    val nativeNodes = when (fq) {
                        is FastQuery.Id -> {
                            val id = fq.value
                            try { node.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { emptyList() }
                        }
                        is FastQuery.Vid -> {
                            val vid = fq.value
                            val idToFind = if (vid.contains(":")) vid else "$packageName:id/$vid"
                            try { node.findAccessibilityNodeInfosByViewId(idToFind) } catch (e: Exception) { emptyList() }
                        }
                        is FastQuery.Text -> {
                            val text = fq.value
                            try { node.findAccessibilityNodeInfosByText(text) } catch (e: Exception) { emptyList() }
                        }
                    }
                    for (nativeNode in nativeNodes) {
                        nodeCache.cacheAncestors(nativeNode, node)
                        yield(nativeNode)
                    }
                }
            }
        }
    )
}
```

- [ ] **Step 3: Run the unit test to verify it passes**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestFastQuery"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/xera/xclicker/service/selector/AndroidNodeTransform.kt
git commit -m "feat: implement cacheAncestors and native traverseFastQueryDescendants in transform"
```

---

### Task 4: Integrate and Sync Rebuilt Cache in Accessibility Service

**Files:**
- Modify: [app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt](file:///Users/xera/GitHub/x-clicker/app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt)

- [ ] **Step 1: Pass `packageName` and use `cacheAncestors` in `SelectToSpeakService`**

Modify the invocation of `createTransformWithFastQuery` to pass `packageName`. Also, in the custom `idMatch` and `textMatch` regex blocks, add a call to `nodeCache.cacheAncestors(nativeNode, node)`.

In `SelectToSpeakService.kt`:
```kotlin
        // Modify lines 279-281
        val androidNodeTransform = dev.xera.xclicker.service.selector.createTransformWithFastQuery(nodeCache, packageName) {
            activeWindowNode
        }
```

In the `idMatch` block:
```kotlin
                val idMatch = Regex("""(?:id|vid)[*\^$]?=['"]([^'"]+)['"]""").find(source)
                if (idMatch != null) {
                    val idToFind = idMatch.groupValues[1]
                    val fullId = if (idToFind.contains(":")) idToFind else "$packageName:id/$idToFind"
                    val nativeNodes = try { node.findAccessibilityNodeInfosByViewId(fullId) } catch (e: Exception) { emptyList() }
                    for (nativeNode in nativeNodes) {
                        nodeCache.cacheAncestors(nativeNode, node)
                        var current: AccessibilityNodeInfo? = nativeNode
                        var depthLimit = 10
                        while (current != null && depthLimit-- > 0) {
                            if (selector.match(current, androidNodeTransform, option) != null) {
                                return current
                            }
                            current = androidNodeTransform.getParent(current)
                        }
                    }
                }
```

In the `textMatch` block:
```kotlin
                val textMatch = Regex("""(?:text|desc)[*\^$]?=['"]([^'"]+)['"]""").find(source)
                if (textMatch != null) {
                    val textToFind = textMatch.groupValues[1]
                    val nativeNodes = try { node.findAccessibilityNodeInfosByText(textToFind) } catch (e: Exception) { emptyList() }
                    for (nativeNode in nativeNodes) {
                        nodeCache.cacheAncestors(nativeNode, node)
                        var current: AccessibilityNodeInfo? = nativeNode
                        var depthLimit = 10
                        while (current != null && depthLimit-- > 0) {
                            if (selector.match(current, androidNodeTransform, option) != null) {
                                return current
                            }
                            current = androidNodeTransform.getParent(current)
                        }
                    }
                }
```

- [ ] **Step 2: Compile and Build Debug APK**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all unit tests**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt
git commit -m "feat: complete SelectToSpeakService integration with package-aware fast query and cache reconstruction"
```
