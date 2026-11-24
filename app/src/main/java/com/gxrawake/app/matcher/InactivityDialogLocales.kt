package com.gxrawake.app.matcher

import java.util.Locale

/**
 * Firmware-verified localization signatures for Galaxy XR's eye-inactivity warning dialog:
 * - `plurals/sec_inactivity_auto_sleep_dialog_title`
 * - `string/sec_inactivity_auto_sleep_dialog_message`
 * - `string/sec_inactivity_auto_sleep_dialog_button`
 *
 * Extracted directly from Samsung Galaxy XR (`SM-I610`, build `UML2.260301.001.I610UEU2AZF3`)
 * `/product/overlay/XrSystemUIRRO.apk` (100 total locale entries = 19 fully localized +
 * 8 English/default + 70 title-localized with English message/button + 3 pseudo-locales).
 *
 * At runtime, [forSystemLocales] selects ONLY the active system language (+ English fallback
 * because Android and the 70 partially localized locales fall back to English strings),
 * avoiding scanning unrelated languages and minimizing false-positive surface.
 *
 * All strings are pre-normalized to `Locale.ROOT` lowercase.
 */
internal object InactivityDialogLocales {

    data class LocaleProfile(
        val locale: String,
        val language: String,
        val buttonTexts: List<String>,
        val messagePhrases: List<String>,
        val titlePatterns: List<List<String>>
    ) {
        fun matchesButton(normalizedButton: String): Boolean {
            if (normalizedButton.isEmpty()) return false
            return buttonTexts.any { normalizedButton == it }
        }

        fun matchesButtonContains(normalizedButton: String): Boolean {
            if (normalizedButton.isEmpty()) return false
            return buttonTexts.any { normalizedButton == it || normalizedButton.contains(it) }
        }

        fun matchesMessage(normalizedText: String): Boolean {
            if (normalizedText.isEmpty()) return false
            return messagePhrases.any { normalizedText.contains(it) }
        }

        fun matchesTitle(normalizedText: String): Boolean {
            if (normalizedText.isEmpty()) return false
            return titlePatterns.any { pattern -> matchesAllParts(normalizedText, pattern) }
        }

        fun matchesContext(normalizedText: String): Boolean {
            return matchesMessage(normalizedText) || matchesTitle(normalizedText)
        }
    }

    data class PartialTitlePattern(
        val locale: String,
        val languages: Set<String>,
        val parts: List<String>
    )

    /**
     * Scoped matcher rules for the current active system locale (+ English fallback).
     */
    data class ActiveLocaleRules(
        val cacheKey: String,
        val resolvedLanguage: String,
        val fullProfiles: List<LocaleProfile>,
        val partialTitlePatterns: List<PartialTitlePattern>,
        val buttonTexts: Set<String>
    ) {
        fun matchFullProfile(
            normalizedDialogText: String,
            normalizedButtonText: String,
            requireButtonMatch: Boolean
        ): LocaleProfile? {
            for (profile in fullProfiles) {
                if (!profile.matchesContext(normalizedDialogText)) continue
                if (!requireButtonMatch || profile.matchesButtonContains(normalizedButtonText)) {
                    return profile
                }
            }
            return null
        }

        fun matchPartialLocaleTitle(normalizedText: String): String? {
            if (normalizedText.isEmpty() || partialTitlePatterns.isEmpty()) return null
            for (pattern in partialTitlePatterns) {
                if (matchesAllParts(normalizedText, pattern.parts)) {
                    return pattern.locale
                }
            }
            return null
        }

        fun matchesAnyProfileForFallback(normalizedButtonText: String, normalizedSummary: String): Boolean {
            for (profile in fullProfiles) {
                if (profile.matchesButton(normalizedButtonText) && profile.matchesContext(normalizedSummary)) {
                    return true
                }
            }
            return false
        }
    }

    private val ENGLISH_PROFILE = LocaleProfile(
        locale = "en",
        language = "en",
        buttonTexts = listOf("keep on", "keep awake", "stay on"),
        messagePhrases = listOf(
            "select keep on to prevent your headset from turning off",
            "prevent your headset from turning off"
        ),
        titlePatterns = listOf(
            listOf("headset turning off in", "second")
        )
    )

