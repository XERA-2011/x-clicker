# 2026-05-26 GKD 快速查找引擎与缓存重建设计规格书

## 📋 背景与目标

在 GKD 规则库中，`quickFind: true` (或 `fastQuery`) 是一项被广泛使用的性能优化特性。它使规则匹配引擎在搜索广告节点时，能够直接使用底层 API (如按照 ID 或文本查找) 快速获取候选节点，而不是使用深度优先搜索（DFS）遍历整颗 DOM 树。

然而，我们目前的系统在支持该特性时存在两个严重的硬伤：
1. `Transform` 中缺少对 `traverseFastQueryDescendants` 方法的实现，导致当 `fastQuery` 启用时直接返回空集。
2. 使用底层原生搜索接口返回的节点是孤立节点，缺失了其在 `NodeCache` 中的父子关系及索引属性，导致层级选择器（如 `A > B` 等）断层失效。

本设计的目标是：
- 完整实现 `traverseFastQueryDescendants` 快速查询接口，并支持包名自动补齐。
- 在通过原生接口查找到节点后，自动向上回溯重建并缓存它们至搜索根节点的完整树关系。
- 保证匹配的 100% 稳定性、准确性以及极高的性能，从而彻底解决哔哩哔哩、网易新闻、新浪微博等 App 的匹配问题。

---

## 🛠️ 方案架构与详细设计

### 1. `NodeCache` 增加回溯重建接口
在 `AndroidNodeTransform.kt` 中为 `NodeCache` 补充 `cacheAncestors` 辅助函数。此函数以查找到的原生节点为起点，以搜索根节点为终点，沿途向上获取 `parent` 并精确解析它们在父节点中的子节点索引关系（`index`），一并填充进缓存以建立双向引用链。

```kotlin
fun cacheAncestors(startNode: AccessibilityNodeInfo, limitNode: AccessibilityNodeInfo?) {
    var curr = startNode
    var depthLimit = 15 // 防止异常死循环
    while (curr != limitNode && depthLimit-- > 0) {
        val p = curr.parent ?: break
        parentCache[curr] = p
        
        // 解析并在 childCache/indexCache 中记录当前节点在父节点中的正确位置
        for (i in 0 until p.childCount) {
            val child = p.getChild(i)
            if (child == curr) {
                childCache[Pair(p, i)] = curr
                indexCache[curr] = i
                break
            } else {
                // 安全释放非目标节点引用，避免无障碍节点内存泄露
                try { child?.recycle() } catch (_: Exception) {}
            }
        }
        curr = p
    }
}
```

### 2. 重构 `createTransformWithFastQuery` 签名与功能
修改 `AndroidNodeTransform.kt` 中的 `createTransformWithFastQuery`。增加 `packageName` 参数，并重写 `traverseFastQueryDescendants`。

```kotlin
fun createTransformWithFastQuery(
    nodeCache: NodeCache,
    packageName: String,
    getRoot: () -> AccessibilityNodeInfo?
): Transform<AccessibilityNodeInfo> {
    return Transform(
        getAttr = androidNodeTransform.getAttr,
        getInvoke = androidNodeTransform.getInvoke,
        getName = androidNodeTransform.getName,
        getChildren = { node -> nodeCache.getChildren(node) },
        getParent = { node -> nodeCache.getParent(node) },
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
                        // 重建链条，让底层 GKD 连接选择器能顺利溯源
                        nodeCache.cacheAncestors(nativeNode, node)
                        yield(nativeNode)
                    }
                }
            }
        }
    )
}
```

### 3. 同步重构 `SelectToSpeakService.kt` 的 `querySelector` 自定义兜底
为了防止即使 `fastQuery = false` 时也因为恶意的广告树干扰导致 DFS 查找失败，我们的自定义穿透正则表达式拦截也必须接入最新的缓存重建系统：
- 调用 `createTransformWithFastQuery` 时传入 `packageName`。
- 在 `idMatch` 和 `textMatch` 匹配分支中，对查询到的原生节点自动调用 `nodeCache.cacheAncestors(nativeNode, node)`，为接下来执行的 `selector.match` 提供坚实的层级缓存基础。

---

## 🧪 自动化与验证计划

根据 Superpowers 的 TDD 纪律要求：
1. **测试驱动开发 (TDD)**：我们将在 `TestParsing.kt` 或新增的测试类中，使用仿真的 `AccessibilityNodeInfo` 树模型编写测试：
   - 验证 `cacheAncestors` 运行后缓存的树链路是否完整，`getParent`、`getChild`、`getIndex` 以及 `getDepth` 均能获取到正确值。
   - 验证在带连接符的规则下（如 `A > B`），通过 `fastQuery` 查找能完美命中目标。
2. **真机集成验证**：编译构建 debug APK，部署至真机，实测哔哩哔哩（`tv.danmaku.bili`）、网易新闻（`com.netease.newsreader.activity`）、新浪微博（`com.sina.weibo`）和金十数据的热启动/冷启动拦截。
