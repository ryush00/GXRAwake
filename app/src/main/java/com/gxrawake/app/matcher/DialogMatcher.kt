package com.gxrawake.app.matcher

import android.content.res.Resources
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

object DialogMatcher {
    const val SYSTEMUI_PACKAGE = "com.android.systemui"

    private const val ALERT_TITLE_ID = "android:id/alertTitle"
    private const val MESSAGE_ID = "android:id/message"
    private const val BUTTON_PANEL_ID = "android:id/buttonPanel"
    private const val POSITIVE_BUTTON_ID = "android:id/button1"
    private const val NEGATIVE_BUTTON_ID = "android:id/button2"
    private const val NEUTRAL_BUTTON_ID = "android:id/button3"

    private const val MAX_NODES_TO_SCAN = 250
    private const val MAX_SUMMARY_LENGTH = 400
    private const val MAX_ANCESTOR_DEPTH = 6

    private val EN_DIALOG_TEXT_HINTS = arrayOf(
        "headset", "turning off", "keep on", "keep awake", "sleep", "standby", "inactivity", "power off"
    )
    private val KO_DIALOG_TEXT_HINTS = arrayOf(
        "헤드셋", "계속 켜", "켜두기", "대기 상태", "전환", "꺼집니다", "종료"
    )
    private val JA_DIALOG_TEXT_HINTS = arrayOf(
        "ヘッドセット", "電源がoff", "電源がオフ", "onを維持", "オンのまま", "スタンバイ", "スリープ"
    )

    private val EN_POSITIVE_BUTTON_HINTS = arrayOf(
        "keep on", "stay on", "keep awake", "ok", "confirm", "continue"
    )
    private val KO_POSITIVE_BUTTON_HINTS = arrayOf(
        "계속", "켜두기", "확인", "유지"
    )
    private val JA_POSITIVE_BUTTON_HINTS = arrayOf(
        "onを維持", "維持", "オンのまま", "続ける", "確認", "はい"
    )

    private val EN_STRICT_KEEP_ON_BUTTON_TEXTS = arrayOf("keep on", "keep awake", "stay on")
    private val KO_STRICT_KEEP_ON_BUTTON_TEXTS = arrayOf("계속 켜두기", "계속 켜 두기")
    private val JA_STRICT_KEEP_ON_BUTTON_TEXTS = arrayOf("onを維持", "オンのまま", "オンのままにする")

    private val EN_HEADSET_HINTS = arrayOf("headset")
    private val KO_HEADSET_HINTS = arrayOf("헤드셋")
    private val JA_HEADSET_HINTS = arrayOf("ヘッドセット")

    private val EN_STANDBY_HINTS = arrayOf("standby", "turning off", "sleep")
    private val KO_STANDBY_HINTS = arrayOf("대기 상태", "전환됨", "꺼집니다")
    private val JA_STANDBY_HINTS = arrayOf("電源がoff", "電源がオフ", "スタンバイ", "スリープ")

    private val NOTIFICATION_CONTEXT_HINTS = arrayOf(
        "notification:",
        "notifications",
        "알림:",
        "시스템 알림",
        "통지:",
        "通知:",
        "システム通知"
    )

    private val NON_SLEEP_ALERT_EXCLUSIONS = arrayOf(
        "%",
        "battery",
        "배터리",
        "バッテリー",
        "电池",
        "電池",
        "akku",
        "batería",
        "batterie",
        "bateria",
        "batteria",
        "батарея",
        "аккумулятор",
        "แบตเตอรี่",
        "बैटरी",
        "baterai",
        "temperature",
        "overheat",
        "온도",
        "과열",
        "温度",
        "过热",
        "過熱",
        "überhitz",
        "surchauffe",
        "sobrecalent",
        "перегрев",
        "storage",
        "저장공간",
        "저장 공간",
        "ストレージ",
        "update",
        "업데이트",
        "アップデート"
    )

    data class MatchResult<T>(
        val dialogMatched: Boolean,
        val button: T?,
        val summary: String,
        val reason: String = ""
    ) {
        companion object {
            fun <T> noMatch(summary: String = ""): MatchResult<T> =
                MatchResult(dialogMatched = false, button = null, summary = summary)

            fun <T> dialogOnly(summary: String, reason: String): MatchResult<T> =
                MatchResult(dialogMatched = true, button = null, summary = summary, reason = reason)

            fun <T> buttonFound(button: T, summary: String, reason: String): MatchResult<T> =
                MatchResult(dialogMatched = true, button = button, summary = summary, reason = reason)
        }
    }

