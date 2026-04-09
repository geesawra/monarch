#!/usr/bin/env bash
#
# Build ozone from source and stage its JVM artifacts inside Monarch so the
# Android build can consume a locally-compiled ozone without a git submodule
# and without touching ~/.m2.
#
# How it works:
#   1. Clones (or fast-forwards) ozone into ~/.cache/monarch-ozone-src/.
#   2. Checks out the pinned OZONE_REF below.
#   3. Generates a throwaway unprotected GPG key (first run only, cached at
#      ~/.local/share/monarch/ozone-publish-key/) -- ozone's ozone-publish
#      convention plugin unconditionally calls signAllPublications().
#   4. Publishes the four modules Monarch needs as ${LOCAL_VERSION} into
#      ~/.m2 via publishJvmPublicationToMavenLocal +
#      publishKotlinMultiplatformPublicationToMavenLocal, overriding
#      POM_VERSION with -P so the submodule's gradle.properties stays clean.
#   5. Copies just the ${LOCAL_VERSION} subtrees out of ~/.m2 into
#      libs/ozone-artifacts/ so Monarch's settings.gradle.kts -- which
#      declares a maven {} repo at that path -- can resolve them.
#
# libs/ozone-artifacts/ is .gitignored, so running this script is required
# after a fresh clone and after any OZONE_REF bump. Monarch's
# libs.versions.toml pins `ozone = "0.3.3-local"`; no version on Maven
# Central matches that, so skipping this step produces a loud Gradle
# resolution failure instead of a silently wrong build against upstream.
#
# Tune the three variables below to point at a different fork or commit.

set -euo pipefail

# --- configuration ---------------------------------------------------------

OZONE_REPO="https://github.com/christiandeange/ozone.git"
OZONE_REF="d312c10"   # v0.3.3-80-gd312c10
LOCAL_VERSION="0.3.3-local"

# --- paths -----------------------------------------------------------------

MONARCH_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OZONE_SRC="$HOME/.cache/monarch-ozone-src"
ARTIFACTS_DIR="$MONARCH_ROOT/libs/ozone-artifacts"
KEY_DIR="$HOME/.local/share/monarch/ozone-publish-key"
KEY_FILE="$KEY_DIR/throwaway-key.asc"
KEY_GNUPGHOME="$KEY_DIR/gnupg"

: "${JAVA_HOME:=/usr/lib/jvm/java-21-openjdk}"
export JAVA_HOME

# --- throwaway GPG key (first run only) ------------------------------------

if [[ ! -f "$KEY_FILE" ]]; then
  echo "==> generating throwaway GPG key for ozone signing"
  mkdir -p "$KEY_GNUPGHOME"
  chmod 700 "$KEY_GNUPGHOME"
  batch_file="$(mktemp)"
  trap 'rm -f "$batch_file"' EXIT
  cat > "$batch_file" <<'BATCH'
%no-protection
Key-Type: RSA
Key-Length: 2048
Name-Real: monarch-local
Name-Email: monarch-local@invalid
Expire-Date: 0
%commit
BATCH
  GNUPGHOME="$KEY_GNUPGHOME" gpg --batch --gen-key "$batch_file" 2>&1 | tail -n 5
  key_id=$(GNUPGHOME="$KEY_GNUPGHOME" gpg --list-secret-keys --with-colons \
    | awk -F: '$1 == "sec" { print $5; exit }')
  GNUPGHOME="$KEY_GNUPGHOME" gpg --armor --export-secret-keys "$key_id" > "$KEY_FILE"
  echo "    wrote $KEY_FILE"
fi

ARMORED_KEY="$(cat "$KEY_FILE")"

# --- fetch ozone source ----------------------------------------------------

if [[ ! -d "$OZONE_SRC/.git" ]]; then
  echo "==> cloning $OZONE_REPO to $OZONE_SRC"
  mkdir -p "$(dirname "$OZONE_SRC")"
  git clone --quiet "$OZONE_REPO" "$OZONE_SRC"
fi

echo "==> fetching and checking out $OZONE_REF"
git -C "$OZONE_SRC" fetch --quiet --all --tags
git -C "$OZONE_SRC" checkout --quiet --detach "$OZONE_REF"

# --- publish ---------------------------------------------------------------

cd "$OZONE_SRC"

MODULES=(
  :bluesky
  :oauth
  :api-gen-runtime
  :api-gen-runtime-internal
)

TASKS=()
for m in "${MODULES[@]}"; do
  TASKS+=("$m:publishJvmPublicationToMavenLocal")
  TASKS+=("$m:publishKotlinMultiplatformPublicationToMavenLocal")
done

echo "==> publishing ozone $LOCAL_VERSION to mavenLocal (JAVA_HOME=$JAVA_HOME)"
./gradlew "${TASKS[@]}" \
  -PPOM_VERSION="$LOCAL_VERSION" \
  -PsigningInMemoryKey="$ARMORED_KEY" \
  -PsigningInMemoryKeyPassword=""

# --- stage into Monarch ----------------------------------------------------

echo "==> staging $LOCAL_VERSION artifacts into $ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

while IFS= read -r src; do
  rel="${src#$HOME/.m2/repository/}"
  dest="$ARTIFACTS_DIR/$rel"
  mkdir -p "$(dirname "$dest")"
  rm -rf "$dest"
  cp -r "$src" "$dest"
done < <(find "$HOME/.m2/repository/sh/christian/ozone" -type d -name "$LOCAL_VERSION")

echo "==> done. Monarch will resolve sh.christian.ozone:*:$LOCAL_VERSION from libs/ozone-artifacts/"
