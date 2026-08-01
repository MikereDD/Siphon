#!/bin/sh
set -eu

GRADLE_VERSION=8.9
GRADLE_SHA256=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/siphon-bootstrap"
ZIP="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
HOME_DIR="$CACHE_ROOT/gradle-$GRADLE_VERSION"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

verify() {
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$ZIP" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    actual=$(shasum -a 256 "$ZIP" | awk '{print $1}')
  else
    echo "A SHA-256 utility (sha256sum or shasum) is required." >&2
    exit 1
  fi
  [ "$actual" = "$GRADLE_SHA256" ] || {
    echo "Gradle download checksum mismatch." >&2
    rm -f "$ZIP"
    exit 1
  }
}

if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  mkdir -p "$CACHE_ROOT"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 -o "$ZIP" "$URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL"
    else
      echo "curl or wget is required for the first build." >&2
      exit 1
    fi
  fi
  verify
  rm -rf "$HOME_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP" -d "$CACHE_ROOT"
  else
    echo "unzip is required for the first build." >&2
    exit 1
  fi
fi

exec "$HOME_DIR/bin/gradle" "$@"
