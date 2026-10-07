# MadoGit Release Notes

## MadoGit v0.4.0-beta (Data Integrity Hardening, Lifecycle Sync & UI Stabilization)

This release delivers critical data integrity hardening, lifecycle-driven background synchronization, security enhancements, and UI state stabilization across the application. It resolves key runtime bugs in notification deduplication, repository monitoring state preservation, pull request transition tracking, and WorkManager retry orchestration, backed by a comprehensive 60-test verification suite.

> **Notice: Experimental Release**  
> This is an active beta release intended for dogfooding, testing, and feedback. Features, UI components, and internal schemas are continuously evolving.

---

### Highlights & Features in v0.4.0-beta

#### Data Integrity & Sync Engine Hardening
- **Targeted URL Deduplication**: Restricted URL-based duplicate purging strictly to canonical pull request and issue URLs, preventing catastrophic deletion of distinct notifications sharing fallback repository URLs.
- **Transactional Repository Refresh**: Ensured user repository monitoring selections and sync timestamps are preserved across remote updates; guarded stale repository pruning to execute only when full listings are retrieved.
- **Silent Baseline Sync**: Established baseline synchronization semantics for new accounts and newly monitored repositories, silently importing existing unread threads to prevent notification floods on first sign-in.
- **Pull Request State Transitions**: Added comprehensive transition tracking for pull requests. Moving from open to merged or closed-without-merge now updates existing database entities and alerts the user according to their notification preferences.
- **Adaptive 3-Tier Rate Protection**: Enforced automatic quota preservation tiers (<100 critical, <500 conservative, >=500 normal) and bounded repository sweeps to 5 repos per cycle.

#### Lifecycle & Background WorkManager Orchestration
- **Auth-Driven WorkManager Scheduling**: Bound background polling directly to the authentication state in `GitHubNotifierApp`. Signing in automatically registers periodic work and runs an immediate initial sweep; signing out immediately cancels all scheduled work.
- **Worker Exponential Retry & Terminal Policies**: Implemented `GitHubSyncWorker.resultFor()` with exponential 30-second backoff up to 3 attempts for transient failures, while instantly flagging HTTP 401 revocations as terminal failures.

#### Security & Session Management
- **Authorization Header Redaction**: Configured `HttpLoggingInterceptor` to redact `Authorization` bearer tokens in debug builds, preventing credential leakage in system logs.
- **Mandatory OAuth CSRF Verification**: Enforced strict validation of the random UUID `state` parameter in OAuth callbacks, rejecting authentication requests with absent or mismatched state tokens.
- **Session Revocation Handling**: Handled HTTP 401 Unauthorized responses by clearing Keystore-stored credentials, cancelling background tasks, and transitioning to an error state prompting re-login.
- **Complete Account Disconnect & Wipe**: Ensured sign-out and account switching purge all Room database tables, cancel active system notifications, and evict cached HTTP data to prevent cross-account contamination.

#### UI Stabilization & Navigation Polishing
- **Calendar-Day Date Bucketing**: Replaced rolling 24-hour window division with calendar-day boundary comparisons in `notificationDayBucket()`, ensuring items are accurately classified as "Today", "Yesterday", "This Week", and "Earlier".
- **Relative Time Coherence**: Aligned `formatRelativeTime()` with calendar day buckets so item subtitles and section headers are visually consistent.
- **Persistent View State on Rotation**: Converted triage tabs, repository filter chips, and onboarding steps to `rememberSaveable`, preserving user selections across device rotation and process recreation.
- **In-App Notification Routing**: Added handler for "View in App" notification intent actions, routing users directly to the selected notification.
- **Exposed Notification Settings**: Added UI controls for previously unexposed preferences: PR closed without merge, thread comments, repository activity (commits pushed, commit comments), and auto-monitoring of new repositories.
- **Dynamic Versioning & Lint Compatibility**: Replaced hardcoded version strings with `BuildConfig.VERSION_NAME` and annotated API-specific theme attributes with `tools:targetApi`.

