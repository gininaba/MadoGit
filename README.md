<p align="center">
  <img src="docs/assets/madogit-logo.jpg" alt="MadoGit Logo" width="160" />
</p>

<h1 align="center">MadoGit</h1>

<p align="center">
  <strong>(Mado = window) — A minimal window into your GitHub activity.</strong>
</p>

<p align="center">
  A lightweight, developer-first personal GitHub notification assistant for Android.
  <br />
  Monitors your repositories, pull requests, issues, and CI workflows with timely Android system push notifications, smart deduplication, and zero intermediate servers.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Status-Experimental-orange?style=flat-square" alt="Status" />
  <img src="https://img.shields.io/badge/Platform-Android_7.0+_(API_24+)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Target_SDK-36_(Android_16)-34A853?style=flat-square" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.2+-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4?style=flat-square" alt="UI" />
  <img src="https://img.shields.io/badge/Architecture-MVVM_+_Offline--First-00BCD4?style=flat-square" alt="Architecture" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square" alt="License" /></a>
</p>

> [!IMPORTANT]
> **Project Status: Experimental**
> MadoGit is currently under active, experimental development. Features, database schemas, and UI flows are subject to rapid iteration and refactoring. It is provided for personal testing, developer dogfooding, and community feedback. Contributions, bug reports, and suggestions are welcome.

---

## Table of Contents

