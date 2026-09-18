#!/bin/sh
set -eu

# Runs the IceJA Writer unit tests in parallel mode inside a throwaway copy of
# the project under the system temp directory. The test build therefore never
# touches target/: a running `java -jar target/...jar` would otherwise fail to
# load classes lazily when `mvn clean` deletes the jar (NoClassDefFoundError).
# No ramdisk is required, so the script works on any developer machine.

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
TEST_AREA="$(mktemp -d "${TMPDIR:-/tmp}/iceja-writer-tests.XXXXXX")"
trap 'rm -rf "$TEST_AREA"' EXIT

cp "$PROJECT_ROOT/pom.xml" "$TEST_AREA/pom.xml"
cp -r "$PROJECT_ROOT/src" "$TEST_AREA/src"

cd "$TEST_AREA"

mvn clean test \
    -DforkCount=8 \
    -DreuseForks=true \
    -DthreadCount=4
