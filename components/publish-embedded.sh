#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir"

exec ./gradlew \
    --console=plain \
    --no-daemon \
    --no-configuration-cache \
    -Pcompose.resources.embeddedTargetsOnly=true \
    "$@" \
    :resources:library:publishKotlinMultiplatformPublicationToMavenLocal \
    :resources:library:publishDesktopPublicationToMavenLocal \
    :resources:library:publishLinuxArm64PublicationToMavenLocal
