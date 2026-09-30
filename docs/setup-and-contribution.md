# MadoGit Developer Setup & Contribution Guide

> [!IMPORTANT]
> **Project Status: Experimental**
> MadoGit is in active, experimental development. Features, internal schemas, and UI components are continuously evolving. Contributions, issues, and feedback are welcome.

## Prerequisites & Development Environment

To build, run, and contribute to MadoGit, ensure your local development workstation meets the following specifications:

- **Operating System**: macOS, Linux, or Windows 10/11 with WSL2.
- **Java Development Kit**: JDK 17 (Azul Zulu, OpenJDK, or Eclipse Temurin recommended).
- **Android Studio**: Android Studio Hedgehog (2023.1.1) or newer (Koala / Ladybug recommended).
- **Android SDK Requirements**:
  - `compileSdk`: 36 (Android 16)
  - `targetSdk`: 36
  - `minSdk`: 24 (Android 7.0 Nougat)
  - Android SDK Build-Tools: `34.0.0` or newer
  - NDK: Not required (pure Kotlin / JVM codebase).

---

## Workspace Setup

### 1. Repository Preparation

Clone the repository and inspect the branch structure:

```bash
git clone https://github.com/gininaba/MadoGit.git
cd MadoGit
```

### 2. JDK Verification

Confirm that JDK 17 is active in your current shell:

```bash
java -version
```

If multiple Java versions exist on your machine, configure `JAVA_HOME`:

```bash
# macOS (zsh)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### 3. Gradle Wrapper Permissions

Ensure the Gradle wrapper executable has execution permissions:

```bash
chmod +x gradlew
```

---

## Build Commands

MadoGit utilizes standard Gradle tasks with configuration caching enabled:

### Build Debug APK

```bash
./gradlew assembleDebug
```
Output artifact location: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK (Unsigned)

```bash
./gradlew assembleRelease
```

### Run All Unit and Robolectric Tests

```bash
./gradlew testDebugUnitTest
```

### Run Static Analysis & Lint

```bash
./gradlew lintDebug
```

---

## Testing Framework & Best Practices

MadoGit's test suite resides in `app/src/test/java/com/aipos/madogit/`:

1. **JUnit 4 (`ExampleUnitTest.kt`)**: Fast, pure unit tests validating pure business logic, string formatting, date parsing, and preference models without Android runtime dependencies.
2. **Robolectric (`ExampleRobolectricTest.kt`)**: JVM-based Android framework simulation (`@Config(sdk = [34])`):
   - Validates resource resolution (`R.string.app_name`, `R.string.app_full_name`).
   - Verifies notification preferences filtering algorithms.
   - Tests WorkManager constraints and parameter bindings.

When writing new tests:
- Favor unit and Robolectric tests for all repository, view model, and business logic changes.
- Ensure all tests are hermetic and do not make live network requests. Use MockWebServer or test fakes for HTTP mocking.

---

## Code Style & Architectural Conventions

### 1. Kotlin & Coroutines Guidelines
- Use explicit types on public API boundaries.
- Favor immutable data classes (`val` over `var`).
- Keep coroutine execution within structured concurrency scopes (`viewModelScope` in ViewModels, `CoroutineWorker` scopes in background tasks).
- Avoid blocking calls (`Thread.sleep`, `runBlocking`) on the main thread.

### 2. Jetpack Compose & Material You Theming
- Always consume colors from `MaterialTheme.colorScheme` (e.g. `colorScheme.surfaceContainer`, `colorScheme.primary`, `colorScheme.onSurface`). Never hardcode hex color values directly into composables.
- Respect dynamic theming: verify that screens render legibly in both Dark Mode and Light Mode with dynamic Monet extraction active and inactive.
- Keep composables modular and stateless where possible; elevate state to parent composables or the ViewModel.
- Provide descriptive `contentDescription` on interactive accessibility targets or set to `null` for purely decorative elements.

### 3. Resource Localization
- Define user-facing strings in `app/src/main/res/values/strings.xml`.
- Do not hardcode string literals inside composables when user-facing.

---

## Contribution Workflow

1. **Create a Topic Branch**:
   ```bash
   git checkout -b feature/monet-dynamic-tiles
   ```
2. **Implement Changes**:
   - Write clean, well-tested code following repository patterns.
   - Run `./gradlew testDebugUnitTest` to verify no regressions occur.
3. **Commit Standards**:
   - Follow standard Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`.
   - **Strict Policy**: Commit messages and documentation must contain **strictly no emojis**.
4. **Submit Pull Request**:
   - Provide a clear description of the problem solved.
   - Detail manual verification steps performed on physical devices or emulators.