    data class TreeSnapshot<T>(
        var fromSystemUi: Boolean = false,
        var hasNotificationContext: Boolean = false,
        var alertTitle: UiNode<T>? = null,
        var message: UiNode<T>? = null,
        var buttonPanel: UiNode<T>? = null,
        var positiveButton: UiNode<T>? = null,
        var negativeButton: UiNode<T>? = null,
        var neutralButton: UiNode<T>? = null,
        val candidateButtons: MutableList<Pair<UiNode<T>, String>> = mutableListOf(),
        var summary: String = "",
        var visitedCount: Int = 0
    )

    fun findKeepOnButton(root: AccessibilityNodeInfo?): MatchResult<AccessibilityNodeInfo> {
        if (root == null) return MatchResult.noMatch()
        return findKeepOnButton(AccessibilityNodeAdapter(root), resolveSystemLocales())
    }

    fun <T> findKeepOnButton(
        root: UiNode<T>?,
        deviceLocale: Locale = Locale.getDefault()
    ): MatchResult<T> = findKeepOnButton(root, listOf(deviceLocale))

    fun <T> findKeepOnButton(
        root: UiNode<T>?,
        systemLocales: List<Locale>
    ): MatchResult<T> {
        if (root == null) return MatchResult.noMatch()
        val rules = InactivityDialogLocales.forSystemLocales(systemLocales)
        val snapshot = scanTree(root, rules)
        if (!snapshot.fromSystemUi || snapshot.hasNotificationContext) {
            return MatchResult.noMatch(snapshot.summary)
        }

        val matchReason = evaluateInactivityDialog(snapshot, rules)
        if (matchReason != null) {
            val targetNode = resolveTappableTarget(snapshot.positiveButton)
                ?: return MatchResult.dialogOnly(snapshot.summary, matchReason)
            return MatchResult.buttonFound(targetNode.raw, snapshot.summary, matchReason)
        }

        // Tier 2 fallback: custom SystemUI dialog without standard AlertDialog view IDs
        val fallbackButton = findTier2FallbackButton(snapshot, rules)
        if (fallbackButton != null) {
            val targetNode = resolveTappableTarget(fallbackButton)
                ?: return MatchResult.dialogOnly(snapshot.summary, "text-fallback")
            return MatchResult.buttonFound(targetNode.raw, snapshot.summary, "text-fallback")
        }

        return MatchResult.noMatch(snapshot.summary)
    }

    /**
     * Produces a compact single-line diagnostic snapshot of a SystemUI node tree
     * for verbose troubleshooting on real hardware.
     */
    fun describeSystemUiTree(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val rules = InactivityDialogLocales.forSystemLocales(resolveSystemLocales())
        val snapshot = scanTree(AccessibilityNodeAdapter(root), rules)
        if (!snapshot.fromSystemUi) return null

        val parts = mutableListOf<String>()
        parts += "nodes=${snapshot.visitedCount}"
        if (snapshot.hasNotificationContext) parts += "notif=true"
        snapshot.alertTitle?.let { parts += "title=${readableText(it)}" }
        snapshot.message?.let { parts += "msg=${readableText(it)}" }
        snapshot.positiveButton?.let {
            parts += "btn1=${readableText(it)}(enabled=${it.isEnabled},clickable=${it.isClickable})"
        }
        snapshot.negativeButton?.let { parts += "btn2=${readableText(it)}" }
        snapshot.neutralButton?.let { parts += "btn3=${readableText(it)}" }
        if (snapshot.alertTitle == null && snapshot.message == null && snapshot.summary.isNotEmpty()) {
            parts += "text=${snapshot.summary.take(180)}"
        }
        return parts.joinToString(" | ")
    }

    /**
     * Reads the OS / SystemUI locale preference list directly via [Resources.getSystem],
     * unaffected by Android 13+ per-app language overrides for GXRAwake itself.
     */
    internal fun resolveSystemLocales(): List<Locale> {
        return try {
            val locales = Resources.getSystem()?.configuration?.locales
            if (locales != null && !locales.isEmpty) {
                List(locales.size()) { index -> locales[index] }
            } else {
                listOf(Locale.getDefault())
            }
        } catch (_: Throwable) {
            listOf(Locale.getDefault())
        }
    }

