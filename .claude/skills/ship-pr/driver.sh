#!/usr/bin/env bash
# Usage: driver.sh [--title "PR title"] [--body "PR body"] [--draft]
# Pushes the current branch, creates a PR, waits for CI, then triggers the build.
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
echo "==> Waiting for CI checks on PR #$PR_NUMBER (this takes a few minutes)..."
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

echo ""
echo "==> Done. Build workflow dispatched."
echo "    Track it: gh run list --workflow=build.yml --branch=$BRANCH"
echo "    or: gh run watch"
