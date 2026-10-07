# Design Document: App Screenshots Showcase for README.md

**Date**: 2026-10-07  
**Author**: Antigravity  
**Status**: Approved  

---

## 1. Context & Motivation

MadoGit is a developer-first personal GitHub notification assistant for Android featuring Material You Monet dynamic theming, offline-first Room persistence, and a polished Jetpack Compose UI. Currently, the repository's `README.md` contains high-level documentation and an architectural diagram, but lacks visual representations of the actual running application.

Adding high-resolution screenshots directly to `README.md` immediately conveys the visual fidelity, UX ergonomics, and feature set of the application to developers and contributors.

---

## 2. Asset Strategy

### 2.1 Storage Location
All documentation assets are organized under the version-controlled `docs/` hierarchy. Screenshots will be copied from `Screenshots/` into `docs/assets/screenshots/` with semantic filenames, while retaining `Screenshots/` for uncompressed original assets:

| Source File | Destination File | Screen Purpose |
|---|---|---|
| `Screenshots/01.png` | `docs/assets/screenshots/01-onboarding.png` | Onboarding & Privacy Architecture |
| `Screenshots/02.png` | `docs/assets/screenshots/02-auth.png` | PAT & OAuth App Credentials |
| `Screenshots/03.png` | `docs/assets/screenshots/03-dashboard.png` | Account Dashboard & Rate Quota Meter |
| `Screenshots/04.png` | `docs/assets/screenshots/04-inbox.png` | Notification Inbox & Date Grouping |
| `Screenshots/05.png` | `docs/assets/screenshots/05-repositories.png` | Monitored Repositories & Language Badges |
| `Screenshots/06.png` | `docs/assets/screenshots/06-assistant.png` | Smart Triage & Inbox Zero Celebration |
| `Screenshots/07.png` | `docs/assets/screenshots/07-settings.png` | Material You Dynamic Monet Theme Swatches |

---

## 3. README.md Layout Specification

### 3.1 Section Placement
A new prominent section titled **"App Preview & Screenshots"** will be added immediately following the status callout banner and preceding **1. Overview & Philosophy**.

The Table of Contents will be updated to include:
```markdown
- [App Preview & Screenshots](#app-preview--screenshots)
```

### 3.2 Gallery Presentation
To maintain responsive alignment on GitHub web and mobile rendering without overwhelming vertical scroll:

#### Tier 1: Core Daily Flow (4 columns)
Showcases the primary interactive screens:
- **Dashboard**: Connected profile card, remaining rate quota meter, metric status cards, and recent activity timeline.
- **Notification Inbox**: Filter chips, search bar, chronological date grouping (*Today*, *Yesterday*, *This Week*), and swipe actions.
- **Repositories**: Monitored repository list, status filter chips, language color dots, branch tags, and active switches.
- **Smart Assistant**: Priority triage with unread reviews, assigned issues, failing CI runs, and Inbox Zero state.

#### Tier 2: Setup & Personalization (3 columns)
Showcases first-run setup, security, and theming:
- **Onboarding**: Feature breakdown highlighting real push notifications, event deduplication, and zero-intermediate-server architecture.
- **Authentication**: Token entry with scope chips, OAuth toggle, Android Keystore encryption highlight, and token generation link.
- **Settings & Theming**: Live Monet palette swatches, theme mode selection (Light/Dark/System), and system notification channel link.

### 3.3 HTML Markup Standard
Each tier will use a standard GitHub-compatible HTML `<table>` with centered titles, explicit image widths (`width="220"`), and concise explanatory captions.

---

## 4. Verification Plan

1. Verify all 7 image files exist and match their destination paths in `docs/assets/screenshots/`.
2. Verify relative links in `README.md` correctly resolve against the repository root.
3. Validate Markdown linting and Table of Contents link anchoring.
