| # | Task | Priority | Status |
|---|------|----------|--------|
| 1 | Add test seam (injectable `GitHubApiService`) + fake API for repository tests | Infra | done |
| 2 | Sync engine: dedupe purge data loss, repo monitor-state clobbering, sync mutex, baseline (no-flood) sync, cancelled runs, rate-limit tiers, URL mapping, stale repo removal, pagination | P0/P1 | done |
| 3 | Auth/session: 401 clears token, disconnect wipes account data, token memory cache, DB singleton fix, logging redaction | P1 | done |
| 4 | App lifecycle: auth-driven WorkManager scheduling + initial sync, worker retry/backoff | P0 | done |
| 5 | MainActivity/OAuth: no re-processing on recreate, error callback, encoded authorize URL, "View in App" action | P1 | done |
| 6 | UI: onboarding completion, auth error display, dashboard unfiltered timeline, repo refresh state, saveable state, settings toggles, nav-rail badges, date grouping, language heuristic | P0-P2 | done |
| 7 | Full verification: unit tests + assembleDebug + assembleRelease (R8) + docs update | Verify | done |
