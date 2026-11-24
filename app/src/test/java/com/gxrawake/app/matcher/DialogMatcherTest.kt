package com.gxrawake.app.matcher

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogMatcherTest {

    private class FakeNode(
        val id: String,
        override val packageName: String? = DialogMatcher.SYSTEMUI_PACKAGE,
        override val viewIdResourceName: String? = null,
        override val className: String? = "android.view.View",
        override val text: String? = null,
        override val contentDescription: String? = null,
        override val isEnabled: Boolean = true,
        override val isClickable: Boolean = false
    ) : UiNode<String> {
        private val children = mutableListOf<FakeNode>()
        private var parentNode: FakeNode? = null

        override val raw: String
            get() = id

        override val childCount: Int
            get() = children.size

        override fun getChild(index: Int): UiNode<String>? = children.getOrNull(index)

        override fun getParent(): UiNode<String>? = parentNode

        fun add(child: FakeNode): FakeNode {
            child.parentNode = this
            children.add(child)
            return this
        }
    }

    private fun buildAlertTree(
        pkg: String = DialogMatcher.SYSTEMUI_PACKAGE,
        title: String,
        message: String,
        positiveText: String = "계속 켜두기",
        positiveEnabled: Boolean = true,
        positiveClickable: Boolean = true,
        negativeText: String? = null,
        extraText: String? = null
    ): FakeNode {
        val root = FakeNode("root", packageName = pkg)
        if (extraText != null) {
            root.add(FakeNode("extra", packageName = pkg, text = extraText))
        }
        root.add(
            FakeNode(
                id = "title",
                packageName = pkg,
                viewIdResourceName = "android:id/alertTitle",
                text = title
            )
        )
        root.add(
            FakeNode(
                id = "message",
                packageName = pkg,
                viewIdResourceName = "android:id/message",
                text = message
            )
        )
        val buttonPanel = FakeNode(
            id = "panel",
            packageName = pkg,
            viewIdResourceName = "android:id/buttonPanel"
        )
        buttonPanel.add(
            FakeNode(
                id = "button1",
                packageName = pkg,
                viewIdResourceName = "android:id/button1",
                text = positiveText,
                isEnabled = positiveEnabled,
                isClickable = positiveClickable
            )
        )
        if (negativeText != null) {
            buttonPanel.add(
                FakeNode(
                    id = "button2",
                    packageName = pkg,
                    viewIdResourceName = "android:id/button2",
                    text = negativeText,
                    isEnabled = true,
                    isClickable = true
                )
            )
        }
        root.add(buttonPanel)
        return root
    }

    @Test
    fun matchesExactKoreanGalaxyXrScreenshotDialog() {
        val root = buildAlertTree(
            title = "헤드셋이 30초 후에 대기 상태로 전환됨",
            message = "대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요.",
            positiveText = "계속 켜두기"
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.KOREAN)
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
        assertEquals("keyword+single-button", result.reason)
    }

    @Test
    fun matchesCustomSystemUiLayoutWithoutStandardAlertIds() {
        val root = FakeNode("root")
        root.add(FakeNode("heading", text = "헤드셋이 30초 후에 대기 상태로 전환됨"))
        root.add(FakeNode("body", text = "대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요."))
        root.add(
            FakeNode(
                id = "custom_keep_on_btn",
                className = "android.widget.Button",
                text = "계속 켜두기",
                isEnabled = true,
                isClickable = true
            )
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.KOREAN)
        assertTrue(result.dialogMatched)
        assertEquals("custom_keep_on_btn", result.button)
        assertEquals("text-fallback", result.reason)
    }

    @Test
    fun matchesCustomSystemUiLayoutWhenKeepOnLabelIsInsideClickableWrapper() {
        val root = FakeNode("root")
        root.add(FakeNode("heading", text = "헤드셋이 30초 후에 대기 상태로 전환됨"))
        root.add(FakeNode("body", text = "대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요."))
        val pillContainer = FakeNode("pill_container", className = "android.view.ViewGroup", isClickable = true)
        pillContainer.add(
            FakeNode(
                id = "pill_label",
                className = "android.widget.TextView",
                text = "계속 켜두기",
                isEnabled = true,
                isClickable = false
            )
        )
        root.add(pillContainer)

        val result = DialogMatcher.findKeepOnButton(root, Locale.KOREAN)
        assertTrue(result.dialogMatched)
        assertEquals("pill_container", result.button)
        assertEquals("text-fallback", result.reason)
    }

    @Test
    fun matchesStandardAlertWithoutNotImportantButtonPanelContainer() {
        // When flagIncludeNotImportantViews is disabled, android:id/buttonPanel (LinearLayout)
        // may be omitted while alertTitle, message, and button1 remain present.
        val root = FakeNode("root")
        root.add(
            FakeNode(
                id = "title",
                viewIdResourceName = "android:id/alertTitle",
                text = "헤드셋이 30초 후에 대기 상태로 전환됨"
            )
        )
        root.add(
            FakeNode(
                id = "message",
                viewIdResourceName = "android:id/message",
                text = "대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요."
            )
        )
        root.add(
            FakeNode(
                id = "button1",
                viewIdResourceName = "android:id/button1",
                text = "계속 켜두기",
                isEnabled = true,
                isClickable = true
            )
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.KOREAN)
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
        assertEquals("keyword+single-button", result.reason)
    }

    @Test
    fun matchesKoreanInactivityCountdownDialog() {
        val root = buildAlertTree(
            title = "헤드셋이 곧 대기 상태로 전환됩니다",
            message = "계속 사용하려면 28초 이내에 확인을 누르세요.",
            positiveText = "계속 켜두기"
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.KOREAN)
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
        assertEquals("keyword+single-button", result.reason)
    }

    @Test
    fun matchesEnglishInactivityDialog() {
        val root = buildAlertTree(
            title = "Headset turning off soon",
            message = "Your headset will enter standby in 25 seconds.",
            positiveText = "Keep on"
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.US)
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
    }

    @Test
    fun matchesJapaneseInactivityDialog() {
        val root = buildAlertTree(
            title = "30秒後にヘッドセットの電源がOFF",
            message = "ヘッドセットの電源がOFFにならないようにするには、[ONを維持]を選択してください。",
            positiveText = "ONを維持"
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.JAPANESE)
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
        assertEquals("keyword+single-button", result.reason)
    }

    @Test
    fun matchesJapaneseCustomSystemUiFallback() {
        val root = FakeNode("root")
        root.add(FakeNode("heading", text = "30秒後にヘッドセットの電源がOFF"))
        root.add(FakeNode("body", text = "ヘッドセットの電源がOFFにならないようにするには、[ONを維持]を選択してください。"))
        root.add(
            FakeNode(
                id = "custom_ja_keep_on_btn",
                className = "android.widget.Button",
                text = "ONを維持",
                isEnabled = true,
                isClickable = true
            )
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.JAPANESE)
        assertTrue(result.dialogMatched)
        assertEquals("custom_ja_keep_on_btn", result.button)
        assertEquals("text-fallback", result.reason)
    }

    @Test
    fun matchesLocaleAgnosticCountdownDialog() {
        val root = buildAlertTree(
            title = "El visor se apagará pronto",
            message = "Quedan 22 segundos.",
            positiveText = "Aceptar"
        )

        val result = DialogMatcher.findKeepOnButton(root, Locale.forLanguageTag("es-ES"))
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
        assertEquals("countdown-single-button", result.reason)
    }

    @Test
    fun matchesTwoButtonDialogWhenPositiveHasKeepOnHint() {
        val root = buildAlertTree(
            title = "Headset turning off soon",
            message = "Entering standby in 19 seconds.",
            positiveText = "Keep on",
            negativeText = "Turn off"
        )

        val result = DialogMatcher.findKeepOnButton(root)
        assertTrue(result.dialogMatched)
        assertEquals("button1", result.button)
        assertEquals("keyword+positive-button", result.reason)
    }

    @Test
    fun ignoresNonSystemUiPackage() {
        val root = buildAlertTree(
            pkg = "com.example.someapp",
            title = "Headset turning off soon",
            message = "Standby in 30 seconds.",
            positiveText = "Keep on"
        )

        val result = DialogMatcher.findKeepOnButton(root)
        assertFalse(result.dialogMatched)
        assertNull(result.button)
    }

    @Test
    fun ignoresNotificationContextToPreventFalsePositive() {
        val root = buildAlertTree(
            title = "헤드셋이 곧 대기 상태로 전환됩니다",
            message = "30초 후 전환",
            positiveText = "계속 켜두기",
            extraText = "시스템 알림"
        )

        val result = DialogMatcher.findKeepOnButton(root)
        assertFalse(result.dialogMatched)
        assertNull(result.button)
    }

    @Test
    fun ignoresLowBatterySystemAlertWithDigits() {
        val root = buildAlertTree(
            title = "배터리 부족",
            message = "배터리가 15% 남았습니다.",
            positiveText = "확인"
        )

        val result = DialogMatcher.findKeepOnButton(root)
        assertFalse(result.dialogMatched)
        assertNull(result.button)
    }

    @Test
    fun climbsToClickableParentWhenPositiveButtonIsNotDirectlyClickable() {
        val root = FakeNode("root")
        root.add(FakeNode("title", viewIdResourceName = "android:id/alertTitle", text = "Headset turning off"))
        root.add(FakeNode("message", viewIdResourceName = "android:id/message", text = "20 seconds left"))
        val panel = FakeNode("panel", viewIdResourceName = "android:id/buttonPanel")
        val wrapper = FakeNode("clickable_wrapper", isEnabled = true, isClickable = true)
        wrapper.add(
            FakeNode(
                id = "button1",
                viewIdResourceName = "android:id/button1",
                text = "Keep on",
                isEnabled = true,
                isClickable = false
            )
        )
        panel.add(wrapper)
        root.add(panel)

        val result = DialogMatcher.findKeepOnButton(root)
        assertTrue(result.dialogMatched)
        assertNotNull(result.button)
        assertEquals("clickable_wrapper", result.button)
    }

    @Test
    fun ignoresDisabledPositiveButton() {
        val root = buildAlertTree(
            title = "Headset turning off soon",
            message = "Standby in 10 seconds.",
            positiveText = "Keep on",
            positiveEnabled = false
        )

        val result = DialogMatcher.findKeepOnButton(root)
        assertFalse(result.dialogMatched)
        assertNull(result.button)
    }

    @Test
    fun matchesAllFullyLocalizedGalaxyXrDialogsInTier1AndTier2() {
        data class LocaleSample(
            val locale: String,
            val title: String,
            val message: String,
            val button: String
        )

        val samples = listOf(
            LocaleSample("en", "Headset turning off in 30 seconds", "Select Keep on to prevent your headset from turning off.", "Keep on"),
            LocaleSample("en-US", "Headset turning off in 1 second", "Select Keep on to prevent your headset from turning off.", "Keep on"),
            LocaleSample("ko-KR", "헤드셋이 30초 후에 대기 상태로 전환됨", "대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요.", "계속 켜두기"),
            LocaleSample("ja-JP", "30秒後にヘッドセットの電源がOFF", "ヘッドセットの電源がOFFにならないようにするには、[ONを維持]を選択してください。", "ONを維持"),
            LocaleSample("zh-CN", "头显将在 30 秒后关闭", "选择【保持开启】以防止头显关闭。", "保持开启"),
            LocaleSample("zh-HK", "頭戴式裝置將於 30 秒後關閉", "選擇 [保持開啟] 以防止頭戴式裝置關閉。", "保持開啟"),
            LocaleSample("zh-TW", "頭戴式裝置將在 30 秒後關閉", "選擇 [保持開啟] 避免頭戴式裝置關閉。", "保持開啟"),
            LocaleSample("de-DE", "Headset wird in 30 Sekunden ausgeschaltet", "Wähle „Eingeschaltet lassen“ aus, um zu verhindern, dass sich dein Headset ausschaltet.", "Eing. lassen"),
            LocaleSample("de-AT", "Headset wird in 1 Sekunde ausgeschaltet", "Wähle „Eingeschaltet lassen“ aus, um zu verhindern, dass sich dein Headset ausschaltet.", "Eing. lassen"),
            LocaleSample("es-ES", "Las gafas se apagarán dentro de 30 segundos", "Selecciona No apagar para evitar que se apaguen las gafas.", "No apagar"),
            LocaleSample("es-US", "Las gafas se apagarán en 1 segundo", "Seleccione No apagar para evitar que se apaguen las gafas.", "No apagar"),
            LocaleSample("fr-FR", "Arrêt du casque dans 30 secondes", "Sélectionnez Continuer pour empêcher votre casque de s'éteindre.", "Continuer"),
            LocaleSample("fr-CA", "Arrêt du casque dans 1 seconde", "Sélectionnez Continuer pour empêcher votre casque de s'éteindre.", "Continuer"),
            LocaleSample("hi-IN", "हेडसेट 30 सेकंड में बंद हो जाएगा", "अपने हेडसेट को बंद होने से रोकने के लिए चालू रखें चुनें।", "चालू रखें"),
            LocaleSample("id-ID", "Headset nonaktif dalam 30 detik", "Pilih Tetap aktif untuk mencegah headset Anda nonaktif.", "Ttap aktif"),
            LocaleSample("it-IT", "Spegnimento del visore tra 30 secondi", "Selezionate Continua per evitare che il visore si spenga.", "Continua"),
            LocaleSample("pl-PL", "Zestaw słuchawkowy wyłączy się za 30 sekund", "Wybierz opcję „Zachowaj”, aby zapobiec wyłączeniu zestawu słuchawkowego.", "Zachowaj"),
            LocaleSample("pt-PT", "Óculos a desligar em 30 segundos", "Selecione Manter ligados, para impedir que os óculos se desliguem.", "Manter ligado"),
            LocaleSample("ru-RU", "Гарнитура выключится через 30 секунд", "Выберите “Не выключать”, чтобы гарнитура продолжила работать.", "Не выкл."),
            LocaleSample("ru", "Гарнитура выключится через 1 секунду", "Выберите “Не выключать”, чтобы гарнитура продолжила работать.", "Не выкл."),
            LocaleSample("th-TH", "ชุดหูฟังจะปิดใน 30 วินาที", "เลือก เปิดค้างไว้ เพื่อป้องกันไม่ให้ชุดหูฟังของคุณปิด", "เปิดค้างไว้"),
            LocaleSample("vi-VN", "Kính sẽ tắt sau 30 giây", "Chọn Tiếp tục để tránh làm kính tắt.", "Tiếp tục")
        )

        for (sample in samples) {
            val deviceLocale = Locale.forLanguageTag(sample.locale)

            // Tier 1: standard AlertDialog
            val tier1Root = buildAlertTree(
                title = sample.title,
                message = sample.message,
                positiveText = sample.button
            )
            val tier1Result = DialogMatcher.findKeepOnButton(tier1Root, deviceLocale)
            assertTrue("Tier 1 failed for ${sample.locale}", tier1Result.dialogMatched)
            assertEquals("Tier 1 button mismatch for ${sample.locale}", "button1", tier1Result.button)
            assertEquals("Tier 1 reason mismatch for ${sample.locale}", "keyword+single-button", tier1Result.reason)

            // Tier 1 two-button dialog: verifies positive button matching per profile
            val twoButtonRoot = buildAlertTree(
                title = sample.title,
                message = sample.message,
                positiveText = sample.button,
                negativeText = "Cancel"
            )
            val twoButtonResult = DialogMatcher.findKeepOnButton(twoButtonRoot, deviceLocale)
            assertTrue("Two-button failed for ${sample.locale}", twoButtonResult.dialogMatched)
            assertEquals("Two-button target mismatch for ${sample.locale}", "button1", twoButtonResult.button)
            assertEquals("Two-button reason mismatch for ${sample.locale}", "keyword+positive-button", twoButtonResult.reason)

            // Tier 2: custom SystemUI layout without AlertDialog view IDs
            val tier2Root = FakeNode("root")
            tier2Root.add(FakeNode("heading", text = sample.title))
            tier2Root.add(FakeNode("body", text = sample.message))
            tier2Root.add(
                FakeNode(
                    id = "custom_btn_${sample.locale}",
                    className = "android.widget.Button",
                    text = sample.button,
                    isEnabled = true,
                    isClickable = true
                )
            )
            val tier2Result = DialogMatcher.findKeepOnButton(tier2Root, deviceLocale)
            assertTrue("Tier 2 failed for ${sample.locale}", tier2Result.dialogMatched)
            assertEquals("Tier 2 button mismatch for ${sample.locale}", "custom_btn_${sample.locale}", tier2Result.button)
            assertEquals("Tier 2 reason mismatch for ${sample.locale}", "text-fallback", tier2Result.reason)
        }
    }

    @Test
    fun matchesAll70PartiallyLocalizedGalaxyXrDialogs() {
        val partialTitles = listOf(
            "am" to "የጆሮ ማዳመጫ በ 30 በሰከንዶች ውስጥ ይዘጋል",
            "ar" to "سيتم إيقاف تشغيل سماعة الرأس في غضون 30 ثانية",
            "ar-one-nodigit" to "سيتم إيقاف تشغيل سماعة الرأس في غضون ثانية واحدة",
            "as" to "30 ছেকেণ্ডত হেডছেট অফ হয়",
            "az-rAZ" to "Qarnitur 30 saniyə sonra sönəcək",
            "b+sr+Latn" to "Slušalice se isključuju za 30 sekundi",
            "be-rBY" to "Гарнітура выключыцца праз 30 секунд",
            "bg" to "Изключване на очилата след 30 секунди",
            "bn-rBD" to "30 সেকেন্ড পরে হেডসেট বন্ধ হবে",
            "bn-rIN" to "30 সেকেন্ডের মধ্যে হেডসেট বন্ধ হয়ে যাবে",
            "bs" to "Slušalice se isključuju za 30 sekundi",
            "ca" to "Les ulleres de realitat virtual s'apagaran d'aquí a 30 segons",
            "cs" to "Náhlavní souprava se vypne za 30 sekund",
            "da" to "Headsettet slukker om 30 sekunder",
            "el" to "Το σετ ακουστικών με μικρόφωνο θα απενεργοποιηθεί σε 30 δευτ.",
            "et-rEE" to "Peakomplekt lülitub välja 30 sekundi pärast",
            "eu-rES" to "Aurikularra 30 segundo barru itzaliko da",
            "fa" to "هدست شما ظرف 30 ثانیه خاموش می\u200cشود",
            "fi" to "Lasit sammutetaan 30 sekunnin kuluttua",
            "ga" to "Casfar as an gléas cinn i gceann 30 soicind",
            "gl-rES" to "As gafas apagaranse en 30 segundos",
            "gu-rIN" to "હેડસેટ 30 સેકંડમાં બંધ થશે",
            "ha" to "Kashe kayan sauraro a cikin daƙiƙoƙi 30",
            "he" to "יתבצע כיבוי לאוזניות בעוד 30 שניות",
            "hr" to "Slušalice će se isključiti za 30 sekundi",
            "hu" to "A headset 30 másodperc múlva kikapcsol",
            "hy-rAM" to "Գլխային հեռախոսը կանջատվի 30 վայրկյանից",
            "ig" to "Ekweisi na-agbanyụ na sekọnd 30",
            "is-rIS" to "Höfuðtólið slekkur á sér eftir 30 sekúndur",
            "iw" to "יתבצע כיבוי לאוזניות בעוד 30 שניות",
            "ka-rGE" to "გარნიტურა 30 წამში გამოირთვება",
            "kk-rKZ" to "Гарнитура 30 секундта өшеді",
            "km-rKH" to "កាស\u200bនឹង\u200bបិទ 30វិនាទី ទៀត",
            "kn-rIN" to "30 ಸೆಕೆಂಡುಗಳಲ್ಲಿ ಹೆಡ್\u200cಸೆಟ್ ಆಫ್ ಆಗುತ್ತಿದೆ",
            "ky-rKG" to "Гарнитура 30 секундда өчөт",
            "lo-rLA" to "ຊຸດຫູຟັງຈະປິດໃນ 30 ວິນາທີ",
            "lt" to "Ausinės išsijungs po 30 sek.",
            "lv" to "Austiņas tiks izslēgtas pēc 30 sekundēm",
            "mk-rMK" to "Слушалките се исклучуваат за 30 секунди",
            "ml-rIN" to "30 സെക്കന്റിനുള്ളിൽ ഹെഡ്\u200cസെറ്റ് ഓഫ് ചെയ്യപ്പെടുന്നു",
            "mn-rMN" to "Чихэвч 30 секундийн дараа унтарна",
            "mr-rIN" to "30 सेकंदांत हेडसेट बंद होत आहे",
            "ms-rMY" to "Set kepala akan dimatikan dalam 30 saat",
            "my-rMM" to "နားကြပ်သည် 30 စက္ကန့်အကြာတွင် ပိတ်မည်",
            "my-rZG" to "နားၾကပ္သည္ 30 စကၠန႔္အၾကာတြင္ ပိတ္မည္",
            "nb" to "Hodesettet slås av om 30 sekunder",
            "ne" to "हेडसेट 30 सेकेन्डमा अफ हुँदै छ",
            "ne-rNP" to "हेडसेट 30 सेकेन्डमा अफ हुँदै छ",
            "nl" to "Headset wordt over 30 seconden uitgeschakeld",
            "or" to "ହେଡସେଟ୍ 30 ସେକେଣ୍ତରେ ଟର୍ନ୍ ଅଫ୍ ହେଉଛି",
            "pa-rIN" to "ਹੈੱਡਸੈੱਟ 30 ਸਕਿੰਟਾਂ ਵਿੱਚ ਬੰਦ ਹੋ ਰਿਹਾ ਹੈ",
            "pt-rBR" to "O fone de ouvido vai se desligar em 30 segundos",
            "ro" to "Căștile se vor opri în 30 secunde",
            "si" to "හෙඩ්සෙටය තත්පර 30කින් ක්\u200dරියා විරහිත වේ",
            "si-rLK" to "හෙඩ්සෙටය තත්පර 30කින් ක්\u200dරියා විරහිත වේ",
            "sk" to "Náhlavná súprava sa o 30 sekúnd vypne",
            "sl" to "Slušalke se bodo izklopile čez 30 sekund",
            "sq-rAL" to "Kaska do të fiket për 30 sekonda",
            "sr" to "Слушалице се искључују за 30 секунди",
            "sv" to "Headsetet stängs av om 30 sekunder",
            "sw" to "Kifaa cha sauti kitazima ndani ya sekunde 30",
            "ta-rIN" to "30 வினாடிகளில் ஹெட்செட் ஆஃப் செய்யப்படும்",
            "te-rIN" to "30 సెకన్లలో హెడ్\u200cసెట్ ఆఫ్ అవుతోంది",
            "tg" to "Гарнитура баъди 30 сония хомӯш мешавад",
            "tk" to "Garnitura 30 sekuntdan ýapylýar",
            "tl" to "Mag-o-off ang headset sa loob ng 30 segundo",
            "tr" to "Başlık 30 saniye içinde kapanacak",
            "uk" to "Гарнітура вимкнеться через 30 секунди",
            "ur-rPK" to "ہیڈسیٹ 30 سیکنڈ میں آف ہو جائے گا",
            "uz-rUZ" to "Garnitura 30 soniyadan keyin oʻchadi",
            "yo" to "Ẹ̀ro àgbékọ́rí yóò kú ní ìṣẹ́jú àáyá 30"
        )

        for ((locale, title) in partialTitles) {
            val bcp47 = when {
                locale == "ar-one-nodigit" -> "ar"
                locale == "b+sr+Latn" -> "sr-Latn"
                locale.contains("-r") -> locale.replace("-r", "-")
                else -> locale
            }
            val deviceLocale = Locale.forLanguageTag(bcp47)

            // Current firmware format: localized title + English message/button
            val currentRoot = buildAlertTree(
                title = title,
                message = "Select Keep on to prevent your headset from turning off.",
                positiveText = "Keep on"
            )
            val currentResult = DialogMatcher.findKeepOnButton(currentRoot, deviceLocale)
            assertTrue("Partial locale Tier 1 failed for $locale", currentResult.dialogMatched)
            assertEquals("button1", currentResult.button)

            // Future-proof check: localized title with hypothetical non-English body & button (no digits required)
            val titleOnlyRoot = buildAlertTree(
                title = title,
                message = "Localized message body",
                positiveText = "Localized button"
            )
            val titleOnlyResult = DialogMatcher.findKeepOnButton(titleOnlyRoot, deviceLocale)
            assertTrue("Title pattern failed for $locale", titleOnlyResult.dialogMatched)
            assertEquals("keyword+single-button", titleOnlyResult.reason)
        }
    }

    @Test
    fun doesNotFalsePositiveOnUnrelatedDialogsWithGenericLocalizedButtons() {
        val unrelatedCases = listOf(
            Triple(Locale.FRENCH, Triple("Autorisation requise", "Voulez-vous autoriser l'accès au microphone ?", "Continuer"), "fr"),
            Triple(Locale.ITALIAN, Triple("Connessione Wi-Fi", "La rete attuale non ha accesso a Internet.", "Continua"), "it"),
            Triple(Locale.forLanguageTag("pl-PL"), Triple("Uprawnienia aplikacji", "Ta aplikacja wymaga dostępu do aparatu.", "Zachowaj"), "pl"),
            Triple(Locale.forLanguageTag("vi-VN"), Triple("Thông báo hệ thống", "Ứng dụng cần quyền truy cập vị trí.", "Tiếp tục"), "vi"),
            Triple(Locale.GERMAN, Triple("Akkustand niedrig", "Der Akku ist fast leer (10%).", "Eing. lassen"), "de"),
            Triple(Locale.forLanguageTag("ru-RU"), Triple("Низкий заряд батареи", "Осталось 15% заряда батареи.", "Не выкл."), "ru")
        )

        for ((deviceLocale, triple, _) in unrelatedCases) {
            val (title, message, button) = triple
            val tier1Root = buildAlertTree(title = title, message = message, positiveText = button)
            val tier1Result = DialogMatcher.findKeepOnButton(tier1Root, deviceLocale)
            assertFalse("Unexpected Tier 1 false positive for '$title'", tier1Result.dialogMatched)

            val tier2Root = FakeNode("root")
            tier2Root.add(FakeNode("h", text = title))
            tier2Root.add(FakeNode("b", text = message))
            tier2Root.add(FakeNode("btn", className = "android.widget.Button", text = button, isClickable = true))
            val tier2Result = DialogMatcher.findKeepOnButton(tier2Root, deviceLocale)
            assertFalse("Unexpected Tier 2 false positive for '$title'", tier2Result.dialogMatched)
        }
    }

    @Test
    fun onlyActivatesCurrentSystemLocalePlusEnglishFallback() {
        val koRules = InactivityDialogLocales.forLocale(Locale.KOREA)
        assertEquals("ko", koRules.resolvedLanguage)
        assertEquals(listOf("ko", "en"), koRules.fullProfiles.map { it.locale })
        assertTrue(koRules.partialTitlePatterns.isEmpty())

        val jaRules = InactivityDialogLocales.forLocale(Locale.JAPAN)
        assertEquals("ja", jaRules.resolvedLanguage)
        assertEquals(listOf("ja", "en"), jaRules.fullProfiles.map { it.locale })
        assertTrue(jaRules.partialTitlePatterns.isEmpty())

        val arRules = InactivityDialogLocales.forLocale(Locale.forLanguageTag("ar-SA"))
        assertEquals("ar", arRules.resolvedLanguage)
        assertEquals(listOf("en"), arRules.fullProfiles.map { it.locale })
        assertEquals(listOf("ar"), arRules.partialTitlePatterns.map { it.locale })

        // On a Korean headset, a French custom layout must NOT match because French rules are inactive
        val frenchCustomRoot = FakeNode("root")
        frenchCustomRoot.add(FakeNode("heading", text = "Arrêt du casque dans 30 secondes"))
        frenchCustomRoot.add(FakeNode("body", text = "Sélectionnez Continuer pour empêcher votre casque de s'éteindre."))
        frenchCustomRoot.add(FakeNode("btn", className = "android.widget.Button", text = "Continuer", isClickable = true))
        val onKoreanDevice = DialogMatcher.findKeepOnButton(frenchCustomRoot, Locale.KOREA)
        assertFalse(onKoreanDevice.dialogMatched)

        // Multi-locale system preference list: skips unsupported primary ("eo") and activates secondary ("ko-KR")
        val fallbackListRules = InactivityDialogLocales.forSystemLocales(
            listOf(Locale.forLanguageTag("eo"), Locale.KOREA)
        )
        assertEquals("ko", fallbackListRules.resolvedLanguage)
        assertEquals(listOf("ko", "en"), fallbackListRules.fullProfiles.map { it.locale })
    }
}