    /**
     * All 17 fully localized languages (19 localized entries + 8 English/default entries)
     * in `XrSystemUIRRO.apk`.
     */
    val FULL_LOCALE_PROFILES: List<LocaleProfile> = listOf(
        ENGLISH_PROFILE,
        // Korean (ko)
        LocaleProfile(
            locale = "ko",
            language = "ko",
            buttonTexts = listOf("계속 켜두기", "계속 켜 두기"),
            messagePhrases = listOf(
                "대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요",
                "대기 상태로 전환하지 않으려면"
            ),
            titlePatterns = listOf(
                listOf("헤드셋이", "초 후에 대기 상태로 전환됨"),
                listOf("초 후에 대기 상태로 전환됨")
            )
        ),
        // Japanese (ja)
        LocaleProfile(
            locale = "ja",
            language = "ja",
            buttonTexts = listOf("onを維持", "オンのまま", "オンのままにする"),
            messagePhrases = listOf(
                "ヘッドセットの電源がoffにならないようにするには",
                "[onを維持]を選択してください"
            ),
            titlePatterns = listOf(
                listOf("秒後にヘッドセットの電源がoff")
            )
        ),
        // Chinese Simplified (zh-rCN)
        LocaleProfile(
            locale = "zh-rCN",
            language = "zh",
            buttonTexts = listOf("保持开启"),
            messagePhrases = listOf(
                "选择【保持开启】以防止头显关闭",
                "防止头显关闭"
            ),
            titlePatterns = listOf(
                listOf("头显将在", "秒后关闭")
            )
        ),
        // Chinese Traditional - Hong Kong (zh-rHK)
        LocaleProfile(
            locale = "zh-rHK",
            language = "zh",
            buttonTexts = listOf("保持開啟"),
            messagePhrases = listOf(
                "選擇 [保持開啟] 以防止頭戴式裝置關閉",
                "防止頭戴式裝置關閉"
            ),
            titlePatterns = listOf(
                listOf("頭戴式裝置將於", "秒後關閉")
            )
        ),
        // Chinese Traditional - Taiwan (zh-rTW)
        LocaleProfile(
            locale = "zh-rTW",
            language = "zh",
            buttonTexts = listOf("保持開啟"),
            messagePhrases = listOf(
                "選擇 [保持開啟] 避免頭戴式裝置關閉",
                "避免頭戴式裝置關閉"
            ),
            titlePatterns = listOf(
                listOf("頭戴式裝置將在", "秒後關閉")
            )
        ),
        // German (de)
        LocaleProfile(
            locale = "de",
            language = "de",
            buttonTexts = listOf("eing. lassen", "eingeschaltet lassen"),
            messagePhrases = listOf(
                "um zu verhindern, dass sich dein headset ausschaltet",
                "wähle „eingeschaltet lassen“"
            ),
            titlePatterns = listOf(
                listOf("headset wird in", "ausgeschaltet")
            )
        ),
        // Spanish (es-rES, es-rUS)
        LocaleProfile(
            locale = "es",
            language = "es",
            buttonTexts = listOf("no apagar"),
            messagePhrases = listOf(
                "no apagar para evitar que se apaguen las gafas",
                "evitar que se apaguen las gafas"
            ),
            titlePatterns = listOf(
                listOf("las gafas se apagarán", "segundo")
            )
        ),
        // French (fr, fr-rCA)
        LocaleProfile(
            locale = "fr",
            language = "fr",
            buttonTexts = listOf("continuer"),
            messagePhrases = listOf(
                "pour empêcher votre casque de s'éteindre",
                "sélectionnez continuer pour empêcher votre casque"
            ),
            titlePatterns = listOf(
                listOf("arrêt du casque dans", "seconde")
            )
        ),
        // Hindi (hi)
        LocaleProfile(
            locale = "hi",
            language = "hi",
            buttonTexts = listOf("चालू रखें"),
            messagePhrases = listOf(
                "अपने हेडसेट को बंद होने से रोकने के लिए चालू रखें चुनें",
                "हेडसेट को बंद होने से रोकने के लिए"
            ),
            titlePatterns = listOf(
                listOf("हेडसेट", "सेकंड में बंद हो जाएगा")
            )
        ),
        // Indonesian (id, in) — official button uses abbreviated "Ttap aktif"
        LocaleProfile(
            locale = "id",
            language = "id",
            buttonTexts = listOf("ttap aktif", "tetap aktif"),
            messagePhrases = listOf(
                "pilih tetap aktif untuk mencegah headset anda nonaktif",
                "mencegah headset anda nonaktif"
            ),
            titlePatterns = listOf(
                listOf("headset nonaktif dalam", "detik")
            )
        ),
        // Italian (it)
        LocaleProfile(
            locale = "it",
            language = "it",
            buttonTexts = listOf("continua"),
            messagePhrases = listOf(
                "selezionate continua per evitare che il visore si spenga",
                "evitare che il visore si spenga"
            ),
            titlePatterns = listOf(
                listOf("spegnimento del visore tra", "second")
            )
        ),
        // Polish (pl)
        LocaleProfile(
            locale = "pl",
            language = "pl",
            buttonTexts = listOf("zachowaj"),
            messagePhrases = listOf(
                "aby zapobiec wyłączeniu zestawu słuchawkowego",
                "zapobiec wyłączeniu zestawu słuchawkowego"
            ),
            titlePatterns = listOf(
                listOf("zestaw słuchawkowy wyłączy się za", "sekund")
            )
        ),
        // Portuguese - Portugal (pt-rPT)
        LocaleProfile(
            locale = "pt-rPT",
            language = "pt",
            buttonTexts = listOf("manter ligado", "manter ligados"),
            messagePhrases = listOf(
                "para impedir que os óculos se desliguem",
                "selecione manter ligados"
            ),
            titlePatterns = listOf(
                listOf("óculos a desligar em", "segundo")
            )
        ),
        // Russian (ru) — official button uses abbreviated "Не выкл."
        LocaleProfile(
            locale = "ru",
            language = "ru",
            buttonTexts = listOf("не выкл.", "не выключать"),
            messagePhrases = listOf(
                "чтобы гарнитура продолжила работать",
                "выберите “не выключать”"
            ),
            titlePatterns = listOf(
                listOf("гарнитура выключится через", "секунд")
            )
        ),
        // Thai (th)
        LocaleProfile(
            locale = "th",
            language = "th",
            buttonTexts = listOf("เปิดค้างไว้"),
            messagePhrases = listOf(
                "เลือก เปิดค้างไว้ เพื่อป้องกันไม่ให้ชุดหูฟังของคุณปิด",
                "เพื่อป้องกันไม่ให้ชุดหูฟังของคุณปิด"
            ),
            titlePatterns = listOf(
                listOf("ชุดหูฟังจะปิดใน", "วินาที")
            )
        ),
        // Vietnamese (vi)
        LocaleProfile(
            locale = "vi",
            language = "vi",
            buttonTexts = listOf("tiếp tục"),
            messagePhrases = listOf(
                "chọn tiếp tục để tránh làm kính tắt",
                "tránh làm kính tắt"
            ),
            titlePatterns = listOf(
                listOf("kính sẽ tắt sau", "giây")
            )
        )
    )

