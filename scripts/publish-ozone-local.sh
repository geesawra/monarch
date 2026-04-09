#!/usr/bin/env bash
#
# Publish the ozone submodule's JVM-targeted artifacts to ~/.m2 so Monarch
# can consume a locally-edited ozone without waiting for upstream releases.
#
# Why this script exists:
#   - The ozone submodule at libs/ozone is a Kotlin Multiplatform project.
#   - Its Maven publications are gated behind a signAllPublications() call in
#     its ozone-publish convention plugin (libs/ozone/build-logic/.../PublishingPlugin.kt),
#     so a bare `publishToMavenLocal` fails with "no configured signatory".
#   - Its build expects JDK 17 (CI) but works on 21 too; 25 breaks Gradle 8.14.
#   - Monarch only needs the JVM variant, so publishing iOS/JS/Native targets
#     is wasted work and also triggers the same signing failure.
#
# What this script does:
#   1. Generates a throwaway unprotected GPG key (first run only, cached).
#   2. Publishes the `jvm` + `kotlinMultiplatform` publications for the four
#      ozone modules Monarch's :bluesky transitively pulls in:
#        :bluesky, :oauth, :api-gen-runtime, :api-gen-runtime-internal
#      using the throwaway key as signingInMemoryKey.
#   3. Overrides POM_VERSION to 0.3.3-local via -P so the published coordinate
#      is sh.christian.ozone:*:0.3.3-local. Matches Monarch's libs.versions.toml
#      `ozone = "0.3.3-local"`. No such version exists on Maven Central, so if
#      this script hasn't been run, Monarch's Gradle resolution fails loudly
#      instead of silently downloading the upstream 0.3.3 artifact (which has
#      a different API than the submodule HEAD).
#   4. Uses JDK 21 from /usr/lib/jvm/java-21-openjdk (override with JAVA_HOME).
#
# After running this, Monarch's settings.gradle.kts -- which has mavenLocal()
# scoped to the sh.christian.ozone group -- will resolve ozone from ~/.m2.
#
# Usage:
#   scripts/publish-ozone-local.sh             # uses /usr/lib/jvm/java-21-openjdk
#   JAVA_HOME=/path/to/jdk21 scripts/publish-ozone-local.sh
#
# Heads up: the pinned submodule commit can diverge from the tagged 0.3.3
# release on Maven Central. If Monarch fails to compile after switching,
# it's almost certainly API drift, not a publish-loop bug -- compare against
# the commit at libs/ozone or roll the submodule back.

set -euo pipefail

MONARCH_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OZONE_DIR="$MONARCH_ROOT/libs/ozone"
KEY_DIR="$HOME/.local/share/monarch/ozone-publish-key"
KEY_FILE="$KEY_DIR/throwaway-key.asc"
KEY_GNUPGHOME="$KEY_DIR/gnupg"

: "${JAVA_HOME:=/usr/lib/jvm/java-21-openjdk}"
export JAVA_HOME

if [[ ! -d "$OZONE_DIR" ]]; then
  echo "ozone submodule not found at $OZONE_DIR" >&2
  echo "did you forget to 'git submodule update --init'?" >&2
  exit 1
fi

if [[ ! -f "$KEY_FILE" ]]; then
  echo "==> generating throwaway GPG key for local ozone publishing"
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

cd "$OZONE_DIR"

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

LOCAL_VERSION="0.3.3-local"

echo "==> publishing ozone modules as $LOCAL_VERSION to mavenLocal (JAVA_HOME=$JAVA_HOME)"
./gradlew "${TASKS[@]}" \
  -PPOM_VERSION="$LOCAL_VERSION" \
  -PsigningInMemoryKey="$ARMORED_KEY" \
  -PsigningInMemoryKeyPassword=""

echo "==> done. Monarch will now pick up sh.christian.ozone:*:$LOCAL_VERSION from ~/.m2"
