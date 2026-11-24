package com.gxrawake.app.matcher

import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityNodeAdapter(
    override val raw: AccessibilityNodeInfo
) : UiNode<AccessibilityNodeInfo> {

    override val packageName: String?
        get() = raw.packageName?.toString()

    override val viewIdResourceName: String?
        get() = raw.viewIdResourceName

    override val className: String?
        get() = raw.className?.toString()

    override val text: String?
        get() = raw.text?.toString()

    override val contentDescription: String?
        get() = raw.contentDescription?.toString()

    override val isEnabled: Boolean
        get() = raw.isEnabled

    override val isClickable: Boolean
        get() = raw.isClickable

    override val childCount: Int
        get() = raw.childCount

    override fun getChild(index: Int): UiNode<AccessibilityNodeInfo>? {
        val child = raw.getChild(index) ?: return null
        return AccessibilityNodeAdapter(child)
    }

    override fun getParent(): UiNode<AccessibilityNodeInfo>? {
        val parent = raw.parent ?: return null
        return AccessibilityNodeAdapter(parent)
    }
}