- [1. Overview & Philosophy](#1-overview--philosophy)
- [2. Key Architectural Pillars](#2-key-architectural-pillars)
- [3. Architecture & Data Flow](#3-architecture--data-flow)
- [4. Core Features](#4-core-features)
- [5. Notification System & Channels](#5-notification-system--channels)
- [6. Material You Theming Engine](#6-material-you-theming-engine)
- [7. GitHub Permissions & Scopes](#7-github-permissions--scopes)
- [8. Quickstart & Build Instructions](#8-quickstart--build-instructions)
- [9. Documentation Suites](#9-documentation-suites)
- [10. Project Structure](#10-project-structure)
- [11. Testing & Quality Assurance](#11-testing--quality-assurance)
- [12. License](#12-license)

---

## 1. Overview & Philosophy

Modern software developers operate in noisy notification environments. GitHub notifications are frequently flooded with automated commit statuses, bot comments, and high-frequency pull request updates that bury high-priority events requiring immediate human intervention.

**MadoGit** (*Mado* is the Japanese word for **window**) functions as a focused, minimal window into your GitHub universe:

- **Signal Over Noise**: Identifies actionable events (review requests waiting on you, issues explicitly assigned to you, and broken CI pipelines in your repositories) and surfaces them directly.
- **Sovereign Privacy**: No cloud proxies, no middleman databases, no analytics trackers. Direct communication occurs exclusively between your Android device and `api.github.com`.
- **Offline-First Resilience**: Full local persistence using Room Database. View cached repositories, prior notification history, and priority digests with zero network latency or while disconnected.
- **Native Android Experience**: Deeply integrated into the Android ecosystem with Material You (Monet dynamic color theming), dedicated notification channels, WorkManager scheduling, and granular vibration/sound controls.

---

## 2. Key Architectural Pillars

| Pillar | Implementation | Technical Benefit |
|---|---|---|
| **Zero Intermediate Servers** | Direct TLS 1.3 to `api.github.com` | Complete privacy; credentials never leave the host device. |
| **Encrypted Token Storage** | `EncryptedSharedPreferences` + Android Keystore | Hardware-backed AES-256 GCM credential security. |
| **Material You Monet Theming** | Dynamic ColorScheme + Harmonized Fallbacks | Native system integration adapting to user wallpaper colors. |
| **Smart Rate-Limit Guard** | Telemetry inspection + ETag Conditional GETs | Protects the 5,000 req/hr GitHub quota with 304 Not Modified caching. |
| **Event Fingerprinting** | SHA-256 Hash Digest in `processed_events` | Guarantees zero duplicate alerts across recurring background syncs. |
| **Battery-Conscious Sync** | Android WorkManager + Doze Constraints | Executes background polling only when device conditions are optimal. |

---

## 3. Architecture & Data Flow

MadoGit implements the standard Modern Android Architecture guidelines, employing unidirectional data flow (UDF) and strict layer boundaries.

```mermaid
graph TD
    subgraph UI ["Presentation Layer (Jetpack Compose)"]
        Screens["Compose UI Screens"]
        ViewModel["MainViewModel (StateFlow)"]
        Screens -->|User Events| ViewModel
        ViewModel -->|State Updates| Screens
    end

    subgraph Domain ["Repository Layer"]
        Repo["GitHubRepository"]
        TokenMgr["TokenManager (Keystore)"]
        Prefs["PreferencesRepository"]
        ViewModel --> Repo
        ViewModel --> Prefs
        Repo --> TokenMgr
    end

    subgraph Data ["Data Layer"]
        DB[("Room Database (SQLite)")]
        API["GitHub REST API (v3)"]
        Repo -->|Reactive Queries| DB
        Repo -->|Retrofit + OkHttp| API
    end

    subgraph Background ["Background Engine"]
        Worker["GitHubSyncWorker (WorkManager)"]
        Notif["NotificationHelper"]
        Worker --> Repo
        Worker --> Notif
        Notif -->|System Push| Tray["Android Notification Tray"]
    end
```

### End-to-End Synchronization Pipeline

```
1. WorkManager triggers periodic GitHubSyncWorker (or user initiates pull-to-refresh).
2. Worker verifies network state and remaining GitHub API rate-limit quota.
3. Retrofit issues conditional GET requests carrying cached ETag headers.
4. If 304 Not Modified: Network payload is zero; sync cycle concludes immediately.
5. If 200 OK: Response models are parsed via Moshi and updated in Room.
6. Unique event fingerprints are computed and cross-referenced against processed_events.
7. Novel events trigger NotificationHelper, posting to dedicated system channels.
8. StateFlow emits updated entity lists to active Jetpack Compose UI screens.
```

---

## 4. Core Features

### Connected Account Dashboard
- Edge-to-edge profile card anchoring the home feed with avatar, username, and display name.
- Real-time GitHub API rate-limit chip (`API: 5000/5000` or `Offline` status) directly inside the profile card.
- One-tap animated manual sync button cleanly integrated beside repository navigation.
- Metric status cards: unread alerts, review requests, assigned issues, and failed workflows.
- Chronological activity timeline with state badges and repository labels.

### Intelligent Priority Assistant
- Specialized triage view filtering actionable items requiring developer attention:
  - Pull requests awaiting your code review.
  - Open issues directly assigned to your handle.
  - GitHub Actions CI/CD workflows terminating with failure conclusion.
- Single-tap actions to open pull requests, issues, or workflow runs directly in your preferred browser or GitHub app.

### Repository Monitoring Engine
- Full repository explorer querying your personal, starred, and organization repositories.
- Individual monitoring toggles (`[ ON / OFF ]`) to track high-priority repositories while ignoring noisy or inactive projects.
- "Monitor All" bulk toggle for rapid setup.
- Active vs. inactive polling prioritization to minimize API traffic.

### Notification History & Archive
- Comprehensive, searchable event history stored locally in Room.
- Clean edge-to-edge search bar and category filtering chips: *Pull Requests*, *Issues*, *Workflows*, *Releases*, *Activity*.
- Read/unread indicators with individual mark-as-read, delete, and bulk-clear capabilities.

### Flexible Authentication Modes
- **Personal Access Token (PAT)**: Instant setup supporting classic tokens (`ghp_...`) and fine-grained tokens (`github_pat_...`) with pre-configured scope templates.
- **GitHub OAuth App Flow**: Full authorization code grant with custom callback scheme (`ghnotifier://oauth/callback`) for organizations requiring OAuth app governance.
- **Zero Third-Party Secrets**: No external servers, proxy services, or third-party AI keys required. The application communicates directly with `api.github.com`.

---

## 5. Notification System & Channels

MadoGit creates dedicated notification channels on Android 8.0+ (API 26+) to ensure users have full OS-level control over alerts:

| Channel Name | Channel ID | Priority | Description |
|---|---|---|---|
| **Pull Requests** | `channel_prs` | High | Review requests, assignments, approvals, and merges. |
| **Issues & Mentions** | `channel_issues` | High | Issue assignments, mentions, and issue state transitions. |
| **GitHub Actions** | `channel_workflows` | Default | Continuous integration failures, cancellations, and workflow completions. |
| **Releases** | `channel_releases` | Default | New releases and tags published in monitored repositories. |
| **Repository Activity** | `channel_activity` | Low | General repository events, stars, and push events. |

### System Features:
- Direct notification actions: "Open on GitHub" (browser/app) and "View in App".
- Notification grouping by repository to prevent status-bar clutter.
- Full compliance with Android 13+ (API 33+) `POST_NOTIFICATIONS` runtime permission model.

---

## 6. Material You Theming Engine

MadoGit is built from the ground up for **Material You (Material 3 Dynamic Color)**:

- **Monet Dynamic Theming**: On Android 12+ (API 31+), the application dynamically extracts color palettes from the user's wallpaper.
- **Harmonized Fallbacks**: For Android versions below 12 or when dynamic color is disabled, MadoGit provides a developer-focused dark palette with slate surfaces (`#0D1117`, `#161B22`) and GitHub accent tones.
- **User Preference Control**: Settings screen includes an explicit toggle allowing users to enable or disable dynamic theming at any time.
- **Surface Elevation Hierarchy**: Uses Material 3 tonal container tokens (`surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`) to achieve depth and contrast without harsh borders.

---

## 7. GitHub Permissions & Scopes

MadoGit requests only the minimum set of scopes required for notification triage:

| Scope | Required | Purpose |
|---|---|---|
| `repo` | Yes | Read access to private and public repositories, pull requests, and commit statuses. |
| `notifications` | Yes | Query unread notification threads from `/notifications`. |
| `workflow` | Yes | Query GitHub Actions workflow runs to identify CI failures. |
| `read:user` | Yes | Retrieve avatar, username, and profile display name for the dashboard. |

> [!NOTE]
> MadoGit never requests administrative permissions (`admin:repo_hook`, `delete_repo`, or `write:repo_hook`). Polling is conducted strictly client-side.

---

## 8. Quickstart & Build Instructions

### Prerequisites
- JDK 17 (Azul Zulu, OpenJDK, or Eclipse Temurin)
- Android SDK Platform 34+ (compileSdk 36)
- Android Studio Hedgehog (2023.1.1) or newer

> [!NOTE]
> No `.env` file, third-party backend, or external API keys are required to build or run MadoGit. All operations connect directly to the public GitHub API via your own token or OAuth credentials configured at runtime.

### Clone the Repository
```bash
git clone https://github.com/gininaba/MadoGit.git
cd MadoGit
chmod +x gradlew
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Execute Test Suite
```bash
./gradlew testDebugUnitTest
```

### Configuring OAuth App (Optional)
If utilizing the OAuth App flow instead of a Personal Access Token:
1. Navigate to **GitHub Settings** > **Developer Settings** > **OAuth Apps** > **New OAuth App**.
2. Configure:
   - **Application name**: `MadoGit`
   - **Homepage URL**: `https://github.com`
   - **Authorization callback URL**: `ghnotifier://oauth/callback`
3. Enter your generated **Client ID** and **Client Secret** on MadoGit's sign-in screen.

---

## 9. Documentation Suites

Comprehensive technical documentation is maintained in the `docs/` directory:

- [Architecture Specification](docs/architecture.md): Deep-dive into MVVM layering, Room database schema, StateFlow design, and dynamic theming internals.
- [Sync Engine & Rate Limiting](docs/sync-and-rate-limiting.md): Polling algorithm, HTTP ETag caching protocol, deduplication hashing, and WorkManager Doze handling.
- [Authentication & Security](docs/authentication-and-security.md): Threat model, Android Keystore encryption, OAuth 2.0 flow, and permission justifications.
- [Developer Setup & Contributing](docs/setup-and-contribution.md): Environment configuration, coding conventions, Jetpack Compose standards, and contribution guidelines.

---

## 10. Project Structure

```
madogit/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/aipos/madogit/
│   │   │   │   ├── data/
│   │   │   │   │   ├── api/          # Retrofit interface, Moshi models, OkHttp client
│   │   │   │   │   ├── auth/         # TokenManager (EncryptedSharedPreferences)
│   │   │   │   │   ├── database/     # AppDatabase, Room DAOs, and Entities
│   │   │   │   │   └── repository/   # GitHubRepository & PreferencesRepository
│   │   │   │   ├── notifications/    # NotificationHelper & Android Notification Channels
│   │   │   │   ├── ui/
│   │   │   │   │   ├── assistant/    # Priority triage screen
│   │   │   │   │   ├── auth/         # Token & OAuth login screens
│   │   │   │   │   ├── components/   # Reusable Compose UI elements
│   │   │   │   │   ├── dashboard/    # Main account activity dashboard
│   │   │   │   │   ├── navigation/   # Typed Compose navigation graph
│   │   │   │   │   ├── notifications/# History and filter view
│   │   │   │   │   ├── onboarding/   # Permissions & first-run guidance
│   │   │   │   │   ├── repositories/ # Repository browser & toggle list
│   │   │   │   │   ├── settings/     # App configuration, diagnostics, theme toggle
│   │   │   │   │   └── theme/        # Material You Monet Color, Type, Shape, Theme
│   │   │   │   └── worker/           # WorkManager GitHubSyncWorker & Scheduler
│   │   │   └── res/
│   │   │       ├── values/           # Strings, colors, styles
│   │   │       └── xml/              # Backup & extraction rules
│   │   └── test/
│   │       └── java/com/aipos/madogit/ # Unit & Robolectric test suite
├── docs/
│   ├── assets/                       # Brand artwork and diagrams
│   ├── architecture.md               # Architecture documentation
│   ├── authentication-and-security.md# Security & auth specification
│   ├── setup-and-contribution.md     # Setup and contribution guide
│   └── sync-and-rate-limiting.md     # Sync engine & rate limiting
├── build.gradle.kts                  # Root Gradle build script
├── settings.gradle.kts               # Project configuration
├── LICENSE                           # Apache License 2.0
└── README.md                         # Main repository documentation
```

---

## 11. Testing & Quality Assurance

MadoGit maintains a comprehensive automated testing pipeline:

- **Unit Tests**: Verify business logic, date formatting, filter rules, and preference schemas.
- **Robolectric Tests**: Execute Android framework-dependent tests on the JVM without an emulator, testing Context resource extraction, notification channel bindings, and WorkManager configurations.
- **Continuous Validation**: All pull requests must pass `./gradlew testDebugUnitTest` and compile without errors.

```bash
# Execute the complete unit test verification suite
./gradlew testDebugUnitTest
```

---

## 12. License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for the complete terms and conditions.

```
Copyright 2026 gininaba (MadoGit Contributors)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

