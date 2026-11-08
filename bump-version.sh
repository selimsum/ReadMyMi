#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$SCRIPT_DIR/app/build.gradle"

if [ ! -f "$GRADLE_FILE" ]; then
  echo "Error: Could not find app/build.gradle" >&2
  exit 1
fi

OLD_VERSION=$(grep -E '^\s*versionName\s+' "$GRADLE_FILE" | head -n1 | sed -E 's/.*versionName[[:space:]=]*["\x27]([^"\x27]+)["\x27].*/\1/')
OLD_CODE=$(grep -E '^\s*versionCode\s+' "$GRADLE_FILE" | head -n1 | sed -E 's/.*versionCode\s+([0-9]+).*/\1/')

if [ -z "$OLD_VERSION" ] || [ -z "$OLD_CODE" ]; then
  echo "Error: Could not extract version from $GRADLE_FILE" >&2
  exit 1
fi

MODE="${1:-}"
COMMIT=false
PUSH=false

for arg in "$@"; do
  case "$arg" in
    --commit|-c) COMMIT=true ;;
    --push|-p) PUSH=true; COMMIT=true ;;
  esac
done

NEW_VERSION=""
if [ "$MODE" = "--patch" ] || [ "$MODE" = "-patch" ]; then
  IFS='.' read -r MAJ MIN PAT <<< "$OLD_VERSION"
  PAT=$((PAT + 1))
  NEW_VERSION="$MAJ.$MIN.$PAT"
elif [ "$MODE" = "--minor" ] || [ "$MODE" = "-minor" ]; then
  IFS='.' read -r MAJ MIN PAT <<< "$OLD_VERSION"
  MIN=$((MIN + 1))
  NEW_VERSION="$MAJ.$MIN.0"
elif [ "$MODE" = "--major" ] || [ "$MODE" = "-major" ]; then
  IFS='.' read -r MAJ MIN PAT <<< "$OLD_VERSION"
  MAJ=$((MAJ + 1))
  NEW_VERSION="$MAJ.0.0"
elif [ -n "$MODE" ] && [[ "$MODE" != -* ]]; then
  NEW_VERSION="${MODE#v}"
else
  echo "Current version: $OLD_VERSION (versionCode: $OLD_CODE)"
  echo ""
  echo "Usage:"
  echo "  ./bump-version.sh --patch [--commit] [--push]"
  echo "  ./bump-version.sh --minor [--commit] [--push]"
  echo "  ./bump-version.sh --major [--commit] [--push]"
  echo "  ./bump-version.sh <new_version> [--commit] [--push]"
  exit 0
fi

NEW_CODE=$((OLD_CODE + 1))

# Replace in app/build.gradle
sed -i.bak -E "s/versionCode [0-9]+/versionCode $NEW_CODE/" "$GRADLE_FILE"
sed -i.bak -E "s/versionName [\"'][^\"']+[\"']/versionName \"$NEW_VERSION\"/" "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

echo "=========================================="
echo " Successfully bumped ReadMyMi version:   "
echo "   versionName: $OLD_VERSION -> $NEW_VERSION"
echo "   versionCode: $OLD_CODE -> $NEW_CODE"
echo "=========================================="

if [ "$COMMIT" = true ]; then
  echo "==> Staging app/build.gradle and creating commit..."
  git add "$GRADLE_FILE"
  git commit -m "chore(release): bump version to $NEW_VERSION"
  if [ "$PUSH" = true ]; then
    echo "==> Pushing to origin main..."
    git push origin main
    echo "==> Pushed successfully! GitHub Actions is now publishing release v$NEW_VERSION."
  fi
else
  echo ""
  echo "To publish the release on GitHub, commit and push:"
  echo "  git commit -am \"chore(release): bump version to $NEW_VERSION\""
  echo "  git push origin main"
  echo ""
  echo "GitHub Actions will automatically build APKs and publish release v$NEW_VERSION on push."
fi