    internal fun <T> scanTree(
        root: UiNode<T>,
        rules: InactivityDialogLocales.ActiveLocaleRules = InactivityDialogLocales.forLocale(Locale.getDefault())
    ): TreeSnapshot<T> {
        val queue = ArrayDeque<UiNode<T>>()
        queue.add(root)
        val snapshot = TreeSnapshot<T>()
        val summaryBuilder = StringBuilder()

        while (queue.isNotEmpty() && snapshot.visitedCount < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            snapshot.visitedCount++

            if (node.packageName == SYSTEMUI_PACKAGE) {
                snapshot.fromSystemUi = true
            }

            val text = normalize(readableText(node))
            if (text.isNotEmpty()) {
                if (summaryBuilder.length < MAX_SUMMARY_LENGTH) {
                    if (summaryBuilder.isNotEmpty()) {
                        summaryBuilder.append(" | ")
                    }
                    summaryBuilder.append(text)
                }
                if (containsAny(text, NOTIFICATION_CONTEXT_HINTS)) {
                    snapshot.hasNotificationContext = true
                }
            }

            captureKnownNode(snapshot, node, text, rules)

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.addLast(child)
                }
            }
        }

        snapshot.summary = summaryBuilder.toString().take(MAX_SUMMARY_LENGTH)
        return snapshot
    }

    private fun <T> captureKnownNode(
        snapshot: TreeSnapshot<T>,
        node: UiNode<T>,
        normalizedText: String,
        rules: InactivityDialogLocales.ActiveLocaleRules
    ) {
        when (node.viewIdResourceName) {
            ALERT_TITLE_ID -> snapshot.alertTitle = node
            MESSAGE_ID -> snapshot.message = node
            BUTTON_PANEL_ID -> snapshot.buttonPanel = node
            POSITIVE_BUTTON_ID -> snapshot.positiveButton = node
            NEGATIVE_BUTTON_ID -> snapshot.negativeButton = node
            NEUTRAL_BUTTON_ID -> snapshot.neutralButton = node
        }
        if (node.isEnabled &&
            normalizedText.isNotEmpty() &&
            normalizedText in rules.buttonTexts &&
            isLikelyActionButton(node)
        ) {
            snapshot.candidateButtons.add(node to normalizedText)
        }
    }

    private fun <T> isLikelyActionButton(node: UiNode<T>): Boolean {
        val isButtonClass = node.className?.contains("Button", ignoreCase = true) == true
        return node.isClickable || isButtonClass || findClickableSelfOrParent(node) != null
    }

    private fun <T> findTier2FallbackButton(
        snapshot: TreeSnapshot<T>,
        rules: InactivityDialogLocales.ActiveLocaleRules
    ): UiNode<T>? {
        if (snapshot.candidateButtons.isEmpty()) return null
        if (containsAny(snapshot.summary, NON_SLEEP_ALERT_EXCLUSIONS)) return null

        // Tier 2A: exact paired button + context match for the active system locale (+ English fallback)
        for ((buttonNode, buttonText) in snapshot.candidateButtons) {
            if (rules.matchesAnyProfileForFallback(buttonText, snapshot.summary)) {
                return buttonNode
            }
        }

        // Tier 2B: generic strict headset + standby context scoped to active locale (+ English fallback)
        if (isStrictKeepOnContext(snapshot.summary, rules.resolvedLanguage)) {
            for ((buttonNode, buttonText) in snapshot.candidateButtons) {
                if (isStrictKeepOnButtonForLanguage(buttonText, rules.resolvedLanguage)) {
                    return buttonNode
                }
            }
        }
        return null
    }

    private fun isStrictKeepOnContext(normalizedSummary: String, lang: String): Boolean {
        if (containsAny(normalizedSummary, NON_SLEEP_ALERT_EXCLUSIONS)) return false
        val hasHeadset = containsAny(normalizedSummary, EN_HEADSET_HINTS) ||
            (lang == "ko" && containsAny(normalizedSummary, KO_HEADSET_HINTS)) ||
            (lang == "ja" && containsAny(normalizedSummary, JA_HEADSET_HINTS))
        val hasStandby = containsAny(normalizedSummary, EN_STANDBY_HINTS) ||
            (lang == "ko" && containsAny(normalizedSummary, KO_STANDBY_HINTS)) ||
            (lang == "ja" && containsAny(normalizedSummary, JA_STANDBY_HINTS))
        return hasHeadset && hasStandby
    }

    private fun isStrictKeepOnButtonForLanguage(normalizedButton: String, lang: String): Boolean {
        if (EN_STRICT_KEEP_ON_BUTTON_TEXTS.any { normalizedButton == it }) return true
        if (lang == "ko" && KO_STRICT_KEEP_ON_BUTTON_TEXTS.any { normalizedButton == it }) return true
        if (lang == "ja" && JA_STRICT_KEEP_ON_BUTTON_TEXTS.any { normalizedButton == it }) return true
        return false
    }

    private fun hasDialogTextHintForLanguage(fullText: String, lang: String): Boolean {
        if (containsAny(fullText, EN_DIALOG_TEXT_HINTS)) return true
        if (lang == "ko" && containsAny(fullText, KO_DIALOG_TEXT_HINTS)) return true
        if (lang == "ja" && containsAny(fullText, JA_DIALOG_TEXT_HINTS)) return true
        return false
    }

    private fun hasPositiveButtonHintForLanguage(
        normalizedButtonText: String,
        rules: InactivityDialogLocales.ActiveLocaleRules
    ): Boolean {
        if (normalizedButtonText in rules.buttonTexts) return true
        if (containsAny(normalizedButtonText, EN_POSITIVE_BUTTON_HINTS)) return true
        if (rules.resolvedLanguage == "ko" && containsAny(normalizedButtonText, KO_POSITIVE_BUTTON_HINTS)) return true
        if (rules.resolvedLanguage == "ja" && containsAny(normalizedButtonText, JA_POSITIVE_BUTTON_HINTS)) return true
        return false
    }

    private fun <T> hasStandardAlertStructure(snapshot: TreeSnapshot<T>): Boolean {
        return snapshot.alertTitle != null &&
            snapshot.message != null &&
            snapshot.positiveButton != null
    }

    private fun <T> evaluateInactivityDialog(
        snapshot: TreeSnapshot<T>,
        rules: InactivityDialogLocales.ActiveLocaleRules
    ): String? {
        if (!hasStandardAlertStructure(snapshot)) return null
        val positiveButton = snapshot.positiveButton ?: return null
        if (!positiveButton.isEnabled) return null

        val titleText = readableText(snapshot.alertTitle)
        val messageText = readableText(snapshot.message)
        val buttonText = readableText(positiveButton)

        val dialogText = normalize("$titleText $messageText")
        val fullText = normalize("$titleText $messageText $buttonText")
        val normalizedButtonText = normalize(buttonText)

        if (containsAny(dialogText, NON_SLEEP_ALERT_EXCLUSIONS)) {
            return null
        }

        val singleButtonOnly = snapshot.negativeButton == null && snapshot.neutralButton == null

        // Primary match A: firmware-verified profile for the active system locale (+ English fallback)
        val matchedProfile = rules.matchFullProfile(
            normalizedDialogText = dialogText,
            normalizedButtonText = normalizedButtonText,
            requireButtonMatch = !singleButtonOnly
        )
        if (matchedProfile != null) {
            return if (singleButtonOnly) "keyword+single-button" else "keyword+positive-button"
        }

        // Primary match B: firmware-verified title pattern for active partially localized locale
        if (rules.matchPartialLocaleTitle(dialogText) != null) {
            if (singleButtonOnly) {
                return "keyword+single-button"
            }
            if (hasPositiveButtonHintForLanguage(normalizedButtonText, rules)) {
                return "keyword+positive-button"
            }
        }

        // Primary match C: generic sleep / standby / keep-on keywords for active locale (+ English fallback)
        if (hasDialogTextHintForLanguage(fullText, rules.resolvedLanguage)) {
            if (singleButtonOnly) {
                return "keyword+single-button"
            }
            if (hasPositiveButtonHintForLanguage(normalizedButtonText, rules)) {
                return "keyword+positive-button"
            }
        }

        // Fallback match for future or custom locales: single-button SystemUI alert with countdown digit
        if (singleButtonOnly && containsDigit(dialogText)) {
            return "countdown-single-button"
        }

        return null
    }

    private fun <T> resolveTappableTarget(node: UiNode<T>?): UiNode<T>? {
        if (node == null || !node.isEnabled) return null
        return findClickableSelfOrParent(node) ?: node
    }

    private fun <T> findClickableSelfOrParent(start: UiNode<T>): UiNode<T>? {
        var current: UiNode<T>? = start
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isClickable && current.isEnabled) {
                return current
            }
            current = current.getParent()
            depth++
        }
        return null
    }

    private fun <T> readableText(node: UiNode<T>?): String {
        if (node == null) return ""
        val primary = node.text
        if (!primary.isNullOrBlank()) return primary
        return node.contentDescription.orEmpty()
    }

    private fun normalize(value: String?): String {
        if (value == null) return ""
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun containsAny(haystack: String, needles: Array<String>): Boolean {
        if (haystack.isEmpty()) return false
        for (needle in needles) {
            if (haystack.contains(needle.lowercase(Locale.ROOT))) {
                return true
            }
        }
        return false
    }

    private fun containsDigit(value: String): Boolean {
        for (ch in value) {
            if (ch in '0'..'9') return true
        }
        return false
    }
}
