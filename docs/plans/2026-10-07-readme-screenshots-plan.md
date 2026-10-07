# App Screenshots in README Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** Integrate high-resolution application screenshots into `README.md` showcasing the key flows, theming engine, and triage features of MadoGit.

**Architecture:** Copy screenshot assets from `Screenshots/` into `docs/assets/screenshots/` with semantic naming. Add an "App Preview & Screenshots" visual showcase section with two-tier GitHub-compatible HTML tables directly after the introductory status banner in `README.md`.

**Tech Stack:** Markdown, HTML tables, PNG image assets.

---

### Task 1: Organize Screenshots in docs/assets/screenshots

**Files:**
- Create directory: `docs/assets/screenshots/`
- Copy assets:
  - `Screenshots/01.png` -> `docs/assets/screenshots/01-onboarding.png`
  - `Screenshots/02.png` -> `docs/assets/screenshots/02-auth.png`
  - `Screenshots/03.png` -> `docs/assets/screenshots/03-dashboard.png`
  - `Screenshots/04.png` -> `docs/assets/screenshots/04-inbox.png`
  - `Screenshots/05.png` -> `docs/assets/screenshots/05-repositories.png`
  - `Screenshots/06.png` -> `docs/assets/screenshots/06-assistant.png`
  - `Screenshots/07.png` -> `docs/assets/screenshots/07-settings.png`

**Step 1: Create target directory and copy files**
Run:
```bash
mkdir -p "docs/assets/screenshots"
cp "Screenshots/01.png" "docs/assets/screenshots/01-onboarding.png"
cp "Screenshots/02.png" "docs/assets/screenshots/02-auth.png"
cp "Screenshots/03.png" "docs/assets/screenshots/03-dashboard.png"
cp "Screenshots/04.png" "docs/assets/screenshots/04-inbox.png"
cp "Screenshots/05.png" "docs/assets/screenshots/05-repositories.png"
cp "Screenshots/06.png" "docs/assets/screenshots/06-assistant.png"
cp "Screenshots/07.png" "docs/assets/screenshots/07-settings.png"
```

**Step 2: Verify all 7 files exist in docs/assets/screenshots**
Run: `ls -lh docs/assets/screenshots`
Expected: 7 PNG files with sizes matching the source screenshots.

---

### Task 2: Update README.md with Screenshots Showcase

**Files:**
- Modify: `README.md`

**Step 1: Update Table of Contents**
Add link to `#app-preview--screenshots` in the Table of Contents.

**Step 2: Add App Preview & Screenshots section**
Add the section with:
- Row 1: Core Daily Flow (Dashboard, Notification Inbox, Repositories, Smart Assistant)
- Row 2: Setup & Customization (Onboarding, Authentication, Material You Settings)
- Centered HTML layout with explicit widths (`width="220"`) and concise captions.

**Step 3: Verify formatting and links**
Ensure all relative paths `docs/assets/screenshots/*.png` resolve to actual files.

---

### Task 3: Final Verification & Plan Completion

**Files:**
- Read: `README.md`
- Inspect: `docs/assets/screenshots`

**Step 1: Verify all referenced images exist**
Verify each image link in `README.md` points to an existing file.

**Step 2: Update live task tracker**
Update `docs/plans/task.md` to indicate all tasks complete.