    /**
     * Exact normalized title patterns for all 70 partially localized locales in
     * `XrSystemUIRRO.apk` (currently paired with English message/button, also future-proofed
     * if Samsung later localizes their message/button or omits digits in singular plural forms).
     */
    val PARTIAL_LOCALE_TITLE_PATTERNS: List<PartialTitlePattern> = listOf(
        partial("am", listOf("የጆሮ ማዳመጫ በ", "ውስጥ ይዘጋል")),
        partial("ar", listOf("سيتم إيقاف تشغيل سماعة الرأس في غضون")),
        partial("as", listOf("ছেকেণ্ডত হেডছেট অফ হয়")),
        partial("az-rAZ", listOf("qarnitur", "saniyə sonra sönəcək"), setOf("az")),
        partial("b+sr+Latn/bs", listOf("slušalice se isključuju za"), setOf("sr", "bs")),
        partial("be-rBY", listOf("гарнітура выключыцца праз"), setOf("be")),
        partial("bg", listOf("изключване на очилата след")),
        partial("bn-rBD", listOf("সেকেন্ড পরে হেডসেট বন্ধ হবে"), setOf("bn")),
        partial("bn-rIN", listOf("সেকেন্ডের মধ্যে হেডসেট বন্ধ হয়ে যাবে"), setOf("bn")),
        partial("ca", listOf("les ulleres de realitat virtual s'apagaran d'aquí a")),
        partial("cs", listOf("náhlavní souprava se vypne za")),
        partial("da", listOf("headsettet slukker om")),
        partial("el", listOf("το σετ ακουστικών με μικρόφωνο θα απενεργοποιηθεί σε", "δευτ.")),
        partial("et-rEE", listOf("peakomplekt lülitub välja", "sekundi pärast"), setOf("et")),
        partial("eu-rES", listOf("aurikularra", "segundo barru itzaliko da"), setOf("eu")),
        partial("fa", listOf("هدست شما ظرف", "ثانیه خاموش می\u200cشود")),
        partial("fi", listOf("lasit sammutetaan", "sekunnin kuluttua")),
        partial("ga", listOf("casfar as an gléas cinn i gceann", "oicind")),
        partial("gl-rES", listOf("as gafas apagaranse en"), setOf("gl")),
        partial("gu-rIN", listOf("હેડસેટ", "સેકંડમાં બંધ થશે"), setOf("gu")),
        partial("ha", listOf("kashe kayan sauraro a cikin daƙiƙ")),
        partial("he/iw", listOf("יתבצע כיבוי לאוזניות בעוד"), setOf("he")),
        partial("hr", listOf("slušalice će se isključiti za")),
        partial("hu", listOf("a headset", "másodperc múlva kikapcsol")),
        partial("hy-rAM", listOf("գլխային հեռախոսը կանջատվի", "վայրկյանից"), setOf("hy")),
        partial("ig", listOf("ekweisi na-agbanyụ na sekọnd")),
        partial("is-rIS", listOf("höfuðtólið slekkur á sér eftir"), setOf("is")),
        partial("ka-rGE", listOf("გარნიტურა", "წამში გამოირთვება"), setOf("ka")),
        partial("kk-rKZ", listOf("гарнитура", "секундта өшеді"), setOf("kk")),
        partial("km-rKH", listOf("កាស\u200bនឹង\u200bបិទ", "វិនាទី ទៀត"), setOf("km")),
        partial("kn-rIN", listOf("ಹೆಡ್\u200cಸೆಟ್ ಆಫ್ ಆಗುತ್ತಿದೆ"), setOf("kn")),
        partial("ky-rKG", listOf("гарнитура", "секундда өчөт"), setOf("ky")),
        partial("lo-rLA", listOf("ຊຸດຫູຟັງຈະປິດໃນ", "ວິນາທີ"), setOf("lo")),
        partial("lt", listOf("ausinės išsijungs po", "sek.")),
        partial("lv", listOf("austiņas tiks izslēgtas pēc")),
        partial("mk-rMK", listOf("слушалките се исклучуваат за"), setOf("mk")),
        partial("ml-rIN", listOf("സെക്കന്റിനുള്ളിൽ ഹെഡ്\u200cസെറ്റ് ഓഫ് ചെയ്യപ്പെടുന്നു"), setOf("ml")),
        partial("mn-rMN", listOf("чихэвч", "секундийн дараа унтарна"), setOf("mn")),
        partial("mr-rIN", listOf("हेडसेट बंद होत आहे"), setOf("mr")),
        partial("ms-rMY", listOf("set kepala akan dimatikan dalam", "saat"), setOf("ms")),
        partial("my-rMM", listOf("နားကြပ်သည်", "စက္ကန့်အကြာတွင် ပိတ်မည်"), setOf("my")),
        partial("my-rZG", listOf("နားၾကပ္သည္", "စကၠန႔္အၾကာတြင္ ပိတ္မည္"), setOf("my")),
        partial("nb", listOf("hodesettet slås av om")),
        partial("ne/ne-rNP", listOf("हेडसेट", "सेकेन्डमा अफ हुँदै छ"), setOf("ne")),
        partial("nl", listOf("headset wordt over", "uitgeschakeld")),
        partial("or", listOf("ହେଡସେଟ୍", "ସେକେଣ୍ତରେ ଟର୍ନ୍ ଅଫ୍ ହେଉଛି")),
        partial("pa-rIN", listOf("ਹੈੱਡਸੈੱਟ", "ਵਿੱਚ ਬੰਦ ਹੋ ਰਿਹਾ ਹੈ"), setOf("pa")),
        partial("pt-rBR", listOf("o fone de ouvido vai se desligar em"), setOf("pt")),
        partial("ro", listOf("căștile se vor opri în")),
        partial("si/si-rLK", listOf("හෙඩ්සෙටය තත්පර", "කින් ක්\u200dරියා විරහිත වේ"), setOf("si")),
        partial("sk", listOf("náhlavná súprava sa o", "vypne")),
        partial("sl", listOf("slušalke se bodo izklopile čez")),
        partial("sq-rAL", listOf("kaska do të fiket për"), setOf("sq")),
        partial("sr", listOf("слушалице се искључују за")),
        partial("sv", listOf("headsetet stängs av om")),
        partial("sw", listOf("kifaa cha sauti kitazima ndani ya sekunde")),
        partial("ta-rIN", listOf("ஹெட்செட் ஆஃப் செய்யப்படும்"), setOf("ta")),
        partial("te-rIN", listOf("హెడ్\u200cసెట్ ఆఫ్ అవుతోంది"), setOf("te")),
        partial("tg", listOf("гарнитура баъди", "сония хомӯш мешавад")),
        partial("tk", listOf("garnitura", "sekuntdan ýapylýar")),
        partial("tl", listOf("mag-o-off ang headset sa loob ng", "segundo")),
        partial("tr", listOf("başlık", "saniye içinde kapanacak")),
        partial("uk", listOf("гарнітура вимкнеться через")),
        partial("ur-rPK", listOf("ہیڈسیٹ", "سیکنڈ میں آف ہو جائے گا"), setOf("ur")),
        partial("uz-rUZ", listOf("garnitura", "soniyadan keyin oʻchadi"), setOf("uz")),
        partial("yo", listOf("ẹ̀ro àgbékọ́rí yóò kú ní ìṣẹ́jú àáyá"))
    )

