---
name: ship-pr
description: Push a PR, wait for CI tests to pass, then trigger the build-image GitHub Actions workflow. Use when asked to ship, push a PR, publish a branch, build an image, or run the build workflow.
---

Automates the full ship cycle for this repo: push branch → open PR → wait for `test.yml` CI → trigger `build.yml` → wait for build to complete → print the deployed image tag.

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

The script exits non-zero and prints which step failed (CI or build). On success it prints the deployed image tag.

## What each step does

| Step | Command used | Notes |
|------|-------------|-------|
| Push | `git push -u origin <branch>` | No-ops if already up to date |
| Create PR | `gh pr create --base main` | Skipped if PR exists for branch |
| Wait for CI | `gh pr checks <n> --watch` | `test.yml` runs on all PRs to main |
| Trigger build | `gh workflow run build.yml --ref <branch>` | `build.yml` is `workflow_dispatch` only |
| Wait for build | `gh run watch <run_id> --exit-status` | Polls until `Build-Containers` job finishes |
| Report tag | `git rev-parse --short HEAD` | Matches `DOCKER_TAG` in `build.yml` |

## Image tag

`build.yml` sets `DOCKER_TAG=$(git rev-parse --short HEAD)` on the runner. Since the driver pushes the branch before dispatching, the local and remote short SHA are identical.

Full image reference format:
```
<GAR_LOCATION>-docker.pkg.dev/<GAR_PROJECT_ID>/<GAR_REPOSITORY>/rssreader:<short-sha>
```

The driver tries to extract the full URL from run logs. If `GAR_*` secret values are masked in the log output, it falls back to showing just the tag suffix (`rssreader:<short-sha>`).

## Gotchas

- **`build.yml` requires repo secrets** (`GAR_LOCATION`, `GAR_PROJECT_ID`, `GAR_REPOSITORY`, `GCP_SA_KEY`). The dispatch succeeds even if secrets are missing; the run itself will fail.
- **`workflow_dispatch` must be enabled on the branch**: `gh workflow run` targets the branch you pass via `--ref`. If the workflow definition doesn't exist on that branch (e.g. you deleted it), the dispatch fails with a 422.
- **`gh pr checks --watch` only sees checks that have started**: if CI is queued and hasn't started within ~30 s, the watch may return immediately with no checks. Re-run the driver; it will skip PR creation and go straight to waiting.
- **Run ID polling**: the driver retries 5 times (with 6 s sleep) to find the queued run after dispatch. On a slow GitHub API it may still print a "not found" warning — in that case track manually with `gh run list --workflow=build.yml`.
- The driver refuses to run on `main` to prevent accidental direct-branch pushes.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `gh: not logged in` | Run `gh auth login` |
| `422 Unprocessable Entity` on workflow run | The `build.yml` trigger or secrets missing on that branch |
| Checks return immediately with 0 results | CI queue delay — re-run driver; it re-polls |
| `--title is required` error | No existing PR found and no title was provided |
| "Could not find workflow run" warning | GitHub API slow — run `gh run watch` manually to track |