#### Automated Testing & Verification
- **Expanded Test Suite (60/60 Passing)**: Added `SyncEngineTest` (23 tests), `SyncWorkerPolicyTest` (3 tests), `TokenManagerTest` (3 tests), and `NotificationDayBucketTest` (1 test) alongside existing suites.
- **Clean Build Validation**: Verified clean compilation across `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `lintDebug` (0 errors), `assembleDebug`, and `assembleRelease` with R8 optimization.

---

## MadoGit v0.3.0-beta (Security Hardening, ETag Cache & Architecture Refactor)

This release delivers critical security hardening for credential storage and external intents, non-destructive Room database migrations, persistent OkHttp caching with socket pooling for GitHub API rate limit conservation, UI modularization, and automated Jetpack Compose instrumented UI test coverage.

> **Notice: Experimental Release**  
> This is an active beta release intended for dogfooding, testing, and feedback. Features, UI components, and internal schemas are continuously evolving.

---

### Highlights & Features in v0.3.0-beta

#### Security Hardening & Cryptographic Integrity
- **Fail-Closed Keystore Encryption**: Updated `CryptoManager.encrypt()` to throw an `IllegalStateException` upon encryption failure rather than silently persisting plaintext tokens.
- **Strict URI Sanitization**: External link navigations and notification tap intent builders now strictly validate URI schemes, rejecting unsafe schemes and permitting only `https://` and `http://`.
- **Architectural OAuth Delegation**: Delegated OAuth code exchange and credential management from UI ViewModels directly to `GitHubRepository`.

#### Data Integrity & Schema Preservation
- **Non-Destructive Room Migrations**: Upgraded Room Database to schema version 3 with structured migration `MIGRATION_2_3`, preserving user notifications, monitored repositories, and sync history across upgrades.
- **Native Repository Language**: Added the standard `language` field to `GitHubRepoDto` and `MonitoredRepoEntity`, eliminating heuristic repository language parsing.
- **Accidental Wipe Protection**: Added a Material 3 confirmation dialog before clearing notification history in the Notifications screen.
- **Clock Skew Resilience**: Corrected chronological date grouping calculations against clock skew and eliminated redundant Calendar allocations.

#### Network & Rate-Limit Optimization
- **On-Disk OkHttp Cache (ETags / 304 Not Modified)**: Implemented a 15 MB persistent OkHttp cache (`http_github_cache`) enabling automatic conditional HTTP requests that do not consume GitHub API hourly quota.
- **Shared Connection & Socket Pooling**: Reused `baseHttpClient.newBuilder()` across Retrofit instances to share TCP keep-alive sockets, thread dispatchers, SSL caches, and Moshi converters.
- **Traceable Sync Logging**: Replaced empty catch blocks in secondary sync routines with descriptive debug logging.

#### UI Modularization & Testing
- **Extracted Settings Components**: Separated reusable settings cards (`SettingsSectionCard`, `CategoryMasterToggle`, `SubOptionCheckbox`, `DiagnosticItem`, `PalettePreviewRow`) into `SettingsComponents.kt`.
- **String Resource Localization**: Migrated hardcoded user-facing settings strings into `res/values/strings.xml`.
- **Jetpack Compose Instrumented UI Tests**: Added automated on-device Compose UI instrumented tests (`MadoGitUiInstrumentedTest.kt`) validating onboarding flows, permissions, and authentication tab switching.

---

## MadoGit v0.2.0-beta (Material You & UI/UX Expressive Revamp)

This release delivers a comprehensive Material 3 Expressive UI and Material You revamp, introducing native mobile gestures, fluid screen transitions, smart notification grouping, and developer-focused visual tokens.

> **Notice: Experimental Release**  
> This is an active beta release intended for dogfooding, testing, and feedback. Features, UI components, and internal schemas are continuously evolving.

---

### Highlights & Features in v0.2.0-beta

#### Material 3 Expressive & Material You Theming
- **Curated Developer Palette Tokens**: Enhanced fallback color tokens with sapphire blue (`#58A6FF`), emerald green (`#3FB950`), amethyst purple (`#BC8CFF`), crimson red (`#F85149`), and warm amber tones.
- **Surface Elevation Spectrum**: Added full tonal container spectrum (`surfaceContainerLowest` through `surfaceContainerHighest`) for natural depth hierarchy.
- **Expressive Shape Scale**: Standardized corner rounding across all components: Extra Small (`6.dp`), Small (`10.dp`), Medium (`16.dp`), Large (`22.dp`), and Extra Large (`28.dp`).
- **Monospace Code Typography**: Added extension typography styles (`Typography.code` and `Typography.codeSmall`) using `FontFamily.Monospace` for commit SHAs, git branch names, and code syntax tokens.
- **Live Monet Palette Swatches**: Added real-time interactive color swatches in Settings displaying active primary, secondary, tertiary, surface, and error tokens reflecting wallpaper extraction.

#### Native Gestures & Micro-Interactions
- **Pull-to-Refresh Gesture**: Integrated `MadoPullToRefreshBox` across all main feeds (Dashboard, Notifications, Assistant, Repositories) for one-swipe manual synchronization.
- **Bidirectional Swipe-to-Dismiss**: Integrated `MadoSwipeToDismissItem` with directional haptic feedback in Notifications:
  - Swipe Right: Surface highlighted in primary dynamic color with checkmark icon to mark item as read.
  - Swipe Left: Surface highlighted in error dynamic color with trash icon to dismiss/archive notification with instant snackbar undo.