    private val FULL_BY_LANGUAGE: Map<String, List<LocaleProfile>> =
        FULL_LOCALE_PROFILES.groupBy { it.language }

    private val PARTIAL_BY_LANGUAGE: Map<String, List<PartialTitlePattern>> = buildMap {
        val grouped = mutableMapOf<String, MutableList<PartialTitlePattern>>()
        for (pattern in PARTIAL_LOCALE_TITLE_PATTERNS) {
            for (lang in pattern.languages) {
                grouped.getOrPut(lang) { mutableListOf() }.add(pattern)
            }
        }
        putAll(grouped)
    }

    @Volatile
    private var cachedRules: ActiveLocaleRules? = null

    fun forLocale(locale: Locale): ActiveLocaleRules {
        return forSystemLocales(listOf(locale))
    }

    /**
     * Resolves matcher rules strictly for the first supported language in the system's
     * locale preference list (matching Android's `XrSystemUIRRO.apk` resource resolution),
     * plus the English fallback profile.
     */
    fun forSystemLocales(locales: List<Locale>): ActiveLocaleRules {
        val key = if (locales.isEmpty()) {
            "en"
        } else {
            locales.joinToString(",") { normalizeLanguageCode(it.language) }
        }
        cachedRules?.let { cached ->
            if (cached.cacheKey == key) return cached
        }

        val resolvedLanguage = locales
            .asSequence()
            .map { normalizeLanguageCode(it.language) }
            .firstOrNull { lang ->
                lang.isNotEmpty() && (lang in FULL_BY_LANGUAGE || lang in PARTIAL_BY_LANGUAGE)
            } ?: "en"

        val selectedProfiles = mutableListOf<LocaleProfile>()
        FULL_BY_LANGUAGE[resolvedLanguage]?.let { selectedProfiles.addAll(it) }
        if (resolvedLanguage != "en") {
            selectedProfiles.add(ENGLISH_PROFILE)
        }

        val selectedPartials = PARTIAL_BY_LANGUAGE[resolvedLanguage].orEmpty()
        val activeButtons = selectedProfiles.flatMap { it.buttonTexts }.toHashSet()

        val rules = ActiveLocaleRules(
            cacheKey = key,
            resolvedLanguage = resolvedLanguage,
            fullProfiles = selectedProfiles,
            partialTitlePatterns = selectedPartials,
            buttonTexts = activeButtons
        )
        cachedRules = rules
        return rules
    }

    internal fun normalizeLanguageCode(rawLanguage: String?): String {
        return when (val lang = rawLanguage.orEmpty().trim().lowercase(Locale.ROOT)) {
            "in" -> "id"   // Legacy Java ISO-639 code for Indonesian
            "iw" -> "he"   // Legacy Java ISO-639 code for Hebrew
            "fil" -> "tl"  // Android Filipino maps to Tagalog (tl) in XrSystemUIRRO
            "no" -> "nb"   // Generic Norwegian maps to Norwegian Bokmål (nb)
            else -> lang
        }
    }

    private fun partial(
        locale: String,
        parts: List<String>,
        languages: Set<String> = setOf(locale)
    ): PartialTitlePattern = PartialTitlePattern(locale = locale, languages = languages, parts = parts)

    private fun matchesAllParts(haystack: String, parts: List<String>): Boolean {
        for (part in parts) {
            if (!haystack.contains(part)) return false
        }
        return true
    }
}
