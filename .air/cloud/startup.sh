#!/bin/bash
set -euo pipefail

# Environment startup for the TeamCity HashiCorp Vault plugin.
#
# Runs on every environment launch. AIR_STARTUP_MODE=warmup is the
# snapshot-baking run: do the expensive cacheable work (JDK install, Gradle
# distribution + dependency download, plugin build, test Docker images) and
# block on `healthcheck` at the end. Any other mode is a real task run: only
# make sure the toolchain is present and return promptly.

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JDK_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# Released TeamCity version whose artifacts are public on
# download.jetbrains.com; used when no packages.jetbrains.team credentials are
# available (the default teamcityVersion is a SNAPSHOT that only resolves from
# the authenticated internal repository).
FALLBACK_TEAMCITY_VERSION=2025.11

if [ "${AIR_STARTUP_MODE:-}" = "warmup" ]; then WARMUP=1; else WARMUP=; fi

log() { echo "[startup] $*"; }

# --- JDK 17 (Gradle 8.14 cannot run on the preinstalled Java 25) -------------
if [ ! -x "$JDK_HOME/bin/java" ]; then
  log "Installing OpenJDK 17..."
  sudo apt-get update -qq
  sudo apt-get install -y -qq openjdk-17-jdk-headless
fi
log "JDK: $("$JDK_HOME/bin/java" -version 2>&1 | head -1)"

# --- Gradle configuration: proxy + Space packages credentials ----------------
mkdir -p "$HOME/.gradle"
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
touch "$GRADLE_PROPS"

set_prop() { # idempotent upsert of key=value in gradle.properties
  local key="$1" value="$2"
  grep -v "^${key}=" "$GRADLE_PROPS" > "$GRADLE_PROPS.tmp" || true
  printf '%s=%s\n' "$key" "$value" >> "$GRADLE_PROPS.tmp"
  mv "$GRADLE_PROPS.tmp" "$GRADLE_PROPS"
}

# JVMs ignore the http(s)_proxy environment variables, so the egress proxy must
# be passed as system properties: gradle.properties covers the build JVM,
# GRADLE_OPTS covers the wrapper's own distribution download.
GRADLE_PROXY_OPTS=""
proxy_url="${https_proxy:-${HTTPS_PROXY:-}}"
if [ -n "$proxy_url" ]; then
  hostport="${proxy_url#*://}"; hostport="${hostport%%/*}"
  proxy_host="${hostport%%:*}"; proxy_port="${hostport##*:}"
  for scheme in http https; do
    set_prop "systemProp.${scheme}.proxyHost" "$proxy_host"
    set_prop "systemProp.${scheme}.proxyPort" "$proxy_port"
  done
  set_prop "systemProp.http.nonProxyHosts" "localhost|127.0.0.1"
  GRADLE_PROXY_OPTS="-Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port"
  log "Configured Gradle to use egress proxy $proxy_host:$proxy_port"
fi
export GRADLE_OPTS="${GRADLE_OPTS:+$GRADLE_OPTS }$GRADLE_PROXY_OPTS"

# Personal secrets MAVEN_REPOSITORY_USER/MAVEN_REPOSITORY_PASSWORD (a
# packages.jetbrains.team access token) unlock the internal Maven repository:
# needed for the default SNAPSHOT teamcityVersion and the internal test
# artifacts (org.jetbrains.teamcity.internal:integration-test).
HAVE_SPACE_CREDS=
if [ -n "${MAVEN_REPOSITORY_USER:-}" ] && [ -n "${MAVEN_REPOSITORY_PASSWORD:-}" ]; then
  set_prop "maven.repository.user" "$MAVEN_REPOSITORY_USER"
  set_prop "maven.repository.password" "$MAVEN_REPOSITORY_PASSWORD"
  HAVE_SPACE_CREDS=1
  log "Configured packages.jetbrains.team credentials in ~/.gradle/gradle.properties"
else
  log "No MAVEN_REPOSITORY_USER/PASSWORD set: the default (SNAPSHOT) teamcityVersion and"
  log "internal test dependencies will not resolve; build with -PteamcityVersion=$FALLBACK_TEAMCITY_VERSION"
fi

# --- Login-shell environment --------------------------------------------------
# Exports made here die with this script; the agent gets a fresh login shell,
# so persist JAVA_HOME/GRADLE_OPTS via a profile hook.
ENV_FILE="$HOME/.air-repo-env.sh"
{
  printf 'export JAVA_HOME=%q\n' "$JDK_HOME"
  printf 'export PATH="$JAVA_HOME/bin:$PATH"\n'
  if [ -n "$GRADLE_PROXY_OPTS" ]; then
    printf 'export GRADLE_OPTS="${GRADLE_OPTS:+$GRADLE_OPTS }%s"\n' "$GRADLE_PROXY_OPTS"
  fi
} > "$ENV_FILE"

MARKER="# air-repo-env"
add_source_line() {
  grep -qF "$MARKER" "$1" 2>/dev/null || printf '\n%s\n. "%s"\n' "$MARKER" "$ENV_FILE" >> "$1"
}
login_profile=""
for f in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
  [ -f "$f" ] && { login_profile="$f"; break; }
done
[ -n "$login_profile" ] || { login_profile="$HOME/.profile"; touch "$login_profile"; }
add_source_line "$login_profile"
add_source_line "$HOME/.bashrc"

export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$REPO_DIR"
chmod +x ./gradlew

# --- Warmup: prime caches and prove the build ---------------------------------
if [ -n "$WARMUP" ]; then
  log "Warmup: waiting for Docker daemon (tests use testcontainers)..."
  until docker info >/dev/null 2>&1; do
    log "Docker daemon not ready yet, retrying..."
    sleep 5
  done

  log "Warmup: pre-pulling test Docker images..."
  docker pull hashicorp/vault-enterprise:1.4.1_ent || log "WARNING: vault image pull failed; tests will pull on demand"
  docker pull testcontainers/ryuk:0.7.0 || log "WARNING: ryuk image pull failed; tests will pull on demand"

  if [ -n "$HAVE_SPACE_CREDS" ]; then
    log "Warmup: building plugin and test classes with default teamcityVersion..."
    ./gradlew assemble testClasses
  else
    log "Warmup: building plugin with -PteamcityVersion=$FALLBACK_TEAMCITY_VERSION (no internal repo credentials)..."
    ./gradlew assemble -PteamcityVersion="$FALLBACK_TEAMCITY_VERSION"
  fi
  log "Warmup build finished"
fi

# --- Healthcheck ---------------------------------------------------------------
# Asserts the environment works the way a real task needs it to: JDK 17 runs,
# the Docker daemon (required by testcontainers-based tests) is up, and the
# Gradle build actually produced the server and agent plugin archives.
healthcheck() {
  log "healthcheck: verifying JDK..."
  "$JDK_HOME/bin/java" -version

  log "healthcheck: waiting for Docker daemon..."
  until docker info >/dev/null 2>&1; do
    log "healthcheck: Docker daemon not ready yet, retrying..."
    sleep 5
  done

  log "healthcheck: verifying plugin archives..."
  for zip in server/build/distributions/hashicorp-vault-plugin.zip \
             agent/build/distributions/hashicorp-vault-plugin.zip; do
    if [ ! -s "$REPO_DIR/$zip" ]; then
      log "healthcheck FAILED: missing build artifact $zip"
      return 1
    fi
    log "healthcheck: found $zip"
  done
  log "healthcheck passed"
}

if [ -n "$WARMUP" ]; then
  healthcheck
fi

log "Startup complete"
