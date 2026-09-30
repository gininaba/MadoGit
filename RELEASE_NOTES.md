# MadoGit v0.1.0-beta (Experimental Release)

MadoGit is a lightweight, developer-first personal GitHub notification assistant for Android. It monitors your repositories, pull requests, issues, and CI workflows with timely Android system push notifications, smart deduplication, and zero intermediate servers.

> **Notice: Experimental Release**  
> This is an early beta release intended for dogfooding, testing, and feedback. Features, UI components, and internal schemas are actively evolving.

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
