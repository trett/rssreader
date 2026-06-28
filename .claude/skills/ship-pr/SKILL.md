---
name: ship-pr
description: Push a PR, wait for CI tests to pass, then trigger the build-image GitHub Actions workflow. Use when asked to ship, push a PR, publish a branch, build an image, or run the build workflow.
---

Automates the full ship cycle for this repo: push branch → open PR → wait for `test.yml` CI → trigger `build.yml` (builds GraalVM native image + pushes to Google Artifact Registry).

Driver: `.claude/skills/ship-pr/driver.sh`  
Requires: `gh` CLI authenticated (`gh auth status`), current branch pushed to `origin`.

## Run (agent path)

```bash
# Create a new PR and ship it
bash .claude/skills/ship-pr/driver.sh \
  --title "feat: my change" \
  --body "$(cat <<'EOF'
## Summary
- what changed

## Test plan
- [ ] CI passes
EOF
)"
```

```bash
# If a PR already exists for the current branch, skip creation:
bash .claude/skills/ship-pr/driver.sh
```

```bash
# Open as draft (skips merge-blocking checks until ready):
bash .claude/skills/ship-pr/driver.sh --title "WIP: my change" --draft
```

The script exits non-zero and prints which check failed if CI doesn't pass. It does **not** trigger the build in that case.

After the build is dispatched, track it:

```bash
gh run list --workflow=build.yml --branch=$(git rev-parse --abbrev-ref HEAD)
gh run watch   # interactive tail of the most recent run
```

## What each step does

| Step | Command used | Notes |
|------|-------------|-------|
| Push | `git push -u origin <branch>` | No-ops if already up to date |
| Create PR | `gh pr create --base main` | Skipped if PR exists for branch |
| Wait for CI | `gh pr checks <n> --watch` | `test.yml` runs on all PRs to main |
| Trigger build | `gh workflow run build.yml --ref <branch>` | `build.yml` is `workflow_dispatch` only |

## Gotchas

- **`build.yml` requires repo secrets** (`GAR_LOCATION`, `GAR_PROJECT_ID`, `GAR_REPOSITORY`, `GCP_SA_KEY`). The dispatch succeeds even if secrets are missing; the run itself will fail. Check with `gh run watch`.
- **`workflow_dispatch` must be enabled on the branch**: `gh workflow run` targets the branch you pass via `--ref`. If the workflow definition doesn't exist on that branch (e.g. you deleted it), the dispatch fails with a 422.
- **`gh pr checks --watch` only sees checks that have started**: if CI is queued and hasn't started within ~30 s, the watch may return immediately with no checks. Re-run the driver; it will skip PR creation and go straight to waiting.
- The driver refuses to run on `main` to prevent accidental direct-branch pushes.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `gh: not logged in` | Run `gh auth login` |
| `422 Unprocessable Entity` on workflow run | The `build.yml` trigger or secrets missing on that branch |
| Checks return immediately with 0 results | CI queue delay — re-run driver; it re-polls |
| `--title is required` error | No existing PR found and no title was provided |
