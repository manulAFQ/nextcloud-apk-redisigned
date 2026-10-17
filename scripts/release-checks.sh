#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/.." && pwd)"
cd "$project_dir"

fail() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

command -v rg >/dev/null 2>&1 || fail "ripgrep (rg) is required"

rg -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml \
    || fail "application backup is not disabled"
rg -q 'android:dataExtractionRules="@xml/data_extraction_rules"' app/src/main/AndroidManifest.xml \
    || fail "data extraction rules are not configured"
rg -q 'isMinifyEnabled = true' app/build.gradle.kts \
    || fail "R8 minification is not enabled for release"
rg -q 'isShrinkResources = true' app/build.gradle.kts \
    || fail "release resource shrinking is not enabled"

if rg -n 'HttpLoggingInterceptor|logging-interceptor' app/src/main app/build.gradle.kts; then
    fail "an HTTP logging interceptor is present in production code"
fi

if rg -n --glob '*.kt' 'android\.util\.Log|\bLog\.(v|d|i|w|e)\(' app/src/main; then
    fail "production logging must be reviewed before release"
fi

signing_material="$(find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name 'keystore.properties' \) \
    -not -path './.gradle/*' -not -path './*/build/*' -print -quit)"
if [[ -n "$signing_material" ]]; then
    fail "signing material must be kept outside the project"
fi

if rg -n -i --glob '*Entity.kt' --glob '*Entities.kt' \
    'password|passwd|credential|secret|token' \
    app/src/main/kotlin/ru/manul/nextcloudgallery/data/local/db; then
    fail "credential-like identifier found in the Room persistence layer"
fi

printf 'Static release boundary checks passed.\n'
