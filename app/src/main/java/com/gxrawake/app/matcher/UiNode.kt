package com.gxrawake.app.matcher

/**
 * Lightweight abstraction over [android.view.accessibility.AccessibilityNodeInfo]
 * so [DialogMatcher] can be unit-tested directly on the JVM.
 */
interface UiNode<T> {
    val raw: T
    val packageName: String?
    val viewIdResourceName: String?
    val className: String?
    val text: String?
    val contentDescription: String?
    val isEnabled: Boolean
    val isClickable: Boolean
    val childCount: Int
    fun getChild(index: Int): UiNode<T>?
    fun getParent(): UiNode<T>?
}
