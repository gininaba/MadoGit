# MadoGit Authentication & Security Specification

## Overview

MadoGit adheres to a strict sovereign-privacy model: **zero intermediate servers, zero analytics, zero crash telemetry, and zero third-party tracking**. All credentials and repository metadata remain strictly stored on the host Android device.

---

## Threat Model & Privacy Guarantees

### Sovereign Data Storage
- Network requests communicate exclusively with `api.github.com` over TLS 1.3.
- No intermediary proxy or synchronization server exists.
- The application collects no telemetry, identifiers, or behavioral analytics.

### Local Credential Storage
Credentials (Personal Access Tokens and OAuth Access Tokens) are persisted using hardware-backed **Android Keystore System** AES-256 GCM encryption:
- Encryption standard: AES-256 GCM (`AES/GCM/NoPadding`) for secret values.
- Fail-closed contract: `CryptoManager.encrypt()` throws `IllegalStateException` if Keystore encryption fails, completely preventing unencrypted plaintext fallback.
- Key protection: Keystore master keys are generated with 256-bit AES encryption.
- Memory lifecycle: Tokens reside in memory only during active execution scopes and are cleared upon sign-out.

### Intent & Deep Link Sanitization
- Outbound intent launches and notification tap `PendingIntent` targets strictly sanitize URIs, accepting only valid `https://` and `http://` schemes.
- OAuth deep links (`ghnotifier://oauth/callback`) require cryptographic state verification (`state` token) to guard against cross-site request forgery (CSRF).

---

## Authentication Methods

MadoGit provides two authentication mechanisms tailored for developers:

### 1. Personal Access Token (PAT)

Personal Access Tokens offer direct, immediate setup without requiring a third-party server or OAuth registration:

- **Classic Tokens**: Prefixed with `ghp_`. Compatible with classic repository and organization access.
- **Fine-Grained Tokens**: Prefixed with `github_pat_`. Compatible with granular, repository-scoped permissions.
- **Scope Verification**: Upon entry, MadoGit immediately executes a validation query (`GET /user`) to verify credential validity, extract the authenticated identity, and inspect granted scopes via the `X-OAuth-Scopes` header.

### 2. GitHub OAuth App Flow

For users who prefer web-based OAuth consent:

1. User registers or supplies an existing GitHub OAuth App (`Client ID` and `Client Secret`).
2. MadoGit launches the device browser to GitHub's authorization endpoint:
   ```
   https://github.com/login/oauth/authorize?client_id={CLIENT_ID}&scope=repo,notifications,workflow,read:user&redirect_uri=ghnotifier://oauth/callback
   ```
3. GitHub redirects the browser to the custom deep-link scheme `ghnotifier://oauth/callback?code={AUTHORIZATION_CODE}`.
4. `MainActivity` intercepts the URI intent, extracts the temporary authorization code, and completes the token exchange via `POST https://github.com/login/oauth/access_token`.
5. The received access token is stored securely in encrypted storage.

---

## Required GitHub Scopes Matrix

MadoGit adheres to the principle of least privilege. Only scopes necessary for notification monitoring and account identification are requested:

| Scope | Required | Justification |
|---|---|---|
| `repo` | Yes | Grants read access to private/public repositories, issues, pull requests, reviews, and repository dispatch events. |
| `notifications` | Yes | Allows fetching thread notifications from `/notifications` and marking threads as read. |
| `workflow` | Yes | Allows querying GitHub Actions workflow run statuses and conclusion states (e.g. failure alerts). |
| `read:user` | Yes | Fetches user profile display name, login handle, and avatar URL for dashboard display. |
| `admin:repo_hook` | No | Not requested. MadoGit uses client-side polling rather than webhooks, avoiding unnecessary administrative access. |
| `write:repo_hook` | No | Not requested. |
| `delete_repo` | No | Not requested. |

---

## Android System Permissions

MadoGit requests only standard Android permissions necessary for background operation and alert delivery:

| Permission | Protection Level | Purpose |
|---|---|---|
| `android.permission.INTERNET` | Normal | Required for HTTPS requests to `api.github.com`. |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Allows WorkManager and Retrofit to detect network availability and meter status. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Explicit user consent required to display notifications in the Android notification drawer. |
| `android.permission.VIBRATE` | Normal | Provides haptic alert feedback when priority notifications are dispatched. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Restores WorkManager periodic synchronization jobs following device reboot. |

---

## Account Disconnection & Data Purging

When a user signs out from the Settings screen:
1. `TokenManager.clearToken()` deletes stored tokens from `EncryptedSharedPreferences`.
2. WorkManager synchronization jobs are cancelled via `WorkManager.cancelAllWork()`.
3. Local Room database tables (`monitored_repositories`, `notifications`, `processed_events`, `sync_logs`) are cleared.
4. Cached HTTP responses and image caches in Coil are evicted.
