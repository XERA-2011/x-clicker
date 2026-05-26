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
