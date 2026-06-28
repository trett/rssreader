#!/usr/bin/env bash
# Usage: driver.sh [--title "PR title"] [--body "PR body"] [--draft]
# Pushes the current branch, creates a PR, waits for CI, triggers the build,
# waits for the build to complete, then prints the deployed image tag.
set -euo pipefail

PR_TITLE=""
PR_BODY=""
DRAFT_FLAG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --title) PR_TITLE="$2"; shift 2 ;;
        --body)  PR_BODY="$2";  shift 2 ;;
        --draft) DRAFT_FLAG="--draft"; shift ;;
        *) echo "Unknown arg: $1" >&2; exit 1 ;;
    esac
done

BRANCH=$(git rev-parse --abbrev-ref HEAD)

if [[ "$BRANCH" == "main" ]]; then
    echo "ERROR: On main branch — check out a feature branch first." >&2
    exit 1
fi

echo "==> Branch: $BRANCH"

# --- 1. Push branch ---
echo "==> Pushing branch to origin..."
git push -u origin "$BRANCH"

# --- 2. Create PR (skip if one already exists) ---
EXISTING_PR=$(gh pr view "$BRANCH" --json number --jq '.number' 2>/dev/null || true)

if [[ -n "$EXISTING_PR" ]]; then
    PR_NUMBER="$EXISTING_PR"
    echo "==> PR #$PR_NUMBER already exists, skipping creation."
else
    if [[ -z "$PR_TITLE" ]]; then
        echo "ERROR: --title is required when creating a new PR." >&2
        exit 1
    fi

    CREATE_ARGS=(--title "$PR_TITLE" --base main)
    [[ -n "$PR_BODY" ]]  && CREATE_ARGS+=(--body "$PR_BODY")
    [[ -n "$DRAFT_FLAG" ]] && CREATE_ARGS+=("$DRAFT_FLAG")

    echo "==> Creating PR: $PR_TITLE"
    PR_URL=$(gh pr create "${CREATE_ARGS[@]}")
    PR_NUMBER=$(echo "$PR_URL" | grep -oE '[0-9]+$')
    echo "==> PR #$PR_NUMBER created: $PR_URL"
fi

# --- 3. Wait for CI checks ---
echo "==> Waiting 60s before first CI check..."
sleep 60
echo "==> Waiting for CI checks on PR #$PR_NUMBER..."
# --watch exits 0 on all-pass, non-zero on any failure
if ! gh pr checks "$PR_NUMBER" --watch --interval 15; then
    echo "ERROR: One or more CI checks failed. Build not triggered." >&2
    echo "==> View failures: gh pr checks $PR_NUMBER"
    exit 1
fi

echo "==> All checks passed."

# --- 4. Trigger build workflow on this branch ---
echo "==> Triggering build.yml on branch $BRANCH..."
gh workflow run build.yml --ref "$BRANCH"

# Wait 4 minutes before first build check to let the run get queued and start
echo "==> Waiting 4 minutes before first build check..."
sleep 240

# --- 5. Find the run that was just triggered ---
# Retry up to 5 times in case the run hasn't appeared in the API yet
RUN_ID=""
for i in $(seq 1 5); do
    RUN_ID=$(gh run list --workflow=build.yml --branch="$BRANCH" \
        --limit=1 --json databaseId,status \
        --jq '.[] | select(.status != "completed") | .databaseId' 2>/dev/null || true)
    # Also accept if it completed very fast (unlikely but possible)
    if [[ -z "$RUN_ID" ]]; then
        RUN_ID=$(gh run list --workflow=build.yml --branch="$BRANCH" \
            --limit=1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)
    fi
    [[ -n "$RUN_ID" ]] && break
    echo "==> Waiting for run to appear (attempt $i/5)..."
    sleep 6
done

if [[ -z "$RUN_ID" ]]; then
    echo "WARNING: Could not find the workflow run. Check manually:" >&2
    echo "    gh run list --workflow=build.yml --branch=$BRANCH" >&2
    echo "    gh run watch" >&2
    exit 1
fi

echo "==> Build run #$RUN_ID started. Watching for completion..."
if ! gh run watch "$RUN_ID" --exit-status --interval 15; then
    echo ""
    echo "ERROR: Build workflow #$RUN_ID failed." >&2
    echo "    Logs: gh run view $RUN_ID --log-failed" >&2
    exit 1
fi

# --- 6. Report the deployed image tag ---
# build.yml sets DOCKER_TAG=$(git rev-parse --short HEAD) on the runner at checkout,
# which equals our local HEAD since we pushed before dispatching.
DOCKER_TAG=$(git rev-parse --short HEAD)

# Try to extract the full image reference from the run logs.
# The "Build and Push Docker Image" step echoes IMAGE_TAG before docker build;
# secrets are masked in CI logs so the prefix may show as *** but we try anyway.
FULL_IMAGE=$(gh run view "$RUN_ID" --log 2>/dev/null \
    | grep -oE '[a-z0-9_.-]+-docker\.pkg\.dev/[^[:space:]]+/rssreader:[a-f0-9]+' \
    | tail -1 || true)

echo ""
echo "============================================"
echo "  Build complete!"
echo "  Image tag (short SHA): $DOCKER_TAG"
if [[ -n "$FULL_IMAGE" ]]; then
    echo "  Full image:            $FULL_IMAGE"
else
    echo "  Full image:            <GAR_LOCATION>-docker.pkg.dev/<PROJECT>/<REPO>/rssreader:$DOCKER_TAG"
    echo "  (Secret values masked in logs; tag suffix is: $DOCKER_TAG)"
fi
echo "============================================"
