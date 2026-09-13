package dev.ashwin.forcestop.service

import android.view.accessibility.AccessibilityNodeInfo

private const val MAX_ANCESTOR_WALK = 5

fun AccessibilityNodeInfo.labelText(): String? =
    (text ?: contentDescription)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

fun AccessibilityNodeInfo.labelEqualsAny(candidates: List<String>): Boolean {
    val own = labelText() ?: return false
    return candidates.any { it.equals(own, ignoreCase = true) }
}

/** The label is usually a TextView inside a clickable container, so walk up to find it. */
fun AccessibilityNodeInfo.clickableSelfOrAncestor(): AccessibilityNodeInfo? {
    var node: AccessibilityNodeInfo? = this
    var depth = 0
    while (node != null && depth < MAX_ANCESTOR_WALK) {
        val current = node
        if (current.isClickable) return current
        node = current.parent
        depth++
    }
    return null
}

fun AccessibilityNodeInfo.findNodesLabelled(candidates: List<String>): List<AccessibilityNodeInfo> =
    candidates
        .flatMap { findAccessibilityNodeInfosByText(it).orEmpty() }
        .filter { it.labelEqualsAny(candidates) }
