import dev.caicompanion.app.HostBlocker
import dev.caicompanion.app.UrlPolicy
import dev.caicompanion.app.FilterEngine
import dev.caicompanion.app.BoundedText
import dev.caicompanion.app.BrowserProfiles
import dev.caicompanion.app.SafeFilename
import dev.caicompanion.app.DownloadPolicy
import dev.caicompanion.app.RuleListParser
import dev.caicompanion.app.IncomingLinkParser
import dev.caicompanion.app.BrowserProfile
import dev.caicompanion.app.BrowserSessionPolicy
import dev.caicompanion.app.RendererRecoveryBudget
import dev.caicompanion.app.NativeLibrarySearch
import dev.caicompanion.app.NativeWorkspaceNavigation
import dev.caicompanion.app.NativeStartupPolicy
import dev.caicompanion.app.DeferredWebReload
import dev.caicompanion.app.OfflineBookmarkEditor
import dev.caicompanion.app.SupportReport
import dev.caicompanion.app.DeviceCompatibility
import dev.caicompanion.app.GeckoTransferPolicy
import java.io.StringReader

fun main() {
    // A cold local-library start never creates Chromium. Explicit shared links bypass it.
    check(NativeStartupPolicy.useHub(true, null))
    check(!NativeStartupPolicy.useHub(false, null))
    check(!NativeStartupPolicy.useHub(true, "https://character.ai/chat?id=abc"))
    check(!NativeStartupPolicy.useHub(false, "https://character.ai/chat?id=abc"))
    check(NativeWorkspaceNavigation.safeDestination("https://character.ai/chat/abc?access_token=secret") ==
        "https://character.ai/chat/abc")
    check(NativeWorkspaceNavigation.safeDestination("https://evil.example/path") == "https://character.ai/")
    check(NativeWorkspaceNavigation.safeDestination(null) == "https://character.ai/")
    check(NativeWorkspaceNavigation.needsInitialLoad(null))
    check(NativeWorkspaceNavigation.needsInitialLoad(""))
    check(NativeWorkspaceNavigation.needsInitialLoad("about:blank"))
    check(!NativeWorkspaceNavigation.needsInitialLoad("https://character.ai/"))
    check(!NativeWorkspaceNavigation.shouldResumeRenderer(false, false))
    check(!NativeWorkspaceNavigation.shouldResumeRenderer(false, true))
    check(!NativeWorkspaceNavigation.shouldResumeRenderer(true, true))
    check(NativeWorkspaceNavigation.shouldResumeRenderer(true, false))
    val library = listOf(
        "Tessa | Gothic" to "https://character.ai/chat/a",
        " Hunter Stories " to "https://character.ai/chat/b?access_token=redacted",
        "NOT SAFE" to "https://evil.example/chat/c",
        "Duplicate" to "https://character.ai/chat/a"
    )
    check(NativeLibrarySearch.filter(library, "").size == 2)
    check(NativeLibrarySearch.filter(library, "GOTHIC").single().first == "Tessa | Gothic")
    check(NativeLibrarySearch.filter(listOf(
        "First title" to "https://character.ai/chat/same",
        "Hunter alias" to "https://character.ai/chat/same"
    ), "hunter").single().first == "Hunter alias")

    check(NativeLibrarySearch.filter(library, "hunter chat").single().second == "https://character.ai/chat/b")
    check(NativeLibrarySearch.filter(library, "not-safe").isEmpty())
    check(NativeLibrarySearch.filter(library, "", 1).size == 1)
    check(NativeLibrarySearch.filter(library, "", 0).isEmpty())
    check(NativeLibrarySearch.filter(library, "not found").isEmpty())
    check(NativeLibrarySearch.filter(library, "HUNTER  chat", 2).size == 1)
    check(NativeLibrarySearch.filter(library, "GOTHIC", 0).isEmpty())
    check(NativeLibrarySearch.filter(emptyList(), "").isEmpty())
    check(NativeLibrarySearch.filter(listOf("Bad" to "https://accounts.google.com/callback"), "").isEmpty())
    check(NativeLibrarySearch.filter(listOf("Good" to "https://character.ai/"), "Good").single().first == "Good")

    val crashBudget = RendererRecoveryBudget(3, 120_000L)
    check(crashBudget.allowAutomaticRestart(10_000))
    check(crashBudget.allowAutomaticRestart(10_100))
    check(crashBudget.allowAutomaticRestart(10_200))
    check(!crashBudget.allowAutomaticRestart(10_300))
    check(crashBudget.allowAutomaticRestart(130_000))
    check(!crashBudget.allowAutomaticRestart(130_001))
    check(crashBudget.allowAutomaticRestart(130_100))
    check(!RendererRecoveryBudget(1, 300).let { it.allowAutomaticRestart(1) && it.allowAutomaticRestart(2) })
    check(RendererRecoveryBudget(1, 300).allowAutomaticRestart(300))
    check(DownloadPolicy.classify("https://cdn.evil.example/file.apk") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("https://a.character.ai/file.txt") == DownloadPolicy.Kind.SAFE_SYSTEM)

    val filters = HostBlocker(
        listOf("# ignore", "doubleclick.net", "example.com", "evilnotallowed.net", "http://bad.invalid"),
        listOf("tracker.test", "..invalid", "foo*.com"),
        listOf("safe.example.com")
    )
    check(filters.shouldBlock("https://doubleclick.net/a"))
    check(filters.shouldBlock("https://foo.doubleclick.net/a"))
    check(!filters.shouldBlock("https://notdoubleclick.net/a"))
    check(!filters.shouldBlock("https://safe.example.com/a"))
    check(filters.shouldBlock("https://other.example.com/a"))
    check(!filters.shouldBlock("https://character.ai/chats"))
    check(!filters.shouldBlock("https://foo.character.ai/chats"))
    check(!filters.shouldBlock("https://c.ai"))
    check(!filters.shouldBlock("https://foo.c.ai"))
    check(!filters.shouldBlock("https://character.ai.attacker.com/"))
    check(!filters.shouldBlock("file:///etc/passwd"))
    check(!filters.shouldBlock("javascript:alert(1)"))
    check(!filters.shouldBlock("https://example.com.evil.org"))
    check(filters.shouldBlock("http://tracker.test/a"))
    check(!filters.shouldBlock("https://www.foo.com"))
    check(HostBlocker.normalizeRule("a..b") == null)
    check(HostBlocker.normalizeRule("https://a.com/") == null)
    check(HostBlocker.normalizeRule("dot.example") == "dot.example")
    check(UrlPolicy.isHttps("https://character.ai/"))
    check(!UrlPolicy.isHttps("http://character.ai/"))
    check(!UrlPolicy.isHttps("https://evil@character.ai/"))
    check(!UrlPolicy.isHttps("intent://character.ai"))
    check(UrlPolicy.isInternal("https://character.ai/"))
    check(UrlPolicy.isInternal("https://accounts.google.com/"))
    check(!UrlPolicy.isInternal("https://notcharacter.ai/"))
    check(!UrlPolicy.isInternal("https://character.ai.evil.test/"))
    check(HostBlocker.normalizeRule("-invalid.example") == null)
    check(!UrlPolicy.isTrustedMediaOrigin("https://accounts.google.com"))
    check(UrlPolicy.safeBookmark("https://foo.c.ai/"))
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/abc?char=123#fragment") ==
        "https://character.ai/chat/abc?char=123")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/abc?access_token=secret") ==
        "https://character.ai/chat/abc")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/abc?c%6fde=123") ==
        "https://character.ai/chat/abc")
    check(UrlPolicy.sanitizeBookmark("https://accounts.google.com/?code=123") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai:8443/chat") == null)
    check(!UrlPolicy.isHttps("https://character.ai:9999/"))
    check(UrlPolicy.safeShareUrl("https://character.ai/chat/abc?char=1#fragment") ==
        "https://character.ai/chat/abc?char=1")
    check(UrlPolicy.safeShareUrl("https://accounts.google.com/o/oauth2?code=secret") == null)
    check(UrlPolicy.safeShareUrl("https://evil.test/?code=secret") == null)
    check(UrlPolicy.safeShareUrl("https://character.ai/login?token=secret") == null)
    check(UrlPolicy.safeShareUrl("https://character.ai/%6fAuth%2fcallback?code=secret") == null)
    check(UrlPolicy.safeShareUrl("https://character.ai/account") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/public") == "https://character.ai/chat/public")
    check(UrlPolicy.isIdentityUrl("https://accounts.google.com/o/oauth2/auth"))
    check(!UrlPolicy.isIdentityUrl("https://accounts.google.com.evil.org"))
    check(BoundedText.read(StringReader("test"), 4) == "test")
    check(BoundedText.read(StringReader("test"), 3) == null)
    check(BoundedText.read(StringReader(""), 4) == "")
    val strictEngine = FilterEngine(listOf("doubleclick.net"), listOf("sentry.io"))
    check(!strictEngine.shouldBlock("https://sentry.io/"))
    strictEngine.strict = true
    strictEngine.replace("", "")
    check(strictEngine.shouldBlock("https://sentry.io/"))
    strictEngine.replace("", "sentry.io")
    check(!strictEngine.shouldBlock("https://sentry.io/"))
    check(strictEngine.shouldBlock("https://doubleclick.net/"))
    val engine = FilterEngine(listOf("doubleclick.net"))
    check(engine.shouldBlock("https://a.doubleclick.net/x"))
    check(engine.blockedRequestCount.get() == 1L)
    engine.enabled = false
    check(!engine.shouldBlock("https://a.doubleclick.net/x"))
    engine.enabled = true
    engine.replace("tracker.test", "a.doubleclick.net")
    check(!engine.shouldBlock("https://a.doubleclick.net/x"))
    check(engine.shouldBlock("https://tracker.test/x"))
    check(BrowserProfiles.options(BrowserProfile.BALANCED).blocking)
    check(!BrowserProfiles.options(BrowserProfile.BALANCED).strictBlocking)
    check(!BrowserProfiles.options(BrowserProfile.COMPATIBILITY).blocking)
    check(BrowserProfiles.options(BrowserProfile.COMPATIBILITY).thirdPartyCookies)
    check(BrowserProfiles.options(BrowserProfile.PRIVATE).strictBlocking)
    check(!BrowserProfiles.options(BrowserProfile.PRIVATE).thirdPartyCookies)
    check(BrowserProfiles.options(BrowserProfile.DATA_SAVER).reduceImages)
    check(BrowserProfiles.options(BrowserProfile.DATA_SAVER).requireMediaGesture)
    val efficient = HostBlocker(
        listOf("ads.example", "example", "doubleclick.net", "specific.ads.example"),
        listOf("tracker.example"), listOf("allowed.ads.example", "sub.tracker.example")
    )
    check(efficient.matchedBlockedDomain("https://ads.example/pixel") == "ads.example")
    check(efficient.matchedBlockedDomain("https://cdn.ads.example/pixel") == "ads.example")
    check(efficient.matchedBlockedDomain("https://specific.ads.example/pixel") == "ads.example")
    check(efficient.matchedBlockedDomain("https://tracker.example/pixel") == "tracker.example")
    check(efficient.matchedBlockedDomain("https://foo.tracker.example/pixel") == "tracker.example")
    check(efficient.matchedBlockedDomain("https://sub.tracker.example/pixel") == null)
    check(efficient.matchedBlockedDomain("https://nested.sub.tracker.example/pixel") == null)
    check(efficient.matchedBlockedDomain("https://allowed.ads.example/pixel") == null)
    check(efficient.matchedBlockedDomain("https://nested.allowed.ads.example/pixel") == null)
    check(efficient.matchedBlockedDomain("https://notads.example/pixel") == null)
    check(efficient.matchedBlockedDomain("https://character.ai/") == null)
    check(efficient.matchedBlockedDomain("https://sub.c.ai/") == null)
    check(efficient.matchedBlockedDomain("https://www.doubleclick.net/") == "doubleclick.net")
    check(efficient.matchedBlockedDomain("https://evil@ads.example/") == null)
    check(efficient.matchedBlockedDomain("file:///ads.example") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/%252Flogin%252Fcallback") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/%25252Fauth%25252Ftoken") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/%2e%2e/login") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/%5caccount") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/%0afoo") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/normal") == "https://character.ai/chat/normal")
    check(DownloadPolicy.classify("https://cdn.character.ai/image.png") == DownloadPolicy.Kind.SAFE_SYSTEM)
    check(DownloadPolicy.classify("https://cdn.example.org/file.zip") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("https://cdn.example.org/file.zip?signature=abc") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("https://cdn.example.org/file.zip#fragment") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("https://accounts.google.com/hello") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("https://character.ai/oauth/token") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("https://character.ai/%2561uth%252Fcallback") == DownloadPolicy.Kind.BROWSER_ONLY)
    check(DownloadPolicy.classify("http://example.org/file.zip") == DownloadPolicy.Kind.REJECT)
    check(DownloadPolicy.classify("https://u:p@example.org/file.zip") == DownloadPolicy.Kind.REJECT)
    check(DownloadPolicy.classify("https://example.org:444/file.zip") == DownloadPolicy.Kind.REJECT)
    check(DownloadPolicy.classify("blob:https://character.ai/b") == DownloadPolicy.Kind.REJECT)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/1?q=secret") == "https://character.ai/chat/1")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/1?char=abc-123") == "https://character.ai/chat/1?char=abc-123")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/1?char=secret%2526token") == "https://character.ai/chat/1")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/%2525252525252Fauth/callback") == null)
    check(SafeFilename.from(null) == "download")
    check(SafeFilename.from("../../weird\nname?.zip") == "_.._weird_name_.zip")
    check(SafeFilename.from("test\u202Eexe.png") == "test_exe.png")
    check(SafeFilename.from("  normal.zip  ") == "normal.zip")
    check(SafeFilename.from("a".repeat(150)).length == 120)
    check(SafeFilename.from("....") == "download")
    val privateStats = FilterEngine(listOf("doubleclick.net", "ads.example"))
    check(privateStats.topBlockedRules().isEmpty())
    check(privateStats.shouldBlock("https://c.doubleclick.net/user/private?token=secret"))
    check(privateStats.shouldBlock("https://ads.example/private-chat"))
    check(privateStats.shouldBlock("https://c.doubleclick.net/another"))
    check(privateStats.blockedRequestCount.get() == 3L)
    check(privateStats.topBlockedRules().size == 2)
    check(privateStats.topBlockedRules()[0] == ("doubleclick.net" to 2L))
    check(privateStats.topBlockedRules().none { "private" in it.first || "secret" in it.first })
    check(privateStats.topBlockedRules(0).isEmpty())
    privateStats.clearStats()
    check(privateStats.blockedRequestCount.get() == 0L)
    check(privateStats.topBlockedRules().isEmpty())
    val concurrent = FilterEngine(listOf("ad.test"))
    val threads = (1..4).map { Thread { repeat(250) { concurrent.shouldBlock("https://sub.ad.test/") } } }
    threads.forEach { it.start() }; threads.forEach { it.join() }
    check(concurrent.blockedRequestCount.get() == 1000L)
    check(concurrent.topBlockedRules()[0] == ("ad.test" to 1000L))
    // A manually imported list is deliberately not a full cosmetic or URL-path adblock engine.
    val imported = RuleListParser.parse("""
        ! example list
        [Adblock Plus 2.0]
        0.0.0.0 ads.cdn.example
        ||tracking.example^
        @@||safe.tracking.example^
        plain.example
        ||images.example^${'$'}third-party
        example.org##.ad-banner
        ||accounts.google.com^
        ||character.ai^
    """.trimIndent())!!
    check(imported.blocked == setOf("ads.cdn.example", "tracking.example", "plain.example"))
    check(imported.allowed == setOf("safe.tracking.example"))
    check(imported.unsupported == 4)
    check(imported.size == 4)
    check(RuleListParser.parse(imported.canonicalText())!!.blocked == imported.blocked)
    check(RuleListParser.parse(imported.canonicalText())!!.allowed == imported.allowed)
    check(RuleListParser.parse("||bad.example^")!!.blocked == setOf("bad.example"))
    check(RuleListParser.parse("@@||bad.example^")!!.allowed == setOf("bad.example"))
    check(RuleListParser.parse("0.0.0.0 localhost") == null)
    check(RuleListParser.parse("0.0.0.0\tspam.example")!!.blocked == setOf("spam.example"))
    check(RuleListParser.parse("::1\tspam2.example")!!.blocked == setOf("spam2.example"))
    check(RuleListParser.parse("0.0.0.0 spam.example another.example") == null)
    check(RuleListParser.parse("@@||accounts.google.com^") == null)
    check(RuleListParser.parse("||character.ai^") == null)
    check(RuleListParser.parse("||c.ai^") == null)
    check(RuleListParser.parse("https://ads.example/path") == null)
    check(RuleListParser.parse("example.org##.ad") == null)
    check(RuleListParser.parse("||a.example^\n".repeat(RuleListParser.MAX_LINES + 1)) == null)
    check(RuleListParser.parse("x".repeat(RuleListParser.MAX_CHARACTERS + 1)) == null)
    check(RuleListParser.parse("||ex.example^\n".repeat(3))!!.size == 1)
    // Oversized rulesets fail as a whole rather than silently truncating protection.
    val hugeRules = buildString {
        for (i in 1..RuleListParser.MAX_DOMAINS + 1) append("host$i.example\n")
    }
    check(RuleListParser.parse(hugeRules) == null)
    val importedEngine = FilterEngine(listOf("doubleclick.net"))
    importedEngine.replace("", "", imported)
    check(importedEngine.shouldBlock("https://tracking.example/banner"))
    check(!importedEngine.shouldBlock("https://safe.tracking.example/banner"))
    check(importedEngine.shouldBlock("https://ads.cdn.example/banner"))
    check(importedEngine.shouldBlock("https://cdn.plain.example/"))
    check(!importedEngine.shouldBlock("https://accounts.google.com/auth"))
    check(!importedEngine.shouldBlock("https://character.ai/login"))
    importedEngine.replace("", "", imported) // same config: cache must preserve rules/statistics
    check(importedEngine.blockedRequestCount.get() == 3L)
    importedEngine.replace("", "")
    check(!importedEngine.shouldBlock("https://tracking.example/banner"))
    check(importedEngine.shouldBlock("https://doubleclick.net/x"))
    // Never allow a shared URL to expand navigation beyond public Character.AI content.
    check(IncomingLinkParser.safeSharedLink("https://character.ai/") == "https://character.ai/")
    check(IncomingLinkParser.safeSharedLink("Check https://character.ai/chat?id=abc-42! ") == "https://character.ai/chat?id=abc-42")
    check(IncomingLinkParser.safeSharedLink("https://character.ai.evil.test/x") == null)
    check(IncomingLinkParser.safeSharedLink("https://evil.example/a https://character.ai/character/abc") == "https://character.ai/character/abc")
    check(IncomingLinkParser.safeSharedLink("https://accounts.google.com/signin") == null)
    check(IncomingLinkParser.safeSharedLink("https://character.ai/login?token=secret") == null)
    check(IncomingLinkParser.safeSharedLink("https://character.ai/oauth/callback") == null)
    check(IncomingLinkParser.safeSharedLink("https://character.ai/?token=SECRET") == "https://character.ai/")
    check(IncomingLinkParser.safeSharedLink("www.character.ai/chat") == null)
    check(IncomingLinkParser.safeSharedLink("x".repeat(16_385)) == null)
    check(IncomingLinkParser.safeSharedLink(null) == null)
    check(IncomingLinkParser.safeSharedLink("https://c.ai/abc#session=hidden") == "https://c.ai/abc")
    check(IncomingLinkParser.safeSharedLink("https://character.ai/@trusted/one)") == "https://character.ai/@trusted/one")
    // Browser-backed session always starts from a safe first-party URL. Cookies
    // are not moved from WebView; OAuth redirects are deliberately discarded.
    check(BrowserSessionPolicy.safeTabUrl(null) == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://character.ai/chat?id=abc") == "https://character.ai/chat?id=abc")
    check(BrowserSessionPolicy.safeTabUrl("https://character.ai/chat?id=abc#token=secret") == "https://character.ai/chat?id=abc")
    check(BrowserSessionPolicy.safeTabUrl("https://character.ai/account/oauth?code=TOPSECRET") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://accounts.google.com/o/oauth2/v2/auth?code=X") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://character.ai.evil.test/a") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://evil@character.ai/") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("javascript:alert(1)") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://c.ai/chats") == "https://c.ai/chats")
    check(BrowserSessionPolicy.safeTabUrl("https://c.ai:444/chats") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://character.ai/?token=SECRET") == "https://character.ai/")
    check(BrowserSessionPolicy.safeTabUrl("https://character.ai/../auth") == "https://character.ai/")
    // Minimal, user-initiated diagnostics cannot accidentally expose a URL or token.
    val support = SupportReport.format(SupportReport.Snapshot(
        appVersion = "0.12.0-rc1\nhttps://example.test/?token=SENSITIVE",
        androidApi = 37,
        webViewPackage = "com.android.webview\nAuthorization:Bearer SECRET",
        webViewVersion = "150.0.0.0", javaHeapMb = 120,
        filterRuleCount = 42, importedRuleCount = 7,
        blockedRequests = 99L, rendererRestarts = 2,
        protectionEnabled = true, strictBlocking = false
    ))
    check("Android 17 runtime: yes" in support)
    check("Imported domains: 7" in support)
    check("Automatic renderer restarts: 2" in support)
    check(!support.contains("https://"))
    check(!support.contains("SENSITIVE"))
    check(!support.contains("Authorization"))
    check(!support.contains("Bearer"))
    check(support.length < 800)
    val budgetForReport = RendererRecoveryBudget(maximum = 2, windowMillis = 10_000)
    check(budgetForReport.recentRestartCount(0L) == 0)
    check(budgetForReport.allowAutomaticRestart(1000L))
    check(budgetForReport.recentRestartCount(1001L) == 1)
    check(budgetForReport.allowAutomaticRestart(2000L))
    check(!budgetForReport.allowAutomaticRestart(3000L))
    check(budgetForReport.recentRestartCount(11_000L) == 1)
    check(budgetForReport.recentRestartCount(12_000L) == 0)

    // Android 17 Pixel 8a / LineageOS compatibility: only generic, local facts.
    val pixel = DeviceCompatibility.Snapshot(
        manufacturer = "Google", model = "Pixel 8a", device = "akita",
        androidApi = 37, androidRelease = "17",
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        refreshRateHz = 120.0f, memoryPageBytes = 16384L, webViewPackage = "com.google.android.webview",
        webViewVersion = "150.0.0.1", gmsCompatiblePackageVisible = false
    )
    check(DeviceCompatibility.isPixel8a(pixel))
    val pixelReport = DeviceCompatibility.format(pixel)
    check("Pixel 8a (akita)" in pixelReport)
    check("Android 17: detected by API" in pixelReport)
    check("120 Hz" in pixelReport)
    check("Kernel page size: 16 KiB" in pixelReport)
    val morpheStack = pixel.copy(
        gmsCompatiblePackageVisible = true,
        gmsPackageVersion = "25.38.40",
        morpheMicrogCurrentVisible = true,
        morpheMicrogCurrentVersion = "7.3.2",
        morpheMicrogVisible = true,
        morpheMicrogVersion = "0.3.1.4"
    )
    val morpheReport = DeviceCompatibility.format(morpheStack)
    check("System GMS package version: 25.38.40" in morpheReport)
    check("Morphe microG current (app.revanced.android.gms): visible" in morpheReport)
    check("Morphe current package version: 7.3.2" in morpheReport)
    check("Morphe microG legacy (app.morphe.android.gms): visible" in morpheReport)
    check("Morphe legacy package version: 0.3.1.4" in morpheReport)
    check("does NOT prove Character.AI sign-in" in morpheReport)
    check("Morphe microG current (app.revanced.android.gms): not visible" in
        DeviceCompatibility.format(pixel.copy(morpheMicrogCurrentVisible = false)))
    check("Morphe microG current (app.revanced.android.gms): unknown" in
        DeviceCompatibility.format(pixel.copy(morpheMicrogCurrentVisible = null)))
    check("Morphe microG legacy (app.morphe.android.gms): not visible" in
        DeviceCompatibility.format(pixel.copy(morpheMicrogVisible = false)))
    check("Morphe microG legacy (app.morphe.android.gms): unknown" in
        DeviceCompatibility.format(pixel.copy(morpheMicrogVisible = null)))
    check("Morphe current package version: unknown" in DeviceCompatibility.format(
        morpheStack.copy(morpheMicrogCurrentVersion = "7.3.2\nSECRETS")
    ))
    check("Morphe legacy package version: unknown" in DeviceCompatibility.format(
        morpheStack.copy(morpheMicrogVersion = "0.3.1\nSECRETS")
    ))
    check("Kernel page size: 4 KiB" in DeviceCompatibility.format(pixel.copy(memoryPageBytes = 4096L)))
    check("Kernel page size: unknown" in DeviceCompatibility.format(pixel.copy(memoryPageBytes = null)))
    check("arm64-v8a" in pixelReport)
    check("no (browser-based sign-in may differ)" in pixelReport)
    check("LineageOS version: not attested" in pixelReport)
    check("OAuth, voice, Play Protect and performance: NOT TESTED" in pixelReport)
    check(!pixelReport.contains("serial="))
    val unknownRom = pixel.copy(manufacturer = "Other", device = "akita", model = "My Phone")
    check(!DeviceCompatibility.isPixel8a(unknownRom))
    val suspicious = pixel.copy(
        model = "Pixel 8a\nAuthorization: Bearer SECRET",
        device = "akita\nhttps://bad.example/?token=LEAK",
        webViewPackage = "com.android.webview\nhttps://attacker.example",
        webViewVersion = "150.1\nBearer SECRET",
        supportedAbis = listOf("arm64-v8a", "leak SECRET", "x86_64"),
        refreshRateHz = Float.NaN, gmsCompatiblePackageVisible = null
    )
    val sanitized = DeviceCompatibility.format(suspicious)
    check(!sanitized.contains("SECRET"))
    check(!sanitized.contains("https://"))
    check("Model: unknown" in sanitized)
    check("Current display rate: unknown" in sanitized)
    check("GMS-compatible package visible: unknown" in sanitized)
    check("x86_64" in sanitized)
    check(DeviceCompatibility.format(pixel.copy(androidApi = 36)).contains("Android 17: not detected"))
    check(DeviceCompatibility.format(pixel.copy(refreshRateHz = 2401.0f)).contains("display rate: unknown"))

    val pendingRefresh = DeferredWebReload()
    check(!pendingRefresh.isPending())
    pendingRefresh.markPending()
    check(pendingRefresh.isPending())
    check(!pendingRefresh.consumeIfVisible(activityResumed = false, workspaceVisible = false))
    check(!pendingRefresh.consumeIfVisible(activityResumed = true, workspaceVisible = true))
    check(pendingRefresh.isPending())
    check(pendingRefresh.consumeIfVisible(activityResumed = true, workspaceVisible = false))
    check(!pendingRefresh.consumeIfVisible(activityResumed = true, workspaceVisible = false))
    pendingRefresh.markPending()
    pendingRefresh.consumedByNavigation()
    check(!pendingRefresh.isPending())

    check(OfflineBookmarkEditor.normalize(" Hunter ", " https://character.ai/ ") ==
        ("Hunter" to "https://character.ai/"))
    check(OfflineBookmarkEditor.normalize("", "https://character.ai/") ==
        ("Character.AI" to "https://character.ai/"))
    check(OfflineBookmarkEditor.normalize("A\u202eB\u2066C", "https://character.ai/")?.first == "ABC")
    check(OfflineBookmarkEditor.normalize("hunter", "https://evil.example/") == null)
    check(OfflineBookmarkEditor.normalize("hunter", "https://character.ai/oauth/callback") == null)
    check(OfflineBookmarkEditor.normalize("hunter", "https://character.ai/?token=secret")?.second == "https://character.ai/")
    check(OfflineBookmarkEditor.normalize("x".repeat(200), "https://c.ai/")?.first?.length == 90)
    // Gecko SAF transfers: untrusted URLs never become external Android downloads.
    check(GeckoTransferPolicy.isDownloadSourceAllowed("https://character.ai/chat/file.pdf", true, 200))
    check(!GeckoTransferPolicy.isDownloadSourceAllowed("https://evil.example/file", true, 200))
    check(!GeckoTransferPolicy.isDownloadSourceAllowed("http://character.ai/file", false, 200))
    check(!GeckoTransferPolicy.isDownloadSourceAllowed("https://character.ai/file", true, 403))
    check(!GeckoTransferPolicy.isDownloadSourceAllowed("https://character.ai@evil.example/file", true, 200))
    check(GeckoTransferPolicy.filename("https://character.ai/a/Hunter%20notes.pdf?token=SECRET") == "Hunter notes.pdf")
    check(!GeckoTransferPolicy.filename("https://character.ai/file?token=SECRET").contains("SECRET"))
    check(!GeckoTransferPolicy.filename("https://character.ai/evil%2Fname%E2%80%AE.exe").contains('\u202E'))
    check(GeckoTransferPolicy.mimeType("application/pdf; charset=utf-8") == "application/pdf")
    check(GeckoTransferPolicy.mimeType("text/plain\nX-Auth: Bearer") == "application/octet-stream")
    check(GeckoTransferPolicy.mimeType(null) == "application/octet-stream")
    check(GeckoTransferPolicy.acceptedMimeTypes(arrayOf("image/png", "IMAGE/JPEG", "invalid", "image/png")) ==
        listOf("image/png", "image/jpeg"))
    check(GeckoTransferPolicy.acceptedMimeTypes(null) == listOf("*/*"))
    check(GeckoTransferPolicy.isSelectedDocumentUri("content://com.android.providers.media.documents/document/image%3A123"))
    check(!GeckoTransferPolicy.isSelectedDocumentUri("file:///sdcard/secret.txt"))
    check(!GeckoTransferPolicy.isSelectedDocumentUri("content://user:pass@provider/path"))
    val copied = java.io.ByteArrayOutputStream()
    check(GeckoTransferPolicy.copy(java.io.ByteArrayInputStream(byteArrayOf(1,2,3,4)), copied, 4) == 4L)
    check(copied.toByteArray().contentEquals(byteArrayOf(1,2,3,4)))
    check(runCatching { GeckoTransferPolicy.copy(
        java.io.ByteArrayInputStream(byteArrayOf(1,2,3,4)), java.io.ByteArrayOutputStream(), 3)
    }.exceptionOrNull() is java.io.IOException)
    println("PolicySmoke: browser policy, concurrency, URL safety and local statistics assertions passed")
}
