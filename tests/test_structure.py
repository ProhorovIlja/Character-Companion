import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]

class Structure(unittest.TestCase):
    def test_manual_windows_android_build_entrypoints(self):
        batch = (ROOT/'gradlew.bat').read_text()
        script = (ROOT/'scripts/build-apk-windows.ps1').read_text()
        self.assertIn('org.gradle.wrapper.GradleWrapperMain %*', batch)
        self.assertIn('Get-FileHash -Algorithm SHA256', script)
        self.assertIn('81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f', script)
        self.assertIn(':app:assembleGeckoDebug', script)
        self.assertIn(':app:assembleLegacyDebug', script)
        self.assertIn('android-36', script)
        self.assertIn('if ($result -ne 0)', script)
        self.assertIn('DEBUG_ONLY=YES', script)
        self.assertFalse((ROOT/'.github/workflows').exists())

    def test_resource_xml(self):
        for path in (ROOT/'app/src/main/res').rglob('*.xml'):
            with self.subTest(path=path):
                ET.parse(path)
        ET.parse(ROOT/'app/src/main/AndroidManifest.xml')

    def test_offline_native_bookmark_editor_and_deferred_reload(self):
        activity = (ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('createOfflineBookmark { refreshNativeBookmarks() }', activity)
        self.assertIn('deferredWebReload.markPending()', activity)
        self.assertIn('flushDeferredWebReload()', activity)
        self.assertIn('deferredWebReload.consumedByNavigation()', activity)
        self.assertNotIn('closeNativeHub()\n                showNativeHub()', activity)
        self.assertNotIn('if (reload) webView.reload()', activity)
        self.assertIn('OfflineBookmarkEditor.normalize(', activity)
        self.assertIn('R.string.hub_bookmark_rename', activity)
        self.assertLess(activity.index('deferredWebReload.consumedByNavigation()\n        if (!ensureWebView())'), activity.index('webView.loadUrl(safeUrl)'))
        self.assertIn('OfflineBookmarkEditor.normalize(entry.optString("title"), url)', (ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text())
        self.assertIn('keyboardVisible || fullscreenView != null || nativeHub != null', activity)

    def test_native_workspace_is_webview_free_until_navigation(self):
        # Eager XML inflation and default-UA lookups defeat the offline startup design.
        activity = (ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        layout = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
        self.assertNotIn('<WebView', layout)
        self.assertNotIn('@+id/web_view', layout)
        self.assertIn('private fun ensureWebView(): Boolean', activity)
        self.assertIn('errorOverlay.visibility = View.GONE\n            openFirstPartyPage(lastUrl)', activity)
        self.assertIn('WebView(this)', activity)
        self.assertIn('NativeStartupPolicy.useHub(store.startInHub, initialShare)', activity)
        self.assertIn('else openFirstPartyPage(lastUrl)', activity)
        self.assertIn('if (intent.action == Intent.ACTION_SEND && ::container.isInitialized)', activity)
        self.assertIn('if (::webView.isInitialized) webView.onPause()', activity)
        self.assertIn('if (::webView.isInitialized) CookieManager.getInstance().flush()', activity)
        self.assertIn('if (::webView.isInitialized) {\n            container.removeView(webView)', activity)
        self.assertIn('header.visibility = View.GONE\n        updateFooterVisibility()', activity)
        startup = activity.split('override fun onCreate', 1)[1].split('private fun configureWebView', 1)[0]
        self.assertNotIn('webView = findViewById', startup)
        self.assertNotIn('WebSettings.getDefaultUserAgent(this)\n        installServiceWorkerFilter()', startup)
        self.assertFalse(list(ROOT.rglob('*.apk')))

    def test_pixel8a_lineageos_runtime_diagnostics(self):
        activity = (ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        probe = (ROOT/'app/src/main/java/dev/caicompanion/app/DeviceRuntimeProbe.kt').read_text()
        policy = (ROOT/'app/src/main/java/dev/caicompanion/app/DeviceCompatibility.kt').read_text()
        manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
        script = (ROOT/'scripts/test-offline.sh').read_text()
        self.assertIn('DeviceCompatibility.format(snapshot)', activity)
        self.assertIn('DeviceRuntimeProbe.capture(this)', activity)
        self.assertIn('DeviceCompatibility,GeckoNavigationPolicy,GeckoTransferPolicy,GeckoTransferOriginPolicy,ExternalBrowserPolicy}.kt', script)
        self.assertIn('Build.SUPPORTED_ABIS.toList()', probe)
        self.assertIn('Os.sysconf(OsConstants._SC_PAGESIZE)', probe)
        self.assertIn('Build.VERSION.SDK_INT', probe)
        self.assertIn('WebView.getCurrentWebViewPackage()', probe)
        self.assertIn('<package android:name="com.google.android.gms" />', manifest)
        self.assertIn('gmsCompatiblePackageVisible', policy)
        self.assertIn('app.morphe.android.gms', manifest)
        self.assertIn('app.morphe.android.gms', probe)
        self.assertIn('app.revanced.android.gms', manifest)
        self.assertIn('app.revanced.android.gms', probe)
        self.assertIn('morpheMicrogCurrentVisible', policy)
        self.assertEqual(manifest.count('<package android:name='), 3)
        preflight = (ROOT/'scripts/device-android17-preflight.sh').read_text()
        self.assertIn('app.revanced.android.gms', preflight)
        self.assertIn('app.morphe.android.gms', preflight)
        self.assertIn('probePackage(activity.packageManager', probe)
        self.assertIn('morpheMicrogVisible', policy)
        self.assertIn('GMS and Morphe microG have different package IDs', policy)

        self.assertNotIn('Build.SERIAL', probe)
        self.assertNotIn('Build.FINGERPRINT', probe)
        self.assertNotIn('getAccounts(', probe)
        self.assertFalse((ROOT/'.github/workflows').exists())

    def test_android_only(self):
        self.assertTrue((ROOT/'app/build.gradle.kts').exists())
        self.assertFalse((ROOT/'src/windows').exists())

    def test_android_resource_references(self):
        # Catch unresolved string and ID names before a full Android SDK build.
        sources = '\n'.join(p.read_text() for p in (ROOT/'app/src/main/java').rglob('*.kt'))
        strings_xml = ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        defined_strings = {node.attrib['name'] for node in strings_xml.findall('string')}
        missing_strings = set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', sources)) - defined_strings
        self.assertEqual(missing_strings, set())
        layout = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
        defined_ids = set(re.findall(r'@\+id/([A-Za-z0-9_]+)', layout))
        missing_ids = set(re.findall(r'R\.id\.([A-Za-z0-9_]+)', sources)) - defined_ids
        self.assertEqual(missing_ids, set())

    def test_privacy_defaults(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:allowBackup="false"',manifest)
        self.assertIn('android:usesCleartextTraffic="false"',manifest)
        self.assertIn('android:exported="false"',manifest)
        self.assertIn('android:exported="true"', (ROOT/'app/src/gecko/AndroidManifest.xml').read_text())
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('handler.cancel()',activity)
        self.assertIn('onRenderProcessGone',activity)
        self.assertIn('ServiceWorkerControllerCompat',activity)
        self.assertNotIn('@JavascriptInterface',activity)

    def test_manual_profiles_and_no_api_proxy(self):
        profiles=(ROOT/'app/src/main/java/dev/caicompanion/app/BrowserProfile.kt').read_text()
        store=(ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text()
        self.assertIn('BrowserProfile.DATA_SAVER', profiles)
        self.assertIn('BrowserProfile.COMPATIBILITY', profiles)
        self.assertIn('fun applyProfile(profile: BrowserProfile)', store)
        self.assertNotIn('retrofit2', ''.join(p.read_text() for p in ROOT.rglob('*.kt')))

    def test_navigation_media_and_link_safety(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('onShowCustomView', activity)
        self.assertIn('WindowManager.LayoutParams.FLAG_FULLSCREEN', activity)
        self.assertIn('download_confirm_title', (ROOT/'app/src/main/res/values/strings.xml').read_text())
        self.assertIn('enqueueDownload(safeUrl, agent, disposition, mime)', activity)
        self.assertIn('leaveFullscreen()', activity)
        self.assertIn('request.hasGesture()', activity)
        self.assertIn('UrlPolicy.safeShareUrl(webView.url)', activity)
        self.assertIn('denyPendingMedia()', activity)
        self.assertNotIn('webView.url?.takeIf(UrlPolicy::isHttps)', activity)

    def test_android17_runtime_strategy_is_explicit(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        build=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('android:resizeableActivity="true"', manifest)
        # AGP 8.13 supports max SDK 36.1: targeting 37 requires a toolchain migration.
        self.assertIn('compileSdk = 36', build)
        self.assertIn('targetSdk = 36', build)
        self.assertIn('API 37', (ROOT/'docs/ANDROID_17.md').read_text())
        preflight=(ROOT/'scripts/device-android17-preflight.sh').read_text()
        self.assertIn('ANDROID_17_RUNTIME=DETECTED', preflight)
        self.assertNotIn('shell logcat', preflight)

    def test_manual_xapk_audit_has_no_proprietary_artifacts(self):
        tool=(ROOT/'tools/inspect_xapk.py').read_text()
        splitter=(ROOT/'tools/split_xapk.py').read_text()
        self.assertIn('validate_info', tool)
        self.assertIn('parse_axml_manifest', tool)
        self.assertIn('inspect_apk', tool)
        self.assertIn('MAX_EXPANDED', tool)
        self.assertIn('--parts', tool)
        self.assertIn('190 * 1024**2', splitter)
        self.assertFalse(list(ROOT.rglob('*.xapk')))
        self.assertFalse(list(ROOT.rglob('*.apk')))

    def test_no_scheduled_actions(self):
        self.assertFalse((ROOT/'.github/workflows').exists())
        source=''.join(p.read_text(errors='ignore') for p in ROOT.rglob('*.kt'))
        self.assertNotIn('WorkManager',source)
        self.assertNotIn('AlarmManager',source)
        self.assertNotIn('JobScheduler',source)

    def test_manifest_has_minimum_permissions(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        permissions=set(re.findall(r'android.permission.[A-Z_]+',manifest))
        self.assertEqual(permissions, {'android.permission.INTERNET','android.permission.RECORD_AUDIO','android.permission.CAMERA'})

    def test_import_memory_bound_and_atomicity(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        store=(ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text()
        self.assertIn('BoundedText.read(it, 200_000)', activity)
        self.assertNotIn('it.readText().take(200_001)', activity)
        self.assertIn('prefs.edit()\n            .putBoolean("blocking"', store)
        self.assertIn('OfflineBookmarkEditor.normalize(obj.optString("title"), u)', store)
        self.assertIn('put("bookmarks", sanitizedBookmarkArray())', store)
        self.assertIn('get() = UrlPolicy.sanitizeBookmark(prefs.getString("last_visited", "")).orEmpty()', store)

    def test_translations_cover_default_strings(self):
        en=ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        ru=ET.parse(ROOT/'app/src/main/res/values-ru/strings.xml').getroot()
        self.assertEqual({n.attrib.get('name') for n in en.findall('string')},
                         {n.attrib.get('name') for n in ru.findall('string')})

    def test_strict_rules_do_not_block_service(self):
        lines=(ROOT/'app/src/main/assets/strict-hosts.txt').read_text().splitlines()
        rules=[s.strip() for s in lines if s.strip() and not s.startswith('#')]
        self.assertGreaterEqual(len(rules), 15)
        self.assertFalse(any('character.ai' in r or 'c.ai' in r for r in rules))

    def test_download_cookie_isolation_and_safe_filenames(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertNotIn('request.addRequestHeader("Cookie"', activity)
        self.assertNotIn('CookieManager.getInstance().getCookie(', activity)
        self.assertIn('SafeFilename.from(URLUtil.guessFileName', activity)
        self.assertIn('setDestinationInExternalPublicDir', activity)

    def test_service_worker_callback_no_activity_capture(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        start=activity.index('    private fun installServiceWorkerFilter()')
        end=activity.index('    private fun requestMediaPermission(', start)
        handler=activity[start:end]
        self.assertIn('val currentFilter = filter', handler)
        self.assertIn('emptyBlockedResponse(currentFilter,', handler)
        self.assertNotIn('filterResponse(request.url.toString())', handler)

    def test_oauth_browser_handoff_and_session_privacy(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('if (UrlPolicy.isIdentityUrl(url))', activity)
        self.assertIn('confirmIdentityNavigation(url)', activity)
        self.assertIn('R.string.external_identity_message', activity)
        self.assertNotIn('@JavascriptInterface', activity)

    def test_local_stats_and_ui_accessibility(self):
        engine=(ROOT/'app/src/main/java/dev/caicompanion/app/FilterEngine.kt').read_text()
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('topBlockedRules', engine)
        self.assertIn('clearStats()', engine)
        self.assertIn('counters.size < 128', engine)
        self.assertIn('contentDescription = "$name. $description"', activity)
        self.assertTrue((ROOT/'app/src/main/res/drawable/settings_action.xml').exists())

    def test_download_and_back_navigation_safety(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        policy=(ROOT/'app/src/main/java/dev/caicompanion/app/DownloadPolicy.kt').read_text()
        self.assertIn('allowContentAccess = false', activity)
        self.assertIn('DownloadPolicy.classify(safeUrl)', activity)
        self.assertIn('onBackInvokedDispatcher.registerOnBackInvokedCallback', activity)
        self.assertIn('onBackInvokedDispatcher.unregisterOnBackInvokedCallback', activity)
        self.assertNotIn('webView.restoreState(savedInstanceState)', activity)
        self.assertNotIn('webView.saveState(outState)', activity)
        self.assertIn('uri.rawQuery != null', policy)
        self.assertIn('Kind.BROWSER_ONLY', policy)

    def test_manual_filter_import_is_user_initiated_and_bounded(self):
        java = ROOT/'app/src/main/java/dev/caicompanion/app'
        parser = (java/'RuleListParser.kt').read_text()
        store = (java/'ImportedRuleStore.kt').read_text()
        activity = (java/'MainActivity.kt').read_text()
        engine = (java/'FilterEngine.kt').read_text()
        script = (ROOT/'scripts/test-offline.sh').read_text()
        self.assertIn('MAX_CHARACTERS = 2_000_000', parser)
        self.assertIn('MAX_DOMAINS = 40_000', parser)
        self.assertIn('MAX_LINES = 100_000', parser)
        self.assertIn('AtomicFile', store)
        self.assertIn('file.finishWrite(stream)', store)
        self.assertIn('file.failWrite(stream)', store)
        self.assertIn('BoundedText.read(reader, RuleListParser.MAX_CHARACTERS)', activity)
        self.assertIn('IMPORT_FILTER_LIST ->', activity)
        self.assertIn('if (configuration == appliedConfiguration) return', engine)
        self.assertIn('RuleListParser,IncomingLinkParser,BrowserSessionPolicy,RendererRecoveryBudget,SupportReport,NativeLibrarySearch,NativeWorkspaceNavigation,NativeStartupPolicy,DeferredWebReload,OfflineBookmarkEditor,DeviceCompatibility,GeckoNavigationPolicy,GeckoTransferPolicy,GeckoTransferOriginPolicy,ExternalBrowserPolicy}.kt', script)
        self.assertNotIn('https://easylist.to/easylist/easylist.txt', activity)
        self.assertNotIn('PeriodicWorkRequest', activity)

    def test_filter_never_blocks_critical_service_or_identity_hosts(self):
        blocker = (ROOT/'app/src/main/java/dev/caicompanion/app/HostBlocker.kt').read_text()
        self.assertIn('isCriticalHost(host)', blocker)
        self.assertIn('accounts.google.com', blocker)
        self.assertIn('appleid.apple.com', blocker)

    def test_manual_verified_gradle_wrapper_distribution(self):
        props=(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
        provenance=(ROOT/'gradle/wrapper/PROVENANCE.md').read_text()
        launcher=(ROOT/'gradlew').read_text()
        self.assertIn('gradle-8.13-bin.zip',props)
        self.assertIn('distributionSha256Sum=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78',props)
        self.assertIn('81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f',provenance)
        self.assertIn('org.gradle.wrapper.GradleWrapperMain',launcher)
        self.assertFalse((ROOT/'.github/workflows').exists())

    def test_conservative_host_rules(self):
        lines=(ROOT/'app/src/main/assets/blocked-hosts.txt').read_text().splitlines()
        rules=[s.strip() for s in lines if s.strip() and not s.startswith('#')]
        self.assertTrue(len(rules)>=20)
        self.assertFalse(any('character.ai' in r or 'c.ai' in r for r in rules))

    def test_share_sheet_is_explicit_and_restricted(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        main=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        parsed=(ROOT/'app/src/main/java/dev/caicompanion/app/IncomingLinkParser.kt').read_text()
        self.assertIn('android.intent.action.SEND', (ROOT/'app/src/legacy/AndroidManifest.xml').read_text())
        self.assertIn('android.intent.action.SEND', (ROOT/'app/src/gecko/AndroidManifest.xml').read_text())
        self.assertNotIn('android.intent.action.VIEW', manifest)
        self.assertIn('android:launchMode="singleTop"', manifest)
        self.assertIn('IncomingLinkParser.safeSharedLink', main)
        self.assertIn('UrlPolicy.sanitizeBookmark', parsed)
        self.assertIn('onNewIntent(intent: Intent)', main)
        self.assertIn('setIntent(Intent(Intent.ACTION_MAIN))', main)

    def test_native_offline_workspace_lifecycle_and_privacy(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        settings=(ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text()
        library=(ROOT/'app/src/main/java/dev/caicompanion/app/NativeLibrarySearch.kt').read_text()
        self.assertIn('if (NativeStartupPolicy.useHub(store.startInHub, initialShare)) showNativeHub()', activity)
        self.assertIn('private fun showNativeHub()', activity)
        self.assertIn('private fun closeNativeHub(loadIfBlank: Boolean = true)', activity)
        self.assertIn('NativeLibrarySearch.filter(store.listBookmarks(), query)', activity)
        self.assertIn('private fun syncRendererLifecycle()', activity)
        self.assertIn('NativeWorkspaceNavigation.shouldResumeRenderer(activityResumed, nativeHub != null)', activity)
        self.assertIn('if (loadIfBlank && NativeWorkspaceNavigation.needsInitialLoad(webView.url))', activity)
        self.assertIn('else openFirstPartyPage(safe)', activity)
        self.assertIn('showNativeBookmarkActions(title, url)', activity)
        self.assertIn('R.string.hub_bookmark_confirm_delete', activity)
        self.assertIn('if (::webView.isInitialized) webView.onPause()\n        container.addView(panel', activity)
        self.assertIn('private fun writeSettingsBackup(uri: Uri)', activity)
        self.assertIn('private fun readSettingsBackup(uri: Uri)', activity)
        self.assertIn('manual-backup-import', activity)
        self.assertIn('manual-backup-export', activity)
        self.assertIn('if (!activityResumed || nativeHub != null || !store.mediaAllowed', activity)
        self.assertIn('if (permissionOk && activityResumed && nativeHub == null', activity)

        self.assertIn('InputFilter.LengthFilter(80)', activity)
        self.assertIn('nativeHub != null -> closeNativeHub()', activity)
        self.assertIn('if (::webView.isInitialized) webView.onPause()\n        container.addView(panel', activity)
        self.assertIn('getBoolean("start_in_native_hub", true)', settings)
        self.assertIn('put("startInHub", startInHub)', settings)
        self.assertIn('.putBoolean("media_allowed", false)', settings)
        self.assertIn('UrlPolicy.sanitizeBookmark(rawUrl)', library)
        self.assertNotIn('android.webkit.WebView', library)
        self.assertNotIn('httpURLConnection', library)
        self.assertNotIn('addJavascriptInterface(', activity)
        for filename in ('values/strings.xml', 'values-ru/strings.xml'):
            text=(ROOT/'app/src/main/res'/filename).read_text()
            self.assertIn('name="hub_title"',text)
            self.assertIn('name="hub_search_hint"',text)

    def test_screen_privacy_opt_in_not_always_on(self):
        main=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        pref=(ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text()
        self.assertIn('FLAG_SECURE', main)
        self.assertIn('getBoolean("protect_screen", false)', pref)
        self.assertIn('put("protectScreen", protectScreen)', pref)
        self.assertIn('.putBoolean("protect_screen", parsed.optBoolean("protectScreen", false))', pref)
        self.assertIn('view.isSaveEnabled = false', main)
        self.assertIn('view.isSaveFromParentEnabled = false', main)
        self.assertIn('Document provider returned no output stream', main)

    def test_browser_session_is_first_party_and_no_cookie_stealing(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        browser=(ROOT/'app/src/main/java/dev/caicompanion/app/BrowserSessionPolicy.kt').read_text()
        build=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('implementation("androidx.browser:browser:1.10.0")', build)
        self.assertIn('CustomTabsIntent.Builder()', activity)
        self.assertIn('BrowserSessionPolicy.safeTabUrl(webView.url)', activity)
        self.assertIn('openBrowserTab(BrowserSessionPolicy.HOME)', activity)
        self.assertIn('UrlPolicy.sanitizeBookmark(current) ?: HOME', browser)
        self.assertNotIn('CookieManager.getInstance().getCookie(', activity)
        self.assertNotIn('setUserAgentString(', activity)
        self.assertNotIn('addJavascriptInterface(', activity)
        self.assertNotIn('.github/workflows', str(ROOT/'app'))

    def test_browser_session_strings_and_persistent_intent_safety(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('setIntent(Intent(Intent.ACTION_MAIN))', activity)
        self.assertIn('val safe = incomingShareLink(intent)\n            // This Activity is singleTop', activity)
        self.assertIn('UrlPolicy.sanitizeBookmark(url)?.let { store.lastVisited = it }', activity)
        self.assertNotIn('store.lastVisited = url.orEmpty()', activity)
        self.assertIn('CookieManager.getInstance().flush()', activity) # onPause / clear-data only
        self.assertIn('BrowserSessionPolicy,', (ROOT/'scripts/test-offline.sh').read_text().replace('}.kt',','))
        en=(ROOT/'app/src/main/res/values/strings.xml').read_text()
        ru=(ROOT/'app/src/main/res/values-ru/strings.xml').read_text()
        for key in ['browser_session_open','browser_session_desc','section_browser_session']:
            self.assertIn('name="'+key+'"', en)
            self.assertIn('name="'+key+'"', ru)

    def test_browser_dependencies_are_pinned_without_new_permissions(self):
        build=(ROOT/'app/build.gradle.kts').read_text()
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('versionName = "0.26.0-rc1"', build)
        self.assertIn('versionCode = 26', build)
        release = (ROOT/'scripts/prepare-release.sh').read_text()
        self.assertIn('VERSION="$(sed -nE', release)
        self.assertIn('VERSION_CODE="$(sed -nE', release)
        self.assertNotIn("VERSION='0.17.0-rc1'", release)
        self.assertIn("versionCode='$VERSION_CODE'", release)
        self.assertIn('androidx.browser:browser:1.10.0', build)
        self.assertIn('androidx.webkit:webkit:1.18.0', build)
        self.assertNotIn('android.permission.QUERY_ALL_PACKAGES', manifest)
        self.assertNotIn('android.permission.READ_EXTERNAL_STORAGE', manifest)
        self.assertNotIn('android.permission.WRITE_EXTERNAL_STORAGE', manifest)
        self.assertNotIn('android:usesCleartextTraffic="true"', manifest)

    def test_debug_apk_build_is_manual_and_verified(self):
        script=(ROOT/'scripts/build-apk.sh').read_text()
        self.assertIn('scripts/doctor.sh', script)
        self.assertIn('--no-daemon "$TASK"', script)
        self.assertIn('assembleGeckoDebug', script)
        self.assertIn('assembleLegacyDebug', script)
        self.assertIn('APK_READY=', script)
        self.assertIn('APK_SHA256=', script)
        self.assertIn('[[ ! -s "$APK" ]]', script)
        self.assertNotIn('upload-file', script)
        self.assertNotIn('schedule', script.lower().split('set -euo pipefail',1)[1])

    def test_renderer_crash_loop_is_bounded_and_no_activity_retain(self):
        main=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        budget=(ROOT/'app/src/main/java/dev/caicompanion/app/RendererRecoveryBudget.kt').read_text()
        self.assertIn('rendererRecovery.allowAutomaticRestart(SystemClock.elapsedRealtime())', main)
        self.assertIn('showError(getString(R.string.renderer_crash_loop))', main)
        self.assertIn('ArrayDeque<Long>()', budget)
        self.assertIn('if (restarts.size >= maximum) return false', budget)
        self.assertIn('denyPendingMedia()\n        super.onStop()', main)

    def test_imported_rule_export_uses_saf_and_excludes_private_data(self):
        main=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('Intent.ACTION_CREATE_DOCUMENT', main)
        self.assertIn('EXPORT_FILTER_LIST ->', main)
        self.assertIn('snapshot.canonicalText()', main)
        self.assertIn('if (importedRules.size > 0)', main)
        self.assertIn('openOutputStream(uri, "w")', main)
        self.assertIn('manual-filter-export', main)
        self.assertNotIn('CookieManager.getInstance().getCookie(', main)

    def test_release_kit_cannot_publish_unsigned_apk(self):
        script=(ROOT/'scripts/prepare-release.sh').read_text()
        self.assertIn('$APKSIGNER verify', script)
        self.assertIn('assembleRelease', (ROOT/'docs/RELEASE_PROCESS.md').read_text())
        self.assertIn('No APK has been produced', (ROOT/'docs/RELEASE_NOTES_v0.11.0-rc1.md').read_text())
        self.assertFalse((ROOT/'.github/workflows').exists())

    def test_all_localized_keys_unique(self):
        for locale in ('values','values-ru'):
            root=ET.parse(ROOT/'app/src/main/res'/locale/'strings.xml').getroot()
            keys=[el.attrib.get('name') for el in root if el.tag == 'string']
            self.assertEqual(len(keys),len(set(keys)))


    def test_android17_ime_and_error_recovery_ui(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        layout=(ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
        self.assertIn('insets.isVisible(WindowInsets.Type.ime())', activity)
        self.assertIn('if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)', activity)
        self.assertIn('updateFooterVisibility()', activity)
        self.assertIn('response.statusCode in 500..599', activity)
        self.assertIn('R.id.error_open_browser', activity)
        self.assertIn('@+id/error_open_browser', layout)
        self.assertIn('BrowserSessionPolicy.safeTabUrl(lastUrl)', activity)

    def test_opt_in_support_report_never_includes_chat_data(self):
        report=(ROOT/'app/src/main/java/dev/caicompanion/app/SupportReport.kt').read_text()
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('data class Snapshot',report)
        self.assertNotIn('getCookie(', report)
        self.assertNotIn('lastVisited', report)
        self.assertNotIn('androidId', report)
        self.assertIn('setNeutralButton(R.string.diagnostics_copy)',activity)
        self.assertIn('SupportReport.format(snapshot)',activity)
        self.assertIn('rendererRecovery.recentRestartCount(',activity)

if __name__=='__main__': unittest.main()