- **Directional Haptic Feedback**: Tactile responses on tab switching, repository monitoring switches, and swipe threshold triggers.
- **Animated List Transitions**: Integrated `Modifier.animateItem()` across activity feeds and triage task lists for smooth insertion and removal animations.

#### Screen-by-Screen Upgrades
- **Dashboard**: Embedded `RateLimitGauge` into the profile card showing real-time API quota, warning thresholds, and reset timer countdown.
- **Notifications Screen**: Introduced smart chronological date grouping with clean section headers (*Today*, *Yesterday*, *This Week*, *Earlier*).
- **Assistant Screen**: Added segmented triage filter chips (*All*, *Reviews*, *Issues*, *CI Runs*) with live item count badges and a celebratory "Inbox Zero" empty state.
- **Repositories Screen**: Added quick filter chips (*All*, *Monitored*, *Private*, *Public*), programming language indicator dots (`LanguageDot`), and monospace branch tags.
- **Settings Screen**: Added direct shortcut button launching Android System Notification Channel Settings (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`) alongside the live Monet palette swatch row and rate limit gauge.
- **Navigation**: Upgraded tab navigation to `AnimatedContent` slide-and-fade transitions with spring physics and dynamic unread badge indicators.

#### Toolchain & Build Compatibility
- Pinned Gradle daemon JVM toolchain to Java 21 LTS (`toolchainVersion=21` in `gradle/gradle-daemon-jvm.properties`), eliminating Groovy/ASM class file major version 69 bytecode errors when newer JDKs are present.

---

## MadoGit v0.1.0-beta (Experimental Release)

---

## Highlights & Features

### Sovereign Privacy & Zero Intermediate Servers
- Communicates directly between your Android device and `api.github.com` via TLS 1.3.
- No middleman proxy, no cloud database, no tracking analytics, and no telemetry.
- Credentials (Personal Access Tokens or OAuth Access Tokens) are hardware-encrypted on-device using Android Keystore and AES-256 GCM.

### Clean Edge-to-Edge UI & Material You
- Built entirely with Jetpack Compose following Material 3 guidelines.
- Dynamic color theming (Monet) adapts harmoniously to your wallpaper on Android 12+, with curated fallback palettes and manual toggle options.
- Edge-to-edge profile card anchors the home screen with your avatar, handle, monitored repositories count, and real-time GitHub API rate-limit meter (`API: 5000/5000` / `Offline`).
- One-tap manual sync with rotating animation integrated directly into the profile card.
- Modern, borderless headers across Notifications, Assistant, Repositories, and Settings tabs.

### Intelligent Priority Assistant
- Focused triage feed surfacing items requiring your direct action:
  - Pull requests awaiting your code review.
  - Open issues directly assigned to your handle.
  - GitHub Actions CI/CD workflow failures.
- Direct links to open items in your browser or official GitHub app.

### Smart Synchronization & Rate-Limit Guard
- Respects GitHub's 5,000 req/hr rate limit through HTTP ETag conditional requests (`304 Not Modified`).
- Event fingerprinting via deterministic SHA-256 hash digests to guarantee zero duplicate alerts.
- Background polling scheduled via Android WorkManager with Doze mode and battery-conscious constraints.

### Notification History & Repositories Manager
- Searchable, local event archive stored in Room Database.
- Category filters: Pull Requests, Issues, Workflows, Releases, and General Activity.
- Granular repository selection: monitor high-priority projects while ignoring noisy or inactive ones.
- Dedicated Android system notification channels (Android 8.0+) with customizable sound, vibration, and priority levels.

---

## Fixes & Improvements in This Release

- Fixed notification mark-as-read state reverting to unread upon background reload or manual refresh.
- Enforced user notification preferences across workflow run evaluations.
- Seeded initial repository sync items as read to prevent overwhelming alert floods on first launch.
- Resolved vertical text wrapping on status badges for repositories with long names.
- Removed duplicate status bar padding on the Notifications screen for consistent top alignment.
- Refactored project package name and application ID to `com.aipos.madogit`.
- Removed unnecessary third-party AI Studio boilerplate and external secrets Gradle plugin.
- Added developer attribution ("Developed by gininaba") and repository links in Settings.

---

## Technical Specifications

- **Package / Application ID**: `com.aipos.madogit`
- **Target SDK**: 36 (Android 16)
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Architecture**: MVVM, Unidirectional Data Flow, Offline-First (Room SQLite)
- **Language**: Kotlin 2.2+
- **UI Toolkit**: Jetpack Compose (Material 3)
- **License**: Apache 2.0

---

## Feedback & Issues

If you encounter issues or have ideas for enhancements, please open an issue on GitHub:
https://github.com/gininaba/MadoGit/issues
